package com.flutter.qyx_flutter_media;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Permission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static android.app.Activity.RESULT_OK;

/**
 * QyxFlutterMediaPlugin
 */
public class QyxFlutterMediaPlugin implements MethodCallHandler,PluginRegistry.ActivityResultListener {
    private static Registrar registrarG;
    private static PermissionDelegate delegate;

    private static Result takeVideoResult;
    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        registrarG = registrar;
        delegate = new PermissionDelegate(registrarG.activity());
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "qyx_flutter_media");
        QyxFlutterMediaPlugin instance = new QyxFlutterMediaPlugin();
        registrarG.addActivityResultListener(instance);

        channel.setMethodCallHandler(instance);


    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        } else if (call.method.equals("takeVideo")) {
            takeVideo(result);
        } else {
            result.notImplemented();
        }
    }

    public void takeVideo(Result result) {
        takeVideoResult =  result;
        ArrayList<ImageSize> size = new ArrayList<ImageSize>();
        Intent getImageByCamera = new Intent(
                registrarG.activity(), CameraActivity.class);
        size.add(new ImageSize(240, 240, ".small"));
        size.add(new ImageSize(720, 720, ""));
        getImageByCamera.putExtra("supportType",0);
        Bundle bundle = new Bundle();
        bundle.putSerializable("size", size);
        getImageByCamera.putExtras(bundle);
        registrarG.activity().startActivityForResult(getImageByCamera,0);
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.i("onActivityResult-----","onActivityResultdddd");
        if(resultCode == RESULT_OK) {
            final String recorderPath =  intent.getStringExtra("recorderPath");
            final String photoPath =  intent.getStringExtra("photoPath");
            final int  time = intent.getIntExtra("time",0);
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(recorderPath);
            //获取第一帧图像的bitmap对象
            Bitmap bitmap = mmr.getFrameAtTime();
            final String screenshot_path = saveCropBitmap(bitmap);
            takeVideoResult.success(new HashMap(){
                {
                    put("cover_path",screenshot_path);
                    put("photo_path",photoPath);
                    put("video_path",recorderPath);
                    put("time",time);
                }
            });
        }
        return true;
    }

    public String saveCropBitmap(Bitmap bmp) {
        if(bmp == null) {
            return "";
        } else {
            File file = null;
            Bitmap.CompressFormat format = Bitmap.CompressFormat.JPEG;
            int quality = 100;
            FileOutputStream stream = null;

            try {
                file = new  File(getAlbumDir(), "cover_tmp.jpg");
                stream = new FileOutputStream(file);
            } catch (IOException var7) {
                var7.printStackTrace();
            }

            return bmp.compress(format, quality, stream)?file.getAbsolutePath():"";
        }
    }
    public File getAlbumDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),  "/big");
        if(!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }
}