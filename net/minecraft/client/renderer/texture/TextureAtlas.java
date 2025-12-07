package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class TextureAtlas extends AbstractTexture implements Dumpable, Tickable {
	private static final Logger LOGGER = LogUtils.getLogger();
	@Deprecated
	public static final ResourceLocation LOCATION_BLOCKS = ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
	@Deprecated
	public static final ResourceLocation LOCATION_PARTICLES = ResourceLocation.withDefaultNamespace("textures/atlas/particles.png");
	private List<SpriteContents> sprites = List.of();
	private List<TextureAtlasSprite.Ticker> animatedTextures = List.of();
	private Map<ResourceLocation, TextureAtlasSprite> texturesByName = Map.of();
	@Nullable
	private TextureAtlasSprite missingSprite;
	private final ResourceLocation location;
	private final int maxSupportedTextureSize;
	private int width;
	private int height;
	private int mipLevel;

	public TextureAtlas(ResourceLocation resourceLocation) {
		this.location = resourceLocation;
		this.maxSupportedTextureSize = RenderSystem.getDevice().getMaxTextureSize();
	}

	private void createTexture(int i, int j, int k) {
		LOGGER.info("Created: {}x{}x{} {}-atlas", i, j, k, this.location);
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.close();
		this.texture = gpuDevice.createTexture(this.location::toString, 7, TextureFormat.RGBA8, i, j, 1, k + 1);
		this.textureView = gpuDevice.createTextureView(this.texture);
		this.width = i;
		this.height = j;
		this.mipLevel = k;
	}

	public void upload(SpriteLoader.Preparations preparations) {
		this.createTexture(preparations.width(), preparations.height(), preparations.mipLevel());
		this.clearTextureData();
		this.setFilter(false, this.mipLevel > 1);
		this.texturesByName = Map.copyOf(preparations.regions());
		this.missingSprite = (TextureAtlasSprite)this.texturesByName.get(MissingTextureAtlasSprite.getLocation());
		if (this.missingSprite == null) {
			throw new IllegalStateException("Atlas '" + this.location + "' (" + this.texturesByName.size() + " sprites) has no missing texture sprite");
		} else {
			List<SpriteContents> list = new ArrayList();
			List<TextureAtlasSprite.Ticker> list2 = new ArrayList();

			for (TextureAtlasSprite textureAtlasSprite : preparations.regions().values()) {
				list.add(textureAtlasSprite.contents());

				try {
					textureAtlasSprite.uploadFirstFrame(this.texture);
				} catch (Throwable var10) {
					CrashReport crashReport = CrashReport.forThrowable(var10, "Stitching texture atlas");
					CrashReportCategory crashReportCategory = crashReport.addCategory("Texture being stitched together");
					crashReportCategory.setDetail("Atlas path", this.location);
					crashReportCategory.setDetail("Sprite", textureAtlasSprite);
					throw new ReportedException(crashReport);
				}

				TextureAtlasSprite.Ticker ticker = textureAtlasSprite.createTicker();
				if (ticker != null) {
					list2.add(ticker);
				}
			}

			this.sprites = List.copyOf(list);
			this.animatedTextures = List.copyOf(list2);
			if (SharedConstants.DEBUG_DUMP_TEXTURE_ATLAS) {
				Path path = TextureUtil.getDebugTexturePath();

				try {
					Files.createDirectories(path);
					this.dumpContents(this.location, path);
				} catch (IOException var9) {
					LOGGER.warn("Failed to dump atlas contents to {}", path);
				}
			}
		}
	}

	@Override
	public void dumpContents(ResourceLocation resourceLocation, Path path) throws IOException {
		String string = resourceLocation.toDebugFileName();
		TextureUtil.writeAsPNG(path, string, this.getTexture(), this.mipLevel, i -> i);
		dumpSpriteNames(path, string, this.texturesByName);
	}

	private static void dumpSpriteNames(Path path, String string, Map<ResourceLocation, TextureAtlasSprite> map) {
		Path path2 = path.resolve(string + ".txt");

		try {
			Writer writer = Files.newBufferedWriter(path2);

			try {
				for (Entry<ResourceLocation, TextureAtlasSprite> entry : map.entrySet().stream().sorted(Entry.comparingByKey()).toList()) {
					TextureAtlasSprite textureAtlasSprite = (TextureAtlasSprite)entry.getValue();
					writer.write(
						String.format(
							Locale.ROOT,
							"%s\tx=%d\ty=%d\tw=%d\th=%d%n",
							entry.getKey(),
							textureAtlasSprite.getX(),
							textureAtlasSprite.getY(),
							textureAtlasSprite.contents().width(),
							textureAtlasSprite.contents().height()
						)
					);
				}
			} catch (Throwable var9) {
				if (writer != null) {
					try {
						writer.close();
					} catch (Throwable var8) {
						var9.addSuppressed(var8);
					}
				}

				throw var9;
			}

			if (writer != null) {
				writer.close();
			}
		} catch (IOException var10) {
			LOGGER.warn("Failed to write file {}", path2, var10);
		}
	}

	public void cycleAnimationFrames() {
		if (this.texture != null) {
			for (TextureAtlasSprite.Ticker ticker : this.animatedTextures) {
				ticker.tickAndUpload(this.texture);
			}
		}
	}

	@Override
	public void tick() {
		this.cycleAnimationFrames();
	}

	public TextureAtlasSprite getSprite(ResourceLocation resourceLocation) {
		TextureAtlasSprite textureAtlasSprite = (TextureAtlasSprite)this.texturesByName.getOrDefault(resourceLocation, this.missingSprite);
		if (textureAtlasSprite == null) {
			throw new IllegalStateException("Tried to lookup sprite, but atlas is not initialized");
		} else {
			return textureAtlasSprite;
		}
	}

	public TextureAtlasSprite missingSprite() {
		return (TextureAtlasSprite)Objects.requireNonNull(this.missingSprite, "Atlas not initialized");
	}

	public void clearTextureData() {
		this.sprites.forEach(SpriteContents::close);
		this.animatedTextures.forEach(TextureAtlasSprite.Ticker::close);
		this.sprites = List.of();
		this.animatedTextures = List.of();
		this.texturesByName = Map.of();
		this.missingSprite = null;
	}

	public ResourceLocation location() {
		return this.location;
	}

	public int maxSupportedTextureSize() {
		return this.maxSupportedTextureSize;
	}

	int getWidth() {
		return this.width;
	}

	int getHeight() {
		return this.height;
	}
}
