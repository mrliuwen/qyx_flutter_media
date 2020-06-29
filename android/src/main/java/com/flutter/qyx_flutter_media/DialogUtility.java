package com.flutter.qyx_flutter_media;

import android.content.Context;
import android.content.DialogInterface;

import com.flutter.qyx_flutter_media.R;

/**
 * @author SL
 * @created 2016-1-3 上午11:41:59
 * @copyRight http://www.qiyunxin.com
 * @function dialog管理
 */
public class DialogUtility {

    /**
     * 普通Dialog
     *
     * @param _Context
     * @param ContentStrResId         提示内容
     * @param LeftBtnStrResId         左按钮文本
     * @param RightBtnStrResId        右按钮文本
     * @param isCancelable            是否可撤销
     * @param iOnBottomDialogListener 回调按钮
     */
    public static void showDialog(Context _Context, int ContentStrResId,
                                  int LeftBtnStrResId, int RightBtnStrResId,
                                  final boolean isCancelable,
                                  final IOnBottomDialogListener iOnBottomDialogListener) {

        MyDialog.Builder alertDialog = new MyDialog.Builder(_Context);
        alertDialog.setTitle(R.string.dialog_title);
        alertDialog.setMessage(ContentStrResId);
        alertDialog.setPositiveButton(LeftBtnStrResId,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        iOnBottomDialogListener.onClicked();
                    }
                });

        alertDialog.setNegativeButton(RightBtnStrResId,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.create().show();
        alertDialog.getDialog().setCancelable(isCancelable);
    }

    /**
     * 普通Dialog
     *
     * @param _Context
     * @param ContentStrResId         提示内容
     * @param LeftBtnStrResId         左按钮文本
     * @param RightBtnStrResId        右按钮文本
     * @param isCancelable            是否可撤销
     * @param iOnBottomDialogListener 回调按钮
     */
    public static void showDialog(Context _Context, String ContentStrResId,
                                  int LeftBtnStrResId, int RightBtnStrResId,
                                  final boolean isCancelable,
                                  final IOnBottomDialogListener iOnBottomDialogListener) {

        MyDialog.Builder alertDialog = new MyDialog.Builder(_Context);
        alertDialog.setTitle(R.string.dialog_title);
        alertDialog.setMessage(ContentStrResId);
        alertDialog.setPositiveButton(LeftBtnStrResId,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        iOnBottomDialogListener.onClicked();
                    }
                });

        alertDialog.setNegativeButton(RightBtnStrResId,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.create().show();
        alertDialog.getDialog().setCancelable(isCancelable);
    }
}
