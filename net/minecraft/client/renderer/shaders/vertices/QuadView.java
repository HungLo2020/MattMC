package net.minecraft.client.renderer.shaders.vertices;

/**
 * View interface for accessing quad vertex data.
 * 
 * VERBATIM from IRIS: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/vertices/views/QuadView.java
 */
public interface QuadView {
	float x(int vertexIndex);
	float y(int vertexIndex);
	float z(int vertexIndex);
	float u(int vertexIndex);
	float v(int vertexIndex);
}
