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
public class QyxFlutterMediaPlugin implements MethodCallHandler, PluginRegistry.ActivityResultListener {
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
            takeVideo(call,result);
        } else {
            result.notImplemented();
        }
    }

    PermissionListener listener;

    public void takeVideo(final MethodCall call, Result result) {
        takeVideoResult = result;
        registrarG.addRequestPermissionsResultListener(delegate);
        if (listener == null) {
            listener = new PermissionListener() {
                @Override
                public void onRequestPermissionsResult(boolean permissionGranted) {
                    if (!permissionGranted) {
                        return;
                    }
                    delegate.requestPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, new PermissionListener() {
                        @Override
                        public void onRequestPermissionsResult(boolean permissionGranted) {
                            if (!permissionGranted) {
                                return;
                            }
                            delegate.requestPermissions(Manifest.permission.RECORD_AUDIO, new PermissionListener() {
                                @Override
                                public void onRequestPermissionsResult(boolean permissionGranted) {
                                    if (!permissionGranted) {
                                        return;
                                    }
                                    delegate.requestPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, new PermissionListener() {
                                        @Override
                                        public void onRequestPermissionsResult(boolean permissionGranted) {
                                            if (!permissionGranted) {
                                                return;
                                            }
                                            if (listener == null) {
                                                return;
                                            }
                                            if (call.method.equals("takeVideo")){
                                                Log.e("call.method====",call.method);
                                                ArrayList<ImageSize> size = new ArrayList<ImageSize>();
                                                Intent getImageByCamera = new Intent(
                                                        registrarG.activity(), CameraActivity.class);
                                                size.add(new ImageSize(240, 240, ".small"));
                                                size.add(new ImageSize(720, 720, ""));
                                                getImageByCamera.putExtra("supportType", 0);
                                                Bundle bundle = new Bundle();
                                                bundle.putSerializable("size", size);
                                                getImageByCamera.putExtras(bundle);
                                                registrarG.activity().startActivityForResult(getImageByCamera, 10001);
                                                listener = null;
                                            }
                                        }
                                    });
                                }
                            });
                        }
                    });
                }
            };
        }
        delegate.requestPermissions(listener);

    }


    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {

        if (resultCode == RESULT_OK && requestCode == 10001) {
            final String recorderPath = intent.getStringExtra("recorderPath");
            final String photoPath = intent.getStringExtra("photoPath");
            takeVideoResult.success(new HashMap() {
                {
                    put("photo_path", photoPath);
                    put("video_path", recorderPath);
                }
            });
            return true;
        }
        return false;
    }

    public String saveCropBitmap(Bitmap bmp) {
        if (bmp == null) {
            return "";
        } else {
            File file = null;
            Bitmap.CompressFormat format = Bitmap.CompressFormat.JPEG;
            int quality = 100;
            FileOutputStream stream = null;

            try {
                file = new File(getAlbumDir(), "cover_tmp.jpg");
                stream = new FileOutputStream(file);
            } catch (IOException var7) {
                var7.printStackTrace();
            }

            return bmp.compress(format, quality, stream) ? file.getAbsolutePath() : "";
        }
    }

    public File getAlbumDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "/big");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }
}
