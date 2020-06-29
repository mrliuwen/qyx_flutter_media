package com.flutter.qyx_flutter_media;

import android.content.Intent;

import io.flutter.plugin.common.PluginRegistry;

/**
 * Created by tt on 2019/6/11.
 */

interface  PermissionManager {
    boolean isPermissionGranted(String permissionName);

    void askForPermission(String permissionName, int requestCode);

    boolean needRequestCameraPermission();
}
