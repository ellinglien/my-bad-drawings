#include <jni.h>
#include <android/bitmap.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <time.h>
#include <stdlib.h>
#include <unistd.h>
#include "panel_end.h"

typedef struct {
    int fd;
    uint8_t *pixels;
    size_t length;
    int width, height, stride;
    int offset;
    uint8_t *previous;
    int canvas_width, canvas_height, x, y;
    uint8_t request_flags, display_mode;
} Display;

// Installed Manta/Nomad HT driver ABI, confirmed against Atelier's region update.
// These are device interface declarations, not vendor implementation code.
typedef struct {
    int32_t left, top, right, bottom;
    int32_t offset;
    uint8_t mode, flags, reserved, padding;
} Update;
_Static_assert(sizeof(Update)==24,"HT region ABI");

static void fail(JNIEnv *env, const char *message) {
    jclass type=(*env)->FindClass(env,"java/lang/IllegalStateException");
    if (type) (*env)->ThrowNew(env,type,message);
}

// Restrict the experiment to queried layouts, never guess an ABI from Android's
// model property (the Manta incorrectly says Nomad). Supporting another panel
// means adding a row here, observed on the device itself. Which plane the
// firmware's binary pen path (mode 9/flags 1) uses is a firmware fact the
// caller passes in; see Device.fastBinaryPlane.
typedef struct {
    int width, height, stride, size;
} Layout;
static const Layout LAYOUTS[]={
    // Manta: the buffer matches the portrait screen.
    {1920,2560,1920,4915200},
    // Nomad: the panel's landscape scan order (firmware hwrota=270), observed
    // from the shared plane on 2026-10-05.
    {1872,1404,1872,2628288},
};

static const Layout *supported_layout(const int32_t *info) {
    int width=(uint32_t)info[1]&0xffff, height=(uint32_t)info[1]>>16;
    for (size_t i=0;i<sizeof(LAYOUTS)/sizeof(*LAYOUTS);i++) {
        const Layout *l=&LAYOUTS[i];
        if (l->width==width && l->height==height && l->stride==info[2] && l->size==info[3]) return l;
    }
    return NULL;
}

// The supported driver buffer as {width, height}, or null when unsupported.
JNIEXPORT jintArray JNICALL
Java_io_github_mpdairy_monopaint_DirectEink_nativeLayout(JNIEnv *env, jclass clazz) {
    (void)clazz;
    int fd=open("/dev/ebc",O_RDWR|O_CLOEXEC);
    if (fd<0) return NULL;
    int32_t info[32]={0};
    const Layout *l=ioctl(fd,0x48545201UL,info)==0 ? supported_layout(info) : NULL;
    close(fd);
    if (!l) return NULL;
    jint values[2]={l->width,l->height};
    jintArray result=(*env)->NewIntArray(env,2);
    if (result) (*env)->SetIntArrayRegion(env,result,0,2,values);
    return result;
}

JNIEXPORT jlong JNICALL
Java_io_github_mpdairy_monopaint_DirectEink_nativeOpen(JNIEnv *env, jclass clazz,
        jobject background,jint x,jint y,jint request_flags,jint display_mode,jint plane) {
    (void)clazz;
    // Compare only the two settings observed in installed display clients.
    if (request_flags!=0 && request_flags!=1) {
        fail(env,"Unsupported display request flags"); return 0;
    }
    // The pen presenter uses mode 9/flags 1 on plane 0 or 1, depending on the
    // firmware. Only exact binary control pixels are supported on that path;
    // mode 7 retains gray drawing on plane 0. Inkwash's strokes: mode 5/flags 0
    // on plane 0, binary only too (StrokePanel; measured on the Manta).
    if (display_mode!=7 && !(display_mode==9 && request_flags==1) && !(display_mode==5 && request_flags==0)) {
        fail(env,"Unsupported display mode"); return 0;
    }
    if (display_mode==9 ? plane!=0 && plane!=1 : plane!=0) {
        fail(env,"Fast binary controls are not supported on this firmware"); return 0;
    }
    int fd=open("/dev/ebc",O_RDWR|O_CLOEXEC);
    if (fd<0) { fail(env,strerror(errno)); return 0; }
    int32_t info[32]={0};
    if (ioctl(fd,0x48545201UL,info)!=0) {
        int error=errno; close(fd); fail(env,strerror(error)); return 0;
    }
    const Layout *layout=supported_layout(info);
    if (!layout) {
        close(fd); fail(env,"Unsupported display buffer layout"); return 0;
    }
    int width=layout->width,height=layout->height;
    AndroidBitmapInfo bitmap_info;
    if (AndroidBitmap_getInfo(env,background,&bitmap_info)!=ANDROID_BITMAP_RESULT_SUCCESS
            || bitmap_info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888
            || bitmap_info.width==0 || bitmap_info.height==0
            || bitmap_info.width>(uint32_t)width || bitmap_info.height>(uint32_t)height
            || x<0 || y<0 || x>width-(int)bitmap_info.width || y>height-(int)bitmap_info.height) {
        close(fd); fail(env,"Display background outside app canvas"); return 0;
    }
    size_t length=((size_t)info[3]*3+95)&~(size_t)63;
    uint8_t *pixels=mmap(NULL,length,PROT_READ|PROT_WRITE,MAP_SHARED,fd,0);
    if (pixels==MAP_FAILED) {
        int error=errno; close(fd); fail(env,strerror(error)); return 0;
    }
    Display *d=calloc(1,sizeof(*d));
    if (!d) { munmap(pixels,length); close(fd); fail(env,"Display allocation failed"); return 0; }
    d->fd=fd; d->pixels=pixels; d->length=length;
    d->width=width; d->height=height; d->stride=info[2];
    d->offset=plane*info[3];
    d->canvas_width=bitmap_info.width; d->canvas_height=bitmap_info.height; d->x=x; d->y=y;
    d->request_flags=(uint8_t)request_flags;
    d->display_mode=(uint8_t)display_mode;
    d->previous=malloc((size_t)d->canvas_width*d->canvas_height);
    void *source=NULL;
    if (!d->previous || AndroidBitmap_lockPixels(env,background,&source)!=ANDROID_BITMAP_RESULT_SUCCESS) {
        free(d->previous); munmap(pixels,length); close(fd); free(d);
        fail(env,"Cannot prepare previous display pixels"); return 0;
    }
    // Seed our private comparison copy. The compositor also uses the mapped
    // plane, so a one-time initialization of it would not stay authoritative.
    // Each submitted region must copy its own background/border below.
    for (int row=0;row<d->canvas_height;row++) {
        const uint8_t *src=(const uint8_t *)source+(size_t)row*bitmap_info.stride;
        uint8_t *dst=d->previous+(size_t)row*d->canvas_width;
        for (int column=0;column<d->canvas_width;column++,src+=4) *dst++=src[0]>>4;
    }
    AndroidBitmap_unlockPixels(env,background);
    return (jlong)(intptr_t)d;
}

/* The last present's parts, ns: finding a change (and checking a fast plane's pixels), copying into the plane, and the
 * driver call. Read with nativePresentParts, on the thread that presents. */
static int64_t part_compare, part_copy, part_ioctl;
static int64_t now_ns(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC,&t); return (int64_t)t.tv_sec*1000000000LL+t.tv_nsec; }

JNIEXPORT void JNICALL
Java_io_github_mpdairy_monopaint_DirectEink_nativePresentParts(JNIEnv *env,jclass clazz,jlongArray out) {
    (void)clazz;
    jlong parts[3]={part_compare,part_copy,part_ioctl};
    (*env)->SetLongArrayRegion(env,out,0,3,parts);
}

JNIEXPORT jint JNICALL
Java_io_github_mpdairy_monopaint_DirectEink_nativePresent(JNIEnv *env, jclass clazz,
        jlong handle,jobject bitmap,jint left,jint top,jint right,jint bottom,jint ox,jint oy,jboolean force) {
    (void)clazz;
    Display *d=(Display *)(intptr_t)handle;
    AndroidBitmapInfo info;
    if (!d || AndroidBitmap_getInfo(env,bitmap,&info)!=ANDROID_BITMAP_RESULT_SUCCESS
            || info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888) {
        fail(env,"Invalid display bitmap/session"); return -1;
    }
    // Reject before locking or writing. Entire app canvas must fit the screen.
    if (ox!=d->x || oy!=d->y || info.width!=(uint32_t)d->canvas_width || info.height!=(uint32_t)d->canvas_height
            || ox<0 || oy<0 || info.width>(uint32_t)d->width || info.height>(uint32_t)d->height
            || ox>d->width-(int)info.width || oy>d->height-(int)info.height
            || left<0 || top<0 || right<=left || bottom<=top
            || (uint32_t)right>info.width || (uint32_t)bottom>info.height) {
        fail(env,"Display region outside app canvas"); return -1;
    }
    void *source=NULL;
    part_compare=part_copy=part_ioctl=0;
    int64_t started=now_ns();
    if (AndroidBitmap_lockPixels(env,bitmap,&source)!=ANDROID_BITMAP_RESULT_SUCCESS) {
        fail(env,"Cannot lock display bitmap"); return -1;
    }
    // Do not threshold grays or submit partially converted data to the pen
    // plane. The caller can use normal Android presentation on rejection.
    if (d->display_mode==9 || d->display_mode==5) {
        for (int y=top;y<bottom;y++) {
            const uint8_t *src=(const uint8_t *)source+(size_t)y*info.stride+(size_t)left*4;
            for (int x=left;x<right;x++,src+=4) {
                if (src[3]!=255 || (src[0]!=0 && src[0]!=255)
                        || src[1]!=src[0] || src[2]!=src[0]) {
                    AndroidBitmap_unlockPixels(env,bitmap);
                    fail(env,"Fast control pixels must be opaque black or white"); return -1;
                }
            }
        }
    }
    int changed=force==JNI_TRUE;
    for (int y=top;y<bottom && !changed;y++) {
        const uint8_t *src=(const uint8_t *)source+(size_t)y*info.stride+(size_t)left*4;
        const uint8_t *old=d->previous+(size_t)y*d->canvas_width+left;
        for (int x=left;x<right;x++,src+=4,old++) {
            if ((src[0]>>4)!=*old) { changed=1; break; }
        }
    }
    int64_t compared=now_ns();
    part_compare=compared-started;
    if (!changed) {
        AndroidBitmap_unlockPixels(env,bitmap); return 0;
    }
    // Preserve the rasterizer's clean border. Tight changed-pixel cropping
    // in 0.7 coincided with physical horizontal/vertical edge artifacts.
    // Keep no-op suppression, without assuming single-pixel refresh bounds.
    for (int y=top;y<bottom;y++) {
        const uint8_t *src=(const uint8_t *)source+(size_t)y*info.stride+(size_t)left*4;
        uint8_t *dst=d->pixels+d->offset+(size_t)(y+oy)*d->stride+left+ox;
        for (int x=left;x<right;x++,src+=4) *dst++=src[0]>>4;
    }
    __sync_synchronize();
    int64_t copied=now_ns();
    part_copy=copied-compared;
    Update update={.left=left+ox,.top=top+oy,.right=right+ox,.bottom=bottom+oy,
                   .offset=d->offset,.mode=d->display_mode,.flags=d->request_flags};
    int result=ioctl(d->fd,0x48545701UL,&update), error=errno;
    part_ioctl=now_ns()-copied;
    if (result>=0) {
        for (int y=top;y<bottom;y++) {
            const uint8_t *src=(const uint8_t *)source+(size_t)y*info.stride+(size_t)left*4;
            uint8_t *old=d->previous+(size_t)y*d->canvas_width+left;
            for (int x=left;x<right;x++,src+=4) *old++=src[0]>>4;
        }
    }
    AndroidBitmap_unlockPixels(env,bitmap);
    if (result>=0) return 1;
    // Busy display queues apply backpressure. Keep accumulating the dirty
    // region and retry on the next sample; never sleep on the pen input thread.
    // ENOMEM too: the Manta's driver answers it while a firmware refresh (a popup
    // closing, an auto-refresh) still holds its buffers, and the next try goes
    // through (logcat, 2026-10-19: every "Direct display failed: Out of memory"
    // came within 2 s of one). DisplayRecovery still gives up after 2 s busy.
    if (error!=EAGAIN && error!=EINTR && error!=ENOMEM) fail(env,strerror(error));
    return -1;
}

/* panel_end's submit: a gray update of the session, canvas pixels placed on the panel. Busy, it pauses a ms first so
 * a submit again (panel_end's second update must go) doesn't spin. */
static int end_submit(void *ctx, int left, int top, int right, int bottom) {
    Display *d=(Display *)ctx;
    Update u={.left=left+d->x,.top=top+d->y,.right=right+d->x,.bottom=bottom+d->y,.offset=d->offset,.mode=d->display_mode,.flags=d->request_flags};
    int result=ioctl(d->fd,0x48545701UL,&u);
    if (result>=0) return result;
    int error=errno;
    if (error==ENOMEM) error=EAGAIN;   // busy, as in nativePresent
    if (error==EAGAIN || error==EINTR) usleep(1000);
    return -error;
}

/* Ends a fast session's showing over a region through this gray session, so every pixel the fast one changed is driven
 * again (panel_end.h). 1 when done; -1 while the display is busy (nothing submitted: try again); -2 when the sessions
 * don't share a plane and a place, or the bitmap isn't the session's (the caller refreshes instead). */
JNIEXPORT jint JNICALL
Java_io_github_mpdairy_monopaint_DirectEink_nativeEndFast(JNIEnv *env,jclass clazz,jlong gray_handle,jlong fast_handle,jobject bitmap,
        jint left,jint top,jint right,jint bottom,jboolean near) {
    (void)clazz;
    Display *g=(Display *)(intptr_t)gray_handle, *a=(Display *)(intptr_t)fast_handle;
    AndroidBitmapInfo info; void *source=NULL;
    if (!g || !a || g->offset!=a->offset || g->x!=a->x || g->y!=a->y
            || g->canvas_width!=a->canvas_width || g->canvas_height!=a->canvas_height
            || AndroidBitmap_getInfo(env,bitmap,&info)!=ANDROID_BITMAP_RESULT_SUCCESS || info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888
            || info.width!=(uint32_t)g->canvas_width || info.height!=(uint32_t)g->canvas_height
            || left<0 || top<0 || right>(int)info.width || bottom>(int)info.height || right<=left || bottom<=top) return -2;
    if (AndroidBitmap_lockPixels(env,bitmap,&source)!=ANDROID_BITMAP_RESULT_SUCCESS) return -2;
    int result=panel_end((const uint8_t *)source,info.stride,a->previous,g->previous,g->canvas_width,
            g->pixels+g->offset+(size_t)g->y*g->stride+g->x,g->stride,left,top,right,bottom,
            end_submit,g,near==JNI_TRUE,NULL);
    AndroidBitmap_unlockPixels(env,bitmap);
    if (result>=0) return 1;
    if (result!=-EAGAIN && result!=-EINTR) { fail(env,strerror(-result)); return -1; }
    return -1;
}

JNIEXPORT void JNICALL
Java_io_github_mpdairy_monopaint_DirectEink_nativeClose(JNIEnv *env,jclass clazz,jlong handle) {
    (void)env; (void)clazz;
    Display *d=(Display *)(intptr_t)handle;
    if (!d) return;
    munmap(d->pixels,d->length); close(d->fd); free(d->previous); free(d);
}

JNIEXPORT jint JNICALL
Java_io_github_mpdairy_monopaint_DirectEink_nativeReadGray(JNIEnv *env,jclass clazz,jlong handle,jint x,jint y) {
    (void)clazz;
    Display *d=(Display *)(intptr_t)handle;
    if (!d || x<0 || y<0 || x>=d->width || y>=d->height) {
        fail(env,"Invalid display sample"); return -1;
    }
    return d->pixels[d->offset+(size_t)y*d->stride+x];
}

// Device-info query used by the installed Atelier's repaintC constructor.
// This capability probe makes no display writes and changes no permissions.
JNIEXPORT jstring JNICALL
Java_io_github_mpdairy_monopaint_DirectEink_probe(JNIEnv *env, jclass clazz) {
    (void)clazz;
    char result[512];
    int fd = open("/dev/ebc", O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        snprintf(result,sizeof(result),"Display device open denied/unavailable: %s",strerror(errno));
    } else {
        int32_t info[32] = {0};
        if (ioctl(fd,0x48545201UL,info) != 0) {
            snprintf(result,sizeof(result),"Display info unavailable: %s",strerror(errno));
        } else {
            snprintf(result,sizeof(result),"Display info: %d %d %d %d %d %d %d %d",
                     info[0],info[1],info[2],info[3],info[4],info[5],info[6],info[7]);
        }
        close(fd);
    }
    return (*env)->NewStringUTF(env,result);
}
