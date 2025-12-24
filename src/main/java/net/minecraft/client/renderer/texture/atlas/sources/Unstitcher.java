package net.minecraft.client.renderer.texture.atlas.sources;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record Unstitcher(ResourceLocation resource, List<Unstitcher.Region> regions, double xDivisor, double yDivisor) implements SpriteSource {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final MapCodec<Unstitcher> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("resource").forGetter(Unstitcher::resource),
				ExtraCodecs.nonEmptyList(Unstitcher.Region.CODEC.listOf()).fieldOf("regions").forGetter(Unstitcher::regions),
				Codec.DOUBLE.optionalFieldOf("divisor_x", 1.0).forGetter(Unstitcher::xDivisor),
				Codec.DOUBLE.optionalFieldOf("divisor_y", 1.0).forGetter(Unstitcher::yDivisor)
			)
			.apply(instance, Unstitcher::new)
	);

	@Override
	public void run(ResourceManager resourceManager, SpriteSource.Output output) {
		ResourceLocation resourceLocation = TEXTURE_ID_CONVERTER.idToFile(this.resource);
		Optional<Resource> optional = resourceManager.getResource(resourceLocation);
		if (optional.isPresent()) {
			LazyLoadedImage lazyLoadedImage = new LazyLoadedImage(resourceLocation, (Resource)optional.get(), this.regions.size());

			for (Unstitcher.Region region : this.regions) {
				output.add(region.sprite, new Unstitcher.RegionInstance(lazyLoadedImage, region, this.xDivisor, this.yDivisor));
			}
		} else {
			LOGGER.warn("Missing sprite: {}", resourceLocation);
		}
	}

	@Override
	public MapCodec<Unstitcher> codec() {
		return MAP_CODEC;
	}

	@Environment(EnvType.CLIENT)
	public record Region(ResourceLocation sprite, double x, double y, double width, double height) {
		public static final Codec<Unstitcher.Region> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					ResourceLocation.CODEC.fieldOf("sprite").forGetter(Unstitcher.Region::sprite),
					Codec.DOUBLE.fieldOf("x").forGetter(Unstitcher.Region::x),
					Codec.DOUBLE.fieldOf("y").forGetter(Unstitcher.Region::y),
					Codec.DOUBLE.fieldOf("width").forGetter(Unstitcher.Region::width),
					Codec.DOUBLE.fieldOf("height").forGetter(Unstitcher.Region::height)
				)
				.apply(instance, Unstitcher.Region::new)
		);
	}

	@Environment(EnvType.CLIENT)
	static class RegionInstance implements SpriteSource.SpriteSupplier {
		private final LazyLoadedImage image;
		private final Unstitcher.Region region;
		private final double xDivisor;
		private final double yDivisor;

		RegionInstance(LazyLoadedImage lazyLoadedImage, Unstitcher.Region region, double d, double e) {
			this.image = lazyLoadedImage;
			this.region = region;
			this.xDivisor = d;
			this.yDivisor = e;
		}

		public SpriteContents apply(SpriteResourceLoader spriteResourceLoader) {
			try {
				NativeImage nativeImage = this.image.get();
				double d = nativeImage.getWidth() / this.xDivisor;
				double e = nativeImage.getHeight() / this.yDivisor;
				int i = Mth.floor(this.region.x * d);
				int j = Mth.floor(this.region.y * e);
				int k = Mth.floor(this.region.width * d);
				int l = Mth.floor(this.region.height * e);
				NativeImage nativeImage2 = new NativeImage(NativeImage.Format.RGBA, k, l, false);
				nativeImage.copyRect(nativeImage2, i, j, 0, 0, k, l, false, false);
				return new SpriteContents(this.region.sprite, new FrameSize(k, l), nativeImage2);
			} catch (Exception var16) {
				Unstitcher.LOGGER.error("Failed to unstitch region {}", this.region.sprite, var16);
			} finally {
				this.image.release();
			}

			return MissingTextureAtlasSprite.create();
		}

		@Override
		public void discard() {
			this.image.release();
		}
	}
}
