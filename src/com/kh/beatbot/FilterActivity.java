package com.kh.beatbot;

import android.os.Bundle;
import android.widget.ToggleButton;

import com.KarlHiner.BeatBot.R;
import com.kh.beatbot.global.GlobalVars;

public class FilterActivity extends EffectActivity {
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.effect_layout);		
		((ToggleButton)findViewById(R.id.effect_toggleOn)).setChecked(GlobalVars.filterOn[trackNum]);
	}
	
	public float getXValue() {
		return GlobalVars.filterX[trackNum];
	}
	
	public float getYValue() {
		return GlobalVars.filterY[trackNum];
	}
	
	public void setXValue(float xValue) {
		GlobalVars.filterX[trackNum] = xValue;
		setFilterCutoff(trackNum, xValue);
	}
	
	public void setYValue(float yValue) {
		GlobalVars.filterY[trackNum] = yValue;
		setFilterQ(trackNum, yValue);
	}
	
	public void setEffectOn(boolean on) {
		GlobalVars.filterOn[trackNum] = on;
		setFilterOn(trackNum, on);
	}
	
	public void setEffectDynamic(boolean dynamic) {
		setFilterDynamic(trackNum, dynamic);
	}
	
	public native void setFilterOn(int trackNum, boolean on);
	public native void setFilterDynamic(int trackNum, boolean dynamic);
	public native void setFilterCutoff(int trackNum, float cutoff);
	public native void setFilterQ(int trackNum, float q);
}
