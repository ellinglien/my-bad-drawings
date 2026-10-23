#include <jni.h>
#include <android/bitmap.h>
#include <math.h>
#include <stdint.h>
#include "layers.h"

/*
 * Shows a floating selection over the page in one area of the display: the layers as they
 * are, less what was lifted from the target layer, with the piece's nearest pixels laid
 * into that layer, or above it as a layer of its own. The page itself is never written.
 */
JNIEXPORT void JNICALL
Java_io_github_mpdairy_monopaint_SelectionPreview_nativeApply(JNIEnv *env,jclass clazz,jobject display,
        jobjectArray tones,jobjectArray alpha,jintArray visible,jint target,jboolean newLayer,
        jbyteArray pieceTones,jbyteArray pieceAlpha,jint pieceWidth,jint pieceHeight,
        jboolean lifted,jint cutX,jint cutY,jfloatArray inverse,jintArray dots,jboolean logical,
        jint left,jint top,jint right,jint bottom) {
    (void)clazz;
    AndroidBitmapInfo info;
    Layers layers;
    if(AndroidBitmap_getInfo(env,display,&info)!=ANDROID_BITMAP_RESULT_SUCCESS
            || info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888) { fail(env,"Invalid selection preview");return; }
    if(!layers_find(env,&layers,tones,alpha,(size_t)info.width*info.height))return;
    int count=layers.count;
    if(target<0 || target>=count || (*env)->GetArrayLength(env,visible)!=count
            || pieceWidth<1 || pieceHeight<1
            || (*env)->GetArrayLength(env,pieceTones)!=pieceWidth*pieceHeight
            || (*env)->GetArrayLength(env,pieceAlpha)!=pieceWidth*pieceHeight
            || (*env)->GetArrayLength(env,inverse)!=6 || (*env)->GetArrayLength(env,dots)!=256*64
            || left<0 || top<0 || right>(jint)info.width || bottom>(jint)info.height) {
        fail(env,"Invalid selection preview");return;
    }
    if(left>=right || top>=bottom)return;
    jint opacity[MAX_LAYERS];
    (*env)->GetIntArrayRegion(env,visible,0,count,opacity);
    jfloat m[6];
    (*env)->GetFloatArrayRegion(env,inverse,0,6,m);
    for(int i=0;i<6;i++) if(!isfinite(m[i])) { fail(env,"Invalid selection transform");return; }
    void *pixels=NULL;
    if(AndroidBitmap_lockPixels(env,display,&pixels)!=ANDROID_BITMAP_RESULT_SUCCESS) {
        fail(env,"Cannot write selection preview");return;
    }
    // Lock the bitmap before pinning arrays; nothing below allocates or calls back into Java.
    const uint8_t *ink=(*env)->GetPrimitiveArrayCritical(env,pieceTones,NULL);
    const uint8_t *cover=ink?(*env)->GetPrimitiveArrayCritical(env,pieceAlpha,NULL):NULL;
    jint *pattern=cover?(*env)->GetPrimitiveArrayCritical(env,dots,NULL):NULL;
    int ready=pattern!=NULL && layers_pin(env,&layers);
    const jint *dotted=logical?NULL:pattern;
    if(ready)for(int y=top;y<bottom;y++) {
        uint32_t *out=(uint32_t *)((uint8_t *)pixels+(size_t)y*info.stride);
        size_t row=(size_t)y*info.width;
        // Step along the row in piece coordinates.
        float ux=m[0]*(left+.5f)+m[1]*(y+.5f)+m[2],uy=m[3]*(left+.5f)+m[4]*(y+.5f)+m[5];
        int cutRow=lifted && y>=cutY && y<cutY+pieceHeight;
        for(int x=left;x<right;x++,ux+=m[0],uy+=m[3]) {
            unsigned pieceTone=255,pieceCover=0;
            if(ux>=0 && uy>=0 && ux<pieceWidth && uy<pieceHeight) {
                size_t at=(size_t)(int)uy*pieceWidth+(int)ux;
                pieceTone=ink[at]; pieceCover=cover[at];
            }
            unsigned value=255;
            for(int layer=0;layer<count;layer++) {
                unsigned tone=layers.tones[layer][row+x],coverage=layers.alpha[layer][row+x];
                if(layer==target && !newLayer) {
                    if(cutRow && x>=cutX && x<cutX+pieceWidth && cover[(size_t)(y-cutY)*pieceWidth+(x-cutX)]) { tone=255; coverage=0; }
                    if(pieceCover) {
                        // As ToneDocument.overTone lays it: a sheet over what the layer holds.
                        unsigned kept=coverage*(255-pieceCover),result=pieceCover+(kept+127)/255;
                        unsigned pigment=pieceTone*pieceCover*255+tone*kept;
                        tone=(pigment+result*255/2)/(result*255); if(tone>255)tone=255;
                        coverage=result>255?255:result;
                    }
                }
                if(opacity[layer])value=over(value,tone,coverage,opacity[layer]);
                if(layer==target && newLayer && pieceCover)value=over(value,pieceTone,pieceCover,100);
            }
            out[x]=shown(value,x,y,dotted);
        }
    }
    layers_release(env,&layers);
    if(pattern)(*env)->ReleasePrimitiveArrayCritical(env,dots,pattern,JNI_ABORT);
    if(cover)(*env)->ReleasePrimitiveArrayCritical(env,pieceAlpha,(void *)cover,JNI_ABORT);
    if(ink)(*env)->ReleasePrimitiveArrayCritical(env,pieceTones,(void *)ink,JNI_ABORT);
    AndroidBitmap_unlockPixels(env,display);
}
