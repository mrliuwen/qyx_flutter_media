package com.flutter.qyx_flutter_media;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.flutter.qyx_flutter_media.BuildConfig;
import com.flutter.qyx_flutter_media.R;
import com.tbruyelle.rxpermissions2.Permission;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import androidx.core.app.ActivityCompat;
import androidx.core.content.PermissionChecker;
import io.reactivex.functions.Consumer;

/**
 * 权限控制工具类：
 */
public class PermissionsUtil {

    public static final String[] permissionList = new String[]{
            Manifest.permission.READ_CALENDAR,          //读取日历
            Manifest.permission.WRITE_CALENDAR,         //写入日历

            Manifest.permission.CAMERA,                 //照相机

            Manifest.permission.WRITE_CONTACTS,         //写入通讯录
            Manifest.permission.GET_ACCOUNTS,           //访问通讯录权限
            Manifest.permission.READ_CONTACTS,          //读取通讯录

            Manifest.permission.ACCESS_FINE_LOCATION,   //获取位置
            Manifest.permission.ACCESS_COARSE_LOCATION, //获取粗略定位

            Manifest.permission.RECORD_AUDIO,           //录音

            Manifest.permission.READ_CALL_LOG,          //看电话记录
            Manifest.permission.READ_PHONE_STATE,       //读取手机状态
            Manifest.permission.CALL_PHONE,             //打电话
            Manifest.permission.WRITE_CALL_LOG,         //编写调用日志
            Manifest.permission.PROCESS_OUTGOING_CALLS, //过程输出调用
            Manifest.permission.ADD_VOICEMAIL,          //添加语音信箱

            Manifest.permission.BODY_SENSORS,           //体传感器

            Manifest.permission.READ_SMS,               //读取信息
            Manifest.permission.RECEIVE_WAP_PUSH,       //收到WAP推送
            Manifest.permission.RECEIVE_MMS,            //接收彩信
            Manifest.permission.RECEIVE_SMS,            //收信息
            Manifest.permission.SEND_SMS,               //发信息

            Manifest.permission.READ_EXTERNAL_STORAGE,  //读取外部存储器 　
            Manifest.permission.WRITE_EXTERNAL_STORAGE, //写外部存储器
    };

    public static final int REQUEST_STATUS_CODE = 0x001;
    public static final int REQUEST_PERMISSION_SETTING = 0x002;
    private static final String TAG = "PermissionsUtil";

    public static boolean selfPermissionGranted(Context context, String permission) {
        // For Android < Android M, self permissions are always granted.

        int targetSdk = 0;
        try {
            final PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            targetSdk = info.applicationInfo.targetSdkVersion;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        boolean result = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (targetSdk >= Build.VERSION_CODES.M) {
                // targetSdkVersion >= Android M, we can
                // use Context#checkSelfPermission
                result = context.checkSelfPermission(permission)
                        == PackageManager.PERMISSION_GRANTED;
            } else {
                // targetSdkVersion < Android M, we have to use PermissionChecker
                result = PermissionChecker.checkSelfPermission(context, permission)
                        == PermissionChecker.PERMISSION_GRANTED;
            }
        }
        return result;
    }

    /**
     * 对权限字符串数组中的所有权限进行申请授权，如果用户选择了“dont ask me again”，则不会弹出系统的Permission申请授权对话框
     */
    @Deprecated
    public static void requestPermissions(Activity activity, String[] permissions) {
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_STATUS_CODE);
    }

    public interface PermisionResultListener {
        void granted();
    }

    //单个或多个动态权限申请方法
    static int num;

    public static void requestPermissions(final Activity activity, final String permissionName, final PermisionResultListener listener, final String... permissions) {
        boolean granted = true;
        final boolean[] isShow = {false};
        num = 0;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            for (String perm : permissions) {
                if (Manifest.permission.CAMERA.equals(perm)) {
                    Camera mCamera = null;
                    try {
                        mCamera = Camera.open();
                        Camera.Parameters mParameters = mCamera.getParameters();
                        mCamera.setParameters(mParameters);
                    } catch (Exception e) {
                        granted = false;
                        break;
                    } finally {
                        if (mCamera != null) {
                            mCamera.release();
                        }
                    }

                } else if (Manifest.permission.RECORD_AUDIO.equals(perm)) {
                    int minBuffer = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    AudioRecord audioRecord = null;
                    short[] point = new short[minBuffer];
                    int readSize;
                    try {
                        audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                                (minBuffer * 100));
                        audioRecord.startRecording();// 检测是否可以进入初始化状态
                        readSize = audioRecord.read(point, 0, point.length);
                        // 检测是否可以获取录音结果
                        if (readSize <= 0) {
                            granted = false;
                            break;
                        }
                    } catch (Exception e) {
                        granted = false;
                        break;
                    } finally {
                        if (audioRecord != null) {
                            audioRecord.release();
                        }
                    }
                }
            }
            if (!granted) {
                showDialog(activity, permissionName);
                return;
            }
        }
        RxPermissions rxPermission = new RxPermissions(activity);
        rxPermission.requestEach(permissions)
                .subscribe(new Consumer<Permission>() {
                    @Override
                    public void accept(Permission permission) throws Exception {
                        if (permission.granted) {
                            // 用户已经同意该权限
                            num++;
                            if (permissions.length == num) {
                                listener.granted();//全部同意了才会执行granted回调
                            }
                        } else if (permission.shouldShowRequestPermissionRationale) {
                            // 用户拒绝了该权限，没有选中『不再询问』（Never ask again）,那么下次再次启动时，还会提示请求权限的对话框
                        } else {
                            // 用户拒绝了该权限，并且选中『不再询问』
                            if (!isShow[0]) {
                                showDialog(activity, permissionName);
                            }
                            isShow[0] = true;
                        }
                    }
                });
    }

    public static void showDialog(final Activity activity, String permissionName) {
        DialogUtility.showDialog(activity, String.format(activity.getResources().getString(R.string.permisssion_hint), permissionName), R.string.goto_setting, R.string.cancel, false, new IOnBottomDialogListener() {
            @Override
            public void onClicked() {
                turnToPerssionSetting(activity);
            }
        });
    }

    public static void turnToPerssionSetting(Activity activity) {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.BRAND.contains("Meizu")) {
            intent.setAction("com.meizu.safe.security.SHOW_APPSEC");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.putExtra("packageName", BuildConfig.APPLICATION_ID);
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                commonSettingPage(activity);
            }
        } else if (Build.BRAND.contains("Huawei")) {
            try {
                ComponentName comp = new ComponentName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity");//华为权限管理
                intent.setComponent(comp);
                activity.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                commonSettingPage(activity);
            }
        } else if (Build.BRAND.contains("Xiaomi")) {
            String rom = getMiuiVersion();
            if (rom.contains("V6") || rom.contains("V7")) {
                intent.setAction("miui.intent.action.APP_PERM_EDITOR");
                intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
                intent.putExtra("extra_pkgname", activity.getPackageName());
                activity.startActivity(intent);
            } else if (rom.contains("V8")) {
                intent.setAction("miui.intent.action.APP_PERM_EDITOR");
                intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
                intent.putExtra("extra_pkgname", activity.getPackageName());
                activity.startActivity(intent);
            } else if (rom.contains("V9")) {
                intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                activity.startActivity(intent);
            } else {
                commonSettingPage(activity);
            }

        } else if (Build.BRAND.contains("OPPO")) {
            intent.putExtra("packageName", activity.getPackageName());
            intent.setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.PermissionManagerActivity"));
        } else {
            commonSettingPage(activity);
        }
    }

    private static String getMiuiVersion() {
        String line;
        BufferedReader input = null;
        try {
            Process p = Runtime.getRuntime().exec("getprop " + "ro.miui.ui.version.name");
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
            line = input.readLine();
            input.close();
        } catch (IOException ex) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                }
            }
        }
        return line;

    }

    public static void commonSettingPage(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        activity.startActivity(intent);
    }
}