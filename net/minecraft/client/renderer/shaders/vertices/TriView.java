package net.minecraft.client.renderer.shaders.vertices;

/**
 * View interface for accessing triangle vertex data.
 * 
 * VERBATIM from IRIS: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/vertices/views/TriView.java
 */
public interface TriView {
	float x(int vertexIndex);
	float y(int vertexIndex);
	float z(int vertexIndex);
	float u(int vertexIndex);
	float v(int vertexIndex);
}
