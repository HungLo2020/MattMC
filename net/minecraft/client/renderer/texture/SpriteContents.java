package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.metadata.animation.AnimationFrame;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.MetadataSectionType.WithValue;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SpriteContents implements Stitcher.Entry, AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	final ResourceLocation name;
	final int width;
	final int height;
	// Made accessible for Distant Horizons
	public final NativeImage originalImage;
	NativeImage[] byMipLevel;
	@Nullable
	// Made accessible for Distant Horizons
	public final SpriteContents.AnimatedTexture animatedTexture;
	private final List<WithValue<?>> additionalMetadata;

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
		this.originalImage = nativeImage;
		this.byMipLevel = new NativeImage[]{this.originalImage};
	}

	public void increaseMipLevel(int i) {
		try {
			this.byMipLevel = MipmapGenerator.generateMipLevels(this.byMipLevel, i);
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

	void upload(int i, int j, int k, int l, NativeImage[] nativeImages, GpuTexture gpuTexture) {
		for (int m = 0; m < this.byMipLevel.length; m++) {
			RenderSystem.getDevice()
				.createCommandEncoder()
				.writeToTexture(gpuTexture, nativeImages[m], m, 0, i >> m, j >> m, this.width >> m, this.height >> m, k >> m, l >> m);
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
		return this.animatedTexture != null ? this.animatedTexture.createTicker() : null;
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
		private final int frameRowSize;
		private final boolean interpolateFrames;

		AnimatedTexture(final List<SpriteContents.FrameInfo> list, final int i, final boolean bl) {
			this.frames = list;
			this.frameRowSize = i;
			this.interpolateFrames = bl;
		}

		// Made public for Distant Horizons
		public int getFrameX(int i) {
			return i % this.frameRowSize;
		}

		// Made public for Distant Horizons
		public int getFrameY(int i) {
			return i / this.frameRowSize;
		}

		void uploadFrame(int i, int j, int k, GpuTexture gpuTexture) {
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

		InterpolationData() {
			for (int i = 0; i < this.activeFrame.length; i++) {
				int j = SpriteContents.this.width >> i;
				int k = SpriteContents.this.height >> i;
				this.activeFrame[i] = new NativeImage(j, k, false);
			}
		}

		void uploadInterpolatedFrame(int i, int j, SpriteContents.Ticker ticker, GpuTexture gpuTexture) {
			SpriteContents.AnimatedTexture animatedTexture = ticker.animationInfo;
			List<SpriteContents.FrameInfo> list = animatedTexture.frames;
			SpriteContents.FrameInfo frameInfo = (SpriteContents.FrameInfo)list.get(ticker.frame);
			float f = (float)ticker.subFrame / frameInfo.time;
			int k = frameInfo.index;
			int l = ((SpriteContents.FrameInfo)list.get((ticker.frame + 1) % list.size())).index;
			if (k != l) {
				for (int m = 0; m < this.activeFrame.length; m++) {
					int n = SpriteContents.this.width >> m;
					int o = SpriteContents.this.height >> m;

					for (int p = 0; p < o; p++) {
						for (int q = 0; q < n; q++) {
							int r = this.getPixel(animatedTexture, k, m, q, p);
							int s = this.getPixel(animatedTexture, l, m, q, p);
							this.activeFrame[m].setPixel(q, p, ARGB.lerp(f, r, s));
						}
					}
				}

				SpriteContents.this.upload(i, j, 0, 0, this.activeFrame, gpuTexture);
				if (SharedConstants.DEBUG_DUMP_INTERPOLATED_TEXTURE_FRAMES) {
					try {
						Path path = TextureUtil.getDebugTexturePath();
						Path path2 = path.resolve(SpriteContents.this.name.toDebugFileName());
						Files.createDirectories(path2);

						for (int o = 0; o < this.activeFrame.length; o++) {
							this.activeFrame[o].writeToFile(path2.resolve(SpriteContents.this.name.toDebugFileName() + "_" + o + "_" + k + "_" + l + ".png"));
						}
					} catch (IOException var18) {
					}
				}
			}
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

		Ticker(final SpriteContents.AnimatedTexture animatedTexture, @Nullable final SpriteContents.InterpolationData interpolationData) {
			this.animationInfo = animatedTexture;
			this.interpolationData = interpolationData;
		}

		@Override
		public void tickAndUpload(int i, int j, GpuTexture gpuTexture) {
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
		}

		@Override
		public void close() {
			if (this.interpolationData != null) {
				this.interpolationData.close();
			}
		}
	}
}
