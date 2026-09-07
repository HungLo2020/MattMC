package net.minecraft.client.renderer.texture;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.textures.GpuTexture;
import net.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.MetadataSectionType.WithValue;
import net.minecraft.util.ARGB;
import net.sodium.client.SodiumClientMod;
import net.sodium.client.render.chunk.compile.pipeline.SpriteContentsExtension;
import net.sodium.client.util.NativeImageHelper;
import net.sodium.client.util.color.ColorSRGB;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SpriteContents implements Stitcher.Entry, AutoCloseable, SpriteContentsExtension, net.irisshaders.iris.pbr.SpriteContentsExtension, net.irisshaders.iris.pbr.texture.SpriteContentsExtension {
	private static final Logger LOGGER = LogUtils.getLogger();
	final ResourceLocation name;
	final int width;
	final int height;
	public final NativeImage originalImage;
	public NativeImage[] byMipLevel;
	@Nullable
	public final SpriteContents.AnimatedTexture animatedTexture;
	private final List<WithValue<?>> additionalMetadata;
	// Sodium: Transparency tracking (from SpriteContentsMixin scan package)
	private boolean sodium$hasTransparentPixels = false;
	private boolean sodium$hasTranslucentPixels = false;
	
	// Iris: From MixinSpriteContents - ticker tracking
	@Nullable
	private SpriteContents.Ticker createdTicker;
	
	// Iris PBR: From texture.pbr.MixinSpriteContents - PBR sprite holder
	@Nullable
	private net.irisshaders.iris.pbr.texture.PBRSpriteHolder iris$pbrHolder;

	public SpriteContents(ResourceLocation resourceLocation, FrameSize frameSize, NativeImage nativeImage) {
		this(resourceLocation, frameSize, nativeImage, Optional.empty(), List.of());
	}

	public SpriteContents(
		ResourceLocation resourceLocation, FrameSize frameSize, NativeImage nativeImage, Optional<AnimationMetadataSection> optional, List<WithValue<?>> list
	) {
		this.name = resourceLocation;
		this.width = frameSize.width();
		this.height = frameSize.height();
		this.additionalMetadata = list;
		this.animatedTexture = (SpriteContents.AnimatedTexture)optional.map(
				animationMetadataSection -> this.createAnimatedTexture(frameSize, nativeImage.getWidth(), nativeImage.getHeight(), animationMetadataSection)
			)
			.orElse(null);
		
		// Sodium: Fill in transparent pixel colors before setting originalImage (from SpriteContentsMixin mipmaps)
		sodium$fillInTransparentPixelColors(nativeImage);
		
		// Sodium: Scan sprite contents for transparency before setting originalImage (from SpriteContentsMixin scan)
		sodium$scanSpriteContents(nativeImage);
		
		this.originalImage = nativeImage;
		this.byMipLevel = new NativeImage[]{this.originalImage};
	}

	public void increaseMipLevel(int i) {
		try {
			// Iris: From MixinSpriteContents - redirect mipmap generation to custom generator if available
			NativeImage[] result;
			if (this instanceof net.irisshaders.iris.pbr.mipmap.CustomMipmapGenerator.Provider provider) {
				net.irisshaders.iris.pbr.mipmap.CustomMipmapGenerator generator = provider.getMipmapGenerator();
				if (generator != null) {
					try {
						result = generator.generateMipLevels(this.byMipLevel, i);
					} catch (Exception e) {
						net.irisshaders.iris.Iris.logger.error("ERROR MIPMAPPING", e);
						result = MipmapGenerator.generateMipLevels(this.byMipLevel, i);
					}
				} else {
					result = MipmapGenerator.generateMipLevels(this.byMipLevel, i);
				}
			} else {
				result = MipmapGenerator.generateMipLevels(this.byMipLevel, i);
			}
			this.byMipLevel = result;
		} catch (Throwable var5) {
			CrashReport crashReport = CrashReport.forThrowable(var5, "Generating mipmaps for frame");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Frame being iterated");
			crashReportCategory.setDetail("Sprite name", this.name);
			crashReportCategory.setDetail("Sprite size", () -> this.width + " x " + this.height);
			crashReportCategory.setDetail("Sprite frames", () -> this.getFrameCount() + " frames");
			crashReportCategory.setDetail("Mipmap levels", i);
			crashReportCategory.setDetail("Original image size", () -> this.originalImage.getWidth() + "x" + this.originalImage.getHeight());
			throw new ReportedException(crashReport);
		}
	}

	private int getFrameCount() {
		return this.animatedTexture != null ? this.animatedTexture.frames.size() : 1;
	}

	public boolean isAnimated() {
		return this.getFrameCount() > 1;
	}

	@Nullable
	private SpriteContents.AnimatedTexture createAnimatedTexture(FrameSize frameSize, int i, int j, AnimationMetadataSection animationMetadataSection) {
		int k = i / frameSize.width();
		int l = j / frameSize.height();
		int m = k * l;
		int n = animationMetadataSection.defaultFrameTime();
		List<SpriteContents.FrameInfo> list;
		if (animationMetadataSection.frames().isEmpty()) {
			list = new ArrayList(m);

			for (int o = 0; o < m; o++) {
				list.add(new SpriteContents.FrameInfo(o, n));
			}
		} else {
			List<AnimationFrame> list2 = (List<AnimationFrame>)animationMetadataSection.frames().get();
			list = new ArrayList(list2.size());

			for (AnimationFrame animationFrame : list2) {
				list.add(new SpriteContents.FrameInfo(animationFrame.index(), animationFrame.timeOr(n)));
			}

			int p = 0;
			IntSet intSet = new IntOpenHashSet();

			for (Iterator<SpriteContents.FrameInfo> iterator = list.iterator(); iterator.hasNext(); p++) {
				SpriteContents.FrameInfo frameInfo = (SpriteContents.FrameInfo)iterator.next();
				boolean bl = true;
				if (frameInfo.time <= 0) {
					LOGGER.warn("Invalid frame duration on sprite {} frame {}: {}", this.name, p, frameInfo.time);
					bl = false;
				}

				if (frameInfo.index < 0 || frameInfo.index >= m) {
					LOGGER.warn("Invalid frame index on sprite {} frame {}: {}", this.name, p, frameInfo.index);
					bl = false;
				}

				if (bl) {
					intSet.add(frameInfo.index);
				} else {
					iterator.remove();
				}
			}

			int[] is = IntStream.range(0, m).filter(ix -> !intSet.contains(ix)).toArray();
			if (is.length > 0) {
				LOGGER.warn("Unused frames in sprite {}: {}", this.name, Arrays.toString(is));
			}
		}

		return list.size() <= 1 ? null : new SpriteContents.AnimatedTexture(List.copyOf(list), k, animationMetadataSection.interpolatedFrames());
	}

	private String graphicsAuditUploadedRgbaFnv64;

	public String graphicsAuditUploadedRgbaFnv64() { return this.graphicsAuditUploadedRgbaFnv64; }

	public String graphicsAuditFirstFrameRgbaFnv64() {
		if (this.animatedTexture == null || this.width > 64 || this.height > 64) return null;
		int frame = this.animatedTexture.frames.getFirst().index();
		int x = this.animatedTexture.getFrameX(frame) * this.width;
		int y = this.animatedTexture.getFrameY(frame) * this.height;
		long pointer = NativeImageHelper.getPointerRGBA(this.byMipLevel[0]);
		long hash = 0xcbf29ce484222325L;
		for (int row = 0; row < this.height; row++) {
			long start = pointer + ((long)(y + row) * this.byMipLevel[0].getWidth() + x) * 4;
			for (int byteIndex = 0; byteIndex < this.width * 4; byteIndex++) {
				hash = (hash ^ (org.lwjgl.system.MemoryUtil.memGetByte(start + byteIndex) & 255L)) * 0x100000001b3L;
			}
		}
		return String.format(java.util.Locale.ROOT, "%016x", hash);
	}

	public void upload(int i, int j, int k, int l, NativeImage[] nativeImages, GpuTexture gpuTexture) {
		for (int m = 0; m < this.byMipLevel.length; m++) {
			VulkanicAPI.createCommandEncoder()
				.writeToTexture(gpuTexture, nativeImages[m], m, 0, i >> m, j >> m, this.width >> m, this.height >> m, k >> m, l >> m);
		}
		// Capture-only observation of pixels actually supplied to the unchanged
		// upload above. Never select a frame or perform another GPU operation.
		if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")
			&& this.name.toString().equals("minecraft:block/magma") && this.width <= 64 && this.height <= 64) {
			long hash = 0xcbf29ce484222325L;
			long pointer = NativeImageHelper.getPointerRGBA(nativeImages[0]);
			for (int y = 0; y < this.height; y++) {
				long row = pointer + ((long)(l + y) * nativeImages[0].getWidth() + k) * 4;
				for (int x = 0; x < this.width * 4; x++) {
					hash = (hash ^ (org.lwjgl.system.MemoryUtil.memGetByte(row + x) & 255L)) * 0x100000001b3L;
				}
			}
			this.graphicsAuditUploadedRgbaFnv64 = String.format(java.util.Locale.ROOT, "%016x", hash);
		}
	}

	@Override
	public int width() {
		return this.width;
	}

	@Override
	public int height() {
		return this.height;
	}

	@Override
	public ResourceLocation name() {
		return this.name;
	}

	public IntStream getUniqueFrames() {
		return this.animatedTexture != null ? this.animatedTexture.getUniqueFrames() : IntStream.of(1);
	}

	@Nullable
	public SpriteTicker createTicker() {
		SpriteTicker ticker = this.animatedTexture != null ? this.animatedTexture.createTicker() : null;
		
		// Iris: From MixinSpriteContents - track created ticker
		if (ticker instanceof SpriteContents.Ticker innerTicker) {
			createdTicker = innerTicker;
		}
		
		return ticker;
	}

	public <T> Optional<T> getAdditionalMetadata(MetadataSectionType<T> metadataSectionType) {
		for (WithValue<?> withValue : this.additionalMetadata) {
			Optional<T> optional = withValue.unwrapToType(metadataSectionType);
			if (optional.isPresent()) {
				return optional;
			}
		}

		return Optional.empty();
	}

	public void close() {
		for (NativeImage nativeImage : this.byMipLevel) {
			nativeImage.close();
		}
		// Iris PBR: From texture.pbr.MixinSpriteContents - close PBR holder
		if (iris$pbrHolder != null) {
			iris$pbrHolder.close();
		}
	}

	public String toString() {
		return "SpriteContents{name=" + this.name + ", frameCount=" + this.getFrameCount() + ", height=" + this.height + ", width=" + this.width + "}";
	}

	public boolean isTransparent(int i, int j, int k) {
		int l = j;
		int m = k;
		if (this.animatedTexture != null) {
			l = j + this.animatedTexture.getFrameX(i) * this.width;
			m = k + this.animatedTexture.getFrameY(i) * this.height;
		}

		return ARGB.alpha(this.originalImage.getPixel(l, m)) == 0;
	}

	public void uploadFirstFrame(int i, int j, GpuTexture gpuTexture) {
		if (this.animatedTexture != null) {
			this.animatedTexture.uploadFirstFrame(i, j, gpuTexture);
		} else {
			this.upload(i, j, 0, 0, this.byMipLevel, gpuTexture);
		}
	}

	@Environment(EnvType.CLIENT)
	public class AnimatedTexture {
		public final List<SpriteContents.FrameInfo> frames;
		public final int frameRowSize;
		private final boolean interpolateFrames;

		AnimatedTexture(final List<SpriteContents.FrameInfo> list, final int i, final boolean bl) {
			this.frames = list;
			this.frameRowSize = i;
			this.interpolateFrames = bl;
		}

		public int getFrameX(int i) {
			return i % this.frameRowSize;
		}

		public int getFrameY(int i) {
			return i / this.frameRowSize;
		}

		public void uploadFrame(int i, int j, int k, GpuTexture gpuTexture) {
			int l = this.getFrameX(k) * SpriteContents.this.width;
			int m = this.getFrameY(k) * SpriteContents.this.height;
			SpriteContents.this.upload(i, j, l, m, SpriteContents.this.byMipLevel, gpuTexture);
		}

		public SpriteTicker createTicker() {
			return SpriteContents.this.new Ticker(this, this.interpolateFrames ? SpriteContents.this.new InterpolationData() : null);
		}

		public void uploadFirstFrame(int i, int j, GpuTexture gpuTexture) {
			this.uploadFrame(i, j, ((SpriteContents.FrameInfo)this.frames.get(0)).index, gpuTexture);
		}

		public IntStream getUniqueFrames() {
			return this.frames.stream().mapToInt(frameInfo -> frameInfo.index).distinct();
		}
	}

	@Environment(EnvType.CLIENT)
	public record FrameInfo(int index, int time) {
	}

	@Environment(EnvType.CLIENT)
	public final class InterpolationData implements AutoCloseable {
		private final NativeImage[] activeFrame = new NativeImage[SpriteContents.this.byMipLevel.length];
		// Sodium: Parent reference for optimized interpolation (merged from SpriteContentsInterpolationMixin)
		private SpriteContents parent;
		private static final int STRIDE = 4;

		InterpolationData() {
			// Sodium: Assign parent (merged from SpriteContentsInterpolationMixin)
			this.parent = SpriteContents.this;
			for (int i = 0; i < this.activeFrame.length; i++) {
				int j = SpriteContents.this.width >> i;
				int k = SpriteContents.this.height >> i;
				this.activeFrame[i] = new NativeImage(j, k, false);
			}
		}

		void uploadInterpolatedFrame(int i, int j, SpriteContents.Ticker ticker, GpuTexture gpuTexture) {
			// Sodium: Optimized interpolated frame upload (merged from SpriteContentsInterpolationMixin)
			SpriteContents.AnimatedTexture animation = ticker.animationInfo;
			SpriteContents.AnimatedTexture animation2 = ticker.animationInfo;
			List<SpriteContents.FrameInfo> frames = animation.frames;
			SpriteContents.FrameInfo animationFrame = (SpriteContents.FrameInfo) (Object) frames.get(ticker.frame);

			int curIndex = animationFrame.index();
			int nextIndex = ((SpriteContents.FrameInfo) (Object) animation2.frames.get((ticker.frame + 1) % frames.size())).index();

			if (curIndex == nextIndex) {
				return;
			}

			// The mix factor between the current and next frame
			float mix = 1.0F - (float) ticker.subFrame / (float) animationFrame.time();

			for (int layer = 0; layer < this.activeFrame.length; layer++) {
				int width = this.parent.width() >> layer;
				int height = this.parent.height() >> layer;

				int curX = ((curIndex % animation2.frameRowSize) * width);
				int curY = ((curIndex / animation2.frameRowSize) * height);

				int nextX = ((nextIndex % animation2.frameRowSize) * width);
				int nextY = ((nextIndex / animation2.frameRowSize) * height);

				NativeImage src = this.parent.byMipLevel[layer];
				NativeImage dst = this.activeFrame[layer];

				long ppSrcPixel = NativeImageHelper.getPointerRGBA(src);
				long ppDstPixel = NativeImageHelper.getPointerRGBA(dst);

				for (int layerY = 0; layerY < height; layerY++) {
					// Pointers to the pixel array for the current and next frame
					long pRgba1 = ppSrcPixel + (curX + (long) (curY + layerY) * src.getWidth()) * STRIDE;
					long pRgba2 = ppSrcPixel + (nextX + (long) (nextY + layerY) * src.getWidth()) * STRIDE;

					for (int layerX = 0; layerX < width; layerX++) {
						int rgba1 = org.lwjgl.system.MemoryUtil.memGetInt(pRgba1);
						int rgba2 = org.lwjgl.system.MemoryUtil.memGetInt(pRgba2);

						// Mix the RGB components and truncate the A component
						int mixedRgb = net.sodium.api.util.ColorMixer.mix(rgba1, rgba2, mix) & 0x00FFFFFF;

						// Take the A component from the source pixel
						int alpha = rgba1 & 0xFF000000;

						// Update the pixel within the interpolated frame using the combined RGB and A components
						org.lwjgl.system.MemoryUtil.memPutInt(ppDstPixel, mixedRgb | alpha);

						pRgba1 += STRIDE;
						pRgba2 += STRIDE;

						ppDstPixel += STRIDE;
					}
				}
			}

			this.parent.upload(i, j, 0, 0, this.activeFrame, gpuTexture);
		}

		private int getPixel(SpriteContents.AnimatedTexture animatedTexture, int i, int j, int k, int l) {
			return SpriteContents.this.byMipLevel[j]
				.getPixel(k + (animatedTexture.getFrameX(i) * SpriteContents.this.width >> j), l + (animatedTexture.getFrameY(i) * SpriteContents.this.height >> j));
		}

		public void close() {
			for (NativeImage nativeImage : this.activeFrame) {
				nativeImage.close();
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public class Ticker implements SpriteTicker {
		public int frame;
		public int subFrame;
		public final SpriteContents.AnimatedTexture animationInfo;
		@Nullable
		public final SpriteContents.InterpolationData interpolationData;
		
		// Sodium: From SpriteContentsTickerMixin - parent tracking for on-demand animation
		private SpriteContents parent;

		Ticker(final SpriteContents.AnimatedTexture animatedTexture, @Nullable final SpriteContents.InterpolationData interpolationData) {
			this.animationInfo = animatedTexture;
			this.interpolationData = interpolationData;
			// Sodium: From SpriteContentsTickerMixin - assign parent
			this.parent = SpriteContents.this;
		}

		@Override
		public void tickAndUpload(int i, int j, GpuTexture gpuTexture) {
			// Sodium: From SpriteContentsTickerMixin - on-demand animation check
			boolean onDemand = SodiumClientMod.options().performance.animateOnlyVisibleTextures;
			
			if (onDemand && !net.sodium.client.render.texture.SpriteContentsExtension.isActive(this.parent)) {
				this.subFrame++;
				if (this.subFrame >= ((SpriteContents.FrameInfo)this.animationInfo.frames.get(this.frame)).time()) {
					this.frame = (this.frame + 1) % this.animationInfo.frames.size();
					this.subFrame = 0;
				}
				return; // Skip the upload
			}
			
			this.subFrame++;
			SpriteContents.FrameInfo frameInfo = (SpriteContents.FrameInfo)this.animationInfo.frames.get(this.frame);
			if (this.subFrame >= frameInfo.time) {
				int k = frameInfo.index;
				this.frame = (this.frame + 1) % this.animationInfo.frames.size();
				this.subFrame = 0;
				int l = ((SpriteContents.FrameInfo)this.animationInfo.frames.get(this.frame)).index;
				if (k != l) {
					this.animationInfo.uploadFrame(i, j, l, gpuTexture);
				}
			} else if (this.interpolationData != null) {
				this.interpolationData.uploadInterpolatedFrame(i, j, this, gpuTexture);
			}
			
			// Sodium: From SpriteContentsTickerMixin - reset active flag after upload
			net.sodium.client.render.texture.SpriteContentsExtension.setActive(this.parent, false);
		}

		@Override
		public void close() {
			if (this.interpolationData != null) {
				this.interpolationData.close();
			}
		}
	}
	
	// Sodium: Scan sprite contents for transparency (from SpriteContentsMixin scan package)
	private void sodium$scanSpriteContents(NativeImage nativeImage) {
		final long ppPixel = NativeImageHelper.getPointerRGBA(nativeImage);
		final int pixelCount = nativeImage.getHeight() * nativeImage.getWidth();

		for (int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
			int color = org.lwjgl.system.MemoryUtil.memGetInt(ppPixel + (pixelIndex * 4L));
			int alpha = net.sodium.api.util.ColorABGR.unpackAlpha(color);

			// 25 is used as the threshold since the alpha cutoff is 0.1
			if (alpha <= 25) { // 0.1 * 255
				this.sodium$hasTransparentPixels = true;
			} else if (alpha < 255) {
				this.sodium$hasTranslucentPixels = true;
			}
		}

		// the image contains transparency also if there are translucent pixels,
		// since translucent pixels prevent a downgrade to the opaque render pass just as transparent pixels do
		this.sodium$hasTransparentPixels |= this.sodium$hasTranslucentPixels;
	}

	@Override
	public boolean sodium$hasTransparentPixels() {
		return this.sodium$hasTransparentPixels;
	}

	@Override
	public boolean sodium$hasTranslucentPixels() {
		return this.sodium$hasTranslucentPixels;
	}

	/**
	 * Sodium: Fixes a common issue in image editing programs where fully transparent pixels are saved with fully black colors.
	 * (Merged from SpriteContentsMixin mipmaps package)
	 *
	 * This causes issues with mipmapped texture filtering, since the black color is used to calculate the final color
	 * even though the alpha value is zero. While ideally it would be disregarded, we do not control that. Instead,
	 * this code tries to calculate a decent average color to assign to these fully-transparent pixels so that their
	 * black color does not leak over into sampling.
	 */
	private static void sodium$fillInTransparentPixelColors(NativeImage nativeImage) {
		final long ppPixel = NativeImageHelper.getPointerRGBA(nativeImage);
		final int pixelCount = nativeImage.getHeight() * nativeImage.getWidth();

		// Calculate an average color from all pixels that are not completely transparent.
		// This average is weighted based on the (non-zero) alpha value of the pixel.
		float r = 0.0f;
		float g = 0.0f;
		float b = 0.0f;

		float totalWeight = 0.0f;

		for (int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
			long pPixel = ppPixel + (pixelIndex * 4L);

			int color = org.lwjgl.system.MemoryUtil.memGetInt(pPixel);
			int alpha = net.sodium.api.util.ColorABGR.unpackAlpha(color);

			// Ignore all fully-transparent pixels for the purposes of computing an average color.
			if (alpha != 0) {
				float weight = (float) alpha;

				// Make sure to convert to linear space so that we don't lose brightness.
				r += ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackRed(color)) * weight;
				g += ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackGreen(color)) * weight;
				b += ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackBlue(color)) * weight;

				totalWeight += weight;
			}
		}

		// Bail if none of the pixels are semi-transparent.
		if (totalWeight == 0.0f) {
			return;
		}

		r /= totalWeight;
		g /= totalWeight;
		b /= totalWeight;

		// Convert that color in linear space back to sRGB.
		// Use an alpha value of zero - this works since we only replace pixels with an alpha value of 0.
		int averageColor = ColorSRGB.linearToSrgb(r, g, b, 0);

		for (int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
			long pPixel = ppPixel + (pixelIndex * 4);

			int color = org.lwjgl.system.MemoryUtil.memGetInt(pPixel);
			int alpha = net.sodium.api.util.ColorABGR.unpackAlpha(color);

			// Replace the color values of pixels which are fully transparent, since they have no color data.
			if (alpha == 0) {
				org.lwjgl.system.MemoryUtil.memPutInt(pPixel, averageColor);
			}
		}
	}
	
	// Iris: From MixinSpriteContents - provide access to created ticker
	@Override
	@Nullable
	public SpriteContents.Ticker getCreatedTicker() {
		return createdTicker;
	}
	
	// Iris PBR: From texture.pbr.MixinSpriteContents - PBR holder interface implementation
	@Override
	@Nullable
	public net.irisshaders.iris.pbr.texture.PBRSpriteHolder getPBRHolder() {
		return iris$pbrHolder;
	}
	
	@Override
	public net.irisshaders.iris.pbr.texture.PBRSpriteHolder getOrCreatePBRHolder() {
		if (iris$pbrHolder == null) {
			iris$pbrHolder = new net.irisshaders.iris.pbr.texture.PBRSpriteHolder();
		}
		return iris$pbrHolder;
	}
	
	// Iris PBR: From texture.pbr.MixinSpriteContents - Sodium active tracking hook
	public void sodium$setActive(boolean active) {
		// Mark PBR sprites active when main sprite is active
		net.irisshaders.iris.pbr.texture.PBRSpriteHolder pbrHolder = getPBRHolder();
		if (pbrHolder != null) {
			net.minecraft.client.renderer.texture.TextureAtlasSprite normalSprite = pbrHolder.getNormalSprite();
			net.minecraft.client.renderer.texture.TextureAtlasSprite specularSprite = pbrHolder.getSpecularSprite();
			if (normalSprite != null) {
				net.sodium.api.texture.SpriteUtil.INSTANCE.markSpriteActive(normalSprite);
			}
			if (specularSprite != null) {
				net.sodium.api.texture.SpriteUtil.INSTANCE.markSpriteActive(specularSprite);
			}
		}
	}
}
