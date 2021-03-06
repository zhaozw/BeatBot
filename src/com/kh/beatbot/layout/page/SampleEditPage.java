package com.kh.beatbot.layout.page;

import com.kh.beatbot.R;
import com.kh.beatbot.global.ImageIconSource;
import com.kh.beatbot.listener.OnPressListener;
import com.kh.beatbot.listener.OnReleaseListener;
import com.kh.beatbot.manager.TrackManager;
import com.kh.beatbot.view.SampleEditBBView;
import com.kh.beatbot.view.control.Button;
import com.kh.beatbot.view.control.ImageButton;
import com.kh.beatbot.view.control.ToggleButton;

public class SampleEditPage extends Page {

	private SampleEditBBView sampleEdit;
	private ImageButton previewButton;
	private ToggleButton loopButton, reverseButton;

	public void init() {
	}
	
	@Override
	public void update() {
		if (sampleEdit != null)
			sampleEdit.update();
		loopButton.setChecked(TrackManager.currTrack.isLooping());
		reverseButton.setChecked(TrackManager.currTrack.isReverse());
	}

	@Override
	protected void loadIcons() {
		previewButton.setIconSource(new ImageIconSource(R.drawable.preview_icon, R.drawable.preview_icon_selected));
		loopButton.setIconSource(new ImageIconSource(R.drawable.loop_icon, R.drawable.loop_selected_icon));
		reverseButton.setIconSource(new ImageIconSource(R.drawable.reverse_icon, R.drawable.reverse_selected_icon));
	}

	@Override
	public void draw() {
		// parent view - no drawing to do
	}

	@Override
	protected void createChildren() {
		sampleEdit = new SampleEditBBView();
		previewButton = new ImageButton();
		
		previewButton.setOnPressListener(new OnPressListener() {
			@Override
			public void onPress(Button button) {
				TrackManager.currTrack.preview();
			}
		});
		
		previewButton.setOnReleaseListener(new OnReleaseListener() {
			@Override
			public void onRelease(Button button) {
				TrackManager.currTrack.stopPreviewing();
			}
		});
		loopButton = new ToggleButton();
		reverseButton = new ToggleButton();
		loopButton.setOnReleaseListener(new OnReleaseListener() {
			public void onRelease(Button arg0) {
				TrackManager.currTrack.toggleLooping();
			}
		});
		reverseButton.setOnReleaseListener(new OnReleaseListener() {
			public void onRelease(Button arg0) {
				TrackManager.currTrack.setReverse(reverseButton.isChecked());
			}
		});
		addChild(sampleEdit);
		addChild(previewButton);
		addChild(loopButton);
		addChild(reverseButton);
	}

	@Override
	public void layoutChildren() {
		float halfHeight = height / 2;
		previewButton.layout(this, 0, 0, height, height);
		sampleEdit.layout(this, height, 0, width - height - halfHeight, height);
		loopButton.layout(this, width - halfHeight, 0, halfHeight, halfHeight);
		reverseButton.layout(this, width - halfHeight, halfHeight, halfHeight, halfHeight);
	}
}
