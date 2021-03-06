package com.kh.beatbot.effect;

import com.kh.beatbot.R;
import com.kh.beatbot.global.GlobalVars;


public class Tremelo extends Effect {

	public static final String NAME = GlobalVars.mainActivity.getString(R.string.tremelo);
	public static final int EFFECT_NUM = 6;
	public static final int NUM_PARAMS = 3;
	public static final ParamData[] PARAMS_DATA = {
		new ParamData("RATE", true, true, "Hz"),
		new ParamData("PHASE", false, false, ""),
		new ParamData("DEPTH", false, false, "")
	};
	
	public Tremelo(int trackNum, int position) {
		super(trackNum, position);
	}

	public String getName() {
		return NAME;
	}
	
	public int getNum() {
		return EFFECT_NUM;
	}

	public int numParams() {
		return NUM_PARAMS;
	}
	
	@Override
	public ParamData[] getParamsData() {
		return PARAMS_DATA;
	}
}
