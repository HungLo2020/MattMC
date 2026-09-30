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

	@Environment(EnvType.CLIENT)
	/** Marker for debug renderers whose state Rust collects semantically. */
	public interface SimpleDebugRenderer {
	}
}
