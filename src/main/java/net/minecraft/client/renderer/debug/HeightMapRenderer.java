package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import java.util.Map.Entry;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class HeightMapRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;
	private static final int CHUNK_DIST = 2;
	private static final float BOX_HEIGHT = 0.09375F;

	public HeightMapRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		LevelAccessor levelAccessor = this.minecraft.level;
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.debugFilledBox());
		BlockPos blockPos = BlockPos.containing(d, 0.0, f);

		for (int i = -2; i <= 2; i++) {
			for (int j = -2; j <= 2; j++) {
				ChunkAccess chunkAccess = levelAccessor.getChunk(blockPos.offset(i * 16, 0, j * 16));

				for (Entry<Types, Heightmap> entry : chunkAccess.getHeightmaps()) {
					Types types = (Types)entry.getKey();
					ChunkPos chunkPos = chunkAccess.getPos();
					Vector3f vector3f = this.getColor(types);

					for (int k = 0; k < 16; k++) {
						for (int l = 0; l < 16; l++) {
							int m = SectionPos.sectionToBlockCoord(chunkPos.x, k);
							int n = SectionPos.sectionToBlockCoord(chunkPos.z, l);
							float g = (float)(levelAccessor.getHeight(types, m, n) + types.ordinal() * 0.09375F - e);
							ShapeRenderer.addChainedFilledBoxVertices(
								poseStack,
								vertexConsumer,
								m + 0.25F - d,
								(double)g,
								n + 0.25F - f,
								m + 0.75F - d,
								(double)(g + 0.09375F),
								n + 0.75F - f,
								vector3f.x(),
								vector3f.y(),
								vector3f.z(),
								1.0F
							);
						}
					}
				}
			}
		}
	}

	/** Copies the bounded height-map overlay into Rust-owned semantic quads. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry) {
		if ((!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()) {
			return;
		}
		LevelAccessor levelAccessor = this.minecraft.level;
		if (levelAccessor == null) return;
		double camX = camera.getPosition().x;
		double camY = camera.getPosition().y;
		double camZ = camera.getPosition().z;
		PoseStack transform = new PoseStack();
		transform.translate(-camX, -camY, -camZ);
		BlockPos blockPos = BlockPos.containing(camX, 0.0, camZ);
		float[] uvs = {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};
		for (int chunkX = -CHUNK_DIST; chunkX <= CHUNK_DIST; chunkX++) {
			for (int chunkZ = -CHUNK_DIST; chunkZ <= CHUNK_DIST; chunkZ++) {
				ChunkAccess chunkAccess = levelAccessor.getChunk(blockPos.offset(chunkX * 16, 0, chunkZ * 16));
				for (Entry<Types, Heightmap> entry : chunkAccess.getHeightmaps()) {
					Types types = entry.getKey();
					int color = ARGB.colorFromFloat(0.35F, this.getColor(types).x(), this.getColor(types).y(), this.getColor(types).z());
					ChunkPos chunkPos = chunkAccess.getPos();
					for (int localX = 0; localX < 16; localX++) {
						for (int localZ = 0; localZ < 16; localZ++) {
							float y0 = (float)(levelAccessor.getHeight(types,
								SectionPos.sectionToBlockCoord(chunkPos.x, localX),
								SectionPos.sectionToBlockCoord(chunkPos.z, localZ))
								+ types.ordinal() * BOX_HEIGHT);
							float x0 = SectionPos.sectionToBlockCoord(chunkPos.x, localX) + 0.25F;
							float z0 = SectionPos.sectionToBlockCoord(chunkPos.z, localZ) + 0.25F;
							float x1 = x0 + 0.5F, y1 = y0 + BOX_HEIGHT, z1 = z0 + 0.5F;
							float[][] faces = {
								{x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0},
								{x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1},
								{x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1},
								{x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0},
								{x0,y0,z0, x0,y0,z1, x1,y0,z1, x1,y0,z0},
								{x0,y1,z1, x0,y1,z0, x1,y1,z0, x1,y1,z1}
							};
							for (float[] face : faces) {
								if (!geometry.submitColoredQuadsSemantic(transform, RenderType.debugFilledBox(), face, uvs, new int[]{color}, 15728880)) {
									throw new IllegalStateException("Rust whole-frame height-map route rejected semantic quads");
								}
							}
						}
					}
				}
			}
		}
	}

	private Vector3f getColor(Types types) {
		return switch (types) {
			case WORLD_SURFACE_WG -> new Vector3f(1.0F, 1.0F, 0.0F);
			case OCEAN_FLOOR_WG -> new Vector3f(1.0F, 0.0F, 1.0F);
			case WORLD_SURFACE -> new Vector3f(0.0F, 0.7F, 0.0F);
			case OCEAN_FLOOR -> new Vector3f(0.0F, 0.0F, 0.5F);
			case MOTION_BLOCKING -> new Vector3f(0.0F, 0.3F, 0.3F);
			case MOTION_BLOCKING_NO_LEAVES -> new Vector3f(0.0F, 0.5F, 0.5F);
			default -> throw new MatchException(null, null);
		};
	}
}
