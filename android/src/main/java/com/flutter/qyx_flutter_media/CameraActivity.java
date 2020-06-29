package com.flutter.qyx_flutter_media;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.flutter.qyx_flutter_media.R;

import com.flutter.qyx_flutter_media.videoEncoder.MovieEncoder;

import java.io.File;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * 拍摄界面
 */
public class CameraActivity extends Activity implements View.OnClickListener {
    /**
     * 获取相册
     */
    public static final int REQUEST_PHOTO = 1;
    /**
     * 获取视频
     */
    public static final int REQUEST_VIDEO = 2;
    /**
     * 最小录制时间
     */
    private static final int MIN_RECORD_TIME = 1 * 1000;
    /**
     * 最长录制时间
     */
    private static final int MAX_RECORD_TIME = 10 * 1000;
    /**
     * 刷新进度的间隔时间
     */
    private static final int PLUSH_PROGRESS = 100;

    private Context mContext;
    /**
     * TextureView
     */
    private TextureView mTextureView;
    /**
     * 带手势识别
     */
    private CameraView mCameraView;
    /**
     * 录制按钮
     */
    private CameraProgressBar mProgressbar;
    /**
     *  顶部像机设置
     */
    private RelativeLayout rl_camera;
    /**
     * 关闭,选择,前后置
     */
    private ImageView iv_close, iv_choice, iv_facing,iv_back;
    /**
     * 闪光
     */
    private TextView tv_flash;
    /**
     * camera manager
     */
    private CameraManager cameraManager;
    /**
     * player manager
     */
    private MediaPlayerManager playerManager;
    /**
     * true代表视频录制,否则拍照
     */
    private boolean isSupportRecord;

    // 0.所有 1.拍照 2.视频
    private int supportType;
    public static int SUPPORT_TYPE_ALL = 0;
    public static int SUPPORT_TYPE_PHOTO = 1;
    public static int SUPPORT_TYPE_VIDEO = 2;
    /**
     * 视频录制地址
     */
    private String recorderPath, photoPath;
    /**
     * 获取照片订阅, 进度订阅
     */
    private Subscription takePhotoSubscription, progressSubscription;
    /**
     * 是否正在录制
     */
    private boolean isRecording, isResume;

    //录音时长
    public int time;

    public static void lanuchForPhoto(Activity context) {
        Intent intent = new Intent(context, CameraActivity.class);
        context.startActivityForResult(intent, REQUEST_PHOTO);
    }

    protected int getLayoutId() {
        return R.layout.activity_camera;
    }

    @Override
    protected void onCreate( Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutId());
        supportType = getIntent().getIntExtra("supportType",0);
        if (supportType==SUPPORT_TYPE_ALL) {
            isSupportRecord = false;
        }else if(supportType==SUPPORT_TYPE_PHOTO){
            isSupportRecord = false;
        }else if(supportType==SUPPORT_TYPE_VIDEO){
            isSupportRecord = true;
        }
        mContext = this;
        mTextureView =  findViewById(R.id.mTextureView);
        mCameraView =  findViewById(R.id.mCameraView);
        mProgressbar =  findViewById(R.id.mProgressbar);
        mProgressbar.setLongOn(supportType==SUPPORT_TYPE_PHOTO?false:true);
        rl_camera =  findViewById(R.id.rl_camera);
        iv_close =  findViewById(R.id.iv_close);
        iv_close.setOnClickListener(this);
        iv_back = findViewById(R.id.iv_back);
        iv_back.setOnClickListener(this);
        iv_choice =  findViewById(R.id.iv_choice);
        iv_choice.setOnClickListener(this);
        iv_close.setOnClickListener(this);
        iv_facing =  findViewById(R.id.iv_facing);
        iv_facing.setOnClickListener(this);
        iv_close.setOnClickListener(this);
        tv_flash =  findViewById(R.id.tv_flash);
        tv_flash.setOnClickListener(this);

        initData();

    }

    protected void initData() {
        cameraManager = CameraManager.getInstance(getApplication());
        playerManager = MediaPlayerManager.getInstance(getApplication());
        cameraManager.setCameraType(isSupportRecord ? 1 : 0);

        tv_flash.setVisibility(cameraManager.isSupportFlashCamera() ? View.VISIBLE : View.GONE);
        setCameraFlashState();
        iv_facing.setVisibility(cameraManager.isSupportFrontCamera() ? View.VISIBLE : View.GONE);
        rl_camera.setVisibility(cameraManager.isSupportFlashCamera()
                || cameraManager.isSupportFrontCamera() ? View.VISIBLE : View.GONE);

        final int max = MAX_RECORD_TIME / PLUSH_PROGRESS;
        mProgressbar.setMaxProgress(max);

        mProgressbar.setOnProgressTouchListener(new CameraProgressBar.OnProgressTouchListener() {
            @Override
            public void onClick(CameraProgressBar progressBar) {
                if (supportType==SUPPORT_TYPE_ALL||supportType==SUPPORT_TYPE_PHOTO){
                    cameraManager.takePhoto(callback);
                }

            }

            @Override
            public void onLongClick(CameraProgressBar progressBar) {
                if (supportType==SUPPORT_TYPE_ALL||supportType==SUPPORT_TYPE_VIDEO){
                    startRecorder(max);
                }

            }

            @Override
            public void onZoom(boolean zoom) {
                
            }

            @Override
            public void onLongClickUp(CameraProgressBar progressBar) {
                if (supportType==SUPPORT_TYPE_ALL||supportType==SUPPORT_TYPE_VIDEO){
                    if (progressSubscription != null) {
                        progressSubscription.unsubscribe();
                    }
                    int recordSecond = mProgressbar.getProgress() * PLUSH_PROGRESS;//录制多少毫秒
                    time = recordSecond;
                    mProgressbar.reset();
                    if (recordSecond < MIN_RECORD_TIME) {//小于最小录制时间作废
                        Toast.makeText(mContext, "录制时间不可小1秒", Toast.LENGTH_SHORT).show();
                        if (recorderPath != null) {
                            FileUtils.delteFiles(new File(recorderPath));
                            recorderPath = null;
                        }
                        setTakeButtonShow(true);
                    } else if (isResume && mTextureView != null && mTextureView.isAvailable()){
                        finishRecorder();
                        return;
                    }
                    stopRecorder();
                }

            }

            @Override
            public void onPointerDown(float rawX, float rawY) {
                if (mTextureView != null) {
                    mCameraView.setFoucsPoint(new PointF(rawX, rawY));
                }
            }
        });

        mCameraView.setOnViewTouchListener(new CameraView.OnViewTouchListener() {
            @Override
            public void handleFocus(float x, float y) {
                cameraManager.handleFocusMetering(x, y);
            }

            @Override
            public void handleZoom(boolean zoom) {
                cameraManager.handleZoom(zoom);
            }
        });
    }

    /**
     * 设置闪光状态
     */
    private void setCameraFlashState() {
        int flashState = cameraManager.getCameraFlash();
        switch (flashState) {
            case 0: //自动
                tv_flash.setSelected(true);
                tv_flash.setText("自动");
                break;
            case 1://open
                tv_flash.setSelected(true);
                tv_flash.setText("开启");
                break;
            case 2: //close
                tv_flash.setSelected(false);
                tv_flash.setText("关闭");
                break;
        }
    }

    /**
     * 是否显示录制按钮
     * @param isShow
     */
    private void setTakeButtonShow(boolean isShow) {
        if (isShow) {
            mProgressbar.setVisibility(View.VISIBLE);
            iv_choice.setVisibility(View.GONE);
            iv_close.setVisibility(View.VISIBLE);
            iv_back.setVisibility(View.GONE);
            rl_camera.setVisibility(cameraManager.isSupportFlashCamera()
                    || cameraManager.isSupportFrontCamera() ? View.VISIBLE : View.GONE);
        } else {
            mProgressbar.setVisibility(View.GONE);
            iv_back.setVisibility(View.VISIBLE);
            iv_choice.setVisibility(View.VISIBLE);
            rl_camera.setVisibility(View.GONE);
            iv_close.setVisibility(View.GONE);
        }
    }

    /**
     * 停止录制
     */
    private void stopRecorder() {
        cameraManager.stopMediaRecord(null);
        isRecording = false;
    }

    private void finishRecorder() {
        LoadingUtils.getInstanceAndShow(CameraActivity.this, "处理中");
        cameraManager.stopMediaRecord(new MovieEncoder.EncordeFinishCallback() {
            @Override
            public void onEncodeFinish() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        LoadingUtils.hideLoading();
                        if (isResume && mTextureView != null && mTextureView.isAvailable()){
                            setTakeButtonShow(false);
                            cameraManager.closeCamera();
                            playerManager.playMedia(new Surface(mTextureView.getSurfaceTexture()), recorderPath);
                        }
                    }
                });
            }
        });
        isRecording = false;
    }

    /**
     * 开始录制
     */
    private void startRecorder(int max) {
        try {
          recorderPath = FileUtils.getUploadVideoFile(mContext);
            cameraManager.startMediaRecord(recorderPath);
            isRecording = true;
            time = 0;
            progressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                    .take(max).subscribe(new Subscriber<Long>() {
                        @Override
                        public void onCompleted() {
                            mProgressbar.reset();
                            finishRecorder();
                        }

                        @Override
                        public void onError(Throwable e) {
                        }

                        @Override
                        public void onNext(Long aLong) {
                            mProgressbar.setProgress(mProgressbar.getProgress() + 1);
                        }
                    });
        } catch (Exception e) {
            Toast.makeText(mContext, "没有权限...", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResume = true;
        isRecording = false;
        if (mTextureView.isAvailable()) {
            if (recorderPath != null) {
                setTakeButtonShow(false);
                playerManager.playMedia(new Surface(mTextureView.getSurfaceTexture()), recorderPath);
            } else {
                openCamera(mTextureView.getSurfaceTexture(), mTextureView.getWidth(), mTextureView.getHeight());
            }
        } else {
            mTextureView.setSurfaceTextureListener(listener);
        }
    }

    @Override
    protected void onPause() {
        isResume = false;
        if (isRecording) {
            stopRecorder();
            if (progressSubscription != null) {
                progressSubscription.unsubscribe();
            }
            mProgressbar.reset();
            FileUtils.delteFiles(new File(recorderPath));
            recorderPath = null;
        }
        if (takePhotoSubscription != null) {
            takePhotoSubscription.unsubscribe();
        }
        photoPath = null;
        cameraManager.closeCamera();
        playerManager.stopMedia();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mCameraView.removeOnZoomListener();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.iv_close) {
            if (backClose()) {
                return;
            }
            finish();

        } else if (i == R.id.iv_back) {
            if (backClose()) {
                return;
            }
            finish();

        } else if (i == R.id.iv_choice) {
            Log.i("---dddzz----","---jjj----");
            if (!onlyClose()) {
                finish();
            }
            Log.i("---result----","---result----");
            Intent intent = new Intent();
            if (!TextUtils.isEmpty(photoPath)) {
                String tempPhotoPath = new File(photoPath).getAbsolutePath();
                intent.putExtra("photoPath", tempPhotoPath);
                setResult(RESULT_OK, intent);
                finish();
            }
            if (!TextUtils.isEmpty(recorderPath)) {
                String tempRecorderPath = new File(recorderPath).getAbsolutePath();
                intent.putExtra("recorderPath", tempRecorderPath);
                intent.putExtra("time", time);
                setResult(RESULT_OK, intent);
                finish();
            }

       /*        List<File> files = new ArrayList<File>();
               if (recorderPath!=null){
                   Log.i("recorderPath:",recorderPath);
                  files.add(new File(recorderPath));
               }
                if (photoPath!=null){
                    Log.i("photoPath:",photoPath);
                    files.add(new File(photoPath));
             }

                FileUploadUtils.fileUpload(files,(long)time);*/

        } else if (i == R.id.tv_flash) {
            cameraManager.changeCameraFlash(mTextureView.getSurfaceTexture(),
                    mTextureView.getWidth(), mTextureView.getHeight());
            setCameraFlashState();

        } else if (i == R.id.iv_facing) {
            cameraManager.changeCameraFacing(mTextureView.getSurfaceTexture(),
                    mTextureView.getWidth(), mTextureView.getHeight());

        }
    }

    private boolean onlyClose(){
        if (recorderPath != null) {//正在录制或正在播放
            if (isRecording) {//正在录制
                stopRecorder();
                if (progressSubscription != null) {
                    progressSubscription.unsubscribe();
                }
                mProgressbar.reset();
                return true;
            }
            playerManager.stopMedia();
            return true;
        }
        if (photoPath != null) {//有拍照
            cameraManager.closeCamera();
     setTakeButtonShow(true);
            return true;
        }
        return false;
    }
    /**
     * 返回关闭界面
     */
    private boolean backClose() {
        if (recorderPath != null) {//正在录制或正在播放
            if (isRecording) {//正在录制
                stopRecorder();
                if (progressSubscription != null) {
                    progressSubscription.unsubscribe();
                }
                mProgressbar.reset();
                FileUtils.delteFiles(new File(recorderPath));
                recorderPath = null;
                if (mTextureView != null && mTextureView.isAvailable()) {
                    openCamera(mTextureView.getSurfaceTexture(), mTextureView.getWidth(), mTextureView.getHeight());
                }
                return true;
            }
            playerManager.stopMedia();
            FileUtils.delteFiles(new File(recorderPath));
            recorderPath = null;
            if (mTextureView != null && mTextureView.isAvailable()) {
                openCamera(mTextureView.getSurfaceTexture(), mTextureView.getWidth(), mTextureView.getHeight());
            }
            return true;
        }
        if (photoPath != null) {//有拍照
            photoPath = null;//有需求也可以删除
            cameraManager.restartPreview();
            setTakeButtonShow(true);
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (backClose()) {
            return;
        }
        super.onBackPressed();
    }

    /**
     * 开启照相机
     * @param texture
     * @param width
     * @param height
     */
    private void openCamera(SurfaceTexture texture, int width, int height) {
        setTakeButtonShow(true);
        try {
            cameraManager.openCamera(texture, width, height);
        } catch (RuntimeException e) {
            Toast.makeText(mContext, "没有权限...", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * camera回调监听
     */
    private TextureView.SurfaceTextureListener listener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            if (recorderPath != null) {
                setTakeButtonShow(false);
                playerManager.playMedia(new Surface(texture), recorderPath);
            } else {
                openCamera(texture, width, height);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    private Camera.PictureCallback callback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(final byte[] data, Camera camera) {
            setTakeButtonShow(false);
            takePhotoSubscription = Observable.create(new Observable.OnSubscribe<Boolean>() {
                @Override
                public void call(Subscriber<? super Boolean> subscriber) {
                    if (!subscriber.isUnsubscribed()) {
                        photoPath = FileUtils.getUploadPhotoFile(mContext);
                        subscriber.onNext(FileUtils.savePhoto(photoPath, data, cameraManager.isCameraFrontFacing()));
                    }
                }
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(new Subscriber<Boolean>() {
                @Override
                public void onCompleted() {

                }

                @Override
                public void onError(Throwable e) {

                }

                @Override
                public void onNext(Boolean aBoolean) {
                    if (aBoolean != null && aBoolean) {
                        iv_choice.setVisibility(View.VISIBLE);
                    } else {
                        setTakeButtonShow(true);
                    }
                }
            });
        }
    };

}
