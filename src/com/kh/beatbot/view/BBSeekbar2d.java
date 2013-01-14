package com.kh.beatbot.view;

import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.kh.beatbot.global.Colors;
import com.kh.beatbot.listenable.LevelListenable;
import com.kh.beatbot.listener.LevelListener;

public class BBSeekbar2d extends LevelListenable {
	private static final int DRAW_OFFSET = 8;
	private float selectX = 0, selectY = 0;
	private static ViewRect viewRect;
	private static FloatBuffer borderVb = null;
	private static FloatBuffer lineVb = null;

	public BBSeekbar2d(Context c, AttributeSet as) {
		super(c, as);
	}

	private void initBorderVb() {
		viewRect = new ViewRect(width, height, 0.08f);
		borderVb = makeRoundedCornerRectBuffer(width - DRAW_OFFSET * 2, height
				- DRAW_OFFSET * 2, viewRect.borderRadius, 25);
	}

	public void setViewLevelX(float x) {
		selectX = viewRect.viewX(x);
		initLines();
	}

	public void setViewLevelY(float y) {
		// top of screen lowest value in my OpenGl window
		selectY = viewRect.viewY(y);
		initLines();
	}

	protected void loadIcons() {
		// no icons to load
	}
	
	@Override
	public void init() {
		super.init();
		initBorderVb();
		initLines();
	}

	@Override
	protected void drawFrame() {
		drawRoundedBg();
		levelColor[3] = 1; // completely opaque alpha
		drawLines(lineVb, levelColor, 5, GL10.GL_LINES);
		drawRoundedBgOutline();
		drawSelection();
	}

	private void drawRoundedBg() {
		gl.glTranslatef(width / 2, height / 2, 0);
		drawTriangleFan(borderVb, Colors.VIEW_BG);
		gl.glTranslatef(-width / 2, -height / 2, 0);
	}
	
	private void drawRoundedBgOutline() {
		gl.glTranslatef(width / 2, height / 2, 0);
		drawLines(borderVb, Colors.VOLUME, 5, GL10.GL_LINE_LOOP);
		gl.glTranslatef(-width / 2, -height / 2, 0);
	}

	private void initLines() {
		lineVb = makeFloatBuffer(new float[] { DRAW_OFFSET, selectY,
				width - DRAW_OFFSET, selectY, selectX, DRAW_OFFSET, selectX,
				height - DRAW_OFFSET });
	}

	private void drawSelection() {
		setColor(levelColor);
		gl.glVertexPointer(2, GL10.GL_FLOAT, 0, makeFloatBuffer(new float[] {
				selectX, selectY }));
		gl.glPointSize(viewRect.borderRadius);
		gl.glDrawArrays(GL10.GL_POINTS, 0, 1);
		levelColor[3] = .4f;
		setColor(levelColor);
		for (float size = viewRect.borderRadius; size < viewRect.borderRadius * 1.5; size++) {
			gl.glPointSize(size);
			gl.glDrawArrays(GL10.GL_POINTS, 0, 1);
		}
	}

	private void selectLocation(float x, float y) {
		selectX = viewRect.clipX(x);
		selectY = viewRect.clipY(y);
		initLines();
		for (LevelListener listener : levelListeners) {
			listener.setLevel(this, viewRect.unitX(selectX), viewRect.unitY(height - selectY));
		}
	}

	@Override
	protected void handleActionDown(int id, float x, float y) {
		selectLocation(x, y);
		levelColor = Colors.LEVEL_SELECTED.clone();
		super.handleActionDown(id, x, y);
	}

	@Override
	protected void handleActionMove(MotionEvent e) {
		selectLocation(e.getX(0), e.getY(0));
	}

	@Override
	protected void handleActionUp(int id, float x, float y) {
		levelColor = Colors.VOLUME.clone();
		super.handleActionUp(id, x, y);
	}
}
