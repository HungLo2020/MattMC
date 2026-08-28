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

	public void setClamp(boolean bl) {
		ensureJavaTextureAccessAvailable("set clamp");
		if (this.texture == null) {
			throw new IllegalStateException("Texture does not exist, can't change its clamp before something initializes it");
		} else {
			this.texture.setAddressMode(bl ? AddressMode.CLAMP_TO_EDGE : AddressMode.REPEAT);
		}
	}

	public void setFilter(boolean bl, boolean bl2) {
		ensureJavaTextureAccessAvailable("set filter");
		if (this.texture == null) {
			throw new IllegalStateException("Texture does not exist, can't get change its filter before something initializes it");
		} else {
			this.texture.setTextureFilter(bl ? FilterMode.LINEAR : FilterMode.NEAREST, bl2);
		}
	}

	public void setUseMipmaps(boolean bl) {
		ensureJavaTextureAccessAvailable("set mipmaps");
		if (this.texture == null) {
			throw new IllegalStateException("Texture does not exist, can't get change its filter before something initializes it");
		} else {
			this.texture.setUseMipmaps(bl);
		}
	}

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
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			this.closeGpuAllocation();
		}
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

	public GpuTexture getTexture() {
		ensureJavaTextureAccessAvailable("get texture");
		if (this.texture == null) {
			throw new IllegalStateException("Texture does not exist, can't get it before something initializes it");
		} else {
			// Iris tracking is a compatibility-only GPU registry. Rust whole-frame
			// semantic collectors must not publish Java texture state into it.
			if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& !net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
				&& lastChecked != this.texture) {
				lastChecked = this.texture;
				net.irisshaders.iris.pbr.TextureTracker.INSTANCE.trackTexture(net.vulkanic.VulkanicCoreAPI.textureId(lastChecked), this);
			}
			
			return this.texture;
		}
	}

	public GpuTextureView getTextureView() {
		ensureJavaTextureAccessAvailable("get texture view");
		if (this.textureView == null) {
			throw new IllegalStateException("Texture view does not exist, can't get it before something initializes it");
		} else {
			if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
				&& !net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
				&& this.texture != null && lastChecked != this.texture) {
				lastChecked = this.texture;
				net.irisshaders.iris.pbr.TextureTracker.INSTANCE.trackTexture(net.vulkanic.VulkanicCoreAPI.textureId(lastChecked), this);
			}
			return this.textureView;
		}
	}

	private static void ensureJavaTextureAccessAvailable(String operation) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			throw new IllegalStateException("Java texture " + operation + " is unavailable while Rust owns Vulkan rendering");
		}
	}
}
