package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugRenderer {
	private final List<DebugRenderer.SimpleDebugRenderer> opaqueRenderers = new ArrayList();
	private final List<DebugRenderer.SimpleDebugRenderer> translucentRenderers = new ArrayList();
	private long lastDebugEntriesVersion;

	public DebugRenderer() {
		this.refreshRendererList();
	}

	public void refreshRendererList() {
		Minecraft minecraft = Minecraft.getInstance();
		this.opaqueRenderers.clear();
		this.translucentRenderers.clear();
		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_BORDERS) && !minecraft.showOnlyReducedInfo()) {
			this.opaqueRenderers.add(new ChunkBorderRenderer(minecraft));
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_OCTREE)) {
			this.opaqueRenderers.add(new OctreeDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_PATHFINDING) {
			this.opaqueRenderers.add(new PathfindingRenderer());
		}

		if (SharedConstants.DEBUG_WATER) {
			this.opaqueRenderers.add(new WaterDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_HEIGHTMAP) {
			this.opaqueRenderers.add(new HeightMapRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_COLLISION) {
			this.opaqueRenderers.add(new CollisionBoxRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_SUPPORT_BLOCKS) {
			this.opaqueRenderers.add(new SupportBlockRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_NEIGHBORSUPDATE) {
			this.opaqueRenderers.add(new NeighborsUpdateRenderer());
		}

		if (SharedConstants.DEBUG_EXPERIMENTAL_REDSTONEWIRE_UPDATE_ORDER) {
			this.opaqueRenderers.add(new RedstoneWireOrientationsRenderer());
		}

		if (SharedConstants.DEBUG_STRUCTURES) {
			this.opaqueRenderers.add(new StructureRenderer());
		}

		if (SharedConstants.DEBUG_LIGHT) {
			this.opaqueRenderers.add(new LightDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_SOLID_FACE) {
			this.opaqueRenderers.add(new SolidFaceRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_VILLAGE_SECTIONS) {
			this.opaqueRenderers.add(new VillageSectionsDebugRenderer());
		}

		if (SharedConstants.DEBUG_BRAIN) {
			this.opaqueRenderers.add(new BrainDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_POI) {
			this.opaqueRenderers.add(new PoiDebugRenderer(new BrainDebugRenderer(minecraft)));
		}

		if (SharedConstants.DEBUG_BEES) {
			this.opaqueRenderers.add(new BeeDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_RAIDS) {
			this.opaqueRenderers.add(new RaidDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_GOAL_SELECTOR) {
			this.opaqueRenderers.add(new GoalSelectorDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_CHUNKS) {
			this.opaqueRenderers.add(new ChunkDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_GAME_EVENT_LISTENERS) {
			this.opaqueRenderers.add(new GameEventListenerRenderer());
		}

		if (SharedConstants.DEBUG_SKY_LIGHT_SECTIONS) {
			this.opaqueRenderers.add(new LightSectionDebugRenderer(minecraft, LightLayer.SKY));
		}

		if (SharedConstants.DEBUG_BREEZE_MOB) {
			this.opaqueRenderers.add(new BreezeDebugRenderer(minecraft));
		}

		if (SharedConstants.DEBUG_ENTITY_BLOCK_INTERSECTION) {
			this.opaqueRenderers.add(new EntityBlockIntersectionDebugRenderer());
		}

		this.translucentRenderers.add(new ChunkCullingDebugRenderer(minecraft));
	}

	public void render(PoseStack poseStack, Frustum frustum, MultiBufferSource.BufferSource bufferSource, double d, double e, double f, boolean bl) {
		Minecraft minecraft = Minecraft.getInstance();
		DebugValueAccess debugValueAccess = minecraft.getConnection().createDebugValueAccess();
		if (minecraft.debugEntries.getCurrentlyEnabledVersion() != this.lastDebugEntriesVersion) {
			this.lastDebugEntriesVersion = minecraft.debugEntries.getCurrentlyEnabledVersion();
			this.refreshRendererList();
		}

		for (DebugRenderer.SimpleDebugRenderer simpleDebugRenderer : bl ? this.translucentRenderers : this.opaqueRenderers) {
			simpleDebugRenderer.render(poseStack, bufferSource, d, e, f, debugValueAccess, frustum);
		}
	}

	public static Optional<Entity> getTargetedEntity(@Nullable Entity entity, int i) {
		if (entity == null) {
			return Optional.empty();
		} else {
			Vec3 vec3 = entity.getEyePosition();
			Vec3 vec32 = entity.getViewVector(1.0F).scale(i);
			Vec3 vec33 = vec3.add(vec32);
			AABB aABB = entity.getBoundingBox().expandTowards(vec32).inflate(1.0);
			int j = i * i;
			EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(entity, vec3, vec33, aABB, EntitySelector.CAN_BE_PICKED, j);
			if (entityHitResult == null) {
				return Optional.empty();
			} else {
				return vec3.distanceToSqr(entityHitResult.getLocation()) > j ? Optional.empty() : Optional.of(entityHitResult.getEntity());
			}
		}
	}

	public static void renderFilledUnitCube(PoseStack poseStack, MultiBufferSource multiBufferSource, BlockPos blockPos, float f, float g, float h, float i) {
		renderFilledBox(poseStack, multiBufferSource, blockPos, blockPos.offset(1, 1, 1), f, g, h, i);
	}

	public static void renderFilledBox(
		PoseStack poseStack, MultiBufferSource multiBufferSource, BlockPos blockPos, BlockPos blockPos2, float f, float g, float h, float i
	) {
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		if (camera.isInitialized()) {
			Vec3 vec3 = camera.getPosition().reverse();
			AABB aABB = AABB.encapsulatingFullBlocks(blockPos, blockPos2).move(vec3);
			renderFilledBox(poseStack, multiBufferSource, aABB, f, g, h, i);
		}
	}

	public static void renderFilledBox(PoseStack poseStack, MultiBufferSource multiBufferSource, BlockPos blockPos, float f, float g, float h, float i, float j) {
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		if (camera.isInitialized()) {
			Vec3 vec3 = camera.getPosition().reverse();
			AABB aABB = new AABB(blockPos).move(vec3).inflate(f);
			renderFilledBox(poseStack, multiBufferSource, aABB, g, h, i, j);
		}
	}

	public static void renderFilledBox(PoseStack poseStack, MultiBufferSource multiBufferSource, AABB aABB, float f, float g, float h, float i) {
		renderFilledBox(poseStack, multiBufferSource, aABB.minX, aABB.minY, aABB.minZ, aABB.maxX, aABB.maxY, aABB.maxZ, f, g, h, i);
	}

	public static void renderFilledBox(
		PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, double g, double h, double i, float j, float k, float l, float m
	) {
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.debugFilledBox());
		ShapeRenderer.addChainedFilledBoxVertices(poseStack, vertexConsumer, d, e, f, g, h, i, j, k, l, m);
	}

	public static void renderTextOverBlock(PoseStack poseStack, MultiBufferSource multiBufferSource, String string, BlockPos blockPos, int i, int j, float f) {
		double d = 1.3;
		double e = 0.2;
		double g = blockPos.getX() + 0.5;
		double h = blockPos.getY() + 1.3 + i * 0.2;
		double k = blockPos.getZ() + 0.5;
		renderFloatingText(poseStack, multiBufferSource, string, g, h, k, j, f, true, 0.0F, true);
	}

	public static void renderTextOverMob(PoseStack poseStack, MultiBufferSource multiBufferSource, Entity entity, int i, String string, int j, float f) {
		double d = 2.4;
		double e = 0.25;
		double g = entity.getBlockX() + 0.5;
		double h = entity.getY() + 2.4 + i * 0.25;
		double k = entity.getBlockZ() + 0.5;
		float l = 0.5F;
		renderFloatingText(poseStack, multiBufferSource, string, g, h, k, j, f, false, 0.5F, true);
	}

	public static void renderFloatingText(PoseStack poseStack, MultiBufferSource multiBufferSource, String string, int i, int j, int k, int l) {
		renderFloatingText(poseStack, multiBufferSource, string, i + 0.5, j + 0.5, k + 0.5, l);
	}

	public static void renderFloatingText(PoseStack poseStack, MultiBufferSource multiBufferSource, String string, double d, double e, double f, int i) {
		renderFloatingText(poseStack, multiBufferSource, string, d, e, f, i, 0.02F);
	}

	public static void renderFloatingText(PoseStack poseStack, MultiBufferSource multiBufferSource, String string, double d, double e, double f, int i, float g) {
		renderFloatingText(poseStack, multiBufferSource, string, d, e, f, i, g, true, 0.0F, false);
	}

	public static void renderFloatingText(
		PoseStack poseStack, MultiBufferSource multiBufferSource, String string, double d, double e, double f, int i, float g, boolean bl, float h, boolean bl2
	) {
		Minecraft minecraft = Minecraft.getInstance();
		Camera camera = minecraft.gameRenderer.getMainCamera();
		if (camera.isInitialized() && minecraft.getEntityRenderDispatcher().options != null) {
			Font font = minecraft.font;
			double j = camera.getPosition().x;
			double k = camera.getPosition().y;
			double l = camera.getPosition().z;
			poseStack.pushPose();
			poseStack.translate((float)(d - j), (float)(e - k) + 0.07F, (float)(f - l));
			poseStack.mulPose(camera.rotation());
			poseStack.scale(g, -g, g);
			float m = bl ? -font.width(string) / 2.0F : 0.0F;
			m -= h / g;
			font.drawInBatch(
				string, m, 0.0F, i, false, poseStack.last().pose(), multiBufferSource, bl2 ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, 0, 15728880
			);
			poseStack.popPose();
		}
	}

	private static Vec3 mixColor(float f) {
		float g = 5.99999F;
		int i = (int)(Mth.clamp(f, 0.0F, 1.0F) * 5.99999F);
		float h = f * 5.99999F - i;

		return switch (i) {
			case 0 -> new Vec3(1.0, h, 0.0);
			case 1 -> new Vec3(1.0F - h, 1.0, 0.0);
			case 2 -> new Vec3(0.0, 1.0, h);
			case 3 -> new Vec3(0.0, 1.0 - h, 1.0);
			case 4 -> new Vec3(h, 0.0, 1.0);
			case 5 -> new Vec3(1.0, 0.0, 1.0 - h);
			default -> throw new IllegalStateException("Unexpected value: " + i);
		};
	}

	private static Vec3 shiftHue(float f, float g, float h, float i) {
		Vec3 vec3 = mixColor(i).scale(f);
		Vec3 vec32 = mixColor((i + 0.33333334F) % 1.0F).scale(g);
		Vec3 vec33 = mixColor((i + 0.6666667F) % 1.0F).scale(h);
		Vec3 vec34 = vec3.add(vec32).add(vec33);
		double d = Math.max(Math.max(1.0, vec34.x), Math.max(vec34.y, vec34.z));
		return new Vec3(vec34.x / d, vec34.y / d, vec34.z / d);
	}

	public static void renderVoxelShape(
		PoseStack poseStack, VertexConsumer vertexConsumer, VoxelShape voxelShape, double d, double e, double f, float g, float h, float i, float j, boolean bl
	) {
		List<AABB> list = voxelShape.toAabbs();
		if (!list.isEmpty()) {
			int k = bl ? list.size() : list.size() * 8;
			ShapeRenderer.renderShape(poseStack, vertexConsumer, Shapes.create((AABB)list.get(0)), d, e, f, ARGB.colorFromFloat(j, g, h, i));

			for (int l = 1; l < list.size(); l++) {
				AABB aABB = (AABB)list.get(l);
				float m = (float)l / k;
				Vec3 vec3 = shiftHue(g, h, i, m);
				ShapeRenderer.renderShape(poseStack, vertexConsumer, Shapes.create(aABB), d, e, f, ARGB.colorFromFloat(j, (float)vec3.x, (float)vec3.y, (float)vec3.z));
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public interface SimpleDebugRenderer {
		void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum);
	}
}
