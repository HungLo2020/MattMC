package net.minecraft.client.renderer.texture;

import net.blaze3d.textures.AddressMode;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractTexture implements AutoCloseable, net.irisshaders.iris.mixinterface.AbstractTextureExtended {
	@Nullable
	protected GpuTexture texture;
	@Nullable
	protected GpuTextureView textureView;
	
	// Iris: From MixinAbstractTexture - texture tracking
	private GpuTexture lastChecked;

	public void close() {
		if (this.texture != null) {
			this.texture.close();
			this.texture = null;
		}

		if (this.textureView != null) {
			this.textureView.close();
			this.textureView = null;
		}
	}

	/** Releases only the legacy GPU allocation when Rust owns selected Vulkan. */
	public void ensureRustSemanticRoute() {
		this.closeGpuAllocation();
	}

	protected final void closeGpuAllocation() {
		if (this.texture != null) {
			this.texture.close();
			this.texture = null;
		}
		if (this.textureView != null) {
			this.textureView.close();
			this.textureView = null;
		}
	}

}
