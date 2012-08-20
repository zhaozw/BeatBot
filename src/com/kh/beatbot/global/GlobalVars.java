package com.kh.beatbot.global;

import java.util.ArrayList;
import java.util.List;

import com.kh.beatbot.EffectActivity.EffectParam;
import com.kh.beatbot.manager.MidiManager;
import com.kh.beatbot.manager.PlaybackManager;

public class GlobalVars {
	public static final float[] BG_COLOR = { 0.12549f, 0.188235f, 0.227451f, 1 };
	public static final float[] WHITE = { 1, 1, 1, 1 };
	public static final float[] GREEN = { 0, 1, 0, 1 };
	public static final float[] YELLOW = { 1, 1, 0, 1 };
	public static final float[] RED = { 1, 0, 0, 1 };

	public static final int UNDO_STACK_SIZE = 40;
	public static final int NUM_EFFECTS = 7;
	public static final short LEVEL_MAX = 127;
	private static MidiManager midiManager = null;
	private static PlaybackManager playbackManager = null;

	// effect settings are stored here instead of in the effect activities
	// because the activities are destroyed after clicking 'back', and we
	// need to persist state
	public static boolean[] delayParamsLinked;
	public static boolean[] tremeloParamsLinked;
	public static boolean[][] effectOn;
	public static List<EffectParam>[][] params;

	public static float[] trackVolume;
	public static float[] trackPan;
	public static float[] trackPitch;
	
	public static float currBeatDivision;

	public static MidiManager getMidiManager() {
		return midiManager;
	}

	public static void setMidiManager(MidiManager midiManager) {
		GlobalVars.midiManager = midiManager;
		int numTracks = midiManager.getNumSamples();
		params = (ArrayList<EffectParam>[][]) new ArrayList[numTracks][NUM_EFFECTS];
		effectOn = new boolean[numTracks][NUM_EFFECTS];
		delayParamsLinked = new boolean[numTracks];
		tremeloParamsLinked = new boolean[numTracks];
		trackVolume = new float[numTracks];
		trackPan = new float[numTracks];
		trackPitch = new float[numTracks];
		for (int track = 0; track < numTracks; track++) {
			delayParamsLinked[track] = tremeloParamsLinked[track] = true;
			trackVolume[track] = .8f;
			trackPan[track] = trackPitch[track] = .5f;
			for (int effect = 0; effect < NUM_EFFECTS; effect++) {
				effectOn[track][effect] = false;
				params[track][effect] = new ArrayList<EffectParam>();
			}
		}
	}

	public static PlaybackManager getPlaybackManager() {
		return playbackManager;
	}

	public static void setPlaybackManager(PlaybackManager playbackManager) {
		GlobalVars.playbackManager = playbackManager;
	}
}
