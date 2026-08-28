package net.sodium.client.render.chunk.compile;

import net.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public class ChunkBuildContext {
    public final ChunkBuildBuffers buffers;
    public final BlockRenderCache cache;

    public ChunkBuildContext(ClientLevel level, ChunkVertexType vertexType) {
		this(level, vertexType,
			net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
					? false
					: net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.shouldUseSeparateAo());
	}

	/**
	 * Creates a CPU meshing context with an explicitly selected vertex layout.
	 * This is used by the Rust whole-frame source so it does not borrow Iris
	 * material-map state while Vulkan is selected.
	 */
	public ChunkBuildContext(ClientLevel level, ChunkVertexType vertexType, boolean separateAo) {
		this.buffers = new ChunkBuildBuffers(vertexType, separateAo);
        this.cache = new BlockRenderCache(Minecraft.getInstance(), level);
    }

    public void cleanup() {
        this.cache.cleanup();
    }

    public void destroy() {
        this.buffers.destroy();
        this.cache.cleanup();
    }
}
