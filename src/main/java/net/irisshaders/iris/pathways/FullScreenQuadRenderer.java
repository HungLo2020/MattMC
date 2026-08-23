package net.irisshaders.iris.pathways;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.vertex.BufferBuilder;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.MeshData;
import net.blaze3d.vertex.Tesselator;
import net.blaze3d.vertex.VertexFormat;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a full-screen textured quad to the screen. Used in composite / deferred rendering.
 */
public class FullScreenQuadRenderer {
	public static final FullScreenQuadRenderer INSTANCE = new FullScreenQuadRenderer();

	@Nullable
	private final GpuBuffer quad;

	private FullScreenQuadRenderer() {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			this.quad = null;
			return;
		}
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
		if (this.quad == null) {
			throw new IllegalStateException("Java Iris fullscreen quad is unavailable while Rust owns whole-frame presentation");
		}
		return quad;
	}
}
