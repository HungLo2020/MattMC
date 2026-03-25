package net.irisshaders.iris.pathways;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.vertex.BufferBuilder;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.MeshData;
import net.blaze3d.vertex.Tesselator;
import net.blaze3d.vertex.VertexFormat;
import net.vulkanic.VulkanicAPI;

/**
 * Renders a full-screen textured quad to the screen. Used in composite / deferred rendering.
 */
public class FullScreenQuadRenderer {
	public static final FullScreenQuadRenderer INSTANCE = new FullScreenQuadRenderer();

	private final GpuBuffer quad;

	private FullScreenQuadRenderer() {
		BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		bufferBuilder.addVertex(0.0F, 0.0F, 0.0F).setUv(0.0F, 0.0F);
		bufferBuilder.addVertex(1.0F, 0.0F, 0.0F).setUv(1.0F, 0.0F);
		bufferBuilder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
		bufferBuilder.addVertex(0.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);
		MeshData meshData = bufferBuilder.build();

		quad = VulkanicAPI.createBuffer(() -> "Quad", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
		meshData.close();
		Tesselator.getInstance().clear();

	}

	public static int init() {
		return -1;
	}

	public GpuBuffer getQuad() {
		return quad;
	}
}
