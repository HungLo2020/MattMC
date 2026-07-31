package net.vulkanic.world;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.dev.DeterministicCameraCapture;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.WorldBorderRenderState;
import net.minecraft.client.renderer.state.BlockBreakingRenderState;
import net.vulkanic.gui.RustGalFrameCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.vulkanic.bridge.VulkanicGalBridge;
import net.logging.LogUtils;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.sodium.client.model.quad.BakedQuadView;

public final class RustGalWorldPrimitiveRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int STRATUM_WORLD_BORDER = 80;
	public static final int STRATUM_WORLD_MATERIAL = 70;
	public static final int STRATUM_WORLD_MOVING_MESH = 68;
	public static final int STRATUM_WORLD_BLOCK_BREAKING_CRACK = 90;
	public static final int STRATUM_WORLD_BLOCK_OUTLINE = 100;
	public static final int STYLE_NORMAL = 1;
	public static final int STYLE_HIGH_CONTRAST = 2;
	public static final int DEPTH_POLICY_DISABLED = 0;
	public static final int DEPTH_POLICY_TEST_WRITE = 1;
	public static final int DEPTH_POLICY_TEST_NO_WRITE = 2;
	public static final int BORDER_TEXTURE_FORCEFIELD = 1;
	public static final int MATERIAL_TEXTURE_STONE = 0x21DF896F;
	public static final int MATERIAL_TEXTURE_DIRT = 0x0B0BBD25;
	public static final int MATERIAL_TEXTURE_OAK_LEAVES = 0x72321EC7;
	public static final int MATERIAL_TEXTURE_DEEPSLATE = 0x715D8D65;
	public static final int MATERIAL_TEXTURE_WHITE_WOOL = 0x2253A2EF;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER = 0x447D596A;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_00 = 0x665DA7AA;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_01 = 0x50E88E0F;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_02 = 0x079E2B74;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_03 = 0x4A7C2B71;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_04 = 0x35E90AE6;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_05 = 0x2F21FECB;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_06 = 0x2A27ABF0;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_07 = 0x0EA4C92D;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_08 = 0x4473CCE2;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_09 = 0x0AB551C7;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_10 = 0x7A250241;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_11 = 0x1F439384;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_12 = 0x4BAB8F5F;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_13 = 0x431688FA;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_14 = 0x0B2BDBBD;
	public static final int MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_15 = 0x019476C0;
	public static final int MATERIAL_ID_OPAQUE_TEXTURED = 0x6A2FD335;
	public static final int MATERIAL_ID_CUTOUT_TEXTURED = 0x129B1B90;
	public static final int MATERIAL_ID_BLOCK_MARKER_CUTOUT = 0x224A8659;
	public static final int MATERIAL_MODE_OPAQUE = 1;
	public static final int MATERIAL_MODE_CUTOUT = 2;
	public static final int MESH_VERTEX_LAYOUT_V1 = 1;
	public static final int MESH_SECTION_ALL = -1;
	private static final ResourceLocation MISSING_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("missingno");
	private static byte[] missingTexturePayload;
	public static final int WORLD_TOPOLOGY_TRIANGLES = 1;
	public static final int WORLD_WINDING_CCW = 1;
	public static final int WORLD_WINDING_CW = 2;
	public static final int BORDER_BLEND_OVERLAY = 1;
	public static final int CRACK_BLEND_MULTIPLY = 1;
	public static final int CULL_NONE = 0;
	public static final int CULL_BACK = 1;
	public static final int BACKGROUND_SKY_OVERWORLD = 1;
	public static final int BACKGROUND_SKY_NETHER = 2;
	public static final int BACKGROUND_SKY_END = 3;
	public static final int BACKGROUND_SKY_CUSTOM = 4;
	public static final int BACKGROUND_LOAD_CLEAR = 1;
	public static final int BACKGROUND_STORE_STORE = 1;
	private static final float CRACK_FACE_OFFSET = 0.002F;
	private static final String DIAGNOSTIC_SCENARIO = System.getProperty("mattmc.dev.rustGalWorldOutline.scenario", "").trim();
	private static final String DIAGNOSTIC_STYLE = System.getProperty("mattmc.dev.rustGalWorldOutline.style", "").trim();
	private static final String DIAGNOSTIC_DEPTH_POLICY = System.getProperty("mattmc.dev.rustGalWorldOutline.depthPolicy", "").trim();
	private static final boolean DIAGNOSTIC_DEPTH_PROBE = Boolean.getBoolean("mattmc.dev.rustGalWorldOutline.depthProbe");
	private static final String DIAGNOSTIC_CRACK_SCENARIO = System.getProperty("mattmc.dev.rustGalWorldCrack.scenario", "").trim();
	private static final String DIAGNOSTIC_CRACK_STAGE = System.getProperty("mattmc.dev.rustGalWorldCrack.stage", "0").trim();
	private static final String DIAGNOSTIC_BORDER_SCENARIO = System.getProperty("mattmc.dev.rustGalWorldBorder.scenario", "").trim();
	private static final String DIAGNOSTIC_BORDER_SCROLL = System.getProperty("mattmc.dev.rustGalWorldBorder.scrollPhase", "").trim();
	private static final String DIAGNOSTIC_BACKGROUND_SCENARIO = System.getProperty("mattmc.dev.rustGalWorldBackground.scenario", "auto").trim();
	private static final boolean BLOCK_OUTLINE_DIAGNOSTICS = Boolean.getBoolean("mattmc.dev.blockOutlineDiagnostics");
	private static final boolean CRACK_DISABLED_CONTROL = Boolean.getBoolean("mattmc.dev.rustGalWorldCrack.disabled");
	private static final ResourceLocation FORCEFIELD_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
	private static final ResourceLocation STONE_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/block/stone.png");
	private static final ResourceLocation DIRT_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/block/dirt.png");
	private static final ResourceLocation OAK_LEAVES_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/block/oak_leaves.png");
	private static final ResourceLocation DEEPSLATE_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/block/deepslate.png");
	private static final ResourceLocation WHITE_WOOL_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/block/white_wool.png");
	private static final ResourceLocation BARRIER_MARKER_LOCATION = ResourceLocation.withDefaultNamespace("textures/item/barrier.png");
	private static final Map<Block, Integer> TERRAIN_PARTICLE_TEXTURE_IDS = Map.of(
		Blocks.STONE, MATERIAL_TEXTURE_STONE,
		Blocks.DIRT, MATERIAL_TEXTURE_DIRT,
		Blocks.OAK_LEAVES, MATERIAL_TEXTURE_OAK_LEAVES,
		Blocks.DEEPSLATE, MATERIAL_TEXTURE_DEEPSLATE,
		Blocks.WHITE_WOOL, MATERIAL_TEXTURE_WHITE_WOOL
	);
	private static final ResourceLocation[] CRACK_STAGE_LOCATIONS = new ResourceLocation[] {
		ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_0.png"),
		ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_1.png"),
		ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_2.png"),
		ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_3.png"),
		ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_4.png"),
		ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_5.png"),
		ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_6.png"),
		ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_7.png"),
		ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_8.png"),
		ResourceLocation.withDefaultNamespace("textures/block/destroy_stage_9.png")
	};
	private static final ResourceLocation[] LIGHT_MARKER_LOCATIONS = new ResourceLocation[] {
		ResourceLocation.withDefaultNamespace("textures/item/light_00.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_01.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_02.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_03.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_04.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_05.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_06.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_07.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_08.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_09.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_10.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_11.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_12.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_13.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_14.png"),
		ResourceLocation.withDefaultNamespace("textures/item/light_15.png")
	};
	private static final int[] LIGHT_MARKER_TEXTURE_IDS = new int[] {
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_00,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_01,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_02,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_03,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_04,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_05,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_06,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_07,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_08,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_09,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_10,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_11,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_12,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_13,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_14,
		MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_15
	};
	private static final Object LOCK = new Object();
	private static final List<VulkanicGalBridge.WorldLineSegmentRecord> PENDING_SEGMENTS = new ArrayList<>();
	private static final List<VulkanicGalBridge.WorldCrackQuadRecord> PENDING_CRACK_QUADS = new ArrayList<>();
	private static final List<VulkanicGalBridge.WorldBorderQuadRecord> PENDING_BORDER_QUADS = new ArrayList<>();
	private static final List<VulkanicGalBridge.WorldMaterialQuadRecord> PENDING_MATERIAL_QUADS = new ArrayList<>();
	private static final List<VulkanicGalBridge.WorldMeshInstanceRecord> PENDING_MESH_INSTANCES = new ArrayList<>();
	private static final Map<Long, VulkanicGalBridge.WorldMeshAssetRecord> WORLD_MESH_ASSETS = new LinkedHashMap<>();
	private static final Map<Integer, VulkanicGalBridge.WorldMeshTextureAssetRecord> WORLD_MESH_TEXTURES = new LinkedHashMap<>();
	private static long worldMeshAssetGeneration;
	private static long uploadedWorldMeshAssetGeneration;
	private static long attemptedWorldMeshAssetGeneration;
	private static long lastWorldMeshAssetPayloadBytes;
	private static long lastWorldMeshAssetPayloadCount;
	private static long worldMeshAssetUpdateFailures;
	private static final float[] MATERIAL_VERTEX_SCRATCH = new float[12];
	private static final Vector3f MATERIAL_BILLBOARD_VERTEX_SCRATCH = new Vector3f();
	private static final float[] MATERIAL_BILLBOARD_CORNERS = {1.0F, -1.0F, 1.0F, 1.0F, -1.0F, 1.0F, -1.0F, -1.0F};
	private static final List<BlockMarkerDiagnostic> BLOCK_MARKER_DIAGNOSTICS = new ArrayList<>();
	private static final List<TerrainParticleDiagnostic> TERRAIN_PARTICLE_DIAGNOSTICS = new ArrayList<>();
	private static final List<BlockDisplayDiagnostic> BLOCK_DISPLAY_DIAGNOSTICS = new ArrayList<>();
	private static final List<FallingBlockDiagnostic> FALLING_BLOCK_DIAGNOSTICS = new ArrayList<>();
	private static final List<FallingBlockRouteDecision> FALLING_BLOCK_ROUTE_DECISIONS = new ArrayList<>();
	private static final float[] PENDING_VIEW = new float[16];
	private static final float[] PENDING_PROJECTION = new float[16];
	private static VulkanicGalBridge.WorldBackgroundRecord pendingBackground = VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
	private static VulkanicGalBridge.WorldBorderAssetRecord pendingWorldBorderAsset =
		new VulkanicGalBridge.WorldBorderAssetRecord(BORDER_TEXTURE_FORCEFIELD, new byte[0]);
	private static long worldBorderAssetGeneration = 1L;
	private static long uploadedWorldBorderAssetGeneration;
	private static long attemptedWorldBorderAssetGeneration;
	private static long lastWorldBorderAssetPayloadCount;
	private static long lastWorldBorderAssetPayloadBytes;
	private static long worldBorderAssetUpdateFailures;
	private static String lastWorldBorderAssetSourcePack = "vanilla";
	private static String lastWorldBorderAssetSha256 = "fallback";
	private static boolean lastWorldBorderAssetFallback = true;
	private static List<VulkanicGalBridge.WorldCrackAssetRecord> pendingWorldCrackAssets = List.of();
	private static long worldCrackAssetGeneration = 1L;
	private static long uploadedWorldCrackAssetGeneration;
	private static long attemptedWorldCrackAssetGeneration;
	private static long lastWorldCrackAssetPayloadCount;
	private static long lastWorldCrackAssetPayloadBytes;
	private static long worldCrackAssetUpdateFailures;
	private static String lastWorldCrackAssetSourcePack = "vanilla";
	private static String lastWorldCrackAssetSha256 = "fallback";
	private static boolean lastWorldCrackAssetFallback = true;
	private static List<VulkanicGalBridge.WorldMaterialAssetRecord> pendingWorldMaterialAssets = List.of();
	private static long worldMaterialAssetGeneration = 1L;
	private static long uploadedWorldMaterialAssetGeneration;
	private static long attemptedWorldMaterialAssetGeneration;
	private static long lastWorldMaterialAssetPayloadCount;
	private static long lastWorldMaterialAssetPayloadBytes;
	private static long worldMaterialAssetUpdateFailures;
	private static String lastWorldMaterialAssetSourcePack = "vanilla";
	private static String lastWorldMaterialAssetSha256 = "fallback";
	private static boolean lastWorldMaterialAssetFallback = true;
	private static int pendingViewportWidth;
	private static int pendingViewportHeight;
	private static int blockOutlineProjectionDiagnosticLogs;
	private static int blockMarkerEnqueueDiagnosticLogs;
	private static int terrainParticleEnqueueDiagnosticLogs;
	private static int fallingBlockEnqueueDiagnosticLogs;

	private RustGalWorldPrimitiveRenderer() {
	}

	public static WorldRenderRoutePolicy.Route currentBlockOutlineRoute() {
		return WorldRenderRoutePolicy.currentBlockOutlineRoute();
	}

	public static WorldRenderRoutePolicy.Route currentCrackRoute() {
		return WorldRenderRoutePolicy.currentCrackRoute();
	}

	public static boolean shouldUseRustWholeFrameOutline() {
		return WorldRenderRoutePolicy.currentBlockOutlineRoute().usesRustWholeFrameVulkan();
	}

	public static boolean shouldUseRustOpenGlOutline() {
		return WorldRenderRoutePolicy.currentBlockOutlineRoute().usesRustOpenGl();
	}

	public static boolean shouldUseRustWholeFrameCrack() {
		return WorldRenderRoutePolicy.currentCrackRoute().usesRustWholeFrameVulkan();
	}

	public static boolean shouldUseRustOpenGlCrack() {
		return WorldRenderRoutePolicy.currentCrackRoute().usesRustOpenGl();
	}

	public static boolean shouldUseRustWholeFrameMaterial() {
		return WorldRenderRoutePolicy.currentMaterialRoute().usesRustWholeFrameVulkan();
	}

	public static boolean shouldUseRustOpenGlMaterial() {
		return WorldRenderRoutePolicy.currentMaterialRoute().usesRustOpenGl();
	}

	private static boolean shouldUseRustOpenGlMeshInstances() {
		return WorldRenderRoutePolicy.currentBlockDisplayRoute().usesRustOpenGl()
			|| WorldRenderRoutePolicy.currentFallingBlockRoute().usesRustOpenGl();
	}

	public static boolean shouldRouteTerrainParticle(BlockState blockState) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentMaterialRoute();
		return (route.usesRustOpenGl() || route.usesRustWholeFrameVulkan())
			&& terrainParticleTextureId(blockState) != 0;
	}

	public static boolean shouldUseRustOpenGlWorldPrimitives() {
		return shouldUseRustOpenGlOutline()
			|| shouldUseRustOpenGlCrack()
			|| WorldRenderRoutePolicy.currentMaterialRoute().usesRustOpenGl()
			|| WorldRenderRoutePolicy.currentBlockDisplayRoute().usesRustOpenGl()
			|| WorldRenderRoutePolicy.currentFallingBlockRoute().usesRustOpenGl();
	}

	public static boolean crackDisabledForDiagnostics() {
		return CRACK_DISABLED_CONTROL;
	}

	public static void reloadWorldAssets(ResourceManager resourceManager) {
		WorldBorderAssetResolution resolution = resolveWorldBorderAsset(resourceManager);
		WorldCrackAssetResolution crackResolution = resolveWorldCrackAssets(resourceManager);
		WorldMaterialAssetResolution materialResolution = resolveWorldMaterialAssets(resourceManager);
		synchronized (LOCK) {
			if (resolution.preserveLastValid()) {
				worldBorderAssetUpdateFailures++;
				auditMessage(
					"Rust VulkanicGAL world-border asset update skipped"
						+ " reason=java-read-failure"
						+ " generation=" + worldBorderAssetGeneration
						+ " uploaded_generation=" + uploadedWorldBorderAssetGeneration
						+ " failures=" + worldBorderAssetUpdateFailures
						+ " preserve_last_valid=true"
				);
			} else {
				worldBorderAssetGeneration++;
				pendingWorldBorderAsset = new VulkanicGalBridge.WorldBorderAssetRecord(BORDER_TEXTURE_FORCEFIELD, resolution.payload());
				attemptedWorldBorderAssetGeneration = Math.min(attemptedWorldBorderAssetGeneration, uploadedWorldBorderAssetGeneration);
				lastWorldBorderAssetPayloadCount = resolution.payload().length == 0 ? 0L : 1L;
				lastWorldBorderAssetPayloadBytes = resolution.payload().length;
				lastWorldBorderAssetSourcePack = resolution.sourcePack();
				lastWorldBorderAssetSha256 = resolution.sha256();
				lastWorldBorderAssetFallback = resolution.fallback();
				auditMessage(
					"Rust VulkanicGAL world-border asset resolved"
						+ " generation=" + worldBorderAssetGeneration
						+ " texture_id=" + BORDER_TEXTURE_FORCEFIELD
						+ " path=" + FORCEFIELD_LOCATION
						+ " source_pack=" + metricValue(lastWorldBorderAssetSourcePack)
						+ " payloads=" + lastWorldBorderAssetPayloadCount
						+ " payload_bytes=" + lastWorldBorderAssetPayloadBytes
						+ " fallback=" + lastWorldBorderAssetFallback
						+ " sha256=" + lastWorldBorderAssetSha256
				);
			}
			if (crackResolution.preserveLastValid()) {
				worldCrackAssetUpdateFailures++;
				auditMessage(
					"Rust VulkanicGAL world crack asset update skipped"
						+ " reason=java-read-failure"
						+ " generation=" + worldCrackAssetGeneration
						+ " uploaded_generation=" + uploadedWorldCrackAssetGeneration
						+ " failures=" + worldCrackAssetUpdateFailures
						+ " preserve_last_valid=true"
				);
			} else {
				worldCrackAssetGeneration++;
				pendingWorldCrackAssets = List.copyOf(crackResolution.assets());
				attemptedWorldCrackAssetGeneration = Math.min(attemptedWorldCrackAssetGeneration, uploadedWorldCrackAssetGeneration);
				lastWorldCrackAssetPayloadCount = crackResolution.assets().size();
				lastWorldCrackAssetPayloadBytes = crackResolution.payloadBytes();
				lastWorldCrackAssetSourcePack = crackResolution.sourcePack();
				lastWorldCrackAssetSha256 = crackResolution.sha256();
				lastWorldCrackAssetFallback = crackResolution.fallback();
				auditMessage(
					"Rust VulkanicGAL world crack asset resolved"
						+ " generation=" + worldCrackAssetGeneration
						+ " payloads=" + lastWorldCrackAssetPayloadCount
						+ " payload_bytes=" + lastWorldCrackAssetPayloadBytes
						+ " source_pack=" + metricValue(lastWorldCrackAssetSourcePack)
						+ " fallback=" + lastWorldCrackAssetFallback
						+ " sha256=" + lastWorldCrackAssetSha256
				);
			}
			if (materialResolution.preserveLastValid()) {
				worldMaterialAssetUpdateFailures++;
				auditMessage(
					"Rust VulkanicGAL world material asset update skipped"
						+ " reason=java-read-failure"
						+ " generation=" + worldMaterialAssetGeneration
						+ " uploaded_generation=" + uploadedWorldMaterialAssetGeneration
						+ " failures=" + worldMaterialAssetUpdateFailures
						+ " preserve_last_valid=true"
				);
			} else {
				worldMaterialAssetGeneration++;
				pendingWorldMaterialAssets = List.copyOf(materialResolution.assets());
				attemptedWorldMaterialAssetGeneration = Math.min(attemptedWorldMaterialAssetGeneration, uploadedWorldMaterialAssetGeneration);
				lastWorldMaterialAssetPayloadCount = materialResolution.assets().size();
				lastWorldMaterialAssetPayloadBytes = materialResolution.payloadBytes();
				lastWorldMaterialAssetSourcePack = materialResolution.sourcePack();
				lastWorldMaterialAssetSha256 = materialResolution.sha256();
				lastWorldMaterialAssetFallback = materialResolution.fallback();
				auditMessage(
					"Rust VulkanicGAL world material asset resolved"
						+ " generation=" + worldMaterialAssetGeneration
						+ " payloads=" + lastWorldMaterialAssetPayloadCount
						+ " payload_bytes=" + lastWorldMaterialAssetPayloadBytes
						+ " source_pack=" + metricValue(lastWorldMaterialAssetSourcePack)
						+ " fallback=" + lastWorldMaterialAssetFallback
					+ " sha256=" + lastWorldMaterialAssetSha256
				);
			}
			worldMeshAssetGeneration++;
			WORLD_MESH_ASSETS.clear();
			WORLD_MESH_TEXTURES.clear();
			PENDING_MESH_INSTANCES.clear();
			attemptedWorldMeshAssetGeneration = Math.min(attemptedWorldMeshAssetGeneration, uploadedWorldMeshAssetGeneration);
			lastWorldMeshAssetPayloadBytes = 0L;
			lastWorldMeshAssetPayloadCount = 0L;
			auditMessage(
				"Rust VulkanicGAL world mesh assets invalidated"
					+ " generation=" + worldMeshAssetGeneration
					+ " reason=resource-reload"
			);
		}
	}

	public static VulkanicGalBridge.Status flushPendingWorldBorderAssets(VulkanicGalBridge bridge) {
		synchronized (LOCK) {
			if (bridge == null || uploadedWorldBorderAssetGeneration >= worldBorderAssetGeneration || attemptedWorldBorderAssetGeneration >= worldBorderAssetGeneration) {
				return null;
			}
			attemptedWorldBorderAssetGeneration = worldBorderAssetGeneration;
			try {
				VulkanicGalBridge.Status status = bridge.updateWorldBorderAsset(worldBorderAssetGeneration, pendingWorldBorderAsset);
				uploadedWorldBorderAssetGeneration = worldBorderAssetGeneration;
				auditMessage(
					"Rust VulkanicGAL world-border asset update accepted"
						+ " generation=" + worldBorderAssetGeneration
						+ " texture_id=" + pendingWorldBorderAsset.textureId()
						+ " payloads=" + lastWorldBorderAssetPayloadCount
						+ " payload_bytes=" + lastWorldBorderAssetPayloadBytes
						+ " source_pack=" + metricValue(lastWorldBorderAssetSourcePack)
						+ " fallback=" + lastWorldBorderAssetFallback
						+ " uploaded_generation=" + uploadedWorldBorderAssetGeneration
				);
				return status;
			} catch (RuntimeException error) {
				worldBorderAssetUpdateFailures++;
				LOGGER.error(
					"Rust VulkanicGAL world-border asset update failed for generation {}; preserving last valid texture",
					worldBorderAssetGeneration,
					error
				);
				auditMessage(
					"Rust VulkanicGAL world-border asset update failed"
						+ " generation=" + worldBorderAssetGeneration
						+ " texture_id=" + pendingWorldBorderAsset.textureId()
						+ " uploaded_generation=" + uploadedWorldBorderAssetGeneration
						+ " failures=" + worldBorderAssetUpdateFailures
						+ " preserve_last_valid=true"
				);
				return null;
			}
		}
	}

	public static VulkanicGalBridge.Status flushPendingWorldCrackAssets(VulkanicGalBridge bridge) {
		synchronized (LOCK) {
			if (bridge == null || uploadedWorldCrackAssetGeneration >= worldCrackAssetGeneration || attemptedWorldCrackAssetGeneration >= worldCrackAssetGeneration) {
				return null;
			}
			attemptedWorldCrackAssetGeneration = worldCrackAssetGeneration;
			try {
				VulkanicGalBridge.Status status = bridge.updateWorldCrackAssets(worldCrackAssetGeneration, pendingWorldCrackAssets);
				uploadedWorldCrackAssetGeneration = worldCrackAssetGeneration;
				auditMessage(
					"Rust VulkanicGAL world crack asset update accepted"
						+ " generation=" + worldCrackAssetGeneration
						+ " payloads=" + lastWorldCrackAssetPayloadCount
						+ " payload_bytes=" + lastWorldCrackAssetPayloadBytes
						+ " source_pack=" + metricValue(lastWorldCrackAssetSourcePack)
						+ " fallback=" + lastWorldCrackAssetFallback
						+ " uploaded_generation=" + uploadedWorldCrackAssetGeneration
				);
				return status;
			} catch (RuntimeException error) {
				worldCrackAssetUpdateFailures++;
				LOGGER.error(
					"Rust VulkanicGAL world crack asset update failed for generation {}; preserving last valid atlas",
					worldCrackAssetGeneration,
					error
				);
				auditMessage(
					"Rust VulkanicGAL world crack asset update failed"
						+ " generation=" + worldCrackAssetGeneration
						+ " uploaded_generation=" + uploadedWorldCrackAssetGeneration
						+ " failures=" + worldCrackAssetUpdateFailures
						+ " preserve_last_valid=true"
				);
				return null;
			}
		}
	}

	public static VulkanicGalBridge.Status flushPendingWorldMaterialAssets(VulkanicGalBridge bridge) {
		synchronized (LOCK) {
			if (bridge == null || uploadedWorldMaterialAssetGeneration >= worldMaterialAssetGeneration || attemptedWorldMaterialAssetGeneration >= worldMaterialAssetGeneration) {
				return null;
			}
			attemptedWorldMaterialAssetGeneration = worldMaterialAssetGeneration;
			try {
				VulkanicGalBridge.Status status = bridge.updateWorldMaterialAssets(worldMaterialAssetGeneration, pendingWorldMaterialAssets);
				uploadedWorldMaterialAssetGeneration = worldMaterialAssetGeneration;
				auditMessage(
					"Rust VulkanicGAL world material asset update accepted"
						+ " generation=" + worldMaterialAssetGeneration
						+ " payloads=" + lastWorldMaterialAssetPayloadCount
						+ " payload_bytes=" + lastWorldMaterialAssetPayloadBytes
						+ " source_pack=" + metricValue(lastWorldMaterialAssetSourcePack)
						+ " fallback=" + lastWorldMaterialAssetFallback
						+ " uploaded_generation=" + uploadedWorldMaterialAssetGeneration
				);
				return status;
			} catch (RuntimeException error) {
				worldMaterialAssetUpdateFailures++;
				LOGGER.error(
					"Rust VulkanicGAL world material asset update failed for generation {}; preserving last valid atlas",
					worldMaterialAssetGeneration,
					error
				);
				auditMessage(
					"Rust VulkanicGAL world material asset update failed"
						+ " generation=" + worldMaterialAssetGeneration
						+ " uploaded_generation=" + uploadedWorldMaterialAssetGeneration
						+ " failures=" + worldMaterialAssetUpdateFailures
						+ " preserve_last_valid=true"
				);
				return null;
			}
		}
	}

	public static VulkanicGalBridge.Status flushPendingWorldMeshAssets(VulkanicGalBridge bridge) {
		synchronized (LOCK) {
			if (bridge == null || uploadedWorldMeshAssetGeneration >= worldMeshAssetGeneration || attemptedWorldMeshAssetGeneration >= worldMeshAssetGeneration) {
				return null;
			}
			attemptedWorldMeshAssetGeneration = worldMeshAssetGeneration;
			try {
				VulkanicGalBridge.Status status = bridge.updateWorldMeshAssets(
					worldMeshAssetGeneration,
					List.copyOf(WORLD_MESH_ASSETS.values()),
					List.copyOf(WORLD_MESH_TEXTURES.values())
				);
				uploadedWorldMeshAssetGeneration = worldMeshAssetGeneration;
				auditMessage(
					"Rust VulkanicGAL world mesh asset update accepted"
						+ " generation=" + worldMeshAssetGeneration
						+ " meshes=" + WORLD_MESH_ASSETS.size()
						+ " textures=" + WORLD_MESH_TEXTURES.size()
						+ " payload_bytes=" + lastWorldMeshAssetPayloadBytes
						+ " uploaded_generation=" + uploadedWorldMeshAssetGeneration
				);
				return status;
			} catch (RuntimeException error) {
				worldMeshAssetUpdateFailures++;
				LOGGER.error(
					"Rust VulkanicGAL world mesh asset update failed for generation {}; preserving last valid meshes",
					worldMeshAssetGeneration,
					error
				);
				auditMessage(
					"Rust VulkanicGAL world mesh asset update failed"
						+ " generation=" + worldMeshAssetGeneration
						+ " uploaded_generation=" + uploadedWorldMeshAssetGeneration
						+ " failures=" + worldMeshAssetUpdateFailures
						+ " preserve_last_valid=true"
				);
				return null;
			}
		}
	}

	public static WorldBorderAssetMetrics worldBorderAssetMetrics() {
		synchronized (LOCK) {
			return new WorldBorderAssetMetrics(
				worldBorderAssetGeneration,
				uploadedWorldBorderAssetGeneration,
				lastWorldBorderAssetPayloadCount,
				lastWorldBorderAssetPayloadBytes,
				worldBorderAssetUpdateFailures,
				lastWorldBorderAssetSourcePack,
				lastWorldBorderAssetSha256,
				lastWorldBorderAssetFallback
			);
		}
	}

	public static WorldCrackAssetMetrics worldCrackAssetMetrics() {
		synchronized (LOCK) {
			return new WorldCrackAssetMetrics(
				worldCrackAssetGeneration,
				uploadedWorldCrackAssetGeneration,
				lastWorldCrackAssetPayloadCount,
				lastWorldCrackAssetPayloadBytes,
				worldCrackAssetUpdateFailures,
				lastWorldCrackAssetSourcePack,
				lastWorldCrackAssetSha256,
				lastWorldCrackAssetFallback
			);
		}
	}

	public static WorldMaterialAssetMetrics worldMaterialAssetMetrics() {
		synchronized (LOCK) {
			return new WorldMaterialAssetMetrics(
				worldMaterialAssetGeneration,
				uploadedWorldMaterialAssetGeneration,
				lastWorldMaterialAssetPayloadCount,
				lastWorldMaterialAssetPayloadBytes,
				worldMaterialAssetUpdateFailures,
				lastWorldMaterialAssetSourcePack,
				lastWorldMaterialAssetSha256,
				lastWorldMaterialAssetFallback
			);
		}
	}

	private static WorldBorderAssetResolution resolveWorldBorderAsset(ResourceManager resourceManager) {
		if (resourceManager == null) {
			return WorldBorderAssetResolution.fallback("missing-resource-manager");
		}
		Optional<Resource> resource = resourceManager.getResource(FORCEFIELD_LOCATION);
		if (resource.isEmpty()) {
			return WorldBorderAssetResolution.fallback("missing");
		}
		String sourcePack = resource.get().sourcePackId();
		if ("vanilla".equals(sourcePack)) {
			return WorldBorderAssetResolution.fallback("vanilla");
		}
		try (InputStream input = resource.get().open()) {
			byte[] bytes = input.readAllBytes();
			return new WorldBorderAssetResolution(bytes, sourcePack, sha256Hex(bytes), false, false);
		} catch (IOException error) {
			LOGGER.warn(
				"Failed to read Rust VulkanicGAL world-border texture {}; preserving last valid texture",
				FORCEFIELD_LOCATION,
				error
			);
			return WorldBorderAssetResolution.preserve("read-error");
		}
	}

	private static WorldCrackAssetResolution resolveWorldCrackAssets(ResourceManager resourceManager) {
		if (resourceManager == null) {
			return WorldCrackAssetResolution.fallback("missing-resource-manager");
		}
		List<VulkanicGalBridge.WorldCrackAssetRecord> assets = new ArrayList<>();
		long payloadBytes = 0L;
		List<String> sourcePacks = new ArrayList<>();
		MessageDigest digest = sha256Digest();
		for (int stage = 0; stage < CRACK_STAGE_LOCATIONS.length; stage++) {
			ResourceLocation location = CRACK_STAGE_LOCATIONS[stage];
			Optional<Resource> resource = resourceManager.getResource(location);
			if (resource.isEmpty()) {
				continue;
			}
			String sourcePack = resource.get().sourcePackId();
			if ("vanilla".equals(sourcePack)) {
				continue;
			}
			try (InputStream input = resource.get().open()) {
				byte[] bytes = input.readAllBytes();
				assets.add(new VulkanicGalBridge.WorldCrackAssetRecord(stage, bytes));
				payloadBytes += bytes.length;
				sourcePacks.add(stage + ":" + sourcePack);
				digest.update((byte)stage);
				digest.update(bytes);
			} catch (IOException error) {
				LOGGER.warn(
					"Failed to read Rust VulkanicGAL world crack texture {}; preserving last valid atlas",
					location,
					error
				);
				return WorldCrackAssetResolution.preserve("read-error");
			}
		}
		if (assets.isEmpty()) {
			return WorldCrackAssetResolution.fallback("vanilla");
		}
		return new WorldCrackAssetResolution(
			assets,
			payloadBytes,
			String.join(",", sourcePacks),
			HexFormat.of().formatHex(digest.digest()),
			false,
			false
		);
	}

	private static WorldMaterialAssetResolution resolveWorldMaterialAssets(ResourceManager resourceManager) {
		if (resourceManager == null) {
			return WorldMaterialAssetResolution.fallback("missing-resource-manager");
		}
		List<VulkanicGalBridge.WorldMaterialAssetRecord> assets = new ArrayList<>();
		long payloadBytes = 0L;
		List<String> sourcePacks = new ArrayList<>();
		MessageDigest digest = sha256Digest();
		WorldMaterialAssetCandidate[] candidates = worldMaterialAssetCandidates();
		for (WorldMaterialAssetCandidate candidate : candidates) {
			Optional<Resource> resource = resourceManager.getResource(candidate.location());
			if (resource.isEmpty()) {
				continue;
			}
			String sourcePack = resource.get().sourcePackId();
			if ("vanilla".equals(sourcePack)) {
				continue;
			}
			try (InputStream input = resource.get().open()) {
				byte[] bytes = input.readAllBytes();
				assets.add(new VulkanicGalBridge.WorldMaterialAssetRecord(candidate.textureId(), bytes));
				payloadBytes += bytes.length;
				sourcePacks.add(candidate.textureId() + ":" + sourcePack);
				digest.update((byte)(candidate.textureId() >>> 24));
				digest.update((byte)(candidate.textureId() >>> 16));
				digest.update((byte)(candidate.textureId() >>> 8));
				digest.update((byte)candidate.textureId());
				digest.update(bytes);
			} catch (IOException error) {
				LOGGER.warn(
					"Failed to read Rust VulkanicGAL world material texture {}; preserving last valid atlas",
					candidate.location(),
					error
				);
				return WorldMaterialAssetResolution.preserve("read-error");
			}
		}
		if (assets.isEmpty()) {
			return WorldMaterialAssetResolution.fallback("vanilla");
		}
		return new WorldMaterialAssetResolution(
			assets,
			payloadBytes,
			String.join(",", sourcePacks),
			HexFormat.of().formatHex(digest.digest()),
			false,
			false
		);
	}

	private static WorldMaterialAssetCandidate[] worldMaterialAssetCandidates() {
		WorldMaterialAssetCandidate[] candidates = new WorldMaterialAssetCandidate[6 + LIGHT_MARKER_LOCATIONS.length];
		candidates[0] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_STONE, STONE_TEXTURE_LOCATION);
		candidates[1] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_DIRT, DIRT_TEXTURE_LOCATION);
		candidates[2] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_OAK_LEAVES, OAK_LEAVES_TEXTURE_LOCATION);
		candidates[3] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_DEEPSLATE, DEEPSLATE_TEXTURE_LOCATION);
		candidates[4] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_WHITE_WOOL, WHITE_WOOL_TEXTURE_LOCATION);
		candidates[5] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER, BARRIER_MARKER_LOCATION);
		for (int i = 0; i < LIGHT_MARKER_LOCATIONS.length; i++) {
			candidates[i + 6] = new WorldMaterialAssetCandidate(LIGHT_MARKER_TEXTURE_IDS[i], LIGHT_MARKER_LOCATIONS[i]);
		}
		return candidates;
	}

	private static MessageDigest sha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 digest is unavailable", error);
		}
	}

	public static void beginFrame(Matrix4f viewMatrix, Matrix4f projectionMatrix, int viewportWidth, int viewportHeight) {
		synchronized (LOCK) {
			PENDING_SEGMENTS.clear();
			PENDING_CRACK_QUADS.clear();
			PENDING_BORDER_QUADS.clear();
			PENDING_MATERIAL_QUADS.clear();
			PENDING_MESH_INSTANCES.clear();
			pendingBackground = VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
			seedFrameMatricesLocked(viewMatrix, projectionMatrix, viewportWidth, viewportHeight);
		}
	}

	public static void reseedFrameMatrices(Matrix4f viewMatrix, Matrix4f projectionMatrix, int viewportWidth, int viewportHeight) {
		synchronized (LOCK) {
			seedFrameMatricesLocked(viewMatrix, projectionMatrix, viewportWidth, viewportHeight);
		}
	}

	private static void seedFrameMatricesLocked(Matrix4f viewMatrix, Matrix4f projectionMatrix, int viewportWidth, int viewportHeight) {
		viewMatrix.get(PENDING_VIEW);
		projectionMatrix.get(PENDING_PROJECTION);
		if (!isFinite(PENDING_VIEW) || !isFinite(PENDING_PROJECTION)) {
			new Matrix4f().get(PENDING_VIEW);
			new Matrix4f().get(PENDING_PROJECTION);
			PENDING_SEGMENTS.clear();
			PENDING_CRACK_QUADS.clear();
			PENDING_BORDER_QUADS.clear();
			PENDING_MATERIAL_QUADS.clear();
			PENDING_MESH_INSTANCES.clear();
			pendingBackground = VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
			pendingViewportWidth = 0;
			pendingViewportHeight = 0;
			return;
		}
		pendingViewportWidth = viewportWidth;
		pendingViewportHeight = viewportHeight;
	}

	public static void clearFrame() {
		synchronized (LOCK) {
			PENDING_SEGMENTS.clear();
			PENDING_CRACK_QUADS.clear();
			PENDING_BORDER_QUADS.clear();
			PENDING_MATERIAL_QUADS.clear();
			PENDING_MESH_INSTANCES.clear();
			pendingBackground = VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
			new Matrix4f().get(PENDING_VIEW);
			new Matrix4f().get(PENDING_PROJECTION);
			pendingViewportWidth = 0;
			pendingViewportHeight = 0;
		}
	}

	public static void enqueueWorldBackground(ClientLevel level, Camera camera, float partialTick) {
		if (!WorldRenderRoutePolicy.currentBackgroundRoute().usesRustWholeFrameVulkan()) {
			return;
		}
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0 || level == null || camera == null) {
				pendingBackground = VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
				return;
			}
			VulkanicGalBridge.WorldBackgroundRecord diagnostic = diagnosticBackground(viewportWidth, viewportHeight);
			if (diagnostic.enabled() || isExplicitDiagnosticBackgroundFallback()) {
				pendingBackground = diagnostic;
				return;
			}
			int color = level.getSkyColor(camera.getPosition(), partialTick);
			pendingBackground = new VulkanicGalBridge.WorldBackgroundRecord(
				true,
				backgroundSkyType(level),
				BACKGROUND_LOAD_CLEAR,
				BACKGROUND_STORE_STORE,
				ARGB.color(255, ARGB.red(color), ARGB.green(color), ARGB.blue(color)),
				viewportWidth,
				viewportHeight
			);
		}
	}

	public static void enqueueBlockBreakingCracks(List<BlockBreakingRenderState> states, Camera camera) {
		if (!shouldUseRustWholeFrameCrack()) {
			return;
		}
		if (enqueueDiagnosticBlockBreakingCrack(camera)) {
			return;
		}
		if (states.isEmpty()) {
			return;
		}
		Vec3 cameraPos = camera.getPosition();
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				return;
			}
			for (BlockBreakingRenderState state : states) {
				if (state.progress < 0 || state.progress >= 10) {
					continue;
				}
				if (state.blockState.isAir()) {
					continue;
				}
				VoxelShape shape = state.blockState.getShape(state.level, state.blockPos, CollisionContext.of(camera.getEntity()));
				if (shape.isEmpty()) {
					shape = Shapes.block();
				}
				appendCrackShape(shape, state.blockPos, cameraPos, state.progress, viewportWidth, viewportHeight);
			}
		}
	}

	public static boolean enqueueBlockMarker(
		BlockState blockState,
		Camera camera,
		double xo,
		double x,
		double yo,
		double y,
		double zo,
		double z,
		float partialTick,
		float quadSize,
		int colorArgb
	) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentMaterialRoute();
		if (!route.usesRustOpenGl() && !route.usesRustWholeFrameVulkan()) {
			return false;
		}
		if (blockState == null || camera == null) {
			throw new IllegalStateException("Rust VulkanicGAL BlockMarker route selected without block state or camera");
		}
		int textureId = blockMarkerTextureId(blockState);
		if (textureId == 0) {
			throw new IllegalStateException(
				"Rust VulkanicGAL BlockMarker route selected for unsupported marker block "
					+ blockState.getBlock().builtInRegistryHolder().key().location()
			);
		}
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				throw new IllegalStateException("Rust VulkanicGAL BlockMarker requires a seeded world primitive frame");
			}
			Vec3 cameraPos = camera.getPosition();
			float centerX = (float)(Mth.lerp(partialTick, xo, x) - cameraPos.x());
			float centerY = (float)(Mth.lerp(partialTick, yo, y) - cameraPos.y());
			float centerZ = (float)(Mth.lerp(partialTick, zo, z) - cameraPos.z());
			float[] vertices = MATERIAL_VERTEX_SCRATCH;
			billboardVertices(camera.rotation(), centerX, centerY, centerZ, quadSize, vertices);
			ProjectedBounds projectedBounds = projectBounds(vertices, viewportWidth, viewportHeight);
			PENDING_MATERIAL_QUADS.add(new VulkanicGalBridge.WorldMaterialQuadRecord(
				STRATUM_WORLD_MATERIAL,
				MATERIAL_ID_BLOCK_MARKER_CUTOUT,
				textureId,
				MATERIAL_MODE_CUTOUT,
				DEPTH_POLICY_TEST_WRITE,
				CULL_NONE,
				WORLD_TOPOLOGY_TRIANGLES,
				WORLD_WINDING_CCW,
				colorArgb,
				vertices[0],
				vertices[1],
				vertices[2],
				vertices[3],
				vertices[4],
				vertices[5],
				vertices[6],
				vertices[7],
				vertices[8],
				vertices[9],
				vertices[10],
				vertices[11],
				1.0F,
				1.0F,
				1.0F,
				0.0F,
				0.0F,
				0.0F,
				0.0F,
				1.0F,
				viewportWidth,
				viewportHeight
			));
			recordBlockMarkerDiagnostic(
				route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl",
				textureId,
				centerX,
				centerY,
				centerZ,
				quadSize,
				colorArgb,
				viewportWidth,
				viewportHeight,
				projectedBounds
			);
			if (blockMarkerEnqueueDiagnosticLogs < 16) {
				blockMarkerEnqueueDiagnosticLogs++;
				auditMessage("Rust VulkanicGAL BlockMarker semantic request"
					+ " route=" + (route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl")
					+ " texture_id=" + textureId
					+ " material_id=" + MATERIAL_ID_BLOCK_MARKER_CUTOUT
					+ " viewport=" + viewportWidth + "x" + viewportHeight
					+ " center=" + centerX + "," + centerY + "," + centerZ
					+ " quad_size=" + quadSize
					+ " result=queued");
			}
		}
		return true;
	}

	public static boolean enqueueBlockDisplay(
		BlockRenderDispatcher blockRenderDispatcher,
		SubmitNodeStorage.BlockSubmit blockSubmit
	) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentBlockDisplayRoute();
		if (!route.usesRustOpenGl() && !route.usesRustWholeFrameVulkan()) {
			return false;
		}
		if (blockSubmit.source() != SubmitNodeStorage.BlockSubmitSource.BLOCK_DISPLAY) {
			return false;
		}
		BlockState blockState = blockSubmit.state();
		if (blockState == null || blockState.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) {
			return false;
		}
		if (blockSubmit.outlineColor() != 0) {
			return false;
		}
		if (Minecraft.getInstance().getModelManager().specialBlockModelRenderer().get().hasRenderer(blockState.getBlock())) {
			return false;
		}
		ChunkSectionLayer layer = ItemBlockRenderTypes.getChunkRenderType(blockState);
		MeshMaterial material = meshMaterialForChunkLayer(layer);
		if (material == null) {
			return false;
		}
		GraphicsFrameBenchmark.beginPhase("world.block-display.java-extraction");
		BlockMeshExtraction extraction;
		try {
			extraction = extractBlockModelMesh(
				blockRenderDispatcher,
				blockState,
				BlockPos.ZERO,
				null,
				null,
				material.materialId(),
				material.materialMode(),
				"BlockDisplay"
			);
		} finally {
			GraphicsFrameBenchmark.endPhase("world.block-display.java-extraction");
		}
		if (extraction == null) {
			return false;
		}
		GraphicsFrameBenchmark.beginPhase("world.block-display.rust-enqueue");
		try {
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) {
					throw new IllegalStateException("Rust VulkanicGAL BlockDisplay requires a seeded world primitive frame");
				}
				ensureMeshAssetLocked(extraction);
				VulkanicGalBridge.WorldMeshAssetRecord cachedAsset = WORLD_MESH_ASSETS.get(extraction.meshKey());
				long meshGeneration = cachedAsset == null ? extraction.meshGeneration() : cachedAsset.meshGeneration();
				float[] transform = new float[16];
				blockSubmit.pose().pose().get(transform);
				PENDING_MESH_INSTANCES.add(new VulkanicGalBridge.WorldMeshInstanceRecord(
					STRATUM_WORLD_MATERIAL,
					extraction.meshKey(),
					meshGeneration,
					MESH_SECTION_ALL,
					DEPTH_POLICY_TEST_WRITE,
					CULL_BACK,
					WORLD_WINDING_CCW,
					0xffffffff,
					transform,
					viewportWidth,
					viewportHeight
				));
				recordBlockDisplayDiagnostic(
					route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl",
					blockState,
					extraction.meshKey(),
					meshGeneration,
					extraction.asset(),
					transform,
					viewportWidth,
					viewportHeight
				);
				GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
					"block-display",
					(route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame:" : "rust-opengl:") + blockState.getBlockHolder().getRegisteredName()
				);
				auditMessage("Rust VulkanicGAL BlockDisplay mesh request"
					+ " route=" + (route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl")
					+ " mesh_key=" + extraction.meshKey()
					+ " mesh_generation=" + meshGeneration
					+ " vertices=" + extraction.asset().vertices().size()
					+ " index_bytes=" + extraction.asset().indexBytes().length
					+ " sections=" + extraction.asset().sections().size()
					+ " result=queued");
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.block-display.rust-enqueue");
		}
		return true;
	}

	public static boolean enqueueFallingBlock(
		BlockRenderDispatcher blockRenderDispatcher,
		SubmitNodeStorage.MovingBlockSubmit movingBlockSubmit
	) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentFallingBlockRoute();
		if (!route.usesRustOpenGl() && !route.usesRustWholeFrameVulkan()) {
			return false;
		}
		if (movingBlockSubmit.source() != SubmitNodeStorage.MovingBlockSubmitSource.FALLING_BLOCK) {
			return false;
		}
		MovingBlockRenderState movingBlockRenderState = movingBlockSubmit.movingBlockRenderState();
		BlockState blockState = movingBlockRenderState.blockState;
		if (blockState == null || blockState.isAir() || blockState.getRenderShape() != RenderShape.MODEL) {
			return false;
		}
		if (Minecraft.getInstance().getModelManager().specialBlockModelRenderer().get().hasRenderer(blockState.getBlock())) {
			return false;
		}
		RenderType renderType = ItemBlockRenderTypes.getMovingBlockRenderType(blockState);
		MeshMaterial material = meshMaterialForMovingRenderType(renderType);
		if (material == null) {
			return false;
		}
		GraphicsFrameBenchmark.beginPhase("world.falling-block.java-extraction");
		BlockMeshExtraction extraction;
		try {
			extraction = extractBlockModelMesh(
				blockRenderDispatcher,
				blockState,
				movingBlockRenderState.randomSeedPos,
				movingBlockRenderState,
				movingBlockRenderState.blockPos,
				material.materialId(),
				material.materialMode(),
				"FallingBlock"
			);
		} finally {
			GraphicsFrameBenchmark.endPhase("world.falling-block.java-extraction");
		}
		if (extraction == null) {
			return false;
		}
		GraphicsFrameBenchmark.beginPhase("world.falling-block.rust-enqueue");
		try {
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) {
					throw new IllegalStateException("Rust VulkanicGAL FallingBlock requires a seeded world primitive frame");
				}
				ensureMeshAssetLocked(extraction);
				VulkanicGalBridge.WorldMeshAssetRecord cachedAsset = WORLD_MESH_ASSETS.get(extraction.meshKey());
				long meshGeneration = cachedAsset == null ? extraction.meshGeneration() : cachedAsset.meshGeneration();
				float[] transform = new float[16];
				movingBlockSubmit.pose().get(transform);
				PENDING_MESH_INSTANCES.add(new VulkanicGalBridge.WorldMeshInstanceRecord(
					STRATUM_WORLD_MOVING_MESH,
					extraction.meshKey(),
					meshGeneration,
					MESH_SECTION_ALL,
					DEPTH_POLICY_TEST_WRITE,
					CULL_BACK,
					WORLD_WINDING_CCW,
					0xffffffff,
					transform,
					viewportWidth,
					viewportHeight
				));
				recordFallingBlockDiagnostic(
					route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl",
					blockState,
					extraction.meshKey(),
					meshGeneration,
					extraction.asset(),
					transform,
					viewportWidth,
					viewportHeight
				);
				GraphicsFrameBenchmark.recordSubmittedWorkIdentity(
					"falling-block",
					(route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame:" : "rust-opengl:") + blockState.getBlockHolder().getRegisteredName()
				);
				if (fallingBlockEnqueueDiagnosticLogs < 24) {
					fallingBlockEnqueueDiagnosticLogs++;
					auditMessage("Rust VulkanicGAL FallingBlock mesh request"
						+ " route=" + (route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl")
						+ " provenance=falling-block"
						+ " mesh_key=" + extraction.meshKey()
						+ " mesh_generation=" + meshGeneration
						+ " block=" + metricValue(blockState.getBlockHolder().getRegisteredName())
						+ " vertices=" + extraction.asset().vertices().size()
						+ " index_bytes=" + extraction.asset().indexBytes().length
						+ " sections=" + extraction.asset().sections().size()
						+ " viewport=" + viewportWidth + "x" + viewportHeight
						+ " result=queued");
				}
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.falling-block.rust-enqueue");
		}
		return true;
	}

	private static void ensureMeshAssetLocked(BlockMeshExtraction extraction) {
		if (WORLD_MESH_ASSETS.containsKey(extraction.meshKey())) {
			return;
		}
		WORLD_MESH_ASSETS.put(extraction.meshKey(), extraction.asset());
		for (VulkanicGalBridge.WorldMeshTextureAssetRecord texture : extraction.textures()) {
			WORLD_MESH_TEXTURES.putIfAbsent(texture.textureId(), texture);
		}
		worldMeshAssetGeneration++;
		attemptedWorldMeshAssetGeneration = Math.min(attemptedWorldMeshAssetGeneration, uploadedWorldMeshAssetGeneration);
		lastWorldMeshAssetPayloadCount = WORLD_MESH_ASSETS.size() + WORLD_MESH_TEXTURES.size();
		lastWorldMeshAssetPayloadBytes = 0L;
		for (VulkanicGalBridge.WorldMeshAssetRecord mesh : WORLD_MESH_ASSETS.values()) {
			lastWorldMeshAssetPayloadBytes += mesh.indexBytes().length;
			lastWorldMeshAssetPayloadBytes += (long)mesh.vertices().size() * 48L;
		}
		for (VulkanicGalBridge.WorldMeshTextureAssetRecord texture : WORLD_MESH_TEXTURES.values()) {
			lastWorldMeshAssetPayloadBytes += texture.pngBytes().length;
		}
	}

	private static MeshMaterial meshMaterialForChunkLayer(ChunkSectionLayer layer) {
		if (layer == ChunkSectionLayer.SOLID) {
			return new MeshMaterial(MATERIAL_ID_OPAQUE_TEXTURED, MATERIAL_MODE_OPAQUE);
		}
		if (layer == ChunkSectionLayer.CUTOUT || layer == ChunkSectionLayer.CUTOUT_MIPPED) {
			return new MeshMaterial(MATERIAL_ID_CUTOUT_TEXTURED, MATERIAL_MODE_CUTOUT);
		}
		return null;
	}

	private static MeshMaterial meshMaterialForMovingRenderType(RenderType renderType) {
		if (renderType == RenderType.solid()) {
			return new MeshMaterial(MATERIAL_ID_OPAQUE_TEXTURED, MATERIAL_MODE_OPAQUE);
		}
		if (renderType == RenderType.cutout() || renderType == RenderType.cutoutMipped()) {
			return new MeshMaterial(MATERIAL_ID_CUTOUT_TEXTURED, MATERIAL_MODE_CUTOUT);
		}
		return null;
	}

	private static BlockMeshExtraction extractBlockModelMesh(
		BlockRenderDispatcher blockRenderDispatcher,
		BlockState blockState,
		BlockPos randomSeedPos,
		BlockAndTintGetter tintGetter,
		BlockPos tintPos,
		int materialId,
		int materialMode,
		String diagnosticName
	) {
		try {
			BlockStateModel model = blockRenderDispatcher.getBlockModel(blockState);
			List<BlockModelPart> parts = model.collectParts(RandomSource.create(blockState.getSeed(randomSeedPos)));
			if (parts.isEmpty()) {
				return null;
			}
			List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = new ArrayList<>();
			List<VulkanicGalBridge.WorldMeshSectionRecord> sections = new ArrayList<>();
			List<VulkanicGalBridge.WorldMeshTextureAssetRecord> textures = new ArrayList<>();
			List<Integer> indices = new ArrayList<>();
			long hash = fnv64("block-model:" + blockState + ":" + randomSeedPos.asLong() + ":" + materialMode);
			for (BlockModelPart part : parts) {
				for (Direction direction : Direction.values()) {
					appendBlockModelQuads(part.getQuads(direction), blockState, tintGetter, tintPos, materialId, materialMode, vertices, indices, sections, textures);
				}
				appendBlockModelQuads(part.getQuads(null), blockState, tintGetter, tintPos, materialId, materialMode, vertices, indices, sections, textures);
			}
			if (vertices.isEmpty() || indices.isEmpty() || sections.isEmpty()) {
				return null;
			}
			byte[] indexBytes;
			int indexType;
			int indexStride;
			if (vertices.size() <= 0xffff) {
				indexType = VulkanicGalBridge.INDEX_U16;
				indexStride = 2;
				indexBytes = new byte[indices.size() * 2];
				for (int i = 0; i < indices.size(); i++) {
					int index = indices.get(i);
					indexBytes[i * 2] = (byte)(index & 0xff);
					indexBytes[i * 2 + 1] = (byte)((index >>> 8) & 0xff);
				}
			} else {
				indexType = VulkanicGalBridge.INDEX_U32;
				indexStride = 4;
				indexBytes = new byte[indices.size() * 4];
				for (int i = 0; i < indices.size(); i++) {
					int index = indices.get(i);
					indexBytes[i * 4] = (byte)(index & 0xff);
					indexBytes[i * 4 + 1] = (byte)((index >>> 8) & 0xff);
					indexBytes[i * 4 + 2] = (byte)((index >>> 16) & 0xff);
					indexBytes[i * 4 + 3] = (byte)((index >>> 24) & 0xff);
				}
			}
			List<VulkanicGalBridge.WorldMeshSectionRecord> byteSections = new ArrayList<>(sections.size());
			for (VulkanicGalBridge.WorldMeshSectionRecord section : sections) {
				byteSections.add(new VulkanicGalBridge.WorldMeshSectionRecord(
					section.materialId(),
					section.textureId(),
					section.materialMode(),
					section.cullPolicy(),
					section.winding(),
					Math.multiplyExact(section.indexOffset(), indexStride),
					section.indexCount()
				));
			}
			long meshKey = hash == 0L ? 1L : hash;
			long meshGeneration = Math.max(1L, worldMeshAssetGeneration + 1L);
			return new BlockMeshExtraction(
				meshKey,
				meshGeneration,
				new VulkanicGalBridge.WorldMeshAssetRecord(
					meshKey,
					meshGeneration,
					MESH_VERTEX_LAYOUT_V1,
					indexType,
					vertices,
					indexBytes,
					byteSections
				),
				textures
			);
		} catch (RuntimeException error) {
			throw new IllegalStateException("Rust VulkanicGAL " + diagnosticName + " mesh extraction failed", error);
		}
	}

	private static void appendBlockModelQuads(
		List<BakedQuad> quads,
		BlockState blockState,
		BlockAndTintGetter tintGetter,
		BlockPos tintPos,
		int materialId,
		int materialMode,
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices,
		List<Integer> indices,
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections,
		List<VulkanicGalBridge.WorldMeshTextureAssetRecord> textures
	) {
		if (quads.isEmpty()) {
			return;
		}
		for (BakedQuad bakedQuad : quads) {
			BakedQuadView quad = (BakedQuadView)(Object)bakedQuad;
			TextureAtlasSprite sprite = quad.getSprite();
			ResourceLocation spriteName = sprite.contents().name();
			int textureId = stableTextureId(spriteName);
			byte[] payload = readTexturePayload(spriteName);
			if (payload == null) {
				throw new IllegalStateException("unsupported block-model texture asset " + spriteName);
			}
			textures.add(new VulkanicGalBridge.WorldMeshTextureAssetRecord(textureId, payload));
			int firstIndex = indices.size();
			int base = vertices.size();
			int tintColor = bakedQuad.isTinted()
				? 0xff000000 | Minecraft.getInstance().getBlockColors().getColor(blockState, tintGetter, tintPos, bakedQuad.tintIndex())
				: 0xffffffff;
			int winding = blockDisplayQuadWinding(quad, bakedQuad.direction());
			for (int i = 0; i < 4; i++) {
				vertices.add(new VulkanicGalBridge.WorldMeshVertexRecord(
					quad.getX(i),
					quad.getY(i),
					quad.getZ(i),
					spriteLocalU(sprite, quad.getTexU(i)),
					spriteLocalV(sprite, quad.getTexV(i)),
					tintColor,
					quad.getVertexNormal(i),
					quad.getLight(i)
				));
			}
			indices.add(base);
			indices.add(base + 1);
			indices.add(base + 2);
			indices.add(base + 2);
			indices.add(base + 3);
			indices.add(base);
			sections.add(new VulkanicGalBridge.WorldMeshSectionRecord(
				materialId,
				textureId,
				materialMode,
				CULL_BACK,
				winding,
				firstIndex,
				6
			));
		}
	}

	private static int blockDisplayQuadWinding(BakedQuadView quad, Direction direction) {
		if (direction == null) {
			return WORLD_WINDING_CCW;
		}
		float ax = quad.getX(1) - quad.getX(0);
		float ay = quad.getY(1) - quad.getY(0);
		float az = quad.getZ(1) - quad.getZ(0);
		float bx = quad.getX(2) - quad.getX(0);
		float by = quad.getY(2) - quad.getY(0);
		float bz = quad.getZ(2) - quad.getZ(0);
		float crossX = ay * bz - az * by;
		float crossY = az * bx - ax * bz;
		float crossZ = ax * by - ay * bx;
		float dot = crossX * direction.getStepX() + crossY * direction.getStepY() + crossZ * direction.getStepZ();
		return dot >= 0.0F ? WORLD_WINDING_CCW : WORLD_WINDING_CW;
	}

	private static byte[] readTexturePayload(ResourceLocation spriteName) {
		if (MISSING_TEXTURE_LOCATION.equals(spriteName)) {
			return missingTexturePayload();
		}
		ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath(
			spriteName.getNamespace(),
			"textures/" + spriteName.getPath() + ".png"
		);
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(textureLocation);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream input = resource.get().open()) {
			return input.readAllBytes();
		} catch (IOException error) {
			LOGGER.warn("Failed to read Rust VulkanicGAL block-model texture {}", textureLocation, error);
			return null;
		}
	}

	private static byte[] missingTexturePayload() {
		byte[] cached = missingTexturePayload;
		if (cached != null) {
			return cached;
		}
		try {
			BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					boolean dark = ((x / 8) + (y / 8)) % 2 == 0;
					image.setRGB(x, y, dark ? 0xff000000 : 0xffff00ff);
				}
			}
			ByteArrayOutputStream output = new ByteArrayOutputStream(256);
			ImageIO.write(image, "png", output);
			cached = output.toByteArray();
			missingTexturePayload = cached;
			return cached;
		} catch (IOException error) {
			LOGGER.warn("Failed to generate Rust VulkanicGAL missing block-model texture payload", error);
			return null;
		}
	}

	private static float spriteLocalU(TextureAtlasSprite sprite, float atlasU) {
		float width = sprite.getU1() - sprite.getU0();
		return width == 0.0F ? 0.0F : Mth.clamp((atlasU - sprite.getU0()) / width, 0.0F, 1.0F);
	}

	private static float spriteLocalV(TextureAtlasSprite sprite, float atlasV) {
		float height = sprite.getV1() - sprite.getV0();
		return height == 0.0F ? 0.0F : Mth.clamp((atlasV - sprite.getV0()) / height, 0.0F, 1.0F);
	}

	private static int stableTextureId(ResourceLocation location) {
		long hash = fnv64(location.toString());
		int id = (int)(hash ^ (hash >>> 32));
		return id == 0 ? 1 : id;
	}

	private static long fnv64(String value) {
		long hash = 0xcbf29ce484222325L;
		for (int i = 0; i < value.length(); i++) {
			hash ^= value.charAt(i);
			hash *= 0x100000001b3L;
		}
		return hash == 0L ? 1L : hash;
	}

	private record BlockMeshExtraction(
		long meshKey,
		long meshGeneration,
		VulkanicGalBridge.WorldMeshAssetRecord asset,
		List<VulkanicGalBridge.WorldMeshTextureAssetRecord> textures
	) {
		public BlockMeshExtraction {
			textures = List.copyOf(textures);
		}
	}

	private record MeshMaterial(int materialId, int materialMode) {
	}

	private static void recordBlockMarkerDiagnostic(
		String route,
		int textureId,
		float centerX,
		float centerY,
		float centerZ,
		float quadSize,
		int colorArgb,
		int viewportWidth,
		int viewportHeight,
		ProjectedBounds projectedBounds
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		if (BLOCK_MARKER_DIAGNOSTICS.size() >= 256) {
			BLOCK_MARKER_DIAGNOSTICS.remove(0);
		}
		BLOCK_MARKER_DIAGNOSTICS.add(new BlockMarkerDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(),
			route,
			textureId,
			centerX,
			centerY,
			centerZ,
			quadSize,
			colorArgb,
			viewportWidth,
			viewportHeight,
			projectedBounds.valid(),
			projectedBounds.left(),
			projectedBounds.top(),
			projectedBounds.right(),
			projectedBounds.bottom()
		));
	}

	public static List<BlockMarkerDiagnostic> blockMarkerDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(BLOCK_MARKER_DIAGNOSTICS);
		}
	}

	public static boolean enqueueTerrainParticle(
		BlockState blockState,
		ResourceLocation spriteId,
		Camera camera,
		double xo,
		double x,
		double yo,
		double y,
		double zo,
		double z,
		Quaternionf rotation,
		float partialTick,
		float quadSize,
		float localU0,
		float localU1,
		float localV0,
		float localV1,
		int colorArgb,
		int packedLight,
		boolean opaque
	) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentMaterialRoute();
		if (!route.usesRustOpenGl() && !route.usesRustWholeFrameVulkan()) {
			return false;
		}
		int textureId = terrainParticleTextureId(blockState);
		if (textureId == 0) {
			return false;
		}
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				throw new IllegalStateException("Rust VulkanicGAL TerrainParticle requires a seeded world primitive frame");
			}
			Vec3 cameraPos = camera.getPosition();
			float centerX = (float)(Mth.lerp(partialTick, xo, x) - cameraPos.x());
			float centerY = (float)(Mth.lerp(partialTick, yo, y) - cameraPos.y());
			float centerZ = (float)(Mth.lerp(partialTick, zo, z) - cameraPos.z());
			float[] vertices = MATERIAL_VERTEX_SCRATCH;
			billboardVertices(rotation, centerX, centerY, centerZ, quadSize, vertices);
			int materialMode = opaque ? MATERIAL_MODE_OPAQUE : MATERIAL_MODE_CUTOUT;
			int materialId = opaque ? MATERIAL_ID_OPAQUE_TEXTURED : MATERIAL_ID_CUTOUT_TEXTURED;
			int litColor = applyPackedLight(colorArgb, packedLight);
			PENDING_MATERIAL_QUADS.add(new VulkanicGalBridge.WorldMaterialQuadRecord(
				STRATUM_WORLD_MATERIAL,
				materialId,
				textureId,
				materialMode,
				DEPTH_POLICY_TEST_WRITE,
				CULL_NONE,
				WORLD_TOPOLOGY_TRIANGLES,
				WORLD_WINDING_CCW,
				litColor,
				vertices[0],
				vertices[1],
				vertices[2],
				vertices[3],
				vertices[4],
				vertices[5],
				vertices[6],
				vertices[7],
				vertices[8],
				vertices[9],
				vertices[10],
				vertices[11],
				localU1,
				localV1,
				localU1,
				localV0,
				localU0,
				localV0,
				localU0,
				localV1,
				viewportWidth,
				viewportHeight
			));
			ProjectedBounds projectedBounds = projectBounds(vertices, viewportWidth, viewportHeight);
			recordTerrainParticleDiagnostic(
				route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl",
				textureId,
				spriteId == null ? "unknown" : spriteId.toString(),
				centerX,
				centerY,
				centerZ,
				quadSize,
				litColor,
				packedLight,
				materialMode,
				localU0,
				localU1,
				localV0,
				localV1,
				viewportWidth,
				viewportHeight,
				projectedBounds
			);
			if (terrainParticleEnqueueDiagnosticLogs < 24) {
				terrainParticleEnqueueDiagnosticLogs++;
				auditMessage("Rust VulkanicGAL TerrainParticle semantic request"
					+ " route=" + (route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl")
					+ " texture_id=" + textureId
					+ " material_id=" + materialId
					+ " mode=" + materialMode
					+ " sprite=" + metricValue(spriteId == null ? "unknown" : spriteId.toString())
					+ " viewport=" + viewportWidth + "x" + viewportHeight
					+ " center=" + centerX + "," + centerY + "," + centerZ
					+ " quad_size=" + quadSize
					+ " light=" + packedLight
					+ " result=queued");
			}
		}
		return true;
	}

	private static void recordTerrainParticleDiagnostic(
		String route,
		int textureId,
		String spriteId,
		float centerX,
		float centerY,
		float centerZ,
		float quadSize,
		int colorArgb,
		int packedLight,
		int materialMode,
		float localU0,
		float localU1,
		float localV0,
		float localV1,
		int viewportWidth,
		int viewportHeight,
		ProjectedBounds projectedBounds
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		if (TERRAIN_PARTICLE_DIAGNOSTICS.size() >= 512) {
			TERRAIN_PARTICLE_DIAGNOSTICS.remove(0);
		}
		TERRAIN_PARTICLE_DIAGNOSTICS.add(new TerrainParticleDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(),
			route,
			textureId,
			spriteId,
			centerX,
			centerY,
			centerZ,
			quadSize,
			colorArgb,
			packedLight,
			materialMode,
			localU0,
			localU1,
			localV0,
			localV1,
			viewportWidth,
			viewportHeight,
			projectedBounds.valid(),
			projectedBounds.left(),
			projectedBounds.top(),
			projectedBounds.right(),
			projectedBounds.bottom()
		));
	}

	public static List<TerrainParticleDiagnostic> terrainParticleDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(TERRAIN_PARTICLE_DIAGNOSTICS);
		}
	}

	private static void recordBlockDisplayDiagnostic(
		String route,
		BlockState blockState,
		long meshKey,
		long meshGeneration,
		VulkanicGalBridge.WorldMeshAssetRecord asset,
		float[] transform,
		int viewportWidth,
		int viewportHeight
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		if (BLOCK_DISPLAY_DIAGNOSTICS.size() >= 512) {
			BLOCK_DISPLAY_DIAGNOSTICS.remove(0);
		}
		ProjectedBounds projectedBounds = projectMeshBounds(asset.vertices(), transform, viewportWidth, viewportHeight);
		String blockId = blockState.getBlock().builtInRegistryHolder().key().location().toString();
		String textureIds = meshTextureIds(asset);
		int materialMode = asset.sections().isEmpty() ? 0 : asset.sections().get(0).materialMode();
		int sectionCount = asset.sections().size();
		BLOCK_DISPLAY_DIAGNOSTICS.add(new BlockDisplayDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(),
			route,
			blockId,
			meshKey,
			meshGeneration,
			asset.vertexLayoutVersion(),
			asset.indexType(),
			asset.vertices().size(),
			asset.indexBytes().length,
			sectionCount,
			textureIds,
			materialMode,
			viewportWidth,
			viewportHeight,
			projectedBounds.valid(),
			projectedBounds.left(),
			projectedBounds.top(),
			projectedBounds.right(),
			projectedBounds.bottom()
		));
	}

	public static List<BlockDisplayDiagnostic> blockDisplayDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(BLOCK_DISPLAY_DIAGNOSTICS);
		}
	}

	private static void recordFallingBlockDiagnostic(
		String route,
		BlockState blockState,
		long meshKey,
		long meshGeneration,
		VulkanicGalBridge.WorldMeshAssetRecord asset,
		float[] transform,
		int viewportWidth,
		int viewportHeight
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		if (FALLING_BLOCK_DIAGNOSTICS.size() >= 512) {
			FALLING_BLOCK_DIAGNOSTICS.remove(0);
		}
		ProjectedBounds projectedBounds = projectMeshBounds(asset.vertices(), transform, viewportWidth, viewportHeight);
		String blockId = blockState.getBlock().builtInRegistryHolder().key().location().toString();
		String textureIds = meshTextureIds(asset);
		int materialMode = asset.sections().isEmpty() ? 0 : asset.sections().get(0).materialMode();
		FALLING_BLOCK_DIAGNOSTICS.add(new FallingBlockDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(),
			route,
			blockId,
			meshKey,
			meshGeneration,
			asset.vertexLayoutVersion(),
			asset.indexType(),
			asset.vertices().size(),
			asset.indexBytes().length,
			asset.sections().size(),
			textureIds,
			materialMode,
			viewportWidth,
			viewportHeight,
			projectedBounds.valid(),
			projectedBounds.left(),
			projectedBounds.top(),
			projectedBounds.right(),
			projectedBounds.bottom()
		));
	}

	public static List<FallingBlockDiagnostic> fallingBlockDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(FALLING_BLOCK_DIAGNOSTICS);
		}
	}

	public static void recordFallingBlockRouteDecision(
		String route,
		BlockState blockState,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		String blockId = blockState == null ? "missing" : blockState.getBlock().builtInRegistryHolder().key().location().toString();
		synchronized (LOCK) {
			if (FALLING_BLOCK_ROUTE_DECISIONS.size() >= 512) {
				FALLING_BLOCK_ROUTE_DECISIONS.remove(0);
			}
			FALLING_BLOCK_ROUTE_DECISIONS.add(new FallingBlockRouteDecision(
				DeterministicCameraCapture.currentRenderedFrameIndex(),
				route,
				blockId,
				rustSelected,
				rustQueued,
				javaDrawn
			));
		}
	}

	public static List<FallingBlockRouteDecision> fallingBlockRouteDecisions() {
		synchronized (LOCK) {
			return List.copyOf(FALLING_BLOCK_ROUTE_DECISIONS);
		}
	}

	public static boolean hasPendingMaterialQuads() {
		synchronized (LOCK) {
			return !PENDING_MATERIAL_QUADS.isEmpty();
		}
	}

	public static boolean hasPendingMeshInstances() {
		synchronized (LOCK) {
			return !PENDING_MESH_INSTANCES.isEmpty();
		}
	}

	public static boolean renderOpenGlPendingMaterialQuads(Minecraft minecraft, String producerLabel) {
		if (!shouldUseRustOpenGlMaterial()) {
			return false;
		}
		PrimitiveFrame frame;
		synchronized (LOCK) {
			if (PENDING_MATERIAL_QUADS.isEmpty()) {
				return false;
			}
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				throw new IllegalStateException("Rust OpenGL world material quads require a seeded world primitive frame");
			}
			frame = new PrimitiveFrame(
				viewportWidth,
				viewportHeight,
				PENDING_VIEW.clone(),
				PENDING_PROJECTION.clone(),
				VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback(),
				List.of(),
				List.of(),
				List.of(),
				List.copyOf(PENDING_MATERIAL_QUADS),
				List.of()
			);
			PENDING_MATERIAL_QUADS.clear();
		}
		auditMessage("Rust VulkanicGAL world material request"
			+ " route=rust-opengl"
			+ " producer=" + metricValue(producerLabel)
			+ " quads=" + frame.materialQuads().size()
			+ " " + materialMarkerSummary(frame.materialQuads())
			+ " result=queued");
		return RustGalFrameCoordinator.executeWorldPrimitiveFrame(minecraft, frame, producerLabel);
	}

	public static boolean renderOpenGlPendingMeshInstances(Minecraft minecraft, String producerLabel) {
		if (!shouldUseRustOpenGlMeshInstances()) {
			return false;
		}
		PrimitiveFrame frame;
		synchronized (LOCK) {
			if (PENDING_MESH_INSTANCES.isEmpty()) {
				return false;
			}
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				throw new IllegalStateException("Rust OpenGL world mesh instances require a seeded world primitive frame");
			}
			frame = new PrimitiveFrame(
				viewportWidth,
				viewportHeight,
				PENDING_VIEW.clone(),
				PENDING_PROJECTION.clone(),
				VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback(),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				List.copyOf(PENDING_MESH_INSTANCES)
			);
			PENDING_MESH_INSTANCES.clear();
		}
		auditMessage("Rust VulkanicGAL world mesh request"
			+ " route=rust-opengl"
			+ " producer=" + metricValue(producerLabel)
			+ " mesh_instances=" + frame.meshInstances().size()
			+ " result=queued");
		return RustGalFrameCoordinator.executeWorldPrimitiveFrame(minecraft, frame, producerLabel);
	}

	public static String materialMarkerSummary(List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads) {
		int barrier = 0;
		int light = 0;
		int terrain = 0;
		int lastTexture = 0;
		int lastLightLevel = -1;
		int lightLevelMask = 0;
		int terrainTextureMask = 0;
		for (VulkanicGalBridge.WorldMaterialQuadRecord quad : materialQuads) {
			int texture = quad.textureId();
			lastTexture = texture;
			if (texture == MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER) {
				barrier++;
			} else if (lightMarkerLevel(texture) >= 0) {
				light++;
				lastLightLevel = lightMarkerLevel(texture);
				lightLevelMask |= 1 << lastLightLevel;
				} else if (isTerrainParticleTextureId(texture)) {
					terrain++;
					terrainTextureMask |= 1 << terrainParticleTextureOrdinal(texture);
				}
		}
		return "material_marker_barrier_quads=" + barrier
			+ " material_marker_light_quads=" + light
			+ " material_terrain_particle_quads=" + terrain
			+ " material_terrain_particle_texture_mask=" + terrainTextureMask
			+ " material_marker_light_level_mask=" + lightLevelMask
			+ " material_marker_last_light_level=" + lastLightLevel
			+ " material_marker_last_texture_id=" + lastTexture;
	}

	private static boolean isTerrainParticleTextureId(int texture) {
		return TERRAIN_PARTICLE_TEXTURE_IDS.containsValue(texture);
	}

	private static int terrainParticleTextureOrdinal(int texture) {
		if (texture == MATERIAL_TEXTURE_STONE) {
			return 0;
		}
		if (texture == MATERIAL_TEXTURE_DIRT) {
			return 1;
		}
		if (texture == MATERIAL_TEXTURE_OAK_LEAVES) {
			return 2;
		}
		if (texture == MATERIAL_TEXTURE_DEEPSLATE) {
			return 3;
		}
		if (texture == MATERIAL_TEXTURE_WHITE_WOOL) {
			return 4;
		}
		return 31;
	}

	private static int lightMarkerLevel(int texture) {
		for (int i = 0; i < LIGHT_MARKER_TEXTURE_IDS.length; i++) {
			if (LIGHT_MARKER_TEXTURE_IDS[i] == texture) {
				return i;
			}
		}
		return -1;
	}

	public static int lightMarkerTextureId(int level) {
		return LIGHT_MARKER_TEXTURE_IDS[Mth.clamp(level, 0, 15)];
	}

	public static boolean renderOpenGlBlockBreakingCracks(
		Minecraft minecraft,
		List<BlockBreakingRenderState> states,
		Camera camera
	) {
		if (!shouldUseRustOpenGlCrack()) {
			return false;
		}
		if (minecraft == null || camera == null || states == null) {
			return false;
		}
		if (states.isEmpty() && DIAGNOSTIC_CRACK_SCENARIO.isBlank()) {
			auditMessage("Rust VulkanicGAL block-breaking crack request"
				+ " route=rust-opengl"
				+ " real_destroy_progress=true"
				+ " states=0"
				+ " quads=0"
				+ " first=none"
				+ " result=no-work");
			return false;
		}
		PrimitiveFrame frame;
		Vec3 cameraPos = camera.getPosition();
		String firstStateSummary = firstCrackStateSummary(states);
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				throw new IllegalStateException("Rust OpenGL block-breaking crack overlay requires a seeded world primitive frame");
			}
			PENDING_CRACK_QUADS.clear();
			if (enqueueDiagnosticBlockBreakingCrack(camera)) {
				// Diagnostic path wrote the requested quads or deliberately left the frame empty.
			} else {
				for (BlockBreakingRenderState state : states) {
					if (state.progress < 0 || state.progress >= 10) {
						continue;
					}
					if (state.blockState.isAir()) {
						continue;
					}
					VoxelShape shape = state.blockState.getShape(state.level, state.blockPos, CollisionContext.of(camera.getEntity()));
					if (shape.isEmpty()) {
						shape = Shapes.block();
					}
					appendCrackShape(shape, state.blockPos, cameraPos, state.progress, viewportWidth, viewportHeight);
				}
			}
			if (PENDING_CRACK_QUADS.isEmpty()) {
				auditMessage("Rust VulkanicGAL block-breaking crack request"
					+ " route=rust-opengl"
					+ " real_destroy_progress=" + DIAGNOSTIC_CRACK_SCENARIO.isBlank()
					+ " states=" + states.size()
					+ " quads=0"
					+ " first=" + metricValue(firstStateSummary)
					+ " result=empty");
				return false;
			}
			frame = new PrimitiveFrame(
				viewportWidth,
				viewportHeight,
				PENDING_VIEW.clone(),
				PENDING_PROJECTION.clone(),
				VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback(),
				List.of(),
				List.copyOf(PENDING_CRACK_QUADS),
				List.of(),
				List.of(),
				List.of()
			);
			PENDING_CRACK_QUADS.clear();
		}
		auditMessage("Rust VulkanicGAL block-breaking crack request"
			+ " route=rust-opengl"
			+ " real_destroy_progress=" + DIAGNOSTIC_CRACK_SCENARIO.isBlank()
			+ " states=" + states.size()
			+ " quads=" + frame.crackQuads().size()
			+ " first=" + metricValue(firstStateSummary)
			+ " result=queued");
		return RustGalFrameCoordinator.executeWorldPrimitiveFrame(minecraft, frame, "minecraft.world.block-breaking-crack");
	}

	public static boolean hasValidOpenGlBlockBreakingCracks(List<BlockBreakingRenderState> states, Camera camera) {
		if (!shouldUseRustOpenGlCrack() || states == null || camera == null) {
			return false;
		}
		if (!DIAGNOSTIC_CRACK_SCENARIO.isBlank()) {
			return !"hidden".equalsIgnoreCase(DIAGNOSTIC_CRACK_SCENARIO)
				&& !"no-target".equalsIgnoreCase(DIAGNOSTIC_CRACK_SCENARIO);
		}
		for (BlockBreakingRenderState state : states) {
			if (state.progress < 0 || state.progress >= 10 || state.blockState.isAir()) {
				continue;
			}
			return true;
		}
		return false;
	}

	private static boolean enqueueDiagnosticBlockBreakingCrack(Camera camera) {
		if (DIAGNOSTIC_CRACK_SCENARIO.isBlank()) {
			return false;
		}
		if ("hidden".equalsIgnoreCase(DIAGNOSTIC_CRACK_SCENARIO) || "no-target".equalsIgnoreCase(DIAGNOSTIC_CRACK_SCENARIO)) {
			return true;
		}
		VoxelShape shape = diagnosticShape(DIAGNOSTIC_CRACK_SCENARIO, "Rust GAL world-crack diagnostic scenario");
		if (shape.isEmpty()) {
			return true;
		}
		int stage = diagnosticCrackStage();
		Vec3 cameraPos = camera.getPosition();
		BlockPos blockPos = diagnosticBlockPos(camera);
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				return true;
			}
			appendCrackShape(shape, blockPos, cameraPos, stage, viewportWidth, viewportHeight);
		}
		return true;
	}

	private static String firstCrackStateSummary(List<BlockBreakingRenderState> states) {
		if (states == null || states.isEmpty()) {
			return "none";
		}
		BlockBreakingRenderState state = states.get(0);
		String block = state.blockPos == null ? "unknown" : state.blockPos.toShortString();
		String blockType = state.blockState == null ? "unknown" : state.blockState.getBlock().builtInRegistryHolder().key().location().toString();
		return "block_" + block + "_stage_" + state.progress + "_type_" + blockType;
	}

	private static int blockMarkerTextureId(BlockState blockState) {
		if (blockState.is(Blocks.BARRIER)) {
			return MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER;
		}
		if (blockState.is(Blocks.LIGHT)) {
			return LIGHT_MARKER_TEXTURE_IDS[Mth.clamp(blockState.getValue(LightBlock.LEVEL), 0, 15)];
		}
		return 0;
	}

	private static int terrainParticleTextureId(BlockState blockState) {
		if (blockState == null) {
			return 0;
		}
		return TERRAIN_PARTICLE_TEXTURE_IDS.getOrDefault(blockState.getBlock(), 0);
	}

	private static int applyPackedLight(int colorArgb, int packedLight) {
		int light = Math.max(LightTexture.block(packedLight), LightTexture.sky(packedLight));
		float brightness = 0.35F + 0.65F * (light / 15.0F);
		int alpha = ARGB.alpha(colorArgb);
		int red = Mth.clamp(Math.round(ARGB.red(colorArgb) * brightness), 0, 255);
		int green = Mth.clamp(Math.round(ARGB.green(colorArgb) * brightness), 0, 255);
		int blue = Mth.clamp(Math.round(ARGB.blue(colorArgb) * brightness), 0, 255);
		return ARGB.color(alpha, red, green, blue);
	}

	private static void billboardVertices(
		Quaternionf rotation,
		float centerX,
		float centerY,
		float centerZ,
		float quadSize,
		float[] vertices
	) {
		for (int i = 0; i < 4; i++) {
			Vector3f vertex = MATERIAL_BILLBOARD_VERTEX_SCRATCH.set(
				MATERIAL_BILLBOARD_CORNERS[i * 2] * quadSize,
				MATERIAL_BILLBOARD_CORNERS[i * 2 + 1] * quadSize,
				0.0F
			);
			rotation.transform(vertex);
			vertices[i * 3] = centerX + vertex.x();
			vertices[i * 3 + 1] = centerY + vertex.y();
			vertices[i * 3 + 2] = centerZ + vertex.z();
		}
	}

	public static boolean enqueueWorldBorder(WorldBorderRenderState state, Vec3 cameraPosition, double renderDistance, double depthFar) {
		if (!WorldRenderRoutePolicy.currentWorldBorderRoute().usesRustWholeFrameVulkan()) {
			return false;
		}
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				return true;
			}
			if (enqueueDiagnosticWorldBorder(cameraPosition, renderDistance, depthFar, viewportWidth, viewportHeight)) {
				return true;
			}
			if (state.alpha <= 0.0) {
				return true;
			}
			appendVisibleWorldBorderSides(
				state.minX,
				state.maxX,
				state.minZ,
				state.maxZ,
				state.alpha,
				state.tint,
				cameraPosition,
				renderDistance,
				depthFar,
				viewportWidth,
				viewportHeight,
				false
			);
		}
		return true;
	}

	private static boolean enqueueDiagnosticWorldBorder(Vec3 cameraPosition, double renderDistance, double depthFar, int viewportWidth, int viewportHeight) {
		if (DIAGNOSTIC_BORDER_SCENARIO.isBlank()) {
			return false;
		}
		DiagnosticBorderBounds bounds = diagnosticBorderBounds(cameraPosition, renderDistance);
		if (bounds.alpha <= 0.0) {
			return true;
		}
		appendVisibleWorldBorderSides(
			bounds.minX,
			bounds.maxX,
			bounds.minZ,
			bounds.maxZ,
			bounds.alpha,
			bounds.tint,
			cameraPosition,
			renderDistance,
			depthFar,
			viewportWidth,
			viewportHeight,
			bounds.forceAllSides
		);
		return true;
	}

	public static boolean applyDiagnosticWorldBorderState(WorldBorderRenderState state, Vec3 cameraPosition, double renderDistance) {
		if (DIAGNOSTIC_BORDER_SCENARIO.isBlank()) {
			return false;
		}
		DiagnosticBorderBounds bounds = diagnosticBorderBounds(cameraPosition, renderDistance);
		state.minX = bounds.minX;
		state.maxX = bounds.maxX;
		state.minZ = bounds.minZ;
		state.maxZ = bounds.maxZ;
		state.alpha = bounds.alpha;
		state.tint = bounds.tint;
		return true;
	}

	private static DiagnosticBorderBounds diagnosticBorderBounds(Vec3 cameraPosition, double renderDistance) {
		String scenario = DIAGNOSTIC_BORDER_SCENARIO.toLowerCase(java.util.Locale.ROOT);
		double near = "corner".equals(scenario) ? 2.0 : 4.0;
		double far = Math.max(renderDistance * 4.0, 512.0);
		if ("hidden".equals(scenario) || "far".equals(scenario) || "no-target".equals(scenario)) {
			return new DiagnosticBorderBounds(
				cameraPosition.x - far,
				cameraPosition.x + far,
				cameraPosition.z - far,
				cameraPosition.z + far,
				0.0,
				0x55ff55,
				false
			);
		}
		if ("corner".equals(scenario)) {
			return new DiagnosticBorderBounds(
				cameraPosition.x - near,
				cameraPosition.x + far,
				cameraPosition.z - near,
				cameraPosition.z + far,
				0.85,
				0x55ff55,
				false
			);
		}
		if ("all-sides".equals(scenario)) {
			return new DiagnosticBorderBounds(
				cameraPosition.x - near,
				cameraPosition.x + near,
				cameraPosition.z - near,
				cameraPosition.z + near,
				0.85,
				0x55ff55,
				true
			);
		}
		return new DiagnosticBorderBounds(
			cameraPosition.x - far,
			cameraPosition.x + far,
			cameraPosition.z - near,
			cameraPosition.z + far,
			0.85,
			0x55ff55,
			false
		);
	}

	private static void appendVisibleWorldBorderSides(
		double minX,
		double maxX,
		double minZ,
		double maxZ,
		double alpha,
		int tint,
		Vec3 cameraPosition,
		double renderDistance,
		double depthFar,
		int viewportWidth,
		int viewportHeight,
		boolean forceAllSides
	) {
		double cameraX = cameraPosition.x;
		double cameraZ = cameraPosition.z;
		List<WorldBorderRenderState.DistancePerDirection> sides = new ArrayList<>();
		sides.add(new WorldBorderRenderState.DistancePerDirection(Direction.NORTH, cameraZ - minZ));
		sides.add(new WorldBorderRenderState.DistancePerDirection(Direction.SOUTH, maxZ - cameraZ));
		sides.add(new WorldBorderRenderState.DistancePerDirection(Direction.WEST, cameraX - minX));
		sides.add(new WorldBorderRenderState.DistancePerDirection(Direction.EAST, maxX - cameraX));
		sides.sort(java.util.Comparator.comparingDouble(WorldBorderRenderState.DistancePerDirection::distance));
		for (WorldBorderRenderState.DistancePerDirection side : sides) {
			if (forceAllSides || side.distance() < renderDistance) {
				appendWorldBorderSide(
					side.direction(),
					minX,
					maxX,
					minZ,
					maxZ,
					alpha,
					tint,
					cameraPosition,
					renderDistance,
					depthFar,
					(float)side.distance(),
					viewportWidth,
					viewportHeight
				);
			}
		}
	}

	private static void appendWorldBorderSide(
		Direction direction,
		double minX,
		double maxX,
		double minZ,
		double maxZ,
		double alpha,
		int tint,
		Vec3 cameraPosition,
		double renderDistance,
		double depthFar,
		float distanceToBorder,
		int viewportWidth,
		int viewportHeight
	) {
		double clippedMinZ = Math.max(Mth.floor(cameraPosition.z - renderDistance), minZ);
		double clippedMaxZ = Math.min(Mth.ceil(cameraPosition.z + renderDistance), maxZ);
		float zUvStart = (Mth.floor(clippedMinZ) & 1) * 0.5F;
		float zUvWidth = (float)(clippedMaxZ - clippedMinZ) / 2.0F;
		double clippedMinX = Math.max(Mth.floor(cameraPosition.x - renderDistance), minX);
		double clippedMaxX = Math.min(Mth.ceil(cameraPosition.x + renderDistance), maxX);
		float xUvStart = (Mth.floor(clippedMinX) & 1) * 0.5F;
		float xUvWidth = (float)(clippedMaxX - clippedMinX) / 2.0F;
		float y0 = (float)-depthFar;
		float y1 = (float)depthFar;
		float topV = (float)(-Mth.frac(cameraPosition.y * 0.5));
		float bottomV = topV + (float)depthFar;
		float scroll = diagnosticWorldBorderScroll((float)(Util.getMillis() % 3000L) / 3000.0F);
		int color = (Mth.clamp((int)Math.round(alpha * 255.0), 0, 255) << 24) | (tint & 0x00ffffff);
		float borderSize = (float)Math.max(maxX - minX, maxZ - minZ);
		switch (direction) {
			case SOUTH -> appendWorldBorderQuad(
				color,
				borderSize,
				distanceToBorder,
				scroll,
				xUvStart,
				bottomV,
				xUvWidth,
				topV - bottomV,
				(float)(clippedMinX - cameraPosition.x), y0, (float)(maxZ - cameraPosition.z),
				(float)(clippedMaxX - cameraPosition.x), y0, (float)(maxZ - cameraPosition.z),
				(float)(clippedMaxX - cameraPosition.x), y1, (float)(maxZ - cameraPosition.z),
				(float)(clippedMinX - cameraPosition.x), y1, (float)(maxZ - cameraPosition.z),
				viewportWidth,
				viewportHeight
			);
			case WEST -> appendWorldBorderQuad(
				color,
				borderSize,
				distanceToBorder,
				scroll,
				zUvStart,
				bottomV,
				zUvWidth,
				topV - bottomV,
				(float)(minX - cameraPosition.x), y0, (float)(clippedMinZ - cameraPosition.z),
				(float)(minX - cameraPosition.x), y0, (float)(clippedMaxZ - cameraPosition.z),
				(float)(minX - cameraPosition.x), y1, (float)(clippedMaxZ - cameraPosition.z),
				(float)(minX - cameraPosition.x), y1, (float)(clippedMinZ - cameraPosition.z),
				viewportWidth,
				viewportHeight
			);
			case NORTH -> appendWorldBorderQuad(
				color,
				borderSize,
				distanceToBorder,
				scroll,
				xUvStart,
				bottomV,
				xUvWidth,
				topV - bottomV,
				(float)(clippedMaxX - cameraPosition.x), y0, (float)(minZ - cameraPosition.z),
				(float)(clippedMinX - cameraPosition.x), y0, (float)(minZ - cameraPosition.z),
				(float)(clippedMinX - cameraPosition.x), y1, (float)(minZ - cameraPosition.z),
				(float)(clippedMaxX - cameraPosition.x), y1, (float)(minZ - cameraPosition.z),
				viewportWidth,
				viewportHeight
			);
			case EAST -> appendWorldBorderQuad(
				color,
				borderSize,
				distanceToBorder,
				scroll,
				zUvStart,
				bottomV,
				zUvWidth,
				topV - bottomV,
				(float)(maxX - cameraPosition.x), y0, (float)(clippedMaxZ - cameraPosition.z),
				(float)(maxX - cameraPosition.x), y0, (float)(clippedMinZ - cameraPosition.z),
				(float)(maxX - cameraPosition.x), y1, (float)(clippedMinZ - cameraPosition.z),
				(float)(maxX - cameraPosition.x), y1, (float)(clippedMaxZ - cameraPosition.z),
				viewportWidth,
				viewportHeight
			);
			default -> {
			}
		}
	}

	public static float diagnosticWorldBorderScroll(float fallback) {
		if (!DIAGNOSTIC_BORDER_SCROLL.isBlank()) {
			try {
				return Float.parseFloat(DIAGNOSTIC_BORDER_SCROLL);
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException("Rust GAL world-border diagnostic scroll phase must be a float: " + DIAGNOSTIC_BORDER_SCROLL, exception);
			}
		}
		return fallback;
	}

	private record DiagnosticBorderBounds(double minX, double maxX, double minZ, double maxZ, double alpha, int tint, boolean forceAllSides) {
	}

	public static void enqueueBlockOutline(Minecraft minecraft, GameRenderer gameRenderer, Camera camera) {
		if (!shouldUseRustWholeFrameOutline()) {
			return;
		}
		if (enqueueDiagnosticBlockOutline(camera)) {
			return;
		}
		if (minecraft.level == null || minecraft.player == null || !gameRenderer.shouldRenderBlockOutline()) {
			return;
		}
		if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult) || blockHitResult.getType() == HitResult.Type.MISS) {
			return;
		}
		BlockPos blockPos = blockHitResult.getBlockPos();
		BlockState blockState = minecraft.level.getBlockState(blockPos);
		if (blockState.isAir() || !minecraft.level.getWorldBorder().isWithinBounds(blockPos)) {
			return;
		}
		if (!mayRenderForPlayer(minecraft, blockPos, blockState)) {
			return;
		}
		CollisionContext collisionContext = CollisionContext.of(camera.getEntity());
		VoxelShape shape = blockState.getShape(minecraft.level, blockPos, collisionContext);
		if (shape.isEmpty()) {
			return;
		}
		boolean highContrast = minecraft.options.highContrastBlockOutline().get();
		Vec3 cameraPos = camera.getPosition();
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (highContrast) {
					appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_HIGH_CONTRAST, DEPTH_POLICY_TEST_NO_WRITE, -16777216, 7.0F);
					appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_HIGH_CONTRAST, DEPTH_POLICY_TEST_WRITE, -11010079, defaultOutlineLineWidth(viewportWidth));
				} else {
					appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_NORMAL, DEPTH_POLICY_TEST_WRITE, 0x66000000, defaultOutlineLineWidth(viewportWidth));
				}
			}
		}

	public static boolean renderOpenGlBlockOutline(
		Minecraft minecraft,
		BlockOutlineRenderState blockOutlineRenderState,
		Vec3 cameraPos
	) {
		if (!shouldUseRustOpenGlOutline()) {
			return false;
		}
		if (minecraft == null || blockOutlineRenderState == null || blockOutlineRenderState.shape().isEmpty()) {
			return false;
		}
		PrimitiveFrame frame;
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				throw new IllegalStateException("Rust OpenGL block outline requires a seeded world primitive frame");
			}
			PENDING_SEGMENTS.clear();
			int depthPolicy = diagnosticDepthPolicy();
			if (blockOutlineRenderState.highContrast()) {
				appendShapeEdges(
					blockOutlineRenderState.shape(),
					blockOutlineRenderState.pos(),
					cameraPos,
					viewportWidth,
					viewportHeight,
						STYLE_HIGH_CONTRAST,
						DEPTH_POLICY_TEST_NO_WRITE,
						-16777216,
						7.0F
					);
					appendShapeEdges(
						blockOutlineRenderState.shape(),
					blockOutlineRenderState.pos(),
					cameraPos,
					viewportWidth,
					viewportHeight,
						STYLE_HIGH_CONTRAST,
						depthPolicy,
						-11010079,
						defaultOutlineLineWidth(viewportWidth)
					);
				} else {
					appendShapeEdges(
					blockOutlineRenderState.shape(),
					blockOutlineRenderState.pos(),
					cameraPos,
					viewportWidth,
					viewportHeight,
						STYLE_NORMAL,
						depthPolicy,
						0x66000000,
						defaultOutlineLineWidth(viewportWidth)
					);
				}
			frame = new PrimitiveFrame(
				viewportWidth,
				viewportHeight,
				PENDING_VIEW.clone(),
				PENDING_PROJECTION.clone(),
				VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback(),
				List.copyOf(PENDING_SEGMENTS),
				List.of(),
				List.of(),
				List.of(),
				List.of()
			);
			logFirstProjectedLineForDiagnostics(frame);
			PENDING_SEGMENTS.clear();
		}
		return RustGalFrameCoordinator.executeWorldPrimitiveFrame(minecraft, frame, "minecraft.world.block-outline");
	}

	private static void logFirstProjectedLineForDiagnostics(PrimitiveFrame frame) {
		if (!BLOCK_OUTLINE_DIAGNOSTICS || blockOutlineProjectionDiagnosticLogs >= 16 || frame.segments().isEmpty()) {
			return;
		}
		VulkanicGalBridge.WorldLineSegmentRecord segment = frame.segments().get(0);
		Matrix4f view = new Matrix4f().set(frame.viewMatrix());
		Matrix4f projection = new Matrix4f().set(frame.projectionMatrix());
		ProjectedEndpoint start = projectEndpoint(segment.startX(), segment.startY(), segment.startZ(), view, projection, frame.viewportWidth(), frame.viewportHeight());
		ProjectedEndpoint end = projectEndpoint(segment.endX(), segment.endY(), segment.endZ(), view, projection, frame.viewportWidth(), frame.viewportHeight());
		ProjectedEndpoint columnStart = projectEndpointColumnVector(segment.startX(), segment.startY(), segment.startZ(), view, projection, frame.viewportWidth(), frame.viewportHeight());
		ProjectedEndpoint columnEnd = projectEndpointColumnVector(segment.endX(), segment.endY(), segment.endZ(), view, projection, frame.viewportWidth(), frame.viewportHeight());
		LOGGER.info(
			"[MattMC graphics-audit] rust-gal block-outline projected-first-segment viewport={}x{} color=0x{} depthPolicy={} rowStart={} rowEnd={} columnStart={} columnEnd={}",
			frame.viewportWidth(),
			frame.viewportHeight(),
			Integer.toUnsignedString(segment.colorArgb(), 16),
			segment.depthPolicy(),
			start,
			end,
			columnStart,
			columnEnd
		);
		blockOutlineProjectionDiagnosticLogs++;
	}

	private static ProjectedEndpoint projectEndpoint(
		float x,
		float y,
		float z,
		Matrix4f view,
		Matrix4f projection,
		int viewportWidth,
		int viewportHeight
	) {
		Vector4f clip = new Vector4f(x, y, z, 1.0F).mul(view).mul(projection);
		if (!Float.isFinite(clip.x()) || !Float.isFinite(clip.y()) || !Float.isFinite(clip.z()) || !Float.isFinite(clip.w()) || Math.abs(clip.w()) < 1.0E-5F) {
			return new ProjectedEndpoint(clip.x(), clip.y(), clip.z(), clip.w(), Float.NaN, Float.NaN, false);
		}
		float ndcX = clip.x() / clip.w();
		float ndcY = clip.y() / clip.w();
		float screenX = (ndcX * 0.5F + 0.5F) * viewportWidth;
		float screenY = (ndcY * 0.5F + 0.5F) * viewportHeight;
		return new ProjectedEndpoint(clip.x(), clip.y(), clip.z(), clip.w(), screenX, screenY, Float.isFinite(screenX) && Float.isFinite(screenY));
	}

	private static ProjectedEndpoint projectEndpointColumnVector(
		float x,
		float y,
		float z,
		Matrix4f view,
		Matrix4f projection,
		int viewportWidth,
		int viewportHeight
	) {
		Vector4f clip = new Vector4f(x, y, z, 1.0F);
		view.transform(clip);
		projection.transform(clip);
		if (!Float.isFinite(clip.x()) || !Float.isFinite(clip.y()) || !Float.isFinite(clip.z()) || !Float.isFinite(clip.w()) || Math.abs(clip.w()) < 1.0E-5F) {
			return new ProjectedEndpoint(clip.x(), clip.y(), clip.z(), clip.w(), Float.NaN, Float.NaN, false);
		}
		float ndcX = clip.x() / clip.w();
		float ndcY = clip.y() / clip.w();
		float screenX = (ndcX * 0.5F + 0.5F) * viewportWidth;
		float screenY = (ndcY * 0.5F + 0.5F) * viewportHeight;
		return new ProjectedEndpoint(clip.x(), clip.y(), clip.z(), clip.w(), screenX, screenY, Float.isFinite(screenX) && Float.isFinite(screenY));
	}

	private static ProjectedBounds projectBounds(float[] vertices, int viewportWidth, int viewportHeight) {
		if (vertices.length < 12 || viewportWidth <= 0 || viewportHeight <= 0) {
			return ProjectedBounds.invalid();
		}
		Matrix4f view = new Matrix4f().set(PENDING_VIEW);
		Matrix4f projection = new Matrix4f().set(PENDING_PROJECTION);
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < 4; i++) {
			ProjectedEndpoint projected = projectEndpointColumnVector(
				vertices[i * 3],
				vertices[i * 3 + 1],
				vertices[i * 3 + 2],
				view,
				projection,
				viewportWidth,
				viewportHeight
			);
			if (!projected.valid()) {
				return ProjectedBounds.invalid();
			}
			minX = Math.min(minX, projected.screenX());
			minY = Math.min(minY, projected.screenY());
			maxX = Math.max(maxX, projected.screenX());
			maxY = Math.max(maxY, projected.screenY());
		}
		float left = Mth.clamp(Math.min(minX, maxX), 0.0F, viewportWidth);
		float right = Mth.clamp(Math.max(minX, maxX), 0.0F, viewportWidth);
		float top = Mth.clamp(Math.min(minY, maxY), 0.0F, viewportHeight);
		float bottom = Mth.clamp(Math.max(minY, maxY), 0.0F, viewportHeight);
		return new ProjectedBounds(left, top, right, bottom, right > left && bottom > top);
	}

	private static ProjectedBounds projectMeshBounds(
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices,
		float[] transform,
		int viewportWidth,
		int viewportHeight
	) {
		if (vertices.isEmpty() || transform.length < 16 || viewportWidth <= 0 || viewportHeight <= 0) {
			return ProjectedBounds.invalid();
		}
		Matrix4f model = new Matrix4f().set(transform);
		Matrix4f view = new Matrix4f().set(PENDING_VIEW);
		Matrix4f projection = new Matrix4f().set(PENDING_PROJECTION);
		float minX = Float.POSITIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		Vector3f transformed = new Vector3f();
		boolean any = false;
		for (VulkanicGalBridge.WorldMeshVertexRecord vertex : vertices) {
			model.transformPosition(vertex.x(), vertex.y(), vertex.z(), transformed);
			ProjectedEndpoint projected = projectEndpointColumnVector(
				transformed.x(),
				transformed.y(),
				transformed.z(),
				view,
				projection,
				viewportWidth,
				viewportHeight
			);
			if (!projected.valid()) {
				continue;
			}
			minX = Math.min(minX, projected.screenX());
			minY = Math.min(minY, projected.screenY());
			maxX = Math.max(maxX, projected.screenX());
			maxY = Math.max(maxY, projected.screenY());
			any = true;
		}
		if (!any) {
			return ProjectedBounds.invalid();
		}
		float left = Mth.clamp(Math.min(minX, maxX), 0.0F, viewportWidth);
		float right = Mth.clamp(Math.max(minX, maxX), 0.0F, viewportWidth);
		float top = Mth.clamp(Math.min(minY, maxY), 0.0F, viewportHeight);
		float bottom = Mth.clamp(Math.max(minY, maxY), 0.0F, viewportHeight);
		return new ProjectedBounds(left, top, right, bottom, right > left && bottom > top);
	}

	private static String meshTextureIds(VulkanicGalBridge.WorldMeshAssetRecord asset) {
		StringBuilder builder = new StringBuilder();
		for (VulkanicGalBridge.WorldMeshSectionRecord section : asset.sections()) {
			if (builder.indexOf(Integer.toUnsignedString(section.textureId())) >= 0) {
				continue;
			}
			if (!builder.isEmpty()) {
				builder.append(',');
			}
			builder.append(Integer.toUnsignedString(section.textureId()));
		}
		return builder.toString();
	}

	private record ProjectedEndpoint(float clipX, float clipY, float clipZ, float clipW, float screenX, float screenY, boolean valid) {
	}

	private record ProjectedBounds(float left, float top, float right, float bottom, boolean valid) {
		private static ProjectedBounds invalid() {
			return new ProjectedBounds(Float.NaN, Float.NaN, Float.NaN, Float.NaN, false);
		}
	}

	public record BlockMarkerDiagnostic(
		long frameIndex,
		String route,
		int textureId,
		float centerX,
		float centerY,
		float centerZ,
		float quadSize,
		int colorArgb,
		int viewportWidth,
		int viewportHeight,
		boolean projected,
		float screenLeft,
		float screenTop,
		float screenRight,
		float screenBottom
	) {
	}

	public record TerrainParticleDiagnostic(
		long frameIndex,
		String route,
		int textureId,
		String spriteId,
		float centerX,
		float centerY,
		float centerZ,
		float quadSize,
		int colorArgb,
		int packedLight,
		int materialMode,
		float localU0,
		float localU1,
		float localV0,
		float localV1,
		int viewportWidth,
		int viewportHeight,
		boolean projected,
		float screenLeft,
		float screenTop,
		float screenRight,
		float screenBottom
	) {
	}

	public record BlockDisplayDiagnostic(
		long frameIndex,
		String route,
		String blockId,
		long meshKey,
		long meshGeneration,
		int vertexLayoutVersion,
		int indexType,
		int vertexCount,
		int indexBytes,
		int sectionCount,
		String textureIds,
		int materialMode,
		int viewportWidth,
		int viewportHeight,
		boolean projected,
		float screenLeft,
		float screenTop,
		float screenRight,
		float screenBottom
	) {
	}

	public record FallingBlockDiagnostic(
		long frameIndex,
		String route,
		String blockId,
		long meshKey,
		long meshGeneration,
		int vertexLayoutVersion,
		int indexType,
		int vertexCount,
		int indexBytes,
		int sectionCount,
		String textureIds,
		int materialMode,
		int viewportWidth,
		int viewportHeight,
		boolean projected,
		float screenLeft,
		float screenTop,
		float screenRight,
		float screenBottom
	) {
	}

	public record FallingBlockRouteDecision(
		long frameIndex,
		String route,
		String blockId,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
	}

	private static boolean enqueueDiagnosticBlockOutline(Camera camera) {
		if (DIAGNOSTIC_SCENARIO.isBlank()) {
			return false;
		}
		if ("no-target".equalsIgnoreCase(DIAGNOSTIC_SCENARIO)) {
			return true;
		}
		VoxelShape shape = diagnosticShape();
		if (shape.isEmpty()) {
			return true;
		}
		int style = diagnosticStyle();
		int depthPolicy = diagnosticDepthPolicy();
		Vec3 cameraPos = camera.getPosition();
		BlockPos blockPos = diagnosticBlockPos(camera);
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				return true;
			}
				if (style == STYLE_HIGH_CONTRAST) {
					appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_HIGH_CONTRAST, depthPolicy, -16777216, 7.0F);
					appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_HIGH_CONTRAST, depthPolicy, -11010079, defaultOutlineLineWidth(viewportWidth));
				} else {
					appendShapeEdges(shape, blockPos, cameraPos, viewportWidth, viewportHeight, STYLE_NORMAL, depthPolicy, 0x66000000, defaultOutlineLineWidth(viewportWidth));
				}
			if (DIAGNOSTIC_DEPTH_PROBE) {
				appendDepthProbe(camera, viewportWidth, viewportHeight, depthPolicy);
			}
		}
		return true;
	}

	private static VoxelShape diagnosticShape() {
		return diagnosticShape(DIAGNOSTIC_SCENARIO, "Rust GAL world-outline diagnostic scenario");
	}

	private static VoxelShape diagnosticShape(String scenario, String label) {
		return switch (scenario.toLowerCase(java.util.Locale.ROOT)) {
			case "full-cube", "cube" -> Shapes.block();
			case "partial-shape", "partial" -> Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
			case "disconnected-shape", "disconnected" -> Shapes.or(
				Shapes.box(0.0, 0.0, 0.0, 0.375, 0.375, 0.375),
				Shapes.box(0.625, 0.625, 0.625, 1.0, 1.0, 1.0)
			);
			default -> throw new IllegalArgumentException("unknown " + label + ": " + scenario);
		};
	}

	private static int diagnosticCrackStage() {
		try {
			int stage = Integer.parseInt(DIAGNOSTIC_CRACK_STAGE);
			if (stage < 0 || stage >= 10) {
				throw new IllegalArgumentException("Rust GAL world-crack diagnostic stage must be in 0..9: " + DIAGNOSTIC_CRACK_STAGE);
			}
			return stage;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Rust GAL world-crack diagnostic stage must be an integer: " + DIAGNOSTIC_CRACK_STAGE, exception);
		}
	}

	private static int diagnosticStyle() {
		return "high-contrast".equalsIgnoreCase(DIAGNOSTIC_STYLE) ? STYLE_HIGH_CONTRAST : STYLE_NORMAL;
	}

	private static int diagnosticDepthPolicy() {
		if ("disabled".equalsIgnoreCase(DIAGNOSTIC_DEPTH_POLICY)) {
			return DEPTH_POLICY_DISABLED;
		}
		if ("test-no-write".equalsIgnoreCase(DIAGNOSTIC_DEPTH_POLICY) || "no-write".equalsIgnoreCase(DIAGNOSTIC_DEPTH_POLICY)) {
			return DEPTH_POLICY_TEST_NO_WRITE;
		}
		return DEPTH_POLICY_TEST_WRITE;
	}

	private static BlockPos diagnosticBlockPos(Camera camera) {
		Vector3f look = camera.getLookVector();
		Vec3 cameraPos = camera.getPosition();
		double distance = 4.0;
		return BlockPos.containing(
			cameraPos.x() + look.x() * distance - 0.5,
			cameraPos.y() + look.y() * distance - 0.5,
			cameraPos.z() + look.z() * distance - 0.5
		);
	}

	private static void appendDepthProbe(Camera camera, int viewportWidth, int viewportHeight, int depthPolicy) {
		Vector3f look = camera.getLookVector();
		Vector3f right = new Vector3f(look).cross(new Vector3f(0.0F, 1.0F, 0.0F));
		if (right.lengthSquared() < 1.0E-4F) {
			right.set(1.0F, 0.0F, 0.0F);
		} else {
			right.normalize();
		}
		Vector3f up = new Vector3f(right).cross(look).normalize();
		Vec3 cameraPos = camera.getPosition();
		appendCameraRelativeSegment(cameraPos, look, right, up, 2.0F, -0.35F, -0.20F, 0.35F, -0.20F, viewportWidth, viewportHeight, STYLE_NORMAL, depthPolicy, 0xff00ff00);
		appendCameraRelativeSegment(cameraPos, look, right, up, 3.0F, -0.525F, -0.30F, 0.525F, -0.30F, viewportWidth, viewportHeight, STYLE_NORMAL, depthPolicy, 0xffff00ff);
	}

	private static void appendCameraRelativeSegment(
		Vec3 cameraPos,
		Vector3f look,
		Vector3f right,
		Vector3f up,
		float depth,
		float startRight,
		float startUp,
		float endRight,
		float endUp,
		int viewportWidth,
		int viewportHeight,
		int style,
		int depthPolicy,
		int color
	) {
		Vector3f start = new Vector3f(look).mul(depth).fma(startRight, right).fma(startUp, up);
		Vector3f end = new Vector3f(look).mul(depth).fma(endRight, right).fma(endUp, up);
		PENDING_SEGMENTS.add(new VulkanicGalBridge.WorldLineSegmentRecord(
			STRATUM_WORLD_BLOCK_OUTLINE,
			style,
			depthPolicy,
			color,
			1.0F,
			start.x(),
			start.y(),
			start.z(),
			end.x(),
			end.y(),
			end.z(),
			viewportWidth,
			viewportHeight
		));
	}

	private static void appendShapeEdges(
		VoxelShape shape,
		BlockPos blockPos,
		Vec3 cameraPos,
		int viewportWidth,
		int viewportHeight,
		int style,
		int depthPolicy,
		int color,
		float lineWidth
	) {
			shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> PENDING_SEGMENTS.add(
				new VulkanicGalBridge.WorldLineSegmentRecord(
					STRATUM_WORLD_BLOCK_OUTLINE,
					style,
					depthPolicy,
					color,
					lineWidth,
					(float)(blockPos.getX() + x0 - cameraPos.x()),
					(float)(blockPos.getY() + y0 - cameraPos.y()),
					(float)(blockPos.getZ() + z0 - cameraPos.z()),
				(float)(blockPos.getX() + x1 - cameraPos.x()),
				(float)(blockPos.getY() + y1 - cameraPos.y()),
				(float)(blockPos.getZ() + z1 - cameraPos.z()),
				viewportWidth,
				viewportHeight
			)
		));
	}

	private static void appendCrackBoxFaces(
		BlockPos blockPos,
		Vec3 cameraPos,
		int stage,
		int viewportWidth,
		int viewportHeight,
		double minX,
		double minY,
		double minZ,
		double maxX,
		double maxY,
		double maxZ
	) {
		float x0 = (float)(blockPos.getX() + minX - cameraPos.x());
		float y0 = (float)(blockPos.getY() + minY - cameraPos.y());
		float z0 = (float)(blockPos.getZ() + minZ - cameraPos.z());
		float x1 = (float)(blockPos.getX() + maxX - cameraPos.x());
		float y1 = (float)(blockPos.getY() + maxY - cameraPos.y());
		float z1 = (float)(blockPos.getZ() + maxZ - cameraPos.z());
		appendCrackQuad(stage, viewportWidth, viewportHeight, x0, y1, z0 - CRACK_FACE_OFFSET, x1, y1, z0 - CRACK_FACE_OFFSET, x1, y0, z0 - CRACK_FACE_OFFSET, x0, y0, z0 - CRACK_FACE_OFFSET);
		appendCrackQuad(stage, viewportWidth, viewportHeight, x1, y1, z1 + CRACK_FACE_OFFSET, x0, y1, z1 + CRACK_FACE_OFFSET, x0, y0, z1 + CRACK_FACE_OFFSET, x1, y0, z1 + CRACK_FACE_OFFSET);
		appendCrackQuad(stage, viewportWidth, viewportHeight, x1 + CRACK_FACE_OFFSET, y1, z0, x1 + CRACK_FACE_OFFSET, y1, z1, x1 + CRACK_FACE_OFFSET, y0, z1, x1 + CRACK_FACE_OFFSET, y0, z0);
		appendCrackQuad(stage, viewportWidth, viewportHeight, x0 - CRACK_FACE_OFFSET, y1, z1, x0 - CRACK_FACE_OFFSET, y1, z0, x0 - CRACK_FACE_OFFSET, y0, z0, x0 - CRACK_FACE_OFFSET, y0, z1);
		appendCrackQuad(stage, viewportWidth, viewportHeight, x0, y1 + CRACK_FACE_OFFSET, z1, x1, y1 + CRACK_FACE_OFFSET, z1, x1, y1 + CRACK_FACE_OFFSET, z0, x0, y1 + CRACK_FACE_OFFSET, z0);
		appendCrackQuad(stage, viewportWidth, viewportHeight, x0, y0 - CRACK_FACE_OFFSET, z0, x1, y0 - CRACK_FACE_OFFSET, z0, x1, y0 - CRACK_FACE_OFFSET, z1, x0, y0 - CRACK_FACE_OFFSET, z1);
	}

	private static void appendCrackShape(
		VoxelShape shape,
		BlockPos blockPos,
		Vec3 cameraPos,
		int stage,
		int viewportWidth,
		int viewportHeight
	) {
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> appendCrackBoxFaces(
			blockPos,
			cameraPos,
			stage,
			viewportWidth,
			viewportHeight,
			minX,
			minY,
			minZ,
			maxX,
			maxY,
			maxZ
		));
	}

	private static void appendCrackQuad(
		int stage,
		int viewportWidth,
		int viewportHeight,
		float p0x,
		float p0y,
		float p0z,
		float p1x,
		float p1y,
		float p1z,
		float p2x,
		float p2y,
		float p2z,
		float p3x,
		float p3y,
		float p3z
	) {
		PENDING_CRACK_QUADS.add(new VulkanicGalBridge.WorldCrackQuadRecord(
			STRATUM_WORLD_BLOCK_BREAKING_CRACK,
			stage,
			DEPTH_POLICY_TEST_WRITE,
			CRACK_BLEND_MULTIPLY,
			CULL_NONE,
			0xFFFFFFFF,
			new float[] {
				p0x, p0y, p0z,
				p1x, p1y, p1z,
				p2x, p2y, p2z,
				p3x, p3y, p3z
			},
			viewportWidth,
				viewportHeight
			));
	}

	private static float defaultOutlineLineWidth(int viewportWidth) {
		return Math.max(2.5F, viewportWidth / 1920.0F * 2.5F);
	}

	private static void appendWorldBorderQuad(
		int color,
		float borderSize,
		float distanceToBorder,
		float scroll,
		float uvU,
		float uvV,
		float uvWidth,
		float uvHeight,
		float p0x,
		float p0y,
		float p0z,
		float p1x,
		float p1y,
		float p1z,
		float p2x,
		float p2y,
		float p2z,
		float p3x,
		float p3y,
		float p3z,
		int viewportWidth,
		int viewportHeight
	) {
		PENDING_BORDER_QUADS.add(new VulkanicGalBridge.WorldBorderQuadRecord(
			STRATUM_WORLD_BORDER,
			BORDER_TEXTURE_FORCEFIELD,
			DEPTH_POLICY_TEST_WRITE,
			BORDER_BLEND_OVERLAY,
			CULL_NONE,
			color,
			borderSize,
			Math.max(distanceToBorder, 0.0F),
			scroll,
			scroll,
			uvU,
			uvV,
			uvWidth,
			uvHeight,
			new float[] {
				p0x, p0y, p0z,
				p1x, p1y, p1z,
				p2x, p2y, p2z,
				p3x, p3y, p3z
			},
			viewportWidth,
			viewportHeight
			));
	}

	private static VulkanicGalBridge.WorldBackgroundRecord diagnosticBackground(int viewportWidth, int viewportHeight) {
		return switch (DIAGNOSTIC_BACKGROUND_SCENARIO) {
			case "hidden", "invalid", "diagnostic", "blue" -> VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
			case "overworld-day" -> backgroundRecord(BACKGROUND_SKY_OVERWORLD, 0xFF78A7FF, viewportWidth, viewportHeight);
			case "overworld-night" -> backgroundRecord(BACKGROUND_SKY_OVERWORLD, 0xFF060915, viewportWidth, viewportHeight);
			case "nether" -> backgroundRecord(BACKGROUND_SKY_NETHER, 0xFF330808, viewportWidth, viewportHeight);
			case "end" -> backgroundRecord(BACKGROUND_SKY_END, 0xFF0A0612, viewportWidth, viewportHeight);
			case "custom" -> backgroundRecord(BACKGROUND_SKY_CUSTOM, 0xFF24402A, viewportWidth, viewportHeight);
			default -> VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
		};
	}

	private static boolean isExplicitDiagnosticBackgroundFallback() {
		return switch (DIAGNOSTIC_BACKGROUND_SCENARIO) {
			case "hidden", "invalid", "diagnostic", "blue" -> true;
			default -> false;
		};
	}

	private static VulkanicGalBridge.WorldBackgroundRecord backgroundRecord(int skyType, int colorArgb, int viewportWidth, int viewportHeight) {
		return new VulkanicGalBridge.WorldBackgroundRecord(
			true,
			skyType,
			BACKGROUND_LOAD_CLEAR,
			BACKGROUND_STORE_STORE,
			colorArgb,
			viewportWidth,
			viewportHeight
		);
	}

	private static int backgroundSkyType(ClientLevel level) {
		if (level.dimension() == Level.OVERWORLD) {
			return BACKGROUND_SKY_OVERWORLD;
		}
		if (level.dimension() == Level.NETHER) {
			return BACKGROUND_SKY_NETHER;
		}
		if (level.dimension() == Level.END) {
			return BACKGROUND_SKY_END;
		}
		return BACKGROUND_SKY_CUSTOM;
	}

	private static boolean isFinite(float[] values) {
		for (float value : values) {
			if (!Float.isFinite(value)) {
				return false;
			}
		}
		return true;
	}

	private static String sha256Hex(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 digest is unavailable", error);
		}
	}

	private static String metricValue(String value) {
		return value == null || value.isBlank() ? "unset" : value.replaceAll("\\s+", "_");
	}

	private static void auditMessage(String message) {
		if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			System.out.println("[MattMC graphics audit] " + message);
		}
	}

	public static PrimitiveFrame consumeFrame() {
		synchronized (LOCK) {
			PrimitiveFrame frame = new PrimitiveFrame(
				pendingViewportWidth,
				pendingViewportHeight,
				PENDING_VIEW.clone(),
				PENDING_PROJECTION.clone(),
				pendingBackground,
				List.copyOf(PENDING_SEGMENTS),
				List.copyOf(PENDING_CRACK_QUADS),
				List.copyOf(PENDING_BORDER_QUADS),
				List.copyOf(PENDING_MATERIAL_QUADS),
				List.copyOf(PENDING_MESH_INSTANCES)
			);
			PENDING_SEGMENTS.clear();
			PENDING_CRACK_QUADS.clear();
			PENDING_BORDER_QUADS.clear();
			PENDING_MATERIAL_QUADS.clear();
			PENDING_MESH_INSTANCES.clear();
			pendingBackground = VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
			return frame;
		}
	}

	private static boolean mayRenderForPlayer(Minecraft minecraft, BlockPos blockPos, BlockState blockState) {
		if (minecraft.player.getAbilities().mayBuild) {
			return true;
		}
		ItemStack itemStack = minecraft.player.getMainHandItem();
		if (minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
			return blockState.getMenuProvider(minecraft.level, blockPos) != null;
		}
		BlockInWorld blockInWorld = new BlockInWorld(minecraft.level, blockPos, false);
		return !itemStack.isEmpty()
			&& (itemStack.canBreakBlockInAdventureMode(blockInWorld) || itemStack.canPlaceOnBlockInAdventureMode(blockInWorld));
	}

	public record PrimitiveFrame(
		int viewportWidth,
		int viewportHeight,
		float[] viewMatrix,
		float[] projectionMatrix,
		VulkanicGalBridge.WorldBackgroundRecord background,
		List<VulkanicGalBridge.WorldLineSegmentRecord> segments,
		List<VulkanicGalBridge.WorldCrackQuadRecord> crackQuads,
		List<VulkanicGalBridge.WorldBorderQuadRecord> borderQuads,
		List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads,
		List<VulkanicGalBridge.WorldMeshInstanceRecord> meshInstances
	) {
	}

	public record WorldBorderAssetMetrics(
		long generation,
		long uploadedGeneration,
		long payloadCount,
		long payloadBytes,
		long failures,
		String sourcePack,
		String sha256,
		boolean fallback
	) {
	}

	public record WorldCrackAssetMetrics(
		long generation,
		long uploadedGeneration,
		long payloadCount,
		long payloadBytes,
		long failures,
		String sourcePack,
		String sha256,
		boolean fallback
	) {
	}

	public record WorldMaterialAssetMetrics(
		long generation,
		long uploadedGeneration,
		long payloadCount,
		long payloadBytes,
		long failures,
		String sourcePack,
		String sha256,
		boolean fallback
	) {
	}

	private record WorldBorderAssetResolution(byte[] payload, String sourcePack, String sha256, boolean fallback, boolean preserveLastValid) {
		private static WorldBorderAssetResolution fallback(String sourcePack) {
			return new WorldBorderAssetResolution(new byte[0], sourcePack, "fallback", true, false);
		}

		private static WorldBorderAssetResolution preserve(String sourcePack) {
			return new WorldBorderAssetResolution(new byte[0], sourcePack, "preserve-last-valid", true, true);
		}
	}

	private record WorldCrackAssetResolution(
		List<VulkanicGalBridge.WorldCrackAssetRecord> assets,
		long payloadBytes,
		String sourcePack,
		String sha256,
		boolean fallback,
		boolean preserveLastValid
	) {
		private static WorldCrackAssetResolution fallback(String sourcePack) {
			return new WorldCrackAssetResolution(List.of(), 0L, sourcePack, "fallback", true, false);
		}

		private static WorldCrackAssetResolution preserve(String sourcePack) {
			return new WorldCrackAssetResolution(List.of(), 0L, sourcePack, "preserve-last-valid", true, true);
		}
	}

	private record WorldMaterialAssetResolution(
		List<VulkanicGalBridge.WorldMaterialAssetRecord> assets,
		long payloadBytes,
		String sourcePack,
		String sha256,
		boolean fallback,
		boolean preserveLastValid
	) {
		private static WorldMaterialAssetResolution fallback(String sourcePack) {
			return new WorldMaterialAssetResolution(List.of(), 0L, sourcePack, "fallback", true, false);
		}

		private static WorldMaterialAssetResolution preserve(String sourcePack) {
			return new WorldMaterialAssetResolution(List.of(), 0L, sourcePack, "preserve-last-valid", true, true);
		}
	}

	private record WorldMaterialAssetCandidate(int textureId, ResourceLocation location) {
	}
}
