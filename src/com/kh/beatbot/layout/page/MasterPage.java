package com.kh.beatbot.layout.page;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ViewFlipper;

import com.kh.beatbot.R;

public class MasterPage extends Page {
	public MasterPageSelect masterPageSelect;
	public ViewFlipper pageFlipper;
	
	private LevelsPage levelsPage = null;
	private EffectsPage effectsPage = null;
	
	private Page[] pages = null;
	
	private int currPageNum = 0;
	
	public MasterPage(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void init() {
		masterPageSelect = (MasterPageSelect)findViewById(R.id.masterPageSelect);
		pageFlipper = (ViewFlipper)findViewById(R.id.masterFlipper);
		levelsPage = (LevelsPage)findViewById(R.id.masterLevelsPage);
		effectsPage = (EffectsPage)findViewById(R.id.masterEffectsPage);
		pages = new Page[] {levelsPage, effectsPage};
		
		masterPageSelect.init();
		for (Page page : pages) {
			page.init();
		}
		effectsPage.setMasterMode(true);
		levelsPage.setMasterMode(true);
	}

	@Override
	public void update() {
		for (Page page : pages) {
			page.update();
		}
	}

	@Override
	public void setVisibilityCode(int code) {
		getPage(currPageNum).setVisibilityCode(code);
	}
	
	public void selectPage(final int pageNum) {
		int prevPageNum = this.currPageNum;
		this.currPageNum = pageNum;
		if (prevPageNum == pageNum)
			return;
		getPage(prevPageNum).setVisible(false);
		pageFlipper.setDisplayedChild(pageNum);
		getPage(pageNum).setVisible(true);
	}
	
	public Page getPage(int pageNum) {
		return pages[pageNum];
	}
}