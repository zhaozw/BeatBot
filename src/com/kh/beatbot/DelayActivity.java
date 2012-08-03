package com.kh.beatbot;

import android.os.Bundle;
import android.widget.ToggleButton;

import com.kh.beatbot.global.GlobalVars;

public class DelayActivity extends EffectActivity {

	@Override
	public void initParams() {
		super.initParams();
		if (GlobalVars.params[trackNum][EFFECT_NUM].isEmpty()) {
			GlobalVars.params[trackNum][EFFECT_NUM].add(new EffectParam(true, 'x', "ms"));
			GlobalVars.params[trackNum][EFFECT_NUM].add(new EffectParam(false, 'y', ""));
			GlobalVars.params[trackNum][EFFECT_NUM].add(new EffectParam(false, ' ', ""));
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		EFFECT_NUM = 2;
		NUM_PARAMS = 3;
		setContentView(R.layout.delay_layout);
		initParams();
		((ToggleButton) findViewById(R.id.effectToggleOn))
				.setChecked(GlobalVars.effectOn[trackNum][EFFECT_NUM]);
	}

	public void setEffectOn(boolean on) {
		GlobalVars.effectOn[trackNum][EFFECT_NUM] = on;
		setDelayOn(trackNum, on);
	}

	@Override
	public void setParamNative(int paramNum, float level) {
		setDelayParam(trackNum, paramNum, level);
	}

	public native void setDelayOn(int trackNum, boolean on);

	public native void setDelayParam(int trackNum, int paramNum, float param);
}
