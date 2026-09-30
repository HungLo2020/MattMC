package net.minecraft.client.renderer;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.Std140Builder;
import net.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CachedOrthoProjectionMatrixBuffer implements AutoCloseable {
	@Nullable
	private GpuBuffer buffer;
	@Nullable
	private GpuBufferSlice bufferSlice;
	private final String label;
	private final float zNear;
	private final float zFar;
	private final boolean invertY;
	private final boolean useZeroToOneDepthWhenVulkan;
	private float width;
	private float height;

	public CachedOrthoProjectionMatrixBuffer(String string, float f, float g, boolean bl) {
		this(string, f, g, bl, false);
	}

	public CachedOrthoProjectionMatrixBuffer(String string, float f, float g, boolean bl, boolean bl2) {
		this.label = "cached-ortho:" + string;
		this.zNear = f;
		this.zFar = g;
		this.invertY = bl;
		this.useZeroToOneDepthWhenVulkan = bl2;
		this.buffer = null;
		this.bufferSlice = null;
		return;
	}

	public void close() {
		if (this.buffer != null) {
			this.buffer.close();
		}
		this.buffer = null;
		this.bufferSlice = null;
	}

	/** Releases a compatibility projection UBO if Rust Vulkan ownership starts late. */
	public void ensureRustSemanticRoute() {
		if (this.buffer != null) {
			this.close();
		}
	}
}
