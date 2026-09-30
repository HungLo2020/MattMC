package net.blaze3d.pipeline;

import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.AddressMode;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import java.util.OptionalInt;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class RenderTarget implements net.irisshaders.iris.targets.Blaze3dRenderTargetExt, net.irisshaders.iris.mixinterface.RenderTargetInterface {
	private static int UNNAMED_RENDER_TARGETS = 0;
	public int width;
	public int height;
	protected final String label;
	public final boolean useDepth;
	@Nullable
	protected GpuTexture colorTexture;
	@Nullable
	protected GpuTextureView colorTextureView;
	@Nullable
	protected GpuTexture depthTexture;
	@Nullable
	protected GpuTextureView depthTextureView;
	public FilterMode filterMode;
	// Iris: Buffer version tracking for detecting texture recreation
	private int iris$depthBufferVersion;
	private int iris$colorBufferVersion;

	public RenderTarget(@Nullable String string, boolean bl) {
		this.label = string == null ? "FBO " + UNNAMED_RENDER_TARGETS++ : string;
		this.useDepth = bl;
	}

	public void resize(int i, int j) {
		RenderSystem.assertOnRenderThread();
		this.destroyBuffers();
		this.createBuffers(i, j);
	}

	public void destroyBuffers() {
		RenderSystem.assertOnRenderThread();
		// Iris: Track buffer recreation
		iris$depthBufferVersion++;
		iris$colorBufferVersion++;
		
		if (this.depthTexture != null) {
			this.depthTexture.close();
			this.depthTexture = null;
		}

		if (this.depthTextureView != null) {
			this.depthTextureView.close();
			this.depthTextureView = null;
		}

		if (this.colorTexture != null) {
			this.colorTexture.close();
			this.colorTexture = null;
		}

		if (this.colorTextureView != null) {
			this.colorTextureView.close();
			this.colorTextureView = null;
		}
	}

	/** Releases compatibility attachments if Rust Vulkan ownership begins after target construction. */
	public void ensureRustSemanticRoute() {
		if (this.colorTexture != null || this.colorTextureView != null
			|| this.depthTexture != null || this.depthTextureView != null) {
			this.destroyBuffers();
		}
	}

	public void createBuffers(int i, int j) {
		RenderSystem.assertOnRenderThread();
		int k = net.vulkanic.VulkanicAPI.getBackendMaxTextureSize();
		if (i > 0 && i <= k && j > 0 && j <= k) {
			this.width = i;
			this.height = j;
			// Rust owns the acquired color/depth attachments and presentation
			// target. Keep dimensions for semantic extraction and layout, but do
			// not allocate a second Java Vulkan framebuffer.
			this.filterMode = FilterMode.NEAREST;
			return;
		} else {
			throw new IllegalArgumentException("Window " + i + "x" + j + " size out of bounds (max. size: " + k + ")");
		}
	}

	public void setFilterMode(FilterMode filterMode) {
		this.setFilterMode(filterMode, false);
	}

	private void setFilterMode(FilterMode filterMode, boolean bl) {
		if (this.colorTexture == null) {
			throw new IllegalStateException("Can't change filter mode, color texture doesn't exist yet");
		} else {
			if (bl || filterMode != this.filterMode) {
				this.filterMode = filterMode;
				this.colorTexture.setTextureFilter(filterMode, false);
			}
		}
	}

	@Nullable
	public GpuTexture getColorTexture() {
		return this.colorTexture;
	}

	@Nullable
	public GpuTextureView getColorTextureView() {
		return this.colorTextureView;
	}

	@Nullable
	public GpuTexture getDepthTexture() {
		return this.depthTexture;
	}

	@Nullable
	public GpuTextureView getDepthTextureView() {
		return this.depthTextureView;
	}
	
	// Iris: Blaze3dRenderTargetExt implementation
	@Override
	public int iris$getDepthBufferVersion() {
		return iris$depthBufferVersion;
	}

	@Override
	public int iris$getColorBufferVersion() {
		return iris$colorBufferVersion;
	}
	
}
