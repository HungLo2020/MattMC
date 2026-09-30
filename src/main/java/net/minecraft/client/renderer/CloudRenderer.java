package net.minecraft.client.renderer;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.Std140Builder;
import net.blaze3d.buffers.Std140SizeCalculator;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.platform.NativeImage;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.VertexFormat;
import net.logging.LogUtils;
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
import net.vulkanic.VulkanicAPI;
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
	private static final long MAX_SEMANTIC_CLOUD_CELLS = 1_048_576L;
	private static final float CELL_SIZE_IN_BLOCKS = 12.0F;
	private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec3().putVec3().get();
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/clouds.png");
	private static final float BLOCKS_PER_SECOND = 0.6F;
	/** Capture-only cap for bounded Rust cloud-route validation. Zero preserves vanilla range. */
	private static final int RUST_ROUTE_RADIUS_LIMIT = Math.max(
		0,
		Math.min(32, Integer.getInteger("mattmc.dev.rustGalClouds.radiusLimit", 0))
	);
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
	@Nullable
	private final VulkanicAPI.AutoStorageIndexBuffer indices;
	@Nullable
	private MappableRingBuffer ubo;
	@Nullable
	private MappableRingBuffer utb;

	public CloudRenderer() {
		this.indices = null;
		this.ubo = null;
	}

	protected Optional<CloudRenderer.TextureData> prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		try {
			InputStream inputStream = resourceManager.open(TEXTURE_LOCATION);

			Optional var20;
			try (NativeImage nativeImage = NativeImage.read(inputStream)) {
				int i = nativeImage.getWidth();
				int j = nativeImage.getHeight();
				long cellCount = (long)i * j;
				if (i <= 0 || j <= 0 || cellCount > MAX_SEMANTIC_CLOUD_CELLS) {
					return Optional.empty();
				}
				long[] ls = new long[(int)cellCount];

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

	/**
	 * Copies ordinary vanilla cloud-cell semantics for the Rust whole-frame
	 * route. This is deliberately separate from {@link #render}: no Java buffer,
	 * pipeline, texture, or callback crosses the semantic boundary.
	 */
	public void enqueueRustGalClouds(int cloudColorArgb, CloudStatus cloudStatus, float cloudHeight, Vec3 cameraPos, float partialTick) {
		if (cloudStatus == CloudStatus.OFF) {
			return;
		}
		if (cameraPos == null) {
			throw new IllegalArgumentException("Rust cloud semantics require a camera position");
		}
		if (!Double.isFinite(cameraPos.x) || !Double.isFinite(cameraPos.y) || !Double.isFinite(cameraPos.z)
			|| !Float.isFinite(cloudHeight) || !Float.isFinite(partialTick)) {
			throw new IllegalArgumentException("Rust cloud semantics require finite camera, height, and partial tick");
		}
		if (this.texture == null) {
			throw new IllegalStateException(
				"Rust whole-frame cloud route requires a decoded semantic cloud-cell field"
			);
		}
		long semanticCellCount = (long)this.texture.width() * this.texture.height();
		if (this.texture.width() <= 0 || this.texture.height() <= 0
			|| semanticCellCount > MAX_SEMANTIC_CLOUD_CELLS
			|| this.texture.cells() == null
			|| this.texture.cells().length != semanticCellCount) {
			throw new IllegalStateException(
				"Rust whole-frame cloud route requires a bounded, dimensionally complete cloud-cell field"
			);
		}
		int distance = Math.min(Minecraft.getInstance().options.cloudRange().get(), MAX_RADIUS_CHUNKS) * 16;
		int radius = Mth.ceil(distance / CELL_SIZE_IN_BLOCKS);
		if (RUST_ROUTE_RADIUS_LIMIT > 0) {
			radius = Math.min(radius, RUST_ROUTE_RADIUS_LIMIT);
		}
		float verticalOffset = cloudHeight - (float)cameraPos.y;
		float relation = verticalOffset + 4.0F;
		RelativeCameraPos cameraRelation = relation < 0.0F
			? RelativeCameraPos.ABOVE_CLOUDS
			: verticalOffset > 0.0F ? RelativeCameraPos.BELOW_CLOUDS : RelativeCameraPos.INSIDE_CLOUDS;
		double scrollX = cameraPos.x + partialTick * BLOCKS_PER_SECOND;
		double scrollZ = cameraPos.z + 3.96F;
		double repeatX = this.texture.width * CELL_SIZE_IN_BLOCKS;
		double repeatZ = this.texture.height * CELL_SIZE_IN_BLOCKS;
		scrollX -= Mth.floor(scrollX / repeatX) * repeatX;
		scrollZ -= Mth.floor(scrollZ / repeatZ) * repeatZ;
		int cellX = Mth.floor(scrollX / CELL_SIZE_IN_BLOCKS);
		int cellZ = Mth.floor(scrollZ / CELL_SIZE_IN_BLOCKS);
		float offsetX = (float)(scrollX - cellX * CELL_SIZE_IN_BLOCKS);
		float offsetZ = (float)(scrollZ - cellZ * CELL_SIZE_IN_BLOCKS);
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueWorldCloudFaces(
			this.texture.cells(),
			this.texture.width(),
			this.texture.height(),
			cloudColorArgb,
			cloudStatus == CloudStatus.FANCY,
			cameraRelation.ordinal(),
			cellX,
			cellZ,
			offsetX,
			verticalOffset,
			offsetZ,
			radius
		);
	}

	public void markForRebuild() {
		this.needsRebuild = true;
	}

	public void endFrame() {
		return;
	}

	public void close() {
		if (this.ubo != null) {
			this.ubo.close();
		}
		if (this.utb != null) {
			this.utb.close();
		}
		this.ubo = null;
		this.utb = null;
	}

	/** Releases Java cloud UBOs if Rust Vulkan ownership begins after construction. */
	public void ensureRustSemanticRoute() {
		if ((this.ubo != null || this.utb != null)) {
			this.close();
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
