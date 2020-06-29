package com.flutter.qyx_flutter_media;

import android.annotation.SuppressLint;
import android.app.ActionBar.LayoutParams;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.flutter.qyx_flutter_media.R;


/**
 * 
 * 
 * @author  SL
 * @Create Date: 2014-9-20 下午5:35:24
 * @copyRight http://www.qiyunxin.com
 */
public class MyDialog extends Dialog {
	
	public MyDialog(Context context) {
		super(context);
	}

	public MyDialog(Context context, int theme) {
		super(context, theme);
	}

	public static class Builder {
		private Context context;
		private String title;
		private String message;
		private String positiveButtonText;
		private String negativeButtonText;
		private View contentView;
		private OnClickListener positiveButtonClickListener;
		private OnClickListener negativeButtonClickListener;
		private ListView mListView;
		private Dialog mDialog;

		public Builder(Context context) {
			this.context = context;
		}

		public Builder setMessage(String message) {
			this.message = message;
			return this;
		}

		/**
		 * Set the Dialog message from resource
		 * 
		 * @param message
		 * @return
		 */
		public Builder setMessage(int message) {
			this.message = (String) context.getText(message);
			return this;
		}

		/**
		 * Set the Dialog title from resource
		 * 
		 * @param title
		 * @return
		 */
		public Builder setTitle(int title) {
			this.title = (String) context.getText(title);
			return this;
		}

		/**
		 * Set the Dialog title from String
		 * 
		 * @param title
		 * @return
		 */

		public Builder setTitle(String title) {
			this.title = title;
			return this;
		}

		public Builder setContentView(View v) {
			this.contentView = v;
			return this;
		}

		/**
		 * Set the positive button resource and it's listener
		 * 
		 * @param positiveButtonText
		 * @return
		 */
		public Builder setPositiveButton(int positiveButtonText,
				OnClickListener listener) {
			this.positiveButtonText = (String) context
					.getText(positiveButtonText);
			this.positiveButtonClickListener = listener;
			return this;
		}

		public Builder setPositiveButton(String positiveButtonText,
				OnClickListener listener) {
			this.positiveButtonText = positiveButtonText;
			this.positiveButtonClickListener = listener;
			return this;
		}

		public Builder setNegativeButton(int negativeButtonText,
				OnClickListener listener) {
			this.negativeButtonText = (String) context
					.getText(negativeButtonText);
			this.negativeButtonClickListener = listener;
			return this;
		}

		public Builder setNegativeButton(String negativeButtonText,
				OnClickListener listener) {
			this.negativeButtonText = negativeButtonText;
			this.negativeButtonClickListener = listener;
			return this;
		}

		// ֧��listview
		public void setListView(ListView lv) {
			this.mListView = lv;
		}

		public Dialog getDialog() {
			return mDialog;
		}

		@SuppressLint("NewApi")
		public MyDialog create() {
			LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			// instantiate the dialog with the custom Theme
			final MyDialog dialog = new MyDialog(context, R.style.dialog);

			FrameLayout layout = (FrameLayout) inflater.inflate(
					R.layout.dialog_normal_layout, null);
			LinearLayout content_layout = (LinearLayout) layout
					.findViewById(R.id.content_layout);
//			if (!TextUtils.isEmpty(title)) {
//				((TextView) layout.findViewById(R.id.title)).setText(title);
//			}

			if (mListView == null) {
				dialog.addContentView(layout, new LayoutParams(
						LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
				// set the dialog title

				// set the confirm button
				if (positiveButtonText != null) {
					((Button) layout.findViewById(R.id.positiveButton))
							.setText(positiveButtonText);
					if (positiveButtonClickListener != null) {
						((Button) layout.findViewById(R.id.positiveButton))
								.setOnClickListener(new View.OnClickListener() {
									public void onClick(View v) {
										positiveButtonClickListener
												.onClick(
														dialog,
														DialogInterface.BUTTON_POSITIVE);
									}
								});
					}
				} else {
					// if no confirm button just set the visibility to GONE
					layout.findViewById(R.id.positiveButton).setVisibility(
							View.GONE);
					layout.findViewById(R.id.middle_line).setVisibility(View.GONE);
					
				}
				// set the cancel button
				if (negativeButtonText != null) {
					((Button) layout.findViewById(R.id.negativeButton))
							.setText(negativeButtonText);
					if (negativeButtonClickListener != null) {
						((Button) layout.findViewById(R.id.negativeButton))
								.setOnClickListener(new View.OnClickListener() {
									public void onClick(View v) {
										negativeButtonClickListener
												.onClick(
														dialog,
														DialogInterface.BUTTON_NEGATIVE);
									}
								});
					}
				} else {
					// if no confirm button just set the visibility to GONE
					layout.findViewById(R.id.negativeButton).setVisibility(
							View.GONE);
					layout.findViewById(R.id.middle_line).setVisibility(View.GONE);
				}
				// set the content message
				if (message != null) {
					((TextView) layout.findViewById(R.id.message))
							.setText(message);
				} else if (contentView != null) {
					// if no message set
					// add the contentView to the dialog body
					((LinearLayout) layout.findViewById(R.id.content_layout))
							.removeAllViews();
					
					((LinearLayout) layout.findViewById(R.id.content_layout))
							.addView(contentView, new LayoutParams(
									LayoutParams.MATCH_PARENT,
									LayoutParams.WRAP_CONTENT));
					((LinearLayout) layout.findViewById(R.id.content_layout))
							.setGravity(Gravity.CENTER_HORIZONTAL);

				}
				dialog.setContentView(layout);
			} else {
				content_layout.addView(mListView);
				content_layout.setBackgroundResource(R.color.the_color_white);
				layout.findViewById(R.id.button_layout)
						.setVisibility(View.GONE);
				layout.findViewById(R.id.last_line_view).setVisibility(
						View.GONE);
//				layout.findViewById(R.id.top_line_view).setVisibility(
//						View.VISIBLE);
//				TextView title = (TextView) layout.findViewById(R.id.title);
//				title.setGravity(Gravity.LEFT);
				layout.findViewById(R.id.message).setVisibility(View.GONE);

				dialog.setContentView(layout);
			}
			mDialog = dialog;
			return dialog;
		}

	}
}
