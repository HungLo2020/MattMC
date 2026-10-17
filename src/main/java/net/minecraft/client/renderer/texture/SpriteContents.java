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
public class SpriteContents implements AutoCloseable, SpriteContentsExtension, net.irisshaders.iris.pbr.SpriteContentsExtension, net.irisshaders.iris.pbr.texture.SpriteContentsExtension {
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

	/**
	 * A sprite built in Java (tests and tools): Rust validates the declared
	 * animation, logging as vanilla did, and applies Sodium's transparent
	 * pixel fill to the image in place.
	 */
	public SpriteContents(
		ResourceLocation resourceLocation, FrameSize frameSize, NativeImage nativeImage, Optional<AnimationMetadataSection> optional, List<WithValue<?>> list
	) {
		this(resourceLocation, frameSize, nativeImage, prepare(resourceLocation, frameSize, nativeImage, optional),
			optional.map(AnimationMetadataSection::interpolatedFrames).orElse(false), list);
	}

	private SpriteContents(
		ResourceLocation resourceLocation, FrameSize frameSize, NativeImage nativeImage, NativeAtlasBuild.Prepared prepared, boolean interpolate,
		List<WithValue<?>> list
	) {
		this(resourceLocation, frameSize.width(), frameSize.height(), nativeImage, prepared.frames(), prepared.frameRowSize(), interpolate,
			prepared.transparent(), prepared.translucent(), list);
	}

	/**
	 * A sprite from a Rust atlas build: `nativeImage` is the preprocessed
	 * sheet, and `frames` the validated animation (null when not animated).
	 */
	SpriteContents(
		ResourceLocation resourceLocation, int width, int height, NativeImage nativeImage, @Nullable List<SpriteContents.FrameInfo> frames,
		int frameRowSize, boolean interpolate, boolean transparent, boolean translucent, List<WithValue<?>> list
	) {
		this.name = resourceLocation;
		this.width = width;
		this.height = height;
		this.additionalMetadata = list;
		this.animatedTexture = frames == null ? null : new SpriteContents.AnimatedTexture(frames, frameRowSize, interpolate);
		this.sodium$hasTransparentPixels = transparent;
		this.sodium$hasTranslucentPixels = translucent;
		this.originalImage = nativeImage;
		this.byMipLevel = new NativeImage[]{this.originalImage};
	}

	private static NativeAtlasBuild.Prepared prepare(
		ResourceLocation resourceLocation, FrameSize frameSize, NativeImage nativeImage, Optional<AnimationMetadataSection> optional
	) {
		if (optional.isEmpty()) {
			return NativeAtlasBuild.prepare(resourceLocation, nativeImage, frameSize.width(), frameSize.height(), null, false, 1, false);
		}
		AnimationMetadataSection animation = optional.get();
		List<int[]> frames = animation.frames()
			.map(list -> list.stream().map(frame -> new int[]{frame.index(), frame.time().orElse(-1)}).toList())
			.orElse(null);
		return NativeAtlasBuild.prepare(resourceLocation, nativeImage, frameSize.width(), frameSize.height(), frames, true,
			animation.defaultFrameTime(), animation.interpolatedFrames());
	}

	private int getFrameCount() {
		return this.animatedTexture != null ? this.animatedTexture.frames.size() : 1;
	}

	public boolean isAnimated() {
		return this.getFrameCount() > 1;
	}

	public void upload(int i, int j, int k, int l, NativeImage[] nativeImages, GpuTexture gpuTexture) {
		// Rust consumes the copied atlas/resource-pack pixels; Java texture
		// uploads are unavailable on the semantic whole-frame device.
		return;
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	public ResourceLocation name() {
		return this.name;
	}

	public IntStream getUniqueFrames() {
		return this.animatedTexture != null ? this.animatedTexture.getUniqueFrames() : IntStream.of(1);
	}

	/**
	 * Copies immutable resource animation declarations and complete CPU mip sheets.
	 * Does not construct a ticker, select a frame, interpolate pixels, or access a
	 * Java GPU texture. Rust must own those rendering decisions after transport.
	 */
	public Optional<SemanticAnimationSource> semanticAnimationSource() {
		if (this.animatedTexture == null) return Optional.empty();
		long bytes = 0L;
		if (this.byMipLevel.length == 0 || this.byMipLevel.length > 32
			|| this.animatedTexture.frames.size() > 16384) {
			throw new IllegalStateException("Invalid semantic sprite animation source");
		}
		// Preflight every level before making any pixel copies.
		for (NativeImage image : this.byMipLevel) {
			if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
				throw new IllegalStateException("Incomplete semantic sprite animation mip sheet");
			}
			bytes = Math.addExact(bytes, Math.multiplyExact(
				Math.multiplyExact((long)image.getWidth(), image.getHeight()), 4L));
			if (bytes > 96L * 1024L * 1024L) {
				throw new IllegalStateException("Semantic sprite animation pixel bound exceeded");
			}
		}
		List<SemanticAnimationMip> mips = new ArrayList<>(this.byMipLevel.length);
		for (NativeImage image : this.byMipLevel) {
			byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(image.getWidth(), image.getHeight()), 4)];
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int argb = image.getPixel(x, y);
					int offset = (y * image.getWidth() + x) * 4;
					rgba[offset] = (byte)ARGB.red(argb);
					rgba[offset + 1] = (byte)ARGB.green(argb);
					rgba[offset + 2] = (byte)ARGB.blue(argb);
					rgba[offset + 3] = (byte)ARGB.alpha(argb);
				}
			}
			mips.add(new SemanticAnimationMip(image.getWidth(), image.getHeight(), rgba));
		}
		return Optional.of(new SemanticAnimationSource(this.width, this.height,
			this.animatedTexture.frameRowSize, this.animatedTexture.interpolateFrames,
			this.animatedTexture.frames.stream()
				.map(frame -> new SemanticAnimationFrame(frame.index(), frame.time())).toList(), mips));
	}

	public record SemanticAnimationFrame(int index, int durationTicks) {}

	public record SemanticAnimationMip(int width, int height, byte[] rgba) {
		public SemanticAnimationMip {
			if (width <= 0 || height <= 0
				|| (long)width * height > 96L * 1024L * 1024L / 4L
				|| (long)width * height * 4L != rgba.length) {
				throw new IllegalArgumentException("Invalid semantic animation mip dimensions");
			}
			rgba = rgba.clone();
		}
		@Override public byte[] rgba() { return this.rgba.clone(); }
	}

	public record SemanticAnimationSource(int frameWidth, int frameHeight, int frameRowSize,
		boolean interpolate, List<SemanticAnimationFrame> frames, List<SemanticAnimationMip> mips) {
		public SemanticAnimationSource {
			frames = List.copyOf(frames);
			mips = List.copyOf(mips);
		}
	}

	/** Returns the currently selected semantic animation frame, if any. */
	public int semanticFrameIndex() {
		if (this.animatedTexture == null) return 0;
		if (this.createdTicker != null) {
			return ((SpriteContents.FrameInfo)this.animatedTexture.frames.get(this.createdTicker.frame)).index();
		}
		return ((SpriteContents.FrameInfo)this.animatedTexture.frames.getFirst()).index();
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

		public boolean interpolateFrames() {
			return this.interpolateFrames;
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
		public boolean tickSemantic() {
			this.subFrame++;
			SpriteContents.FrameInfo frameInfo = (SpriteContents.FrameInfo)this.animationInfo.frames.get(this.frame);
			if (this.subFrame >= frameInfo.time) {
				this.frame = (this.frame + 1) % this.animationInfo.frames.size();
				this.subFrame = 0;
				return true;
			}
			return false;
		}

		@Override
		public void close() {
			if (this.interpolationData != null) {
				this.interpolationData.close();
			}
		}
	}
	
	@Override
	public boolean sodium$hasTransparentPixels() {
		return this.sodium$hasTransparentPixels;
	}

	@Override
	public boolean sodium$hasTranslucentPixels() {
		return this.sodium$hasTranslucentPixels;
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
		throw new IllegalStateException("Java Iris PBR sprite state is unavailable on the Rust Vulkan route");
	}
	
	// Iris PBR: From texture.pbr.MixinSpriteContents - Sodium active tracking hook
	public void sodium$setActive(boolean active) {
		return;
	}
}
