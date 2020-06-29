package com.flutter.qyx_flutter_media;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

/**
 * Created by tt on 2019/6/11.
 */

interface PermissionListener {
   public void onRequestPermissionsResult(boolean permissionGranted);
}

public class PermissionDelegate
        implements  PluginRegistry.RequestPermissionsResultListener {

    private PermissionListener listener;
    @VisibleForTesting
    static final int REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY = 2342;
    @VisibleForTesting
    static final int REQUEST_CODE_TAKE_IMAGE_WITH_CAMERA = 2343;
    @VisibleForTesting
    static final int REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION = 2344;
    @VisibleForTesting
    static final int REQUEST_CAMERA_IMAGE_PERMISSION = 2345;
    @VisibleForTesting
    static final int REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY = 2352;
    @VisibleForTesting
    static final int REQUEST_CODE_TAKE_VIDEO_WITH_CAMERA = 2353;
    @VisibleForTesting
    static final int REQUEST_EXTERNAL_VIDEO_STORAGE_PERMISSION = 2354;
    @VisibleForTesting
    static final int REQUEST_CAMERA_VIDEO_PERMISSION = 2355;


    interface PermissionManager {
        boolean isPermissionGranted(String permissionName);

        void askForPermission(String permissionName, int requestCode);

        boolean needRequestCameraPermission();
    }


    interface OnPathReadyListener {
        void onPathReady(String path);
    }

    private Uri pendingCameraMediaUri;
    private MethodChannel.Result pendingResult;
    private MethodCall methodCall;

    public PermissionManager permissionManager;

    public PermissionDelegate(
            final Activity activity) {
        permissionManager = new PermissionManager() {
            @Override
            public boolean isPermissionGranted(String permissionName) {
                return ActivityCompat.checkSelfPermission(activity, permissionName)
                        == PackageManager.PERMISSION_GRANTED;
            }

            @Override
            public void askForPermission(String permissionName, int requestCode) {
                ActivityCompat.requestPermissions(activity, new String[]{permissionName}, requestCode);
            }

            @Override
            public boolean needRequestCameraPermission() {
                return ImagePickerUtils.needRequestCameraPermission(activity);
            }
        };
    }


    @Override
    public boolean onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        boolean permissionGranted =
                grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        Log.i("permissionGranted--", String.valueOf(permissionGranted));
        if(this.listener!=null) {
            this.listener.onRequestPermissionsResult(permissionGranted);
        }

        switch (requestCode) {
            case REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION:
                if (permissionGranted) {
                }
                break;
            case REQUEST_EXTERNAL_VIDEO_STORAGE_PERMISSION:
                if (permissionGranted) {
                }
                break;
            case REQUEST_CAMERA_IMAGE_PERMISSION:
                if (permissionGranted) {
                }
                break;
            case REQUEST_CAMERA_VIDEO_PERMISSION:
                if (permissionGranted) {
                }
                break;
            default:
                return false;
        }

        if (!permissionGranted) {
           // finishWithSuccess(null);
        }

        return true;
    }

    void requestPermissions(PermissionListener listener) {
        this.listener = listener;
        if (permissionManager.needRequestCameraPermission()
                && !permissionManager.isPermissionGranted(Manifest.permission.CAMERA)) {
            permissionManager.askForPermission(
                    Manifest.permission.CAMERA, REQUEST_CAMERA_VIDEO_PERMISSION);
            return;
        }
        listener.onRequestPermissionsResult(true);
    }

    void requestPermissions(String permission, PermissionListener listener) {
        this.listener = listener;
        if (permissionManager.needRequestCameraPermission()
                && !permissionManager.isPermissionGranted(permission)) {
            permissionManager.askForPermission(
                    permission, 0);
            return;
        }
        listener.onRequestPermissionsResult(true);
    }

}