package com.kh.beatbot.view;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

import com.kh.beatbot.global.Colors;
import com.kh.beatbot.global.GeneralUtils;
import com.kh.beatbot.view.mesh.RoundedRect;

public abstract class BBView implements Comparable<BBView> {
	public class Position {
		public float x, y;

		Position(float x, float y) {
			set(x, y);
		}

		public void set(float x, float y) {
			this.x = x;
			this.y = y;
		}
	}

	public static final float � = (float) Math.PI;

	public static GLSurfaceViewBase root;

	protected List<BBView> children = new ArrayList<BBView>();
	protected BBView parent;

	public static GL10 gl;

	// where is the view currently clipped to?
	// used to keep track of SCISSOR clipping of parent views,
	// so child views don't draw outside of any parent (granparent, etc)
	// this should be reset every frame by the parent using resetClipWindow()
	public int currClipX = Integer.MIN_VALUE, currClipY = Integer.MIN_VALUE,
			currClipW = Integer.MAX_VALUE, currClipH = Integer.MAX_VALUE;

	public float absoluteX = 0, absoluteY = 0;
	public float x = 0, y = 0;
	public float width = 0, height = 0;

	protected float[] backgroundColor = Colors.BG_COLOR;
	protected float[] clearColor = Colors.BG_COLOR;

	private int id = -1; // optional

	private static FloatBuffer circleVb = null;
	private static final float CIRCLE_RADIUS = 100;

	protected boolean initialized = false;

	protected RoundedRect bgRect = null;

	protected float minX = 0, maxX = 0, minY = 0, maxY = 0;
	protected float borderWidth = 0, borderHeight = 0, borderOffset = 0;

	static { // init circle
		float theta = 0;
		float coords[] = new float[128];
		for (int i = 0; i < coords.length; i += 2) {
			coords[i] = (float) Math.cos(theta) * CIRCLE_RADIUS;
			coords[i + 1] = (float) Math.sin(theta) * CIRCLE_RADIUS;
			theta += 4 * Math.PI / coords.length;
		}
		circleVb = makeFloatBuffer(coords);
	}

	public BBView() {
		createChildren();
	}

	public void initBgRect(float[] fillColor, float[] borderColor) {
		bgRect = new RoundedRect(null, fillColor, borderColor);
	}
	
	public float getBgRectRadius() {
		return bgRect.cornerRadius;
	}
	
	public void addChild(BBView child) {
		children.add(child);
		if (initialized)
			child.initAll();
	}

	public int numChildren() {
		return children.size();
	}

	public void setDimensions(float width, float height) {
		this.width = width;
		this.height = height;
	}

	public void setPosition(float x, float y) {
		this.x = x;
		this.y = y;
		if (parent != null) {
			this.absoluteX = parent.absoluteX + x;
			this.absoluteY = parent.absoluteY + y;
		}
		layoutChildren();
	}

	public boolean containsPoint(float x, float y) {
		return x > this.x && x < this.x + width && y > this.y
				&& y < this.y + height;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	public abstract void init();

	public abstract void draw();

	protected abstract void createChildren();

	public abstract void layoutChildren();

	protected abstract void loadIcons();

	public void clipWindow(int parentClipX, int parentClipY, int parentClipW,
			int parentClipH) {
		currClipX = (int) absoluteX;
		currClipY = (int) (root.getHeight() - absoluteY - height);
		currClipW = (int) width;
		currClipH = (int) height;
		if (currClipX < parentClipX) {
			currClipW -= parentClipX - currClipX;
			currClipX = parentClipX;
		}
		float parentMaxX = parentClipX + parentClipW;
		if (parentMaxX > 1 && currClipX + currClipW > parentMaxX) {
			currClipW = parentClipW + parentClipW - currClipW;
		}
		if (currClipY < parentClipY) {
			currClipH -= parentClipY - currClipY;
			currClipY = parentClipY;
		}
		float parentMaxY = parentClipY + parentClipH;
		if (parentMaxY > 1 && currClipY + currClipH > parentMaxY) {
			currClipH = parentClipY + parentClipH - currClipY;
		}

		gl.glScissor(currClipX, currClipY, currClipW, currClipH);
	}

	public void initAll() {
		initBackgroundColor();
		init();
		for (BBView child : children) {
			child.initAll();
		}
		initialized = true;
	}

	public void drawAll() {
		// scissor ensures that each view can only draw within its rect
		gl.glEnable(GL10.GL_SCISSOR_TEST);
		if (parent != null) {
			clipWindow(parent.currClipX, parent.currClipY, parent.currClipW,
					parent.currClipH);
		} else {
			clipWindow(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE,
					Integer.MAX_VALUE);
		}
		if (bgRect != null) {
			bgRect.draw();
		}
		draw();
		drawChildren();
		gl.glDisable(GL10.GL_SCISSOR_TEST);
	}

	protected void drawChildren() {
		for (int i = 0; i < children.size(); i++) {
			// not using foreach to avoid concurrent modification
			BBView child = children.get(i);
			push();
			translate(child.x, child.y);
			child.drawAll();
			pop();
		}
	}
	
	public void initGl(GL10 _gl) {
		gl = _gl;
		loadAllIcons();
	}

	public void loadAllIcons() {
		loadIcons();
		for (BBView child : children) {
			child.loadAllIcons();
		}
	}

	protected void layoutBgRect(float borderWeight, float borderRadius) {
		if (bgRect != null) {
			borderOffset = borderWeight / 2;
			bgRect.setBorderWeight(borderWeight);
			bgRect.setCornerRadius(borderRadius);
			bgRect.layout(borderOffset, borderOffset, width - borderOffset * 2,
					height - borderOffset * 2);
			minX = minY = bgRect.cornerRadius + borderOffset;
			maxX = width - bgRect.cornerRadius - borderOffset;
			maxY = height - bgRect.cornerRadius - borderOffset;
			borderWidth = width - 2 * minX;
			borderHeight = height - 2 * minY;
		}
	}
	
	public void layout(BBView parent, float x, float y, float width,
			float height) {
		this.parent = parent;
		setDimensions(width, height);
		setPosition(x, y);
	}

	protected BBView findChildAt(float x, float y) {
		for (BBView child : children) {
			if (child.containsPoint(x, y)) {
				return child;
			}
		}
		return null;
	}

	public static final FloatBuffer makeFloatBuffer(List<Float> vertices) {
		return makeFloatBuffer(GeneralUtils.floatListToArray(vertices));
	}

	public static final FloatBuffer makeFloatBuffer(float[] vertices) {
		return makeFloatBuffer(vertices, 0);
	}

	public static final FloatBuffer makeFloatBuffer(float[] vertices,
			int position) {
		ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
		bb.order(ByteOrder.nativeOrder());
		FloatBuffer fb = bb.asFloatBuffer();
		fb.put(vertices);
		fb.position(position);
		return fb;
	}

	public static final FloatBuffer makeRectFloatBuffer(float x1, float y1,
			float x2, float y2) {
		return makeFloatBuffer(new float[] { x1, y1, x1, y2, x2, y2, x2, y1 });
	}

	public static final void translate(float x, float y) {
		gl.glTranslatef(x, y, 0);
	}

	public static final void scale(float x, float y) {
		gl.glScalef(x, y, 1);
	}

	public static final void push() {
		gl.glPushMatrix();
	}

	public static final void pop() {
		gl.glPopMatrix();
	}

	public static final void drawText(String text, float[] color, int height,
			float x, float y) {
		setColor(color);
		GLSurfaceViewBase.drawText(text, height, x, y);
	}

	public static final void drawRectangle(float x1, float y1, float x2,
			float y2, float[] color) {
		drawTriangleFan(makeRectFloatBuffer(x1, y1, x2, y2), color);
	}

	public static void drawRectangleOutline(float x1, float y1, float x2,
			float y2, float[] color, float width) {
		drawLines(makeRectFloatBuffer(x1, y1, x2, y2), color, width,
				GL10.GL_LINE_LOOP);
	}

	public static final void drawTriangleStrip(FloatBuffer vb, float[] color,
			int numVertices) {
		drawTriangleStrip(vb, color, 0, numVertices);
	}

	public static final void drawTriangleStrip(FloatBuffer vb, float[] color) {
		if (vb == null)
			return;
		drawTriangleStrip(vb, color, vb.capacity() / 2);
	}

	public static final void drawTriangleStrip(FloatBuffer vb, float[] color,
			int beginVertex, int endVertex) {
		if (vb == null)
			return;
		setColor(color);
		gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vb);
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, beginVertex, endVertex
				- beginVertex);
	}

	public static final void drawTriangleFan(FloatBuffer vb, float[] color) {
		setColor(color);
		gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vb);
		gl.glDrawArrays(GL10.GL_TRIANGLE_FAN, 0, vb.capacity() / 2);
	}

	public static final void drawLines(FloatBuffer vb, float[] color,
			float width, int type, int stride) {
		setColor(color);
		gl.glLineWidth(width);
		gl.glVertexPointer(2, GL10.GL_FLOAT, stride, vb);
		gl.glDrawArrays(type, 0, vb.capacity() / (2 + stride / 8));
	}

	public static final void drawLines(FloatBuffer vb, float[] color,
			float width, int type) {
		drawLines(vb, color, width, type, 0);
	}

	public static final void drawPoint(float pointSize, float[] color, float x,
			float y) {
		push();
		translate(x, y);
		float scale = pointSize / CIRCLE_RADIUS;
		scale(scale, scale);
		drawTriangleFan(circleVb, color);
		pop();
	}

	protected final float distanceFromCenterSquared(float x, float y) {
		return (x - width / 2) * (x - width / 2) + (y - height / 2)
				* (y - height / 2);
	}

	public final void setBackgroundColor(float[] color) {
		backgroundColor = color;
	}

	public static final void setColor(float[] color) {
		gl.glColor4f(color[0], color[1], color[2], color[3]);
	}

	protected void initBackgroundColor() {
		gl.glClearColor(backgroundColor[0], backgroundColor[1],
				backgroundColor[2], backgroundColor[3]);
	}

	public float viewX(float x) {
		return x * borderWidth + minX;
	}

	public float viewY(float y) {
		return (1 - y) * borderHeight + minY;
	}

	public float unitX(float viewX) {
		return (viewX - minX) / borderWidth;
	}

	public float unitY(float viewY) {
		// bottom == height in pixels == 0 in value
		// top == 0 in pixels == 1 in value
		return (height - viewY - minY) / borderHeight;
	}

	public float clipX(float x) {
		return x < minX ? minX : (x > maxX ? maxX : x);
	}

	public float clipY(float y) {
		return y < minY ? minY : (y > maxY ? maxY : y);
	}

	@Override
	public int compareTo(BBView another) {
		float diff = this.x - another.x;
		if (diff == 0)
			return 0;
		else if (diff > 0)
			return 1;
		else
			return -1;
	}
}
