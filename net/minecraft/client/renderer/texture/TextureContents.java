package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record TextureContents(NativeImage image, @Nullable TextureMetadataSection metadata) implements Closeable {
	public static TextureContents load(ResourceManager resourceManager, ResourceLocation resourceLocation) throws IOException {
		Resource resource = resourceManager.getResourceOrThrow(resourceLocation);
		InputStream inputStream = resource.open();

		NativeImage nativeImage;
		try {
			nativeImage = NativeImage.read(inputStream);
		} catch (Throwable var8) {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (Throwable var7) {
					var8.addSuppressed(var7);
				}
			}

			throw var8;
		}

		if (inputStream != null) {
			inputStream.close();
		}

		TextureMetadataSection textureMetadataSection = (TextureMetadataSection)resource.metadata().getSection(TextureMetadataSection.TYPE).orElse(null);
		return new TextureContents(nativeImage, textureMetadataSection);
	}

	public static TextureContents createMissing() {
		return new TextureContents(MissingTextureAtlasSprite.generateMissingImage(), null);
	}

	public boolean blur() {
		return this.metadata != null ? this.metadata.blur() : false;
	}

	public boolean clamp() {
		return this.metadata != null ? this.metadata.clamp() : false;
	}

	public void close() {
		this.image.close();
	}
}
