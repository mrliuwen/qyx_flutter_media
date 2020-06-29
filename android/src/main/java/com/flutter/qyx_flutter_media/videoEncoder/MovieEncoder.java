package com.flutter.qyx_flutter_media.videoEncoder;

import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.flutter.qyx_flutter_media.videoEncoder.video.MP4Builder;
import com.flutter.qyx_flutter_media.videoEncoder.video.Mp4Movie;
import com.libyuv.util.YuvUtil;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;



public class MovieEncoder implements PreviewCallback {
    private static final String TAG = "MovieEncoder";
    private static final boolean VERBOSE = true;           // lots of logging
    // parameters for the encoder
    private final int TIMEOUT_USEC = 2500;

    public final static String VIDEO_MIME_TYPE = "video/avc";
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private final static int PROCESSOR_TYPE_OTHER = 0;
    private final static int PROCESSOR_TYPE_QCOM = 1;
    private final static int PROCESSOR_TYPE_INTEL = 2;
    private final static int PROCESSOR_TYPE_MTK = 3;
    private final static int PROCESSOR_TYPE_SEC = 4;
    private final static int PROCESSOR_TYPE_TI = 5;

    private final Object sync = new Object();
    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;

    private AudioRecord audioRecorder;
    private int audioTrackIndex = -5;
    private int videoTrackIndex = -5;

    private long mStartTime = -1;

    private volatile EncoderHandler handler;

    private MediaCodec.BufferInfo videoBufferInfo;
    private MediaCodec.BufferInfo audioBufferInfo;
    private ByteBuffer[] videoEncoderInputBuffers;
    private ByteBuffer[] videoEncoderOutputBuffers;
    private ArrayList<AudioBufferInfo> buffersToWrite = new ArrayList<>();
    private Mp4Movie movie = null;
    private MP4Builder mediaMuxer = null;
    private MovieObject mMovieObject = null;

    // encode data
    private boolean running = false;
    private boolean ready = false;
    private boolean inputDone = false;
    private int colorFormat = 0;
    private int padding = 0;
    private int mBufferSize = 0;
    private int endBufIndex = -1;

    private EncordeFinishCallback callback;

    private static  class LazyHolder {
        static  final MovieEncoder INSTANCE = new MovieEncoder();
    }

    private MovieEncoder () {}

    static public MovieEncoder getInstance() {
        return LazyHolder.INSTANCE;
    }

    public interface EncordeFinishCallback {
        void onEncodeFinish();
    }

    public void startRecording(int previewWidth, int previewHeight, int outputWidth, int outputHeight, int rotate, boolean flip, String path) {
        if ((outputWidth % 16) != 0 || (outputHeight % 16) != 0) {
            Log.e(TAG, "ERROR: width or height not multiple of 16");
        }
        MovieObject movie = new MovieObject();
        movie.originalWidth = previewWidth;
        movie.originalHeight = previewHeight;
        movie.resultHeight = outputHeight;
        movie.resultWidth = outputWidth;
        movie.originalPath = path;
        movie.rotationValue = rotate;
        movie.flip = flip;
        movie.bitrate = outputHeight * outputWidth * 6;
        mMovieObject = movie;
        synchronized (sync) {
            if (running) {
                return;
            }
            running = true;
            MovieEncodeRunnable.runEncode(this);
            while (!ready) {
                try {
                    sync.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }
        handler.sendMessage(handler.obtainMessage(MSG_START_RECORDING));
    }

    public void stopRecording(EncordeFinishCallback cb) {
        if (!running) {
            return;
        }
        handler.sendMessage(handler.obtainMessage(MSG_STOP_RECORDING));
        callback = cb;
    }

    public boolean recording () {
        return running;
    }

    public static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        MediaCodecInfo lastCodecInfo = null;
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    lastCodecInfo = codecInfo;
                    if (!lastCodecInfo.getName().equals("OMX.SEC.avc.enc")) {
                        return lastCodecInfo;
                    } else if (lastCodecInfo.getName().equals("OMX.SEC.AVC.Encoder")) {
                        return lastCodecInfo;
                    }
                }
            }
        }
        return lastCodecInfo;
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    public static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        int lastColorFormat = 0;
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                lastColorFormat = colorFormat;
                if (!(codecInfo.getName().equals("OMX.SEC.AVC.Encoder") && colorFormat == 19)) {
                    return colorFormat;
                }
            }
        }
        return lastColorFormat;
    }

    private class AudioBufferInfo {
        byte[] buffer = new byte[2048 * 10];
        long[] offset = new long[10];
        int[] read = new int[10];
        int results;
        int lastWroteBuffer;
        boolean last;
    }

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_VIDEOFRAME_AVAILABLE = 2;
    private static final int MSG_AUDIOFRAME_AVAILABLE = 3;

    private static class EncoderHandler extends Handler {
        private WeakReference<MovieEncoder> mWeakEncoder;

        public EncoderHandler(MovieEncoder encoder) {
            mWeakEncoder = new WeakReference<>(encoder);
        }

        @Override
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;

            MovieEncoder encoder = mWeakEncoder.get();
            if (encoder == null) {
                return;
            }

            switch (what) {
                case MSG_START_RECORDING: {
                    try {
                        Log.e(TAG, "start encoder");
                        encoder.prepareEncoder();
                    } catch (Exception e) {
                        e.printStackTrace();
                        encoder.handleStopRecording();
                        Looper.myLooper().quit();
                    }
                    break;
                }
                case MSG_STOP_RECORDING: {
                    Log.e(TAG, "stop encoder");
                    encoder.handleStopRecording();
                    break;
                }
                case MSG_VIDEOFRAME_AVAILABLE: {
                    try {
                        encoder.handleVideoFrameAvalibale();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case MSG_AUDIOFRAME_AVAILABLE: {
                    encoder.handleAudioFrameAvailable((AudioBufferInfo) inputMessage.obj);
                    break;
                }
            }
        }

        public void exit() {
            Looper.myLooper().quit();
        }
    }

    private static class MovieEncodeRunnable implements Runnable {
        MovieEncoder mEncoder;
        private MovieEncodeRunnable(MovieEncoder encoder) {
            mEncoder = encoder;
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (mEncoder.sync) {
                mEncoder.handler = new EncoderHandler(mEncoder);
                mEncoder.ready = true;
                mEncoder.sync.notify();
            }
            Looper.loop();

            synchronized (mEncoder.sync) {
                mEncoder.ready = false;
            }
        }

        public static void runEncode(final MovieEncoder encoder) {
            MovieEncodeRunnable wrapper = new MovieEncodeRunnable(encoder);
            Thread th = new Thread(wrapper, "MovieEncodeRunnable");
            th.start();
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (inputDone) {
            return;
        }
        inputVideoData(data);
        camera.addCallbackBuffer(data);
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime() {
        if (mStartTime == -1) {
            mStartTime = System.nanoTime();
            return 0;
        }
        long endTime = System.nanoTime();
        return (endTime - mStartTime) / 1000;
    }

    private ArrayBlockingQueue<AudioBufferInfo> buffers = new ArrayBlockingQueue<>(10);

    private Runnable recorderRunnable = new Runnable() {

        @Override
        public void run() {
            long audioPresentationTimeNs;
            int readResult;
            boolean done = false;
            while (!done) {
                if (mStartTime == -1) {
                    Log.v(TAG, "ignore audio");
                    continue;
                }
                if (!running && audioRecorder.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED) {
                    try {
                        audioRecorder.stop();
                    } catch (Exception e) {
                        done = true;
                    }
                }
                AudioBufferInfo buffer;
                if (buffers.isEmpty()) {
                    buffer = new AudioBufferInfo();
                } else {
                    buffer = buffers.poll();
                }
                buffer.lastWroteBuffer = 0;
                buffer.results = 10;
                for (int a = 0; a < 10; a++) {
                    audioPresentationTimeNs = System.nanoTime();
                    readResult = audioRecorder.read(buffer.buffer, a * 2048, 2048);
                    if (readResult <= 0) {
                        buffer.results = a;
                        if (!running) {
                            buffer.last = true;
                        }
                        break;
                    }
                    buffer.offset[a] = audioPresentationTimeNs;
                    buffer.read[a] = readResult;
                }
                if (buffer.results >= 0 || buffer.last) {
                    if (!running && buffer.results < 10) {
                        done = true;
                    }
                    handler.sendMessage(handler.obtainMessage(MSG_AUDIOFRAME_AVAILABLE, buffer));
                } else {
                    if (!running) {
                        done = true;
                    } else {
                        try {
                            buffers.put(buffer);
                        } catch (Exception ignore) {

                        }
                    }
                }
                Log.v(TAG, "record audio");
            }
            try {
                audioRecorder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            handler.sendMessage(handler.obtainMessage(MSG_STOP_RECORDING));
            Log.v(TAG, "stop recording");
        }
    };

    private void handleStopRecording() {
        if (running) {
            running = false;
            return;
        }
        try {
            drainEncoder(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (videoEncoder != null) {
            try {
                videoEncoder.stop();
                videoEncoder.release();
                videoEncoder = null;
                videoEncoderInputBuffers = null;
                videoEncoderOutputBuffers = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (audioEncoder != null) {
            try {
                audioEncoder.stop();
                audioEncoder.release();
                audioEncoder = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mediaMuxer != null) {
            try {
                mediaMuxer.finishMovie();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaMuxer = null;
        }

        videoBufferInfo = null;
        audioBufferInfo = null;
        running = false;
        ready = false;
        inputDone = false;
        colorFormat = 0;
        movie = null;
        mMovieObject = null;
        /*
        AudioBufferInfo buffer = buffers.poll();
        while (buffer != null) {
            buffer = buffers.poll();
        }
        */
        YuvUtil.release();
        handler.exit();
        if (callback != null) {
            callback.onEncodeFinish();
        }
    }

    private void prepareEncoder() {
        int resultWidth = mMovieObject.resultWidth;
        int resultHeight = mMovieObject.resultHeight;
        int rotationValue = mMovieObject.rotationValue;
        int originalWidth = mMovieObject.originalWidth;
        int originalHeight = mMovieObject.originalHeight;
        int bitrate = mMovieObject.bitrate;
        File cacheFile = new File(mMovieObject.originalPath);
        YuvUtil.init(originalWidth, originalHeight);

        if (rotationValue == 90 || rotationValue == 270) {
            int temp = resultHeight;
            resultHeight = resultWidth;
            resultWidth = temp;
        }

        try {
            boolean swapUV = false;

            // movie builder
            movie = new Mp4Movie();
            movie.setCacheFile(cacheFile);
            movie.setRotation(rotationValue);
            movie.setSize(resultWidth, resultHeight);
            mediaMuxer = new MP4Builder().createMovie(movie);

            // bufferInfo
            videoBufferInfo = new MediaCodec.BufferInfo();
            audioBufferInfo = new MediaCodec.BufferInfo();

            // video encoder
            mStartTime = -1;
            int processorType = PROCESSOR_TYPE_OTHER;
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            MediaCodecInfo codecInfo = selectCodec(VIDEO_MIME_TYPE);
            colorFormat = selectColorFormat(codecInfo, VIDEO_MIME_TYPE);
            if (colorFormat == 0) {
                throw new RuntimeException("no supported color format");
            }
            String codecName = codecInfo.getName();
            if (codecName.contains("OMX.qcom.")) {
                processorType = PROCESSOR_TYPE_QCOM;
                if (Build.VERSION.SDK_INT == 16) {
                    if (manufacturer.equals("lge") || manufacturer.equals("nokia")) {
                        swapUV = true;
                    }
                }
            } else if (codecName.contains("OMX.Intel.")) {
                processorType = PROCESSOR_TYPE_INTEL;
            } else if (codecName.equals("OMX.MTK.VIDEO.ENCODER.AVC")) {
                processorType = PROCESSOR_TYPE_MTK;
            } else if (codecName.equals("OMX.SEC.AVC.Encoder")) {
                processorType = PROCESSOR_TYPE_SEC;
                swapUV = true;
            } else if (codecName.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                processorType = PROCESSOR_TYPE_TI;
            }
            Log.e(TAG, "codec = " + codecInfo.getName() + " manufacturer = " + manufacturer + "device = " + Build.MODEL);
            Log.e(TAG, "colorFormat = " + colorFormat);

            int resultHeightAligned = resultHeight;
            padding = 0;
            mBufferSize = resultWidth * resultHeight * 3 / 2;
//            if (processorType == PROCESSOR_TYPE_OTHER) {
//                if (resultHeight % 16 != 0) {
//                    resultHeightAligned += (16 - (resultHeight % 16));
//                    padding = resultWidth * (resultHeightAligned - resultHeight);
//                    mBufferSize += padding * 5 / 4;
//                }
//            } else if (processorType == PROCESSOR_TYPE_QCOM) {
//                if (!manufacturer.toLowerCase().equals("lge")) {
//                    int uvoffset = (resultWidth * resultHeight + 2047) & ~2047;
//                    padding = uvoffset - (resultWidth * resultHeight);
//                    mBufferSize += padding;
//                }
//            } else if (processorType == PROCESSOR_TYPE_TI) {
//                    /*
//                    resultHeightAligned = 368;
//                    mBufferSize = resultWidth * resultHeightAligned * 3 / 2;
//                    resultHeightAligned += (16 - (resultHeight % 16));
//                    padding = resultWidth * (resultHeightAligned - resultHeight);
//                    mBufferSize += padding * 5 / 4;
//                    */
//            } else if (processorType == PROCESSOR_TYPE_MTK) {
//                if (manufacturer.equals("baidu")) {
//                    resultHeightAligned += (16 - (resultHeight % 16));
//                    padding = resultWidth * (resultHeightAligned - resultHeight);
//                    mBufferSize += padding * 5 / 4;
//                }
//            }

            MediaFormat outputFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, resultWidth, resultHeight);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate > 0 ? bitrate : 921600);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
            // 这里的设置会导致oppo录制花屏
                /*
                outputFormat.setInteger("stride", resultWidth + 32);
                outputFormat.setInteger("slice-height", resultHeight);
                */

            videoTrackIndex = -5;
            endBufIndex = -1;
            videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            videoEncoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoEncoder.start();

            videoEncoderInputBuffers = videoEncoder.getInputBuffers();
            videoEncoderOutputBuffers = videoEncoder.getOutputBuffers();

            // audio recorder
            int recordBufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (recordBufferSize <= 0) {
                recordBufferSize = 3584;
            }
            int bufferSize = 2048 * 24;
            if (bufferSize < recordBufferSize) {
                bufferSize = ((recordBufferSize / 2048) + 1) * 2048 * 2;
            }
            for (int a = 0; a < 3; a++) {
                buffers.add(new AudioBufferInfo());
            }
            audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioRecorder.startRecording();
            new Thread(recorderRunnable, "AudioRecorderRunnable").start();

            // audio encoder
            audioTrackIndex = -5;
             MediaFormat audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, 44100, 1);
            // MediaFormat audioFormat = new MediaFormat();
            audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 32000);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2048 * 10);

            audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void inputVideoData (byte[] input) {
        if (videoEncoderInputBuffers == null) {
            return;
        }

        int resultWidth = mMovieObject.resultWidth;
        int resultHeight = mMovieObject.resultHeight;
        int originalWidth = mMovieObject.originalWidth;
        int originalHeight = mMovieObject.originalHeight;
        int rotationValue = mMovieObject.rotationValue;
        if (rotationValue == 90 || rotationValue == 270) {
            int temp = resultHeight;
            resultHeight = resultWidth;
            resultWidth = temp;
        }
        try {
            int inputBufIndex = videoEncoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (VERBOSE) Log.d(TAG, "inputBufIndex=" + inputBufIndex);
            if (inputBufIndex >= 0) {
                long ptsUsec = computePresentationTime();
                if (!running) {
                    endBufIndex = inputBufIndex;
                    inputDone = true;
                } else {
                    //进行yuv数据颜色格式转换及缩放操作
                    long start = System.nanoTime();
                    ByteBuffer inputBuf = videoEncoderInputBuffers[inputBufIndex];
                    inputBuf.clear();
                    // the buffer should be sized to hold one full frame
                    //assertTrue(inputBuf.capacity() >= mBufferSize);
                    YuvUtil.compressYUV(input, originalWidth, originalHeight, inputBuf, resultWidth, resultHeight, 0, colorFormat, mMovieObject.flip);
                    if (VERBOSE) Log.e(TAG, "compress time" + (System.nanoTime() - start) / 1000000.0);

                    videoEncoder.queueInputBuffer(inputBufIndex, 0, mBufferSize, ptsUsec, 0);
                    handler.sendMessage(handler.obtainMessage(MSG_VIDEOFRAME_AVAILABLE));
                }
            } else {
                // either all in use, or we timed out during initial setup
                if (VERBOSE) Log.d(TAG, "input buffer not available");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleVideoFrameAvalibale() {
        try {
            drainEncoder(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleAudioFrameAvailable(AudioBufferInfo input) {
        if (mStartTime == -1) {
            return;
        }
        buffersToWrite.add(input);
        if (buffersToWrite.size() > 1) {
            input = buffersToWrite.get(0);
        }
        try {
            drainEncoder(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            boolean isLast = false;
            while (input != null) {
                int inputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer;
                    if (Build.VERSION.SDK_INT >= 21) {
                        inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex);
                    } else {
                        ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();
                        inputBuffer = inputBuffers[inputBufferIndex];
                        inputBuffer.clear();
                    }
                    long startWriteTime = input.offset[input.lastWroteBuffer];
                    for (int a = input.lastWroteBuffer; a <= input.results; a++) {
                        if (a < input.results) {
                            if (inputBuffer.remaining() < input.read[a]) {
                                input.lastWroteBuffer = a;
                                input = null;
                                break;
                            }
                            inputBuffer.put(input.buffer, a * 2048, input.read[a]);
                        }
                        if (a >= input.results - 1) {
                            buffersToWrite.remove(input);
                            if (running) {
                                buffers.put(input);
                            }
                            if (!buffersToWrite.isEmpty()) {
                                input = buffersToWrite.get(0);
                            } else {
                                isLast = input.last;
                                input = null;
                                break;
                            }
                        }
                    }
                    audioEncoder.queueInputBuffer(inputBufferIndex, 0, inputBuffer.position(), startWriteTime == 0 ? 0 : (startWriteTime - mStartTime) / 1000, isLast ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void drainEncoder(boolean endOfStream) throws Exception {
        int videoHeight = mMovieObject.resultHeight;
        int videoWidth = mMovieObject.resultWidth;
        int originalWidth = mMovieObject.originalWidth;
        int originalHeight = mMovieObject.originalHeight;

        if (videoHeight > videoWidth && videoWidth != originalWidth && videoHeight != originalHeight) {
            int temp = videoHeight;
            videoHeight = videoWidth;
            videoWidth = temp;
        }

        if (endOfStream && endBufIndex != -1) {
            long ptsUsec = computePresentationTime();
            videoEncoder.queueInputBuffer(endBufIndex, 0, 0, ptsUsec, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
        while (true) {
            int encoderStatus = videoEncoder.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break;
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                videoEncoderOutputBuffers = videoEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = videoEncoder.getOutputFormat();
                if (videoTrackIndex == -5) {
                    videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                }
            } else if (encoderStatus >= 0) {
                ByteBuffer encodedData = videoEncoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                if (videoBufferInfo.size > 1) {
                    if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        if (mediaMuxer.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo, true)) {
                        }
                    } else if (videoTrackIndex == -5) {
                        byte[] csd = new byte[videoBufferInfo.size];
                        encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size);
                        encodedData.position(videoBufferInfo.offset);
                        encodedData.get(csd);
                        ByteBuffer sps = null;
                        ByteBuffer pps = null;
                        for (int a = videoBufferInfo.size - 1; a >= 0; a--) {
                            if (a > 3) {
                                if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                    sps = ByteBuffer.allocate(a - 3);
                                    pps = ByteBuffer.allocate(videoBufferInfo.size - (a - 3));
                                    sps.put(csd, 0, a - 3).position(0);
                                    pps.put(csd, a - 3, videoBufferInfo.size - (a - 3)).position(0);
                                    break;
                                }
                            } else {
                                break;
                            }
                        }

                        MediaFormat newFormat = MediaFormat.createVideoFormat("video/avc", videoWidth, videoHeight);
                        if (sps != null && pps != null) {
                            newFormat.setByteBuffer("csd-0", sps);
                            newFormat.setByteBuffer("csd-1", pps);
                        }
                        videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                    }
                }
                videoEncoder.releaseOutputBuffer(encoderStatus, false);
                if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }

        ByteBuffer[] encoderOutputBuffers = null;
        if (Build.VERSION.SDK_INT < 21) {
            encoderOutputBuffers = audioEncoder.getOutputBuffers();
        }
        while (true) {
            int encoderStatus = audioEncoder.dequeueOutputBuffer(audioBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream || !running) {
                    break;
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (Build.VERSION.SDK_INT < 21) {
                    encoderOutputBuffers = audioEncoder.getOutputBuffers();
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = audioEncoder.getOutputFormat();
                if (audioTrackIndex == -5) {
                    audioTrackIndex = mediaMuxer.addTrack(newFormat, true);
                }
            } else if (encoderStatus >= 0) {
                ByteBuffer encodedData;
                if (Build.VERSION.SDK_INT < 21) {
                    encodedData = encoderOutputBuffers[encoderStatus];
                } else {
                    encodedData = audioEncoder.getOutputBuffer(encoderStatus);
                }
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    audioBufferInfo.size = 0;
                }
                if (audioBufferInfo.size != 0) {
                    if (mediaMuxer.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo, false)) {
                    }
                }
                audioEncoder.releaseOutputBuffer(encoderStatus, false);
                if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }

}
