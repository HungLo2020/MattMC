package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class CloudRenderer extends SimplePreparableReloadListener<Optional<CloudRenderer.TextureData>> implements AutoCloseable {
	private static final int FLAG_INSIDE_FACE = 16;
	private static final int FLAG_USE_TOP_COLOR = 32;
	private static final int MAX_RADIUS_CHUNKS = 128;
	private static final float CELL_SIZE_IN_BLOCKS = 12.0F;
	private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec3().putVec3().get();
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
	private static final float BLOCKS_PER_SECOND = 0.6F;
	private static final long EMPTY_CELL = 0L;
	private static final int COLOR_OFFSET = 4;
	private static final int NORTH_OFFSET = 3;
	private static final int EAST_OFFSET = 2;
	private static final int SOUTH_OFFSET = 1;
	private static final int WEST_OFFSET = 0;
	private boolean needsRebuild = true;
	private int prevCellX = Integer.MIN_VALUE;
	private int prevCellZ = Integer.MIN_VALUE;
	private CloudRenderer.RelativeCameraPos prevRelativeCameraPos = CloudRenderer.RelativeCameraPos.INSIDE_CLOUDS;
	@Nullable
	private CloudStatus prevType;
	@Nullable
	private CloudRenderer.TextureData texture;
	private int quadCount = 0;
	private final RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
	private final MappableRingBuffer ubo = new MappableRingBuffer(() -> "Cloud UBO", 130, UBO_SIZE);
	@Nullable
	private MappableRingBuffer utb;

	protected Optional<CloudRenderer.TextureData> prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		try {
			InputStream inputStream = resourceManager.open(TEXTURE_LOCATION);

			Optional var20;
			try (NativeImage nativeImage = NativeImage.read(inputStream)) {
				int i = nativeImage.getWidth();
				int j = nativeImage.getHeight();
				long[] ls = new long[i * j];

				for (int k = 0; k < j; k++) {
					for (int l = 0; l < i; l++) {
						int m = nativeImage.getPixel(l, k);
						if (isCellEmpty(m)) {
							ls[l + k * i] = 0L;
						} else {
							boolean bl = isCellEmpty(nativeImage.getPixel(l, Math.floorMod(k - 1, j)));
							boolean bl2 = isCellEmpty(nativeImage.getPixel(Math.floorMod(l + 1, j), k));
							boolean bl3 = isCellEmpty(nativeImage.getPixel(l, Math.floorMod(k + 1, j)));
							boolean bl4 = isCellEmpty(nativeImage.getPixel(Math.floorMod(l - 1, j), k));
							ls[l + k * i] = packCellData(m, bl, bl2, bl3, bl4);
						}
					}
				}

				var20 = Optional.of(new CloudRenderer.TextureData(ls, i, j));
			} catch (Throwable var18) {
				if (inputStream != null) {
					try {
						inputStream.close();
					} catch (Throwable var15) {
						var18.addSuppressed(var15);
					}
				}

				throw var18;
			}

			if (inputStream != null) {
				inputStream.close();
			}

			return var20;
		} catch (IOException var19) {
			LOGGER.error("Failed to load cloud texture", (Throwable)var19);
			return Optional.empty();
		}
	}

	private static int getSizeForCloudDistance(int i) {
		int j = 4;
		int k = (i + 1) * 2 * (i + 1) * 2 / 2;
		int l = k * 4 + 54;
		return l * 3;
	}

	protected void apply(Optional<CloudRenderer.TextureData> optional, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		this.texture = (CloudRenderer.TextureData)optional.orElse(null);
		this.needsRebuild = true;
	}

	private static boolean isCellEmpty(int i) {
		return ARGB.alpha(i) < 10;
	}

	private static long packCellData(int i, boolean bl, boolean bl2, boolean bl3, boolean bl4) {
		return (long)i << 4 | (bl ? 1 : 0) << 3 | (bl2 ? 1 : 0) << 2 | (bl3 ? 1 : 0) << 1 | (bl4 ? 1 : 0) << 0;
	}

	private static boolean isNorthEmpty(long l) {
		return (l >> 3 & 1L) != 0L;
	}

	private static boolean isEastEmpty(long l) {
		return (l >> 2 & 1L) != 0L;
	}

	private static boolean isSouthEmpty(long l) {
		return (l >> 1 & 1L) != 0L;
	}

	private static boolean isWestEmpty(long l) {
		return (l >> 0 & 1L) != 0L;
	}

	public void render(int i, CloudStatus cloudStatus, float f, Vec3 vec3, float g) {
		if (this.texture != null) {
			int j = Math.min(Minecraft.getInstance().options.cloudRange().get(), 128) * 16;
			int k = Mth.ceil(j / 12.0F);
			int l = getSizeForCloudDistance(k);
			if (this.utb == null || this.utb.currentBuffer().size() != l) {
				if (this.utb != null) {
					this.utb.close();
				}

				this.utb = new MappableRingBuffer(() -> "Cloud UTB", 258, l);
			}

			float h = (float)(f - vec3.y);
			float m = h + 4.0F;
			CloudRenderer.RelativeCameraPos relativeCameraPos;
			if (m < 0.0F) {
				relativeCameraPos = CloudRenderer.RelativeCameraPos.ABOVE_CLOUDS;
			} else if (h > 0.0F) {
				relativeCameraPos = CloudRenderer.RelativeCameraPos.BELOW_CLOUDS;
			} else {
				relativeCameraPos = CloudRenderer.RelativeCameraPos.INSIDE_CLOUDS;
			}

			double d = vec3.x + g * 0.030000001F;
			double e = vec3.z + 3.96F;
			double n = this.texture.width * 12.0;
			double o = this.texture.height * 12.0;
			d -= Mth.floor(d / n) * n;
			e -= Mth.floor(e / o) * o;
			int p = Mth.floor(d / 12.0);
			int q = Mth.floor(e / 12.0);
			float r = (float)(d - p * 12.0F);
			float s = (float)(e - q * 12.0F);
			boolean bl = cloudStatus == CloudStatus.FANCY;
			RenderPipeline renderPipeline = bl ? RenderPipelines.CLOUDS : RenderPipelines.FLAT_CLOUDS;
			if (this.needsRebuild || p != this.prevCellX || q != this.prevCellZ || relativeCameraPos != this.prevRelativeCameraPos || cloudStatus != this.prevType) {
				this.needsRebuild = false;
				this.prevCellX = p;
				this.prevCellZ = q;
				this.prevRelativeCameraPos = relativeCameraPos;
				this.prevType = cloudStatus;
				this.utb.rotate();

				try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.utb.currentBuffer(), false, true)) {
					this.buildMesh(relativeCameraPos, mappedView.data(), p, q, bl, k);
					this.quadCount = mappedView.data().position() / 3;
				}
			}

			if (this.quadCount != 0) {
				try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.ubo.currentBuffer(), false, true)) {
					Std140Builder.intoBuffer(mappedView.data())
						.putVec4(ARGB.redFloat(i), ARGB.greenFloat(i), ARGB.blueFloat(i), 1.0F)
						.putVec3(-r, h, -s)
						.putVec3(12.0F, 4.0F, 12.0F);
				}

				GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
					.writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);
				RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();
				RenderTarget renderTarget2 = Minecraft.getInstance().levelRenderer.getCloudsTarget();
				RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
				GpuBuffer gpuBuffer = autoStorageIndexBuffer.getBuffer(6 * this.quadCount);
				GpuTextureView gpuTextureView;
				GpuTextureView gpuTextureView2;
				if (renderTarget2 != null) {
					gpuTextureView = renderTarget2.getColorTextureView();
					gpuTextureView2 = renderTarget2.getDepthTextureView();
				} else {
					gpuTextureView = renderTarget.getColorTextureView();
					gpuTextureView2 = renderTarget.getDepthTextureView();
				}

				try (RenderPass renderPass = RenderSystem.getDevice()
						.createCommandEncoder()
						.createRenderPass(() -> "Clouds", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
					renderPass.setPipeline(renderPipeline);
					RenderSystem.bindDefaultUniforms(renderPass);
					renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
					renderPass.setIndexBuffer(gpuBuffer, autoStorageIndexBuffer.type());
					renderPass.setUniform("CloudInfo", this.ubo.currentBuffer());
					renderPass.setUniform("CloudFaces", this.utb.currentBuffer());
					renderPass.drawIndexed(0, 0, 6 * this.quadCount, 1);
				}
			}
		}
	}

	private void buildMesh(CloudRenderer.RelativeCameraPos relativeCameraPos, ByteBuffer byteBuffer, int i, int j, boolean bl, int k) {
		if (this.texture != null) {
			long[] ls = this.texture.cells;
			int l = this.texture.width;
			int m = this.texture.height;

			for (int n = 0; n <= 2 * k; n++) {
				for (int o = -n; o <= n; o++) {
					int p = n - Math.abs(o);
					if (p >= 0 && p <= k && o * o + p * p <= k * k) {
						if (p != 0) {
							this.tryBuildCell(relativeCameraPos, byteBuffer, i, j, bl, o, l, -p, m, ls);
						}

						this.tryBuildCell(relativeCameraPos, byteBuffer, i, j, bl, o, l, p, m, ls);
					}
				}
			}
		}
	}

	private void tryBuildCell(
		CloudRenderer.RelativeCameraPos relativeCameraPos, ByteBuffer byteBuffer, int i, int j, boolean bl, int k, int l, int m, int n, long[] ls
	) {
		int o = Math.floorMod(i + k, l);
		int p = Math.floorMod(j + m, n);
		long q = ls[o + p * l];
		if (q != 0L) {
			if (bl) {
				this.buildExtrudedCell(relativeCameraPos, byteBuffer, k, m, q);
			} else {
				this.buildFlatCell(byteBuffer, k, m);
			}
		}
	}

	private void buildFlatCell(ByteBuffer byteBuffer, int i, int j) {
		this.encodeFace(byteBuffer, i, j, Direction.DOWN, 32);
	}

	private void encodeFace(ByteBuffer byteBuffer, int i, int j, Direction direction, int k) {
		int l = direction.get3DDataValue() | k;
		l |= (i & 1) << 7;
		l |= (j & 1) << 6;
		byteBuffer.put((byte)(i >> 1)).put((byte)(j >> 1)).put((byte)l);
	}

	private void buildExtrudedCell(CloudRenderer.RelativeCameraPos relativeCameraPos, ByteBuffer byteBuffer, int i, int j, long l) {
		if (relativeCameraPos != CloudRenderer.RelativeCameraPos.BELOW_CLOUDS) {
			this.encodeFace(byteBuffer, i, j, Direction.UP, 0);
		}

		if (relativeCameraPos != CloudRenderer.RelativeCameraPos.ABOVE_CLOUDS) {
			this.encodeFace(byteBuffer, i, j, Direction.DOWN, 0);
		}

		if (isNorthEmpty(l) && j > 0) {
			this.encodeFace(byteBuffer, i, j, Direction.NORTH, 0);
		}

		if (isSouthEmpty(l) && j < 0) {
			this.encodeFace(byteBuffer, i, j, Direction.SOUTH, 0);
		}

		if (isWestEmpty(l) && i > 0) {
			this.encodeFace(byteBuffer, i, j, Direction.WEST, 0);
		}

		if (isEastEmpty(l) && i < 0) {
			this.encodeFace(byteBuffer, i, j, Direction.EAST, 0);
		}

		boolean bl = Math.abs(i) <= 1 && Math.abs(j) <= 1;
		if (bl) {
			for (Direction direction : Direction.values()) {
				this.encodeFace(byteBuffer, i, j, direction, 16);
			}
		}
	}

	public void markForRebuild() {
		this.needsRebuild = true;
	}

	public void endFrame() {
		this.ubo.rotate();
	}

	public void close() {
		this.ubo.close();
		if (this.utb != null) {
			this.utb.close();
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum RelativeCameraPos {
		ABOVE_CLOUDS,
		INSIDE_CLOUDS,
		BELOW_CLOUDS;
	}

	@Environment(EnvType.CLIENT)
	public record TextureData(long[] cells, int width, int height) {
	}
}
