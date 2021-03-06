/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.GregorianCalendar;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpFragment;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Allows fetching and showing the actual TV-Screen content
 * 
 * @author sre
 * 
 */
public class ScreenShotFragment extends AbstractHttpFragment {
	public static final int TYPE_OSD = 0;
	public static final int TYPE_VIDEO = 1;
	public static final int TYPE_ALL = 2;
	public static final int FORMAT_JPG = 0;
	public static final int FORMAT_PNG = 1;

	public static final String KEY_TYPE = "type";
	public static final String KEY_FORMAT = "format";
	public static final String KEY_SIZE = "size";
	public static final String KEY_FILENAME = "filename";

	private ImageView mImageView;
	private int mType;
	private int mFormat;
	private int mSize;
	private String mFilename;
	private byte[] mRawImage;
	private MediaScannerConnection mScannerConn;
	private GetScreenshotTask mGetScreenshotTask;

	private class GetScreenshotTask extends AsyncTask<ArrayList<NameValuePair>, Void, Boolean> {
		private byte[] mBytes;

		@Override
		protected Boolean doInBackground(ArrayList<NameValuePair>... params) {
			if (isCancelled())
				return false;
			publishProgress();
			mShc.fetchPageContent(URIStore.SCREENSHOT, params[0]);
			mBytes = mShc.getBytes();

			if (mBytes.length > 0) {
				return true;
			}

			return false;
		}

		@Override
		protected void onProgressUpdate(Void... progress) {
			if (!isCancelled())
				updateProgress();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				onScreenshotAvailable(mBytes);
			} else {
				String error = getString(R.string.get_content_error);
				if (mShc.hasError()) {
					error = error.concat("\n").concat(mShc.getErrorText());
				}
				showToast(error);
			}
		}
	}

	private class DummyMediaScannerConnectionClient implements MediaScannerConnectionClient {
		@Override
		public void onMediaScannerConnected() {
			return;
		}

		@Override
		public void onScanCompleted(String arg0, Uri arg1) {
			return;
		}

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		getSherlockActivity().setTitle(getText(R.string.screenshot));
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);
		mBaseTitle = mCurrentTitle = getString(R.string.screenshot);
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mImageView = new ImageView(getSherlockActivity());
		mImageView.setBackgroundColor(Color.BLACK);

		Bundle extras = getArguments();

		if (extras == null) {
			extras = new Bundle();
		}

		mType = extras.getInt(KEY_TYPE, TYPE_ALL);
		mFormat = extras.getInt(KEY_FORMAT, FORMAT_PNG);
		mSize = extras.getInt(KEY_SIZE, 720);
		mFilename = extras.getString(KEY_FILENAME);
		mScannerConn = new MediaScannerConnection(getSherlockActivity(), new DummyMediaScannerConnectionClient());
		mScannerConn.connect();

		if (savedInstanceState == null) {
			reload();
		} else {
			byte[] image = savedInstanceState.getByteArray("rawImage");
			onScreenshotAvailable(image);
		}

		return mImageView;
	}

	@Override
	public void onDestroy() {
		cancelTaskIfRunning();
		mScannerConn.disconnect();
		super.onDestroy();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.reload, menu);
		inflater.inflate(R.menu.save, menu);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putByteArray("rawImage", mRawImage);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case Statics.ITEM_RELOAD:
			reload();
			break;
		case Statics.ITEM_SAVE:
			saveToFile();
		}

		return true;
	}

	/**
	 * 
	 */
	private void updateProgress() {
		getSherlockActivity().setProgressBarIndeterminateVisibility(true);
	}

	/**
	 * @param bytes
	 */
	private void onScreenshotAvailable(byte[] bytes) {
		if (this.isDetached())
			return;
		mRawImage = bytes;
		mImageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
		getSherlockActivity().setProgressBarIndeterminateVisibility(false);
	}

	/**
	 * 
	 */
	@SuppressWarnings("unchecked")
	protected void reload() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

		switch (mType) {
		case (TYPE_OSD):
			params.add(new BasicNameValuePair("o", ""));
			params.add(new BasicNameValuePair("n", ""));
			break;
		case (TYPE_VIDEO):
			params.add(new BasicNameValuePair("v", ""));
			break;
		case (TYPE_ALL):
			break;
		}

		switch (mFormat) {
		case (FORMAT_JPG):
			params.add(new BasicNameValuePair("format", "jpg"));
			break;
		case (FORMAT_PNG):
			params.add(new BasicNameValuePair("format", "png"));
			break;
		}

		params.add(new BasicNameValuePair("r", String.valueOf(mSize)));

		if (mFilename == null) {
			long ts = (new GregorianCalendar().getTimeInMillis()) / 1000;
			mFilename = "/tmp/dreamDroid-" + ts;
		}
		params.add(new BasicNameValuePair("filename", mFilename));

		cancelTaskIfRunning();
		mGetScreenshotTask = new GetScreenshotTask();
		mGetScreenshotTask.execute(params);
	}

	private void cancelTaskIfRunning() {
		if (mGetScreenshotTask != null) {
			if (mGetScreenshotTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
				mGetScreenshotTask.cancel(true);
			}
		}
	}

	/**
	 * 
	 */
	private void saveToFile() {
		if (mRawImage != null) {
			long timestamp = GregorianCalendar.getInstance().getTimeInMillis();

			File root = Environment.getExternalStorageDirectory();
			root = new File(String.format("%s%s%s", root.getAbsolutePath(), File.separator, "media/screenshots"));

			String extension = "";

			if (mFormat == FORMAT_JPG) {
				extension = "jpg";
			} else if (mFormat == FORMAT_PNG) {
				extension = "png";
			}

			String fileName = String.format("dreamDroid_%s.%s", timestamp, extension);
			FileOutputStream out;
			try {
				if (!root.exists()) {
					root.mkdirs();
				}

				File file = new File(root, fileName);
				file.createNewFile();
				out = new FileOutputStream(file);
				out.write(mRawImage);
				out.close();
				mScannerConn.scanFile(file.getAbsolutePath(), "image/*");
				showToast(getString(R.string.screenshot_saved, file.getAbsolutePath()));

			} catch (IOException e) {
				Log.e(DreamDroid.LOG_TAG, e.getLocalizedMessage());
				showToast(e.toString());
			}
		}
	}

}
