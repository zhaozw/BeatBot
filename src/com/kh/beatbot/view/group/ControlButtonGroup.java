package com.kh.beatbot.view.group;

import android.widget.Toast;

import com.kh.beatbot.R;
import com.kh.beatbot.global.GlobalVars;
import com.kh.beatbot.global.ImageIconSource;
import com.kh.beatbot.listener.OnReleaseListener;
import com.kh.beatbot.manager.Managers;
import com.kh.beatbot.manager.PlaybackManager;
import com.kh.beatbot.manager.RecordManager;
import com.kh.beatbot.view.BpmView;
import com.kh.beatbot.view.TouchableBBView;
import com.kh.beatbot.view.control.Button;
import com.kh.beatbot.view.control.ImageButton;
import com.kh.beatbot.view.control.ToggleButton;

public class ControlButtonGroup extends TouchableBBView {

	ToggleButton playButton, recordButton, copyButton;
	ImageButton stopButton, undoButton, deleteButton;
	
	public BpmView bpmView;
	
	@Override
	public void init() {
		bpmView.setBPM(Managers.midiManager.getBPM());
	}

	@Override
	public void draw() {
		// parent
	}

	@Override
	protected void createChildren() {
		playButton = new ToggleButton();
		stopButton = new ImageButton();
		recordButton = new ToggleButton();
		copyButton = new ToggleButton();
		deleteButton = new ImageButton();
		undoButton = new ImageButton();
		bpmView = new BpmView();

		playButton.setOnReleaseListener(new OnReleaseListener() {
			@Override
			public void onRelease(Button button) {
				((ToggleButton) button).setChecked(true);
				if (Managers.playbackManager.getState() == PlaybackManager.State.PLAYING) {
					Managers.playbackManager.reset();
					Managers.midiManager.reset();
				} else if (Managers.playbackManager.getState() == PlaybackManager.State.STOPPED) {
					Managers.playbackManager.play();
				}
			}
		});

		recordButton.setOnReleaseListener(new OnReleaseListener() {
			@Override
			public void onRelease(Button button) {
				if (Managers.recordManager.getState() != RecordManager.State.INITIALIZING) {
					// Managers.recordManager.stopListening();
					String fileName = Managers.recordManager
							.stopRecordingAndWriteWav();
					// make sure the recorded instrument shows the newly
					// recorded beat
					Managers.directoryManager.updateDirectories();

					Toast.makeText(
							GlobalVars.mainActivity.getApplicationContext(),
							"Recorded file to " + fileName, Toast.LENGTH_SHORT)
							.show();
				} else {
					GlobalVars.mainPage.midiView.reset();
					playButton.setChecked(true);
					Managers.recordManager.startRecordingNative();
					if (Managers.playbackManager.getState() != PlaybackManager.State.PLAYING)
						playButton.getOnReleaseListener().onRelease(playButton);
				}
			}
		});

		stopButton.setOnReleaseListener(new OnReleaseListener() {
			@Override
			public void onRelease(Button button) {
				playButton.setChecked(false);
				if (Managers.recordManager.getState() != RecordManager.State.INITIALIZING) {
					recordButton.setChecked(false);
					playButton.getOnReleaseListener().onRelease(playButton);
					recordButton.getOnReleaseListener().onRelease(recordButton);
				}
				if (Managers.playbackManager.getState() == PlaybackManager.State.PLAYING) {
					playButton.setChecked(false);
					Managers.playbackManager.stop();
					Managers.midiManager.reset();
				}
			}
		});

		undoButton.setOnReleaseListener(new OnReleaseListener() {
			@Override
			public void onRelease(Button button) {
				Managers.midiManager.undo();
			}
		});

		copyButton.setOnReleaseListener(new OnReleaseListener() {
			@Override
			public void onRelease(Button button) {
				String msg = null;
				if (((ToggleButton) button).isChecked()) {
					Managers.midiManager.copy();
					msg = "Tap To Paste";
				} else {
					Managers.midiManager.cancelCopy();
					msg = "Copy Cancelled";
				}
				Toast.makeText(GlobalVars.mainActivity.getApplicationContext(),
						msg, Toast.LENGTH_SHORT).show();
			}
		});

		deleteButton.setOnReleaseListener(new OnReleaseListener() {
			@Override
			public void onRelease(Button button) {
				Managers.midiManager.deleteSelectedNotes();
			}
		});
		
		addChild(playButton);
		addChild(stopButton);
		addChild(recordButton);
		addChild(copyButton);
		addChild(deleteButton);
		addChild(undoButton);
		addChild(bpmView);
	}
	
	public void setEditIconsEnabled(final boolean enabled) {
		deleteButton.setEnabled(enabled);
		copyButton.setEnabled(enabled);
	}

	public void uncheckCopyButton() {
		copyButton.setChecked(false);
	}

	@Override
	public void layoutChildren() {
		// left-aligned buttons
		playButton.layout(this, 0, 0, height, height);
		stopButton.layout(this, height, 0, height, height);
		recordButton.layout(this, height * 2, 0, height, height);
		
		// right-aligned buttons
		bpmView.layout(this, width - 2 * height, 0, 2 * height, height);
		undoButton.layout(this, width - 3 * height, 0, height, height);
		deleteButton.layout(this, width - 4 * height, 0, height, height);
		copyButton.layout(this, width - 5 * height, 0, height, height);
	}

	@Override
	protected void loadIcons() {
		ImageIconSource playButtonIcon = new ImageIconSource(R.drawable.play_icon, R.drawable.play_icon_pressed, 
				R.drawable.play_icon_selected, -1);
		ImageIconSource stopButtonIcon = new ImageIconSource(R.drawable.stop_icon, R.drawable.stop_icon_pressed);
		ImageIconSource recButtonIcon = new ImageIconSource(R.drawable.rec_off_icon, R.drawable.rec_icon_pressed,
				R.drawable.rec_on_icon_selected);
		ImageIconSource copyButtonIcon = new ImageIconSource(R.drawable.copy_icon, R.drawable.copy_icon_pressed,
				R.drawable.copy_icon_pressed, R.drawable.copy_icon_inactive);
		ImageIconSource deleteButtonIcon = new ImageIconSource(R.drawable.delete_icon, R.drawable.delete_icon_pressed, 
				-1, R.drawable.delete_icon_inactive);
		ImageIconSource undoButtonIcon = new ImageIconSource(R.drawable.undo_icon, R.drawable.undo_icon_pressed);
		
		playButton.setIconSource(playButtonIcon);
		stopButton.setIconSource(stopButtonIcon);
		recordButton.setIconSource(recButtonIcon);
		copyButton.setIconSource(copyButtonIcon);
		deleteButton.setIconSource(deleteButtonIcon);
		undoButton.setIconSource(undoButtonIcon);
		
		setEditIconsEnabled(false);
	}
}
