package com.kh.beatbot.manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.database.DataSetObserver;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.kh.beatbot.R;
import com.kh.beatbot.global.BBDirectory;
import com.kh.beatbot.global.BeatBotIconSource;
import com.kh.beatbot.global.GlobalVars;
import com.kh.beatbot.global.Instrument;
import com.kh.beatbot.layout.page.TrackPage;
import com.kh.beatbot.layout.page.TrackPageFactory;

public class DirectoryManager {

	public static String appDirectoryPath;
	
	public static final String[] drumNames = { "kick", "snare",
		"hh_closed", "hh_open", "rim" };

	private AlertDialog.Builder instrumentSelectAlertBuilder, sampleSelectAlertBuilder;
	private AlertDialog instrumentSelectAlert = null, sampleSelectAlert = null;
	private ListAdapter instrumentSelectAdapter = null;
	private OnShowListener instrumentSelectOnShowListener = null;

	public void initIcons() {
		getDrumInstrument(0).setIconResources(R.drawable.kick_icon_src,
				R.drawable.kick_icon,
				R.drawable.kick_icon_selected,
				R.drawable.kick_icon_listview);
		getDrumInstrument(1).setIconResources(R.drawable.snare_icon_src,
				R.drawable.snare_icon,
				R.drawable.snare_icon_selected,
				R.drawable.snare_icon_listview);
		getDrumInstrument(2).setIconResources(R.drawable.hh_closed_icon_src,
				R.drawable.hh_closed_icon,
				R.drawable.hh_closed_icon_selected,
				R.drawable.hh_closed_icon_listview);
		getDrumInstrument(3).setIconResources(R.drawable.hh_open_icon_src,
				R.drawable.hh_open_icon,
				R.drawable.hh_open_icon_selected,
				R.drawable.hh_open_icon_listview);
		getDrumInstrument(4).setIconResources(R.drawable.rimshot_icon_src,
				R.drawable.rimshot_icon,
				R.drawable.rimshot_icon_selected,
				R.drawable.rimshot_icon_listview);
		internalRecordDirectory.setIconResources(R.drawable.microphone_icon_src,
				R.drawable.microphone_icon,
				R.drawable.microphone_icon_selected,
				R.drawable.microphone_icon_listview);
	}
	
	private static DirectoryManager singletonInstance = null;
	private BBDirectory internalDirectory = null;
	private BBDirectory drumsDirectory = null;
	private BBDirectory internalRecordDirectory = null;
	private BBDirectory userRecordDirectory = null;
	
	public static DirectoryManager getInstance() {
		if (singletonInstance == null) {
			singletonInstance = new DirectoryManager();
		}
		return singletonInstance;
	}
	
	private DirectoryManager() {
		initDataDir();
		internalDirectory = new BBDirectory(null, "internal", null);
		userRecordDirectory = new BBDirectory(null, "recorded", null);
		drumsDirectory = new BBDirectory(internalDirectory, "drums", null);
		internalRecordDirectory = new Instrument(internalDirectory, "recorded", new BeatBotIconSource());
		for (String drumName : drumNames) {
			new Instrument(drumsDirectory, drumName, new BeatBotIconSource());
		}
		initBuilders(GlobalVars.mainActivity);
	}
	
	public void updateDirectories() {
		for (BBDirectory dir : drumsDirectory.getChildren()) {
			((Instrument)dir).updateFiles();
		}
	}
	
	private void initDataDir() {
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			// we can read and write to external storage
			String extStorageDir = Environment.getExternalStorageDirectory()
					.toString();
			appDirectoryPath = extStorageDir + "/BeatBot/";
		} else { // we need read AND write access for this app - default to
					// internal storage
			//appDirectoryPath = getFilesDir().toString() + "/";
			// TODO throw / catch exception - need External SD Card!
		}
	}
	
	public void initInstrumentSelect(final Activity activity) {
		initInstrumentSelectAdapter(activity);
		initInstrumentSelectOnShowListener();
		initInstrumentSelectAlert(activity);
	}
	
	/**
	 * The Select Instrument Alert is shown when adding a new track
	 */
	private void initInstrumentSelectAlert(Activity activity) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Choose Instrument");
		builder.setAdapter(instrumentSelectAdapter,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						Managers.trackManager.addTrack(item);
					}
				});
		instrumentSelectAlert = builder.create();
		instrumentSelectAlert.setOnShowListener(instrumentSelectOnShowListener);
	}
	
	private void initInstrumentSelectAdapter(final Activity activity) {
		instrumentSelectAdapter = new ArrayAdapter<String>(activity,
				android.R.layout.select_dialog_item, android.R.id.text1, drumNames) {
			public View getView(int position, View convertView, ViewGroup parent) {
				View v = super.getView(position, convertView, parent);
				TextView tv = (TextView) v.findViewById(android.R.id.text1);
				// Put the image on the TextView
				tv.setCompoundDrawablesWithIntrinsicBounds(
						getDrumInstrument(position).getBBIconSource().listViewIcon.resourceId,
						0, 0, 0);
				// Add margin between image and text (support various screen
				// densities)
				int dp5 = (int) (5 * activity.getResources()
						.getDisplayMetrics().density + 0.5f);
				tv.setCompoundDrawablePadding(dp5);

				return v;
			}
		};
	}

	private void setInstrument(Instrument instrument) {
		TrackPage.getTrack().setInstrument(instrument);
		Managers.trackManager.trackClicked(TrackPage.getTrack().getId());
		TrackPageFactory.updatePages();
	}
	
	public void updateInstrumentSelectAlert() {
		instrumentSelectAlertBuilder.setAdapter(instrumentSelectAdapter,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						Instrument newInstrument = getDrumInstrument(item); 
						setInstrument(newInstrument);
						// update the sample select alert names with the new
						// instrument samples
						updateSampleSelectAlert();
					}
				});
		instrumentSelectAlert = instrumentSelectAlertBuilder.create();
		instrumentSelectAlert
				.setOnShowListener(instrumentSelectOnShowListener);
	}

	public void updateSampleSelectAlert() {
		sampleSelectAlertBuilder.setItems(TrackPage.getTrack().getInstrument().getSampleNames(),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						TrackPage.getTrack().setSampleNum(item);
						setInstrument(TrackPage.getTrack().getInstrument());
					}
				});
		sampleSelectAlert = sampleSelectAlertBuilder.create();
	}
	
	public void showInstrumentSelectAlert() {
		instrumentSelectAlert.show();
	}
	
	public void showSampleSelectAlert() {
		sampleSelectAlert.show();
	}
	
	public void updateRecordDirectory() {
		((Instrument)internalRecordDirectory).updateFiles();
	}
	
	public Instrument getDrumInstrument(int drumNum) {
		return (Instrument)drumsDirectory.getChild(drumNum);
	}

	public String getInternalDirectory() {
		return internalDirectory.getPath();
	}
	
	public String getUserRecordDirectory() {
		return userRecordDirectory.getPath();
	}
	
	public String getInternalRecordDirectory() {
		return internalRecordDirectory.getPath();
	}
	
	private void initBuilders(Context context) {
		instrumentSelectAlertBuilder = new AlertDialog.Builder(context);
		instrumentSelectAlertBuilder.setTitle("Choose Instrument");
		sampleSelectAlertBuilder = new AlertDialog.Builder(context);
		sampleSelectAlertBuilder.setTitle("Choose Sample");
	}
	
	private void initInstrumentSelectOnShowListener() {
		instrumentSelectOnShowListener = new OnShowListener() {
			@Override
			public void onShow(DialogInterface alert) {
				ListView listView = ((AlertDialog) alert).getListView();
				final ListAdapter originalAdapter = listView.getAdapter();

				listView.setAdapter(new ListAdapter() {
					@Override
					public int getCount() {
						return originalAdapter.getCount();
					}

					@Override
					public Object getItem(int id) {
						return originalAdapter.getItem(id);
					}

					@Override
					public long getItemId(int id) {
						return originalAdapter.getItemId(id);
					}

					@Override
					public int getItemViewType(int id) {
						return originalAdapter.getItemViewType(id);
					}

					@Override
					public View getView(int position, View convertView,
							ViewGroup parent) {
						View view = originalAdapter.getView(position,
								convertView, parent);
						TextView textView = (TextView) view;
						textView.setTypeface(GlobalVars.font);
						textView.setText(textView.getText().toString()
								.toUpperCase());
						return view;
					}

					@Override
					public int getViewTypeCount() {
						return originalAdapter.getViewTypeCount();
					}

					@Override
					public boolean hasStableIds() {
						return originalAdapter.hasStableIds();
					}

					@Override
					public boolean isEmpty() {
						return originalAdapter.isEmpty();
					}

					@Override
					public void registerDataSetObserver(DataSetObserver observer) {
						originalAdapter.registerDataSetObserver(observer);

					}

					@Override
					public void unregisterDataSetObserver(
							DataSetObserver observer) {
						originalAdapter.unregisterDataSetObserver(observer);

					}

					@Override
					public boolean areAllItemsEnabled() {
						return originalAdapter.areAllItemsEnabled();
					}

					@Override
					public boolean isEnabled(int position) {
						return originalAdapter.isEnabled(position);
					}
				});
			}
		};
	}
}
