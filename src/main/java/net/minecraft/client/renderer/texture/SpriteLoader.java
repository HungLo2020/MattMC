package net.minecraft.client.renderer.texture;

import net.blaze3d.platform.NativeImage;
import net.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.MetadataSectionType.WithValue;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Builds an atlas from resource packs. Rust (`assets/textures`) runs the
 * atlas definition's sprite sources, decodes and preprocesses sprites,
 * stitches them and generates mip levels; Java decodes extra metadata
 * sections (such as the GUI atlas's) and keeps the sprite objects.
 */
@Environment(EnvType.CLIENT)
public class SpriteLoader {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final ResourceLocation location;
	private final int maxSupportedTextureSize;
	private final int minWidth;
	private final int minHeight;

	public SpriteLoader(ResourceLocation resourceLocation, int i, int j, int k) {
		this.location = resourceLocation;
		this.maxSupportedTextureSize = i;
		this.minWidth = j;
		this.minHeight = k;
	}

	public static SpriteLoader create(TextureAtlas textureAtlas) {
		return new SpriteLoader(textureAtlas.location(), textureAtlas.maxSupportedTextureSize(), textureAtlas.getWidth(), textureAtlas.getHeight());
	}

	/**
	 * Decodes each kept sidecar's extra sections; a sprite whose sections fail
	 * to decode is dropped, as `SpriteResourceLoader` returned null for it.
	 */
	private static Map<ResourceLocation, List<WithValue<?>>> decodeExtraMetadata(NativeAtlasBuild build, Set<MetadataSectionType<?>> set) {
		Map<ResourceLocation, List<WithValue<?>>> metadata = new HashMap<>();
		if (set.isEmpty()) {
			return metadata;
		}
		List<NativeAtlasBuild.Metadata> kept = build.metadata();
		int[] rejected = new int[kept.size()];
		int count = 0;
		for (NativeAtlasBuild.Metadata sidecar : kept) {
			try {
				ResourceMetadata resourceMetadata = ResourceMetadata.fromJsonStream(new ByteArrayInputStream(sidecar.json().getBytes(StandardCharsets.UTF_8)));
				List<WithValue<?>> sections = resourceMetadata.getTypedSections(set);
				if (!sections.isEmpty()) {
					metadata.put(sidecar.name(), sections);
				}
			} catch (Exception exception) {
				LOGGER.error("Unable to parse metadata from {}", sidecar.name(), exception);
				rejected[count++] = sidecar.index();
			}
		}
		build.drop(java.util.Arrays.copyOf(rejected, count));
		return metadata;
	}

	private SpriteLoader.Preparations stitch(NativeAtlasBuild build, int mipLevel, Set<MetadataSectionType<?>> set, Executor executor) {
		Zone zone = Profiler.get().zone(() -> "stitch " + this.location);

		try {
			Map<ResourceLocation, List<WithValue<?>>> metadata = decodeExtraMetadata(build, set);
			NativeAtlasBuild.Layout layout = build.stitch(mipLevel, this.maxSupportedTextureSize, this.minWidth, this.minHeight);
			List<NativeAtlasBuild.Placed> placed = layout.sprites();
			int[] indices = new int[placed.size()];
			NativeImage[] sheets = new NativeImage[placed.size()];
			for (int position = 0; position < placed.size(); position++) {
				NativeAtlasBuild.Placed sprite = placed.get(position);
				indices[position] = sprite.index();
				sheets[position] = new NativeImage(sprite.sheetWidth(), sprite.sheetHeight(), false);
			}
			build.copy(0, indices, sheets);
			Map<ResourceLocation, TextureAtlasSprite> map = new HashMap<>();
			SpriteContents[] contents = new SpriteContents[placed.size()];
			for (int position = 0; position < placed.size(); position++) {
				NativeAtlasBuild.Placed sprite = placed.get(position);
				contents[position] = new SpriteContents(sprite.name(), sprite.width(), sprite.height(), sheets[position], sprite.frames(),
					sprite.frameRowSize(), sprite.interpolate(), sprite.transparent(), sprite.translucent(), metadata.getOrDefault(sprite.name(), List.of()));
				map.put(sprite.name(), new TextureAtlasSprite(this.location, contents[position], layout.width(), layout.height(), sprite.x(), sprite.y()));
			}
			TextureAtlasSprite missing = map.get(MissingTextureAtlasSprite.getLocation());
			int level = layout.mipLevel();
			CompletableFuture<Void> readyForUpload;
			if (level > 0) {
				readyForUpload = CompletableFuture.runAsync(() -> {
					try (build) {
						build.generateMips();
						NativeImage[][] levels = new NativeImage[contents.length][];
						for (int position = 0; position < contents.length; position++) {
							levels[position] = new NativeImage[level + 1];
							levels[position][0] = sheets[position];
						}
						for (int mip = 1; mip <= level; mip++) {
							NativeImage[] images = new NativeImage[contents.length];
							for (int position = 0; position < contents.length; position++) {
								images[position] = new NativeImage(sheets[position].getWidth() >> mip, sheets[position].getHeight() >> mip, false);
								levels[position][mip] = images[position];
							}
							build.copy(mip, indices, images);
						}
						for (int position = 0; position < contents.length; position++) {
							contents[position].byMipLevel = levels[position];
						}
					}
				}, executor);
			} else {
				build.close();
				readyForUpload = CompletableFuture.completedFuture(null);
			}
			return new SpriteLoader.Preparations(layout.width(), layout.height(), level, missing, map, readyForUpload);
		} catch (RuntimeException failure) {
			build.close();
			throw failure;
		} finally {
			if (zone != null) {
				zone.close();
			}
		}
	}

	public CompletableFuture<SpriteLoader.Preparations> loadAndStitch(
		ResourceManager resourceManager, ResourceLocation resourceLocation, int i, Executor executor, Set<MetadataSectionType<?>> set
	) {
		MultiPackResourceManager manager = MultiPackResourceManager.nativeManager(resourceManager);
		return CompletableFuture.supplyAsync(() -> NativeAtlasBuild.load(manager, this.location, resourceLocation, !set.isEmpty()), executor)
			.thenApply(build -> this.stitch(build, i, set, executor));
	}

	@Environment(EnvType.CLIENT)
	public record Preparations(
		int width, int height, int mipLevel, TextureAtlasSprite missing, Map<ResourceLocation, TextureAtlasSprite> regions, CompletableFuture<Void> readyForUpload
	) {
		@Nullable
		public TextureAtlasSprite getSprite(ResourceLocation resourceLocation) {
			return (TextureAtlasSprite)this.regions.get(resourceLocation);
		}
	}
}
