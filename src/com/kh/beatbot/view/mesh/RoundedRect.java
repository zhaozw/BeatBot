package com.kh.beatbot.view.mesh;

public class RoundedRect extends Shape {
	private float cornerRadius;
	
	public RoundedRect(ShapeGroup group, float x, float y, float width,
			float height, float cornerRadius, float[] fillColor,
			float[] outlineColor) {
		super(group, x, y, width, height, new Mesh2D(16 * 4 * 3), new Mesh2D(16 * 4 * 2));
		this.cornerRadius = cornerRadius;
		createVertices(fillColor, outlineColor);
	}
	
	protected void createVertices(float[] fillColor, float[] outlineColor) {
		float theta = 0, addX, addY;
		float centerX = x + width / 2;
		float centerY = y + height / 2;
		float lastX = 0, lastY = 0;
		for (int i = 0; i < outlineMesh.getNumVertices() / 2; i++) {
			if (theta < � / 2) { // lower right
				addX = width - cornerRadius;
				addY = height - cornerRadius;
			} else if (theta < �) { // lower left
				addX = cornerRadius;
				addY = height - cornerRadius;
			} else if (theta < 3 * � / 2) { // upper left
				addX = cornerRadius;
				addY = cornerRadius;
			} else { // upper right
				addX = width - cornerRadius;
				addY = cornerRadius;
			}
			float vertexX = (float) Math.cos(theta) * cornerRadius + addX + x;
			float vertexY = (float) Math.sin(theta) * cornerRadius + addY + y;
			if (lastX != 0 && lastY != 0) {
				fillMesh.vertex(vertexX, vertexY);
				fillMesh.vertex(lastX, lastY);
				fillMesh.vertex(centerX, centerY);
				outlineMesh.vertex(vertexX, vertexY);
				outlineMesh.vertex(lastX, lastY);
			}
			lastX = vertexX;
			lastY = vertexY;
			theta += 4 * � / outlineMesh.getNumVertices();
		}
		fillMesh.vertex(fillMesh.getVertices()[0], fillMesh.getVertices()[1]);
		fillMesh.vertex(lastX, lastY);
		fillMesh.vertex(centerX, centerY);
		outlineMesh.vertex(outlineMesh.getVertices()[0], outlineMesh.getVertices()[1]);
		outlineMesh.vertex(lastX, lastY);
		
		fillMesh.setColor(fillColor);
		outlineMesh.setColor(outlineColor);
	}
}
