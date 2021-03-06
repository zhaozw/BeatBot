package com.kh.beatbot.view.control;

import java.nio.FloatBuffer;

import android.util.FloatMath;

import com.kh.beatbot.global.Colors;

public class Knob extends ControlView1dBase {

	private FloatBuffer circleVb;

	private int drawIndex = 0;

	protected void loadIcons() {
		// none
	}
	
	private void initCircleVbs(float width, float height) {
		float[] circleVertices = new float[128];
		float theta = 3 * � / 4; // start at 1/8 around the circle
		for (int i = 0; i < circleVertices.length / 4; i++) {
			// theta will range from �/4 to 7�/8,
			// with the �/8 gap at the "bottom" of the view
			theta += 6 * � / circleVertices.length;
			// main circles will show when user is not touching
			circleVertices[i * 4] = FloatMath.cos(theta) * width / 2.3f + width
					/ 2;
			circleVertices[i * 4 + 1] = FloatMath.sin(theta) * width / 2.3f
					+ width / 2;
			circleVertices[i * 4 + 2] = FloatMath.cos(theta) * width / 3.1f
					+ width / 2;
			circleVertices[i * 4 + 3] = FloatMath.sin(theta) * width / 3.1f
					+ width / 2;
		}
		circleVb = makeFloatBuffer(circleVertices);
	}

	@Override
	public void draw() {
		// level background
		drawTriangleStrip(circleVb, Colors.VIEW_BG);
		// main selection
		drawTriangleStrip(circleVb, selected ? Colors.RED : levelColor, drawIndex);
	}

	private void updateDrawIndex() {
		if (circleVb == null)
			return;
		drawIndex = (int) (circleVb.capacity() * level / 2);
		drawIndex += drawIndex % 2;
	}

	@Override
	public void setViewLevel(float level) {
		super.setViewLevel(level);
		updateDrawIndex();
	}
	
	@Override
	protected float posToLevel(float x, float y) {
		float unitX = (x - width / 2) / width;
		float unitY = (y - height / 2) / height;
		float theta = (float) Math.atan(unitY / unitX) + � / 2;
		// atan ranges from 0 to �, and produces symmetric results around the y
		// axis.
		// we need 0 to 2*�, so ad � if right of x axis.
		if (unitX > 0)
			theta += �;
		// convert to level - remember, min theta is �/4, max is 7�/8
		float level = (4 * theta / � - 1) / 6;
		return level > 0 ? (level < 1 ? level : 1) : 0;
	}

	@Override
	protected void createChildren() {
		// none
	}
	
	@Override
	public void layoutChildren() {
		initCircleVbs(width, height);
	}
}
