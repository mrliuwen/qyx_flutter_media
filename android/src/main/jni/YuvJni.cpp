
#include <jni.h>
#include <string>
#include <android/log.h>
#include "libyuv.h"

#define TAG "YuvJni" // 这个是自定义的LOG的标识
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__) // 定义LOGD类型
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__) // 定义LOGI类型
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,TAG ,__VA_ARGS__) // 定义LOGW类型
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG ,__VA_ARGS__) // 定义LOGE类型
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,TAG ,__VA_ARGS__) // 定义LOGF类型

//分别用来存储压缩中间数据
static jbyte *i420_data = NULL;

enum COLOR_FORMATTYPE {
    COLOR_FormatYUV411Planar            = 17,
    COLOR_FormatYUV411PackedPlanar      = 18,
    COLOR_FormatYUV420Planar            = 19,
    COLOR_FormatYUV420PackedPlanar      = 20,
    COLOR_FormatYUV420SemiPlanar        = 21,
    COLOR_FormatYUV422Planar            = 22,
    COLOR_FormatYUV422PackedPlanar      = 23,
    COLOR_FormatYUV422SemiPlanar        = 24,
    COLOR_FormatYCbYCr                  = 25,
    COLOR_FormatYCrYCb                  = 26,
    COLOR_FormatCbYCrY                  = 27,
    COLOR_FormatCrYCbY                  = 28,
    COLOR_FormatYUV444Interleaved       = 29,

    COLOR_TI_FormatYUV420PackedSemiPlanar = 0x7f000100,
    COLOR_QCOM_FormatYUV420SemiPlanar     = 0x7fa30c00
};

void releaseBuffer() {
    if (i420_data) {
        free(i420_data);
        i420_data = NULL;
    }
}

void createBuffer(jint width, jint height) {
    if (i420_data) {
        releaseBuffer();
    }
    i420_data = (jbyte *) malloc(sizeof(jbyte) * width * height * 3 / 2);
}

JNIEXPORT void JNI_OnUnload(JavaVM *jvm, void *reserved) {
    releaseBuffer();
}

void scaleI420(jbyte *src_i420_data, jint width, jint height, jbyte *dst_i420_data, jint dst_width,
               jint dst_height, jint mode) {

    jint src_i420_y_size = width * height;
    jint src_i420_u_size = (width >> 1) * (height >> 1);
    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_i420_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    jint dst_i420_y_size = dst_width * dst_height;
    jint dst_i420_u_size = (dst_width >> 1) * (dst_height >> 1);
    jbyte *dst_i420_y_data = dst_i420_data;
    jbyte *dst_i420_u_data = dst_i420_data + dst_i420_y_size;
    jbyte *dst_i420_v_data = dst_i420_data + dst_i420_y_size + dst_i420_u_size;

    libyuv::I420Scale((const uint8 *) src_i420_y_data, width,
                      (const uint8 *) src_i420_u_data, width >> 1,
                      (const uint8 *) src_i420_v_data, width >> 1,
                      width, height,
                      (uint8 *) dst_i420_y_data, dst_width,
                      (uint8 *) dst_i420_u_data, dst_width >> 1,
                      (uint8 *) dst_i420_v_data, dst_width >> 1,
                      dst_width, dst_height,
                      (libyuv::FilterMode) mode);
}

void rotateI420(jbyte *src_i420_data, jint width, jint height, jbyte *dst_i420_data, jint degree) {
    jint src_i420_y_size = width * height;
    jint src_i420_u_size = (width >> 1) * (height >> 1);

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_i420_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    jbyte *dst_i420_y_data = dst_i420_data;
    jbyte *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    jbyte *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    //要注意这里的width和height在旋转之后是相反的
    if (degree == libyuv::kRotate90 || degree == libyuv::kRotate270) {
        libyuv::I420Rotate((const uint8 *) src_i420_y_data, width,
                           (const uint8 *) src_i420_u_data, width >> 1,
                           (const uint8 *) src_i420_v_data, width >> 1,
                           (uint8 *) dst_i420_y_data, height,
                           (uint8 *) dst_i420_u_data, height >> 1,
                           (uint8 *) dst_i420_v_data, height >> 1,
                           width, height,
                           (libyuv::RotationMode) degree);
    }
}

void mirrorI420(jbyte *src_i420_data, jint width, jint height, jbyte *dst_i420_data) {
    jint src_i420_y_size = width * height;
    jint src_i420_u_size = (width >> 1) * (height >> 1);

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_i420_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_i420_y_size + src_i420_u_size;

    jbyte *dst_i420_y_data = dst_i420_data;
    jbyte *dst_i420_u_data = dst_i420_data + src_i420_y_size;
    jbyte *dst_i420_v_data = dst_i420_data + src_i420_y_size + src_i420_u_size;

    libyuv::I420Mirror((const uint8 *) src_i420_y_data, width,
                       (const uint8 *) src_i420_u_data, width >> 1,
                       (const uint8 *) src_i420_v_data, width >> 1,
                       (uint8 *) dst_i420_y_data, width,
                       (uint8 *) dst_i420_u_data, width >> 1,
                       (uint8 *) dst_i420_v_data, width >> 1,
                       width, height);
}


void nv21ToI420(jbyte *src_nv21_data, jint width, jint height, jbyte *src_i420_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv21_y_data = src_nv21_data;
    jbyte *src_nv21_vu_data = src_nv21_data + src_y_size;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;


    libyuv::NV21ToI420((const uint8 *) src_nv21_y_data, width,
                       (const uint8 *) src_nv21_vu_data, width,
                       (uint8 *) src_i420_y_data, width,
                       (uint8 *) src_i420_u_data, width >> 1,
                       (uint8 *) src_i420_v_data, width >> 1,
                       width, height);
}

void I420ToNv12(jbyte *src_i420_data, jint width, jint height, jbyte *src_nv12_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv12_y_data = src_nv12_data;
    jbyte *src_nv12_uv_data = src_nv12_data + src_y_size;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;


    libyuv::I420ToNV12(
            (const uint8 *) src_i420_y_data, width,
            (const uint8 *) src_i420_u_data, width >> 1,
            (const uint8 *) src_i420_v_data, width >> 1,
            (uint8 *) src_nv12_y_data, width,
            (uint8 *) src_nv12_uv_data, width,
            width, height);
}

void I420ToNv21(jbyte *src_i420_data, jint width, jint height, jbyte *src_nv21_data) {
    jint src_y_size = width * height;
    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv21_y_data = src_nv21_data;
    jbyte *src_nv21_uv_data = src_nv21_data + src_y_size;

    jbyte *src_i420_y_data = src_i420_data;
    jbyte *src_i420_u_data = src_i420_data + src_y_size;
    jbyte *src_i420_v_data = src_i420_data + src_y_size + src_u_size;


    libyuv::I420ToNV21(
            (const uint8 *) src_i420_y_data, width,
            (const uint8 *) src_i420_u_data, width >> 1,
            (const uint8 *) src_i420_v_data, width >> 1,
            (uint8 *) src_nv21_y_data, width,
            (uint8 *) src_nv21_uv_data, width,
            width, height);
}

int isSemiPlanarYUV(int colorFormat) {
    switch (colorFormat) {
        case COLOR_FormatYUV420Planar:
//        case COLOR_FormatYUV420PackedPlanar:
            return 0;
        case COLOR_FormatYUV420SemiPlanar:
//        case COLOR_FormatYUV420PackedSemiPlanar:
//        case COLOR_TI_FormatYUV420PackedSemiPlanar:
            return 1;
        default:
            return 0;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_libyuv_util_YuvUtil_init(JNIEnv *env, jclass type,
                                         jint width,
                                         jint height) {
    LOGD("create buffer width is %d height is %d", width, height);
    createBuffer(width, height);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_libyuv_util_YuvUtil_release(JNIEnv *env, jclass type) {
    LOGD("release yuv convert buffer ");
    releaseBuffer();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_libyuv_util_YuvUtil_compressYUV(JNIEnv *env, jclass type,
                                         jbyteArray src_, jint width,
                                         jint height, jobject dst_,
                                         jint dst_width, jint dst_height,
                                         jint mode, jint destFormat, jint isMirror) {
    //为中间操作需要的分配空间

    jbyte *Src_data = env->GetByteArrayElements(src_, NULL);
    jbyte *Dst_data = (jbyte *)env->GetDirectBufferAddress(dst_);

    /*
    int half_width = (width + 1) / 2;
    int half_height = (height + 1) / 2;
     */

    if (isSemiPlanarYUV(destFormat)) {
        //nv21转化为i420
        nv21ToI420(Src_data, width, height, i420_data);
        if (width != dst_width || height != dst_height) {
            scaleI420(i420_data, width, height, Src_data, dst_width, dst_height, mode); // 中间操作使用Src_data保存数据，避免分配多余空间
            if (isMirror) {
                mirrorI420(Src_data, dst_width, dst_height, i420_data);
                I420ToNv12(i420_data, dst_width, dst_height, Dst_data);
            } else {
                I420ToNv12(Src_data, dst_width, dst_height, Dst_data);
            }
        } else {
            if (isMirror) {
                mirrorI420(i420_data, dst_width, dst_height, Src_data);
                I420ToNv12(Src_data, dst_width, dst_height, Dst_data);
            } else {
                I420ToNv12(i420_data, dst_width, dst_height, Dst_data);
            }
        }
    } else {
        if (width != dst_width || height != dst_height) {
            //nv21转化为i420
            nv21ToI420(Src_data, width, height, i420_data);
            if (isMirror) {
                scaleI420(i420_data, width, height, Src_data, dst_width, dst_height, mode);
                mirrorI420(Src_data, dst_width, dst_height, Dst_data);
            } else {
                scaleI420(i420_data, width, height, Dst_data, dst_width, dst_height, mode);
            }
        } else {
            if (isMirror) {
                //nv21转化为i420
                nv21ToI420(Src_data, width, height, i420_data);
                mirrorI420(i420_data, dst_width, dst_height, Dst_data);
            } else {
                //nv21转化为i420
                nv21ToI420(Src_data, width, height, Dst_data);
            }
        }
    }

    /*
    if (degree) {
        scaleI420(Src_i420_data, width, height, Src_i420_data_scale, dst_height, dst_width, mode);
        rotateI420(Src_i420_data_scale, dst_height, dst_width, Src_i420_data_rotate, degree);
        I420ToNv12(Src_i420_data_rotate, dst_width, dst_height, Dst_data);
    } else {
    }
    if (width != dst_width || height != dst_height) {
        //进行缩放的操作
        scaleI420(Src_i420_data, width, height, Src_i420_data_scale, dst_width, dst_height, mode);
    }
    if (isMirror) {
        //进行旋转的操作
        rotateI420(Src_i420_data, dst_width, dst_height, Src_i420_data_rotate, degree);
        //因为旋转的角度都是90和270，那后面的数据width和height是相反的
        mirrorI420(Src_i420_data_rotate, dst_height, dst_width, Dst_data);
    } else {
        rotateI420(Src_i420_data, dst_width, dst_height, Dst_data, degree);
    }
     */
    env->ReleaseByteArrayElements(src_, Src_data, 0);
//    env->ReleaseByteArrayElements(dst_, Dst_data, 0);
}
