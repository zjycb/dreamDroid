/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.DreamDroidListFragment;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SimpleChoiceDialog;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.DeviceDetector;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Shows a list of all connection profiles
 * 
 * @author sre
 * 
 */
public class ProfileListFragment extends DreamDroidListFragment implements ActionDialog.DialogActionListener {
	private Profile mProfile;
	private ArrayList<Profile> mDetectedProfiles;

	private SimpleCursorAdapter mAdapter;
	private Cursor mCursor;
	private Activity mActivity;
	private DetectDevicesTask mDetectDevicesTask;

	private ProgressDialog mProgress;

	private class DetectDevicesTask extends AsyncTask<Void, Void, ArrayList<Profile>> {

		@Override
		protected ArrayList<Profile> doInBackground(Void... params) {
			ArrayList<Profile> profiles = DeviceDetector.getAvailableHosts();
			return profiles;
		}

		@Override
		protected void onPostExecute(ArrayList<Profile> profiles) {
			onDevicesDetected(profiles);
		}

	}

	/**
	 * 
	 */
	private void detectDevices() {
		if (mDetectedProfiles == null) {
			if (mDetectDevicesTask != null) {
				mDetectDevicesTask.cancel(true);
				mDetectDevicesTask = null;
			}

			if (mProgress != null) {
				mProgress.dismiss();
			}
			mProgress = ProgressDialog.show(getActivity(), getText(R.string.searching),
					getText(R.string.searching_known_devices));
			mProgress.setCancelable(false);
			mDetectDevicesTask = new DetectDevicesTask();
			mDetectDevicesTask.execute();
		} else {
			if (mDetectedProfiles.size() == 0) {
				mDetectedProfiles = null;
				detectDevices();
			} else {
				onDevicesDetected(mDetectedProfiles);
			}
		}
	}

	/**
	 * 
	 */
	private void addAllDetectedDevices() {
		for (Profile p : mDetectedProfiles) {
			if (DreamDroid.addProfile(p)) {
				showToast(getText(R.string.profile_added) + " '" + p.getName() + "'");
			} else {
				showToast(getText(R.string.profile_not_added) + " '" + p.getName() + "'");
			}
		}
		mCursor.requery();
	}

	/**
	 * @param hosts
	 *            A list of profiles for auto-discovered dreamboxes
	 */
	private void onDevicesDetected(ArrayList<Profile> profiles) {
		mProgress.dismiss();
		mDetectedProfiles = profiles;

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.autodiscover_dreamboxes);

		if (mDetectedProfiles.size() > 0) {
			CharSequence[] items = new CharSequence[profiles.size()];

			for (int i = 0; i < profiles.size(); i++) {
				items[i] = profiles.get(i).getName();
			}

			builder.setItems(items, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					mProfile = mDetectedProfiles.get(which);
					editProfile();
					dialog.dismiss();
				}

			});

			builder.setPositiveButton(R.string.reload, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					mDetectedProfiles = null;
					dialog.dismiss();
					detectDevices();
				}
			});

			builder.setNegativeButton(R.string.add_all, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					dialog.dismiss();
					addAllDetectedDevices();
				}
			});
		} else {
			builder.setMessage(R.string.autodiscovery_failed);
			builder.setNeutralButton(android.R.string.ok, new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			});
		}
		builder.show();
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);

		mActivity = getActivity();
		mBaseTitle = mCurrentTitle = getString(R.string.profiles);
		mActivity.setProgressBarIndeterminateVisibility(false);

		mCursor = DreamDroid.getProfiles();

		mProfile = new Profile();
		if (savedInstanceState != null) {
			int pos = savedInstanceState.getInt("cursorPosition");
			mCursor.moveToPosition(pos);
			mProfile.set(mCursor);
		}
	}

	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		mAdapter = new SimpleCursorAdapter(mActivity, android.R.layout.two_line_list_item, mCursor, new String[] {
				DreamDroid.KEY_PROFILE, DreamDroid.KEY_HOST }, new int[] { android.R.id.text1, android.R.id.text2 });

		setListAdapter(mAdapter);

		getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> a, View v, int position, long id) {
				return onListItemLongClick(a, v, position, id);
			}
		});
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mProfile.set(mCursor);
		activateProfile();
	}

	protected boolean onListItemLongClick(AdapterView<?> a, View v, int position, long id) {
		mProfile.set(mCursor);

		CharSequence[] actions = { getText(R.string.edit), getText(R.string.delete) };
		int[] actionIds = { Statics.ACTION_EDIT, Statics.ACTION_DELETE };
		getMultiPaneHandler().showDialogFragment(SimpleChoiceDialog.newInstance(mProfile.getName(), actions, actionIds),
				"dialog_profile_selected");
		return true;
	}

	public void onSaveInstanceState(Bundle outState) {
		outState.putInt("cursorPosition", mCursor.getPosition());
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Statics.REQUEST_EDIT_PROFILE) {
			mActivity.setResult(resultCode, null);
			if (resultCode == Activity.RESULT_OK) {
				mCursor.requery();
				mAdapter.notifyDataSetChanged();
				// Reload the current profile as it may have been
				// changed/altered
				DreamDroid.reloadActiveProfile();
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.profiles, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onItemClicked(item.getItemId());
	}

	/**
	 * @param id
	 *            The id of the selected menu item (<code>MENU_*</code> statics)
	 * @return
	 */
	protected boolean onItemClicked(int id) {
		switch (id) {
		case (Statics.ITEM_ADD_PROFILE):
			createProfile();
			break;
		case Statics.ITEM_DETECT_DEVICES:
			detectDevices();
			break;
		default:
			return false;
		}

		return true;
	}

	/**
	 * Activates the selected profile
	 */
	private void activateProfile() {
		if (DreamDroid.setActiveProfile(mProfile.getId())) {
			showToast(getText(R.string.profile_activated) + " '" + mProfile.getName() + "'");
			if (!getMultiPaneHandler().isMultiPane()) {
				getActivity().finish();
			}
		} else {
			showToast(getText(R.string.profile_not_activated) + " '" + mProfile.getName() + "'");
		}
	}

	/**
	 * Opens a <code>ProfileEditActivity</code> for the selected profile
	 */
	private void editProfile() {
		Bundle args = new Bundle();
		args.putString("action", Intent.ACTION_EDIT);
		args.putSerializable("profile", mProfile);

		Fragment f = new ProfileEditFragment();
		f.setArguments(args);
		f.setTargetFragment(this, Statics.REQUEST_EDIT_PROFILE);
		getMultiPaneHandler().showDetails(f, true);
	}

	/**
	 * Opens a <code>ProfileEditActivity</code> for creating a new profile
	 */
	private void createProfile() {
		Bundle args = new Bundle();
		args.putString("action", Intent.ACTION_EDIT);

		Fragment f = new ProfileEditFragment();
		f.setArguments(args);
		f.setTargetFragment(this, Statics.REQUEST_EDIT_PROFILE);
		getMultiPaneHandler().showDetails(f);
	}

	/**
	 * Shows a toast
	 * 
	 * @param text
	 *            The text to show as toast
	 */
	protected void showToast(String text) {
		Toast toast = Toast.makeText(mActivity, text, Toast.LENGTH_LONG);
		toast.show();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public void onDialogAction(int action) {
		switch (action) {
		case Statics.ACTION_EDIT:
			editProfile();
			break;
		case Statics.ACTION_DELETE:
			getMultiPaneHandler().showDialogFragment(PositiveNegativeDialog.newInstance(mProfile.getName(),
					R.string.confirm_delete_profile, android.R.string.yes, Statics.ACTION_DELETE_CONFIRMED,
					android.R.string.no, Statics.ACTION_NONE), "dialog_delete_profile_confirm");
			break;
		case Statics.ACTION_DELETE_CONFIRMED:
			if (DreamDroid.deleteProfile(mProfile)) {
				showToast(getString(R.string.profile_deleted) + " '" + mProfile.getName() + "'");
			} else {
				showToast(getString(R.string.profile_not_deleted) + " '" + mProfile.getName() + "'");
			}
			// TODO Add error handling
			mCursor.requery();
			mProfile = new Profile();
			mAdapter.notifyDataSetChanged();
			break;
		}
	}
}
