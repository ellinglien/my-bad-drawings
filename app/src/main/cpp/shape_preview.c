#include <jni.h>
#include <android/bitmap.h>
#include <stdint.h>
#include "layers.h"

typedef struct {
    const uint8_t **tones,**alpha;
    int visible[MAX_LAYERS],count,active,shade,width;
    uint8_t *pixels;
    uint32_t stride;
    const jint *dots;
    int left,top,right,bottom;
} Raster;

static void span(Raster *r,int y,int left,int right,int paint) {
    if(left<0)left=0;
    if(right>r->width)right=r->width;
    if(left>=right)return;
    if(left<r->left)r->left=left;
    if(right>r->right)r->right=right;
    if(y<r->top)r->top=y;
    if(y+1>r->bottom)r->bottom=y+1;
    uint32_t *out=(uint32_t *)(r->pixels+(size_t)y*r->stride);
    int offset=y*r->width;
    for(int x=left;x<right;x++) {
        int opaque=paint && r->shade>=0 && r->visible[r->active]==100;
        int value=opaque?r->shade:255;
        for(int layer=opaque?r->active+1:0;layer<r->count;layer++) {
            if(!r->visible[layer] || (paint && r->shade<0 && layer==r->active))continue;
            int replacement=paint && layer==r->active;
            value=over(value,replacement?r->shade:r->tones[layer][offset+x],
                    replacement?255:r->alpha[layer][offset+x],r->visible[layer]);
        }
        out[x]=shown(value,x,y,r->dots);
    }
}
static void difference(Raster *r,int y,const jint *source,const jint *subtract,int paint) {
    for(int part=0;part<4;part+=2) {
        int left=source[part],right=source[part+1];
        if(left>=right)continue;
        for(int cut=0;cut<4 && left<right;cut+=2) {
            int a=subtract[cut],b=subtract[cut+1];
            if(a>=b || b<=left || a>=right)continue;
            if(a>left)span(r,y,left,a,paint);
            if(b>left)left=b;
        }
        if(left<right)span(r,y,left,right,paint);
    }
}

JNIEXPORT void JNICALL
Java_io_github_mpdairy_monopaint_ShapePreview_nativeApply(JNIEnv *env,jclass clazz,jobject display,
        jobjectArray tones,jobjectArray alpha,jintArray visible,jint active,jint shade,
        jintArray previous,jintArray next,jintArray dots,jboolean logical,jintArray dirty) {
    (void)clazz;
    AndroidBitmapInfo info;
    Layers layers;
    if(AndroidBitmap_getInfo(env,display,&info)!=ANDROID_BITMAP_RESULT_SUCCESS
            || info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888) { fail(env,"Invalid shape preview");return; }
    if(!layers_find(env,&layers,tones,alpha,(size_t)info.width*info.height))return;
    int count=layers.count;
    if(active<0 || active>=count || shade< -1 || shade>255 || (*env)->GetArrayLength(env,visible)!=count
            || (*env)->GetArrayLength(env,previous)!=(int)info.height*4
            || (*env)->GetArrayLength(env,next)!=(int)info.height*4
            || (*env)->GetArrayLength(env,dots)!=256*64 || (*env)->GetArrayLength(env,dirty)!=4) {
        fail(env,"Invalid shape preview");return;
    }
    Raster r={.tones=layers.tones,.alpha=layers.alpha,.count=count,.active=active,.shade=shade,.width=(int)info.width,
              .stride=info.stride,.left=(int)info.width,.top=(int)info.height};
    (*env)->GetIntArrayRegion(env,visible,0,count,r.visible);
    void *pixels=NULL;
    if(AndroidBitmap_lockPixels(env,display,&pixels)!=ANDROID_BITMAP_RESULT_SUCCESS) {
        fail(env,"Cannot write shape preview");return;
    }
    r.pixels=pixels;
    // Get metadata/lock the bitmap before entering nested critical array sections.
    // No allocation, blocking, or arbitrary JNI calls occur while the arrays are pinned.
    jint *old=(*env)->GetPrimitiveArrayCritical(env,previous,NULL);
    jint *current=old?(*env)->GetPrimitiveArrayCritical(env,next,NULL):NULL;
    jint *pattern=current?(*env)->GetPrimitiveArrayCritical(env,dots,NULL):NULL;
    int ready=pattern!=NULL && layers_pin(env,&layers);
    r.dots=logical?NULL:pattern;
    if(ready)for(int y=0;y<(int)info.height;y++) {
        difference(&r,y,old+y*4,current+y*4,0);
        difference(&r,y,current+y*4,old+y*4,1);
    }
    layers_release(env,&layers);
    if(pattern)(*env)->ReleasePrimitiveArrayCritical(env,dots,pattern,JNI_ABORT);
    if(current)(*env)->ReleasePrimitiveArrayCritical(env,next,current,JNI_ABORT);
    if(old)(*env)->ReleasePrimitiveArrayCritical(env,previous,old,JNI_ABORT);
    AndroidBitmap_unlockPixels(env,display);
    if(!ready)return;
    jint bounds[4]={r.left,r.top,r.right,r.bottom};
    (*env)->SetIntArrayRegion(env,dirty,0,4,bounds);
}
