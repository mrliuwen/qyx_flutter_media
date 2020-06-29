package com.libyuv.util;

import java.nio.ByteBuffer;

/**
 * Created by zhangmin on 2018/4/13.
 */

public class YuvUtil {

    static {
        System.loadLibrary("yuvutil");
    }

    public static native void init(int width, int height);
    public static native void release();

    /**
     * YUV数据的基本的处理
     *
     * @param src        原始数据
     * @param width      原始的宽
     * @param height     原始的高
     * @param dst        输出数据
     * @param dst_width  输出的宽
     * @param dst_height 输出的高
     * @param mode       压缩模式。这里为0，1，2，3 速度由快到慢，质量由低到高，一般用0就好了，因为0的速度最快
     * @param colorFormat     颜色格式
     * @param isMirror   是否镜像，一般只有270的时候才需要镜像
     **/
    public static native void compressYUV(byte[] src, int width, int height, ByteBuffer dst, int dst_width, int dst_height, int mode, int colorFormat, boolean isMirror);
}
