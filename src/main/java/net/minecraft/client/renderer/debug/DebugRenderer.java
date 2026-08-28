package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
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
	private final Minecraft minecraft;
	private final List<DebugRenderer.SimpleDebugRenderer> opaqueRenderers = new ArrayList();
	private final List<DebugRenderer.SimpleDebugRenderer> translucentRenderers = new ArrayList();
	@Nullable
	private CollisionBoxRenderer collisionBoxRenderer;
	@Nullable
	private SolidFaceRenderer solidFaceRenderer;
	@Nullable
	private SupportBlockRenderer supportBlockRenderer;
	@Nullable
	private StructureRenderer structureRenderer;
	@Nullable
	private GameEventListenerRenderer gameEventListenerRenderer;
	@Nullable
	private RedstoneWireOrientationsRenderer redstoneWireOrientationsRenderer;
	@Nullable
	private ChunkBorderRenderer chunkBorderRenderer;
	@Nullable
	private BreezeDebugRenderer breezeDebugRenderer;
	@Nullable
	private PathfindingRenderer pathfindingRenderer;
	@Nullable
	private LightSectionDebugRenderer lightSectionDebugRenderer;
	@Nullable
	private HeightMapRenderer heightMapRenderer;
	@Nullable
	private ChunkCullingDebugRenderer chunkCullingDebugRenderer;
	@Nullable
	private WaterDebugRenderer waterDebugRenderer;
	@Nullable
	private LightDebugRenderer lightDebugRenderer;
	@Nullable
	private VillageSectionsDebugRenderer villageSectionsDebugRenderer;
	@Nullable
	private ChunkDebugRenderer chunkDebugRenderer;
	@Nullable
	private EntityBlockIntersectionDebugRenderer entityBlockIntersectionDebugRenderer;
	@Nullable
	private GoalSelectorDebugRenderer goalSelectorDebugRenderer;
	@Nullable
	private RaidDebugRenderer raidDebugRenderer;
	@Nullable
	private BrainDebugRenderer brainDebugRenderer;
	@Nullable
	private PoiDebugRenderer poiDebugRenderer;
	@Nullable
	private BeeDebugRenderer beeDebugRenderer;
	@Nullable
	private OctreeDebugRenderer octreeDebugRenderer;
	private long lastDebugEntriesVersion;

	public DebugRenderer() {
		this.minecraft = Minecraft.getInstance();
		this.refreshRendererList();
	}

	public void refreshRendererList() {
		Minecraft minecraft = Minecraft.getInstance();
		this.opaqueRenderers.clear();
		this.translucentRenderers.clear();
		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_BORDERS) && !minecraft.showOnlyReducedInfo()) {
			this.chunkBorderRenderer = new ChunkBorderRenderer(minecraft);
			this.opaqueRenderers.add(this.chunkBorderRenderer);
		} else {
			this.chunkBorderRenderer = null;
		}

		if (minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_OCTREE)) {
			this.octreeDebugRenderer = new OctreeDebugRenderer(minecraft);
			this.opaqueRenderers.add(this.octreeDebugRenderer);
		} else {
			this.octreeDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_PATHFINDING) {
			this.pathfindingRenderer = new PathfindingRenderer();
			this.opaqueRenderers.add(this.pathfindingRenderer);
		} else {
			this.pathfindingRenderer = null;
		}

		if (SharedConstants.DEBUG_WATER) {
			this.waterDebugRenderer = new WaterDebugRenderer(minecraft);
			this.opaqueRenderers.add(this.waterDebugRenderer);
		} else {
			this.waterDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_HEIGHTMAP) {
			this.heightMapRenderer = new HeightMapRenderer(minecraft);
			this.opaqueRenderers.add(this.heightMapRenderer);
		} else {
			this.heightMapRenderer = null;
		}

		if (SharedConstants.DEBUG_COLLISION) {
			this.collisionBoxRenderer = new CollisionBoxRenderer(minecraft);
			this.opaqueRenderers.add(this.collisionBoxRenderer);
		} else {
			this.collisionBoxRenderer = null;
		}

		if (SharedConstants.DEBUG_SUPPORT_BLOCKS) {
			this.supportBlockRenderer = new SupportBlockRenderer(minecraft);
			this.opaqueRenderers.add(this.supportBlockRenderer);
		} else {
			this.supportBlockRenderer = null;
		}

		if (SharedConstants.DEBUG_NEIGHBORSUPDATE) {
			this.opaqueRenderers.add(new NeighborsUpdateRenderer());
		}

		if (SharedConstants.DEBUG_EXPERIMENTAL_REDSTONEWIRE_UPDATE_ORDER) {
			this.redstoneWireOrientationsRenderer = new RedstoneWireOrientationsRenderer();
			this.opaqueRenderers.add(this.redstoneWireOrientationsRenderer);
		} else {
			this.redstoneWireOrientationsRenderer = null;
		}

		if (SharedConstants.DEBUG_STRUCTURES) {
			this.structureRenderer = new StructureRenderer();
			this.opaqueRenderers.add(this.structureRenderer);
		} else {
			this.structureRenderer = null;
		}

		if (SharedConstants.DEBUG_LIGHT) {
			this.lightDebugRenderer = new LightDebugRenderer(minecraft);
			this.opaqueRenderers.add(this.lightDebugRenderer);
		} else {
			this.lightDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_SOLID_FACE) {
			this.solidFaceRenderer = new SolidFaceRenderer(minecraft);
			this.opaqueRenderers.add(this.solidFaceRenderer);
		} else {
			this.solidFaceRenderer = null;
		}

		if (SharedConstants.DEBUG_VILLAGE_SECTIONS) {
			this.villageSectionsDebugRenderer = new VillageSectionsDebugRenderer();
			this.opaqueRenderers.add(this.villageSectionsDebugRenderer);
		} else {
			this.villageSectionsDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_BRAIN) {
			this.brainDebugRenderer = new BrainDebugRenderer(minecraft);
			this.opaqueRenderers.add(this.brainDebugRenderer);
		} else {
			this.brainDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_POI) {
			BrainDebugRenderer brain = this.brainDebugRenderer != null ? this.brainDebugRenderer : new BrainDebugRenderer(minecraft);
			this.poiDebugRenderer = new PoiDebugRenderer(brain);
			this.opaqueRenderers.add(this.poiDebugRenderer);
		} else {
			this.poiDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_BEES) {
			this.beeDebugRenderer = new BeeDebugRenderer(minecraft);
			this.opaqueRenderers.add(this.beeDebugRenderer);
		} else {
			this.beeDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_RAIDS) {
			this.raidDebugRenderer = new RaidDebugRenderer(minecraft);
			this.opaqueRenderers.add(this.raidDebugRenderer);
		} else {
			this.raidDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_GOAL_SELECTOR) {
			this.goalSelectorDebugRenderer = new GoalSelectorDebugRenderer(minecraft);
			this.opaqueRenderers.add(this.goalSelectorDebugRenderer);
		} else {
			this.goalSelectorDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_CHUNKS) {
			this.chunkDebugRenderer = new ChunkDebugRenderer(minecraft);
			this.opaqueRenderers.add(this.chunkDebugRenderer);
		} else {
			this.chunkDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_GAME_EVENT_LISTENERS) {
			this.gameEventListenerRenderer = new GameEventListenerRenderer();
			this.opaqueRenderers.add(this.gameEventListenerRenderer);
		} else {
			this.gameEventListenerRenderer = null;
		}

		if (SharedConstants.DEBUG_SKY_LIGHT_SECTIONS) {
			this.lightSectionDebugRenderer = new LightSectionDebugRenderer(minecraft, LightLayer.SKY);
			this.opaqueRenderers.add(this.lightSectionDebugRenderer);
		} else {
			this.lightSectionDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_BREEZE_MOB) {
			this.breezeDebugRenderer = new BreezeDebugRenderer(minecraft);
			this.opaqueRenderers.add(this.breezeDebugRenderer);
		} else {
			this.breezeDebugRenderer = null;
		}

		if (SharedConstants.DEBUG_ENTITY_BLOCK_INTERSECTION) {
			this.entityBlockIntersectionDebugRenderer = new EntityBlockIntersectionDebugRenderer();
			this.opaqueRenderers.add(this.entityBlockIntersectionDebugRenderer);
		} else {
			this.entityBlockIntersectionDebugRenderer = null;
		}

		this.chunkCullingDebugRenderer = new ChunkCullingDebugRenderer(minecraft);
		this.translucentRenderers.add(this.chunkCullingDebugRenderer);
	}

	/** Collects the collision-debug family for Rust whole-frame Vulkan. */
	public void collectRustCollisionSemantics(PoseStack poseStack, SubmitNodeStorage geometry, Camera camera) {
		if (!SharedConstants.DEBUG_COLLISION || this.collisionBoxRenderer == null) return;
		this.collisionBoxRenderer.collectRustSemantics(poseStack, geometry, camera);
	}

	/** Collects the solid-face debug family for Rust whole-frame Vulkan. */
	public void collectRustSolidFaceSemantics(SubmitNodeStorage geometry, Camera camera) {
		if (!SharedConstants.DEBUG_SOLID_FACE || this.solidFaceRenderer == null) return;
		this.solidFaceRenderer.collectRustSemantics(geometry, camera);
	}

	/** Collects support-block debug geometry for Rust whole-frame Vulkan. */
	public void collectRustSupportBlockSemantics(Camera camera) {
		if (!SharedConstants.DEBUG_SUPPORT_BLOCKS || this.supportBlockRenderer == null) return;
		this.supportBlockRenderer.collectRustSemantics(camera);
	}

	/** Collects neighbor-update debug geometry and labels for Rust Vulkan. */
	public void collectRustNeighborUpdateSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_NEIGHBORSUPDATE) return;
		new NeighborsUpdateRenderer().collectRustSemantics(camera, geometry, text);
	}

	/** Collects structure-debug boxes for Rust whole-frame Vulkan. */
	public void collectRustStructureSemantics(Camera camera) {
		if (!SharedConstants.DEBUG_STRUCTURES || this.structureRenderer == null) return;
		this.structureRenderer.collectRustSemantics(camera);
	}

	/** Collects game-event listener debug geometry and text for Rust Vulkan. */
	public void collectRustGameEventListenerSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_GAME_EVENT_LISTENERS || this.gameEventListenerRenderer == null) return;
		this.gameEventListenerRenderer.collectRustSemantics(camera, geometry, text);
	}

	/** Collects redstone orientation vectors for Rust whole-frame Vulkan. */
	public void collectRustRedstoneWireOrientationSemantics(Camera camera) {
		if (!SharedConstants.DEBUG_EXPERIMENTAL_REDSTONEWIRE_UPDATE_ORDER || this.redstoneWireOrientationsRenderer == null) return;
		this.redstoneWireOrientationsRenderer.collectRustSemantics(camera);
	}

	/** Collects chunk-border debug segments for Rust whole-frame Vulkan. */
	public void collectRustChunkBorderSemantics(Camera camera) {
		if (this.chunkBorderRenderer == null) return;
		this.chunkBorderRenderer.collectRustSemantics(camera);
	}

	/** Collects Breeze debug primitives for Rust whole-frame Vulkan. */
	public void collectRustBreezeSemantics(Camera camera, SubmitNodeStorage geometry) {
		if (!SharedConstants.DEBUG_BREEZE_MOB || this.breezeDebugRenderer == null) return;
		this.breezeDebugRenderer.collectRustSemantics(camera, geometry);
	}

	/** Collects pathfinding debug primitives for Rust whole-frame Vulkan. */
	public void collectRustPathfindingSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_PATHFINDING || this.pathfindingRenderer == null) return;
		this.pathfindingRenderer.collectRustSemantics(camera, geometry, text);
	}

	/** Collects light-section debug fields for Rust whole-frame Vulkan. */
	public void collectRustLightSectionSemantics(Camera camera, SubmitNodeStorage geometry) {
		if (!SharedConstants.DEBUG_SKY_LIGHT_SECTIONS || this.lightSectionDebugRenderer == null) return;
		this.lightSectionDebugRenderer.collectRustSemantics(camera, geometry);
	}

	/** Collects height-map debug overlays for Rust whole-frame Vulkan. */
	public void collectRustHeightMapSemantics(Camera camera, SubmitNodeStorage geometry) {
		if (!SharedConstants.DEBUG_HEIGHTMAP || this.heightMapRenderer == null) return;
		this.heightMapRenderer.collectRustSemantics(camera, geometry);
	}

	/** Collects chunk-culling paths, visibility, and captured-frustum diagnostics. */
	public void collectRustChunkCullingSemantics(Camera camera, SubmitNodeStorage geometry) {
		if (this.chunkCullingDebugRenderer == null) return;
		this.chunkCullingDebugRenderer.collectRustSemantics(camera, geometry);
	}

	/** Collects nearby water debug levels and labels for Rust whole-frame Vulkan. */
	public void collectRustWaterSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_WATER || this.waterDebugRenderer == null) return;
		this.waterDebugRenderer.collectRustSemantics(camera, geometry, text);
	}

	/** Collects nearby light-engine diagnostics into the Rust semantic text stream. */
	public void collectRustLightSemantics(Camera camera, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_LIGHT || this.lightDebugRenderer == null) return;
		this.lightDebugRenderer.collectRustSemantics(camera, text);
	}

	/** Collects subscribed village-section markers for Rust whole-frame Vulkan. */
	public void collectRustVillageSectionSemantics(Camera camera, SubmitNodeStorage geometry) {
		if (!SharedConstants.DEBUG_VILLAGE_SECTIONS || this.villageSectionsDebugRenderer == null) return;
		this.villageSectionsDebugRenderer.collectRustSemantics(this.minecraft, camera, geometry);
	}

	/** Collects periodic client/server chunk diagnostics for Rust whole-frame Vulkan. */
	public void collectRustChunkSemantics(Camera camera, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_CHUNKS || this.chunkDebugRenderer == null) return;
		this.chunkDebugRenderer.collectRustSemantics(camera, text);
	}

	/** Collects entity/block intersection subscriptions for Rust whole-frame Vulkan. */
	public void collectRustEntityBlockIntersectionSemantics(Camera camera, SubmitNodeStorage geometry) {
		if (!SharedConstants.DEBUG_ENTITY_BLOCK_INTERSECTION || this.entityBlockIntersectionDebugRenderer == null) return;
		this.entityBlockIntersectionDebugRenderer.collectRustSemantics(this.minecraft, camera, geometry);
	}

	/** Collects goal-selector subscription labels for Rust whole-frame Vulkan. */
	public void collectRustGoalSelectorSemantics(Camera camera, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_GOAL_SELECTOR || this.goalSelectorDebugRenderer == null) return;
		this.goalSelectorDebugRenderer.collectRustSemantics(camera, text);
	}

	/** Collects raid-center subscriptions for Rust whole-frame Vulkan. */
	public void collectRustRaidSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_RAIDS || this.raidDebugRenderer == null) return;
		this.raidDebugRenderer.collectRustSemantics(camera, geometry, text);
	}

	/** Collects POI and ghost-POI diagnostics for Rust whole-frame Vulkan. */
	public void collectRustPoiSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_POI || this.poiDebugRenderer == null) return;
		this.poiDebugRenderer.collectRustSemantics(this.minecraft, camera, geometry, text);
	}

	/** Collects complete brain-debug labels for Rust whole-frame Vulkan. */
	public void collectRustBrainSemantics(Camera camera, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_BRAIN || this.brainDebugRenderer == null) return;
		this.brainDebugRenderer.collectRustSemantics(camera, text);
	}

	/** Collects bee, flower, hive, and ghost-hive diagnostics for Rust Vulkan. */
	public void collectRustBeeSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if (!SharedConstants.DEBUG_BEES || this.beeDebugRenderer == null) return;
		this.beeDebugRenderer.collectRustSemantics(camera, geometry, text);
	}

	/** Collects frustum-filtered octree diagnostics for Rust whole-frame Vulkan. */
	public void collectRustOctreeSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text, Frustum frustum) {
		if (!minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_OCTREE) || this.octreeDebugRenderer == null) return;
		this.octreeDebugRenderer.collectRustSemantics(camera, geometry, text, frustum);
	}

	public void render(PoseStack poseStack, Frustum frustum, MultiBufferSource.BufferSource bufferSource, double d, double e, double f, boolean bl) {
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java debug rendering is unavailable while Rust owns whole-frame presentation");
		}
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
