package com.kh.beatbot.view.mesh;

import javax.microedition.khronos.opengles.GL11;

import com.kh.beatbot.global.Drawable;
import com.kh.beatbot.view.BBView;

public abstract class Shape implements Drawable {
	public static final float � = (float) Math.PI;
	
	protected ShapeGroup group;
	protected Mesh2D fillMesh, outlineMesh;
	protected float x, y, width, height;
	protected boolean shouldDraw;
	
	public Shape(ShapeGroup group, float x, float y, float width, float height) {
		// must draw via some parent group.  if one is given, use that, 
		// otherwise create a new group and render upon request using that group
		shouldDraw = group == null;
		this.group = group != null ? group : new ShapeGroup();
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
	
	public Shape(ShapeGroup group, float x, float y, float width, float height, Mesh2D fillMesh, Mesh2D outlineMesh) {
		this(group, x, y, width, height);
		this.fillMesh = fillMesh;
		this.outlineMesh = outlineMesh;
	}
	
	public Mesh2D getFillMesh() {
		return fillMesh;
	}
	
	public Mesh2D getOutlineMesh() {
		return outlineMesh;
	}
	
	@Override
	public float getX() {
		return x;
	}
	
	@Override
	public float getY() {
		return y;
	}
	
	@Override
	public float getWidth() {
		return width;
	}
	
	@Override
	public float getHeight() {
		return height;
	}
	
	@Override
	public void draw() {
		draw(x, y);
	}
	
	@Override
	public void draw(float x, float y) {
		draw(x, y, width, height);
	}

	@Override
	public void draw(float x, float y, float width, float height) {
		if (shouldDraw) {
			group.draw((GL11)BBView.gl, 1);
		}
	}
	
	public ShapeGroup getGroup() {
		return group;
	}
}