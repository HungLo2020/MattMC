package net.minecraft.client.renderer.texture;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.systems.RenderSystem;
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
		this.texture = net.vulkanic.VulkanicAPI.createTexture(supplier, 5, TextureFormat.RGBA8, this.pixels.getWidth(), this.pixels.getHeight(), 1, 1);
		this.texture.setTextureFilter(FilterMode.NEAREST, false);
		this.textureView = net.vulkanic.VulkanicAPI.createTextureView(this.texture);
	}

	private void createTexture(String string) {
		this.texture = net.vulkanic.VulkanicAPI.createTexture(string, 5, TextureFormat.RGBA8, this.pixels.getWidth(), this.pixels.getHeight(), 1, 1);
		this.texture.setTextureFilter(FilterMode.NEAREST, false);
		this.textureView = net.vulkanic.VulkanicAPI.createTextureView(this.texture);
	}

	public void upload() {
		if (this.pixels != null && this.texture != null) {
			net.vulkanic.VulkanicAPI.createCommandEncoder().writeToTexture(this.texture, this.pixels);
		} else {
			LOGGER.warn("Trying to upload disposed texture {}", this.getTexture().getLabel());
		}
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
