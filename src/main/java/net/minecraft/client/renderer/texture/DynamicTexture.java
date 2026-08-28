package net.minecraft.client.renderer.texture;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.TextureFormat;
import net.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class DynamicTexture extends AbstractTexture implements Dumpable {
	private static final Logger LOGGER = LogUtils.getLogger();
	@Nullable
	private NativeImage pixels;
	private boolean semanticLinearFilter;
	private boolean semanticMipmaps;
	private boolean semanticClamp;

	public DynamicTexture(Supplier<String> supplier, NativeImage nativeImage) {
		this.pixels = nativeImage;
		this.createTexture(supplier);
		this.upload();
	}

	public DynamicTexture(String string, int i, int j, boolean bl) {
		this.pixels = new NativeImage(i, j, bl);
		this.createTexture(string);
	}

	public DynamicTexture(Supplier<String> supplier, int i, int j, boolean bl) {
		this.pixels = new NativeImage(i, j, bl);
		this.createTexture(supplier);
	}

	private void createTexture(Supplier<String> supplier) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			// Dynamic textures are copied into the Rust semantic asset registry;
			// constructing a Java GPU image here would create an unused renderer
			// resource before the semantic GUI/world route consumes the pixels.
			return;
		}
		this.texture = net.vulkanic.VulkanicAPI.createTexture(supplier, 5, TextureFormat.RGBA8, this.pixels.getWidth(), this.pixels.getHeight(), 1, 1);
		this.texture.setTextureFilter(FilterMode.NEAREST, false);
		this.textureView = net.vulkanic.VulkanicAPI.createTextureView(this.texture);
	}

	private void createTexture(String string) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			return;
		}
		this.texture = net.vulkanic.VulkanicAPI.createTexture(string, 5, TextureFormat.RGBA8, this.pixels.getWidth(), this.pixels.getHeight(), 1, 1);
		this.texture.setTextureFilter(FilterMode.NEAREST, false);
		this.textureView = net.vulkanic.VulkanicAPI.createTextureView(this.texture);
	}

	public void upload() {
		if (this.pixels == null) {
			LOGGER.warn("Trying to upload disposed dynamic texture");
			return;
		}
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			// A DynamicTexture is a CPU source in the whole-frame route. Its
			// registered resource identity is copied to VulkanicGAL; Java never
			// executes a texture upload or owns the resulting GPU image. The
			// source may be staged before registration during client bootstrap;
			// registration stages it again once the semantic identity exists.
			releaseJavaGpuTextureForSemanticRoute();
			net.vulkanic.gui.RustGalGuiRawImageAssets.stageDynamicTexture(this);
			return;
		}
		if (this.texture == null) {
			LOGGER.warn("Trying to upload dynamic texture before GPU initialization");
			return;
		}
		net.vulkanic.VulkanicAPI.createCommandEncoder().writeToTexture(this.texture, this.pixels);
	}

	/**
	 * The Rust whole-frame route keeps dynamic images as CPU semantic sources;
	 * there is deliberately no Java GPU texture on which to apply sampler state.
	 * Retain the producer's request for future semantic sampler admission while
	 * avoiding the legacy AbstractTexture exception during mod bootstrap.
	 */
	@Override
	public void setFilter(boolean linearFilter, boolean mipmap) {
		this.semanticLinearFilter = linearFilter;
		this.semanticMipmaps = mipmap;
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& !net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			super.setFilter(linearFilter, mipmap);
		}
	}

	@Override
	public void setClamp(boolean clamp) {
		this.semanticClamp = clamp;
		if (!net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			&& !net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			super.setClamp(clamp);
		}
	}

	public final boolean semanticLinearFilter() {
		return this.semanticLinearFilter;
	}

	public final boolean semanticMipmaps() {
		return this.semanticMipmaps;
	}

	public final boolean semanticClamp() {
		return this.semanticClamp;
	}

	@Nullable
	public NativeImage getPixels() {
		return this.pixels;
	}

	public void setPixels(NativeImage nativeImage) {
		if (this.pixels != null) {
			this.pixels.close();
		}

		this.pixels = nativeImage;
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			// Keep the Rust-owned copy current even when a producer replaces pixels
			// without immediately calling upload(). Unregistered textures are
			// deliberately ignored until TextureManager publishes their identity.
			releaseJavaGpuTextureForSemanticRoute();
			net.vulkanic.gui.RustGalGuiRawImageAssets.stageDynamicTexture(this);
		}
	}

	private void releaseJavaGpuTextureForSemanticRoute() {
		if (this.texture != null || this.textureView != null) {
			super.close();
		}
	}

	@Override
	public void close() {
		if (this.pixels != null) {
			this.pixels.close();
			this.pixels = null;
		}

		super.close();
	}

	@Override
	public void dumpContents(ResourceLocation resourceLocation, Path path) throws IOException {
		if (this.pixels != null) {
			String string = resourceLocation.toDebugFileName() + ".png";
			Path path2 = path.resolve(string);
			this.pixels.writeToFile(path2);
		}
	}
}
