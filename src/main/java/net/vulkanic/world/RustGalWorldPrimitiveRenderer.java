package net.vulkanic.world;

import net.minecraft.Util;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.pipeline.BlendFunction;
import net.iris.api.v0.item.IrisItemLightProvider;
import com.seibel.distanthorizons.api.DhApi;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.dev.DeterministicCameraCapture;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.client.renderer.entity.state.ExperienceOrbRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.PigRenderState;
import net.minecraft.client.renderer.entity.state.RabbitRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.model.ChestModel;
import net.minecraft.client.model.BellModel;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.LlamaSpitModel;
import net.minecraft.client.model.EvokerFangsModel;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.client.renderer.state.WorldBorderRenderState;
import net.minecraft.client.renderer.state.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.WeatherRenderState;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import net.sodium.client.model.quad.BakedQuadView;
import net.sodium.client.util.FogParameters;

public final class RustGalWorldPrimitiveRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ThreadLocal<Integer> ITEM_ENTITY_SUBMISSION_DEPTH = ThreadLocal.withInitial(() -> 0);
	public static final int STRATUM_WORLD_BORDER = 80;
	public static final int STRATUM_WORLD_MATERIAL = 70;
	public static final int STRATUM_WORLD_MOVING_MESH = 68;
	/** Generic copied entity-model mesh; source-plan admission is independent. */
	public static final int STRATUM_WORLD_ENTITY_MESH = 67;
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
	public static final int MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS = 0x54A17A1A;
	/**
	 * Stable semantic identity for the copied resource-pack specular atlas. This
	 * is an asset identity only: it is never an OpenGL texture ID, Iris PBR
	 * object, or backend handle.
	 */
	public static final int MATERIAL_TEXTURE_TERRAIN_BLOCK_SPECULAR_ATLAS = 0x54A17A1B;
	/** Stable semantic identity for the copied resource-pack normal atlas. */
	public static final int MATERIAL_TEXTURE_TERRAIN_BLOCK_NORMAL_ATLAS = 0x54A17A1C;
	public static final int MATERIAL_TEXTURE_WATER_STILL = 0x5A71A501;
	public static final int MATERIAL_TEXTURE_WATER_FLOW = 0x5A71A502;
	public static final int MATERIAL_TEXTURE_WATER_OVERLAY = 0x5A71A503;
	public static final int MATERIAL_TEXTURE_WEATHER_RAIN = 0x3C497A11;
	public static final int MATERIAL_TEXTURE_WEATHER_SNOW = 0x74B52E96;
	/**
	 * Stable semantic identities for the copied vanilla celestial textures.
	 * These are resource-location assets, never Java texture-manager entries or
	 * backend texture handles. The selected source sky route remains unavailable
	 * until it owns a complete `gbuffers_skytextured` writer.
	 */
	public static final int MATERIAL_TEXTURE_SKY_SUN = 0x534B5901;
	public static final int MATERIAL_TEXTURE_SKY_MOON_PHASES = 0x534B5902;
	/** Stable semantic texture identity for the copied vanilla experience-orb sheet. */
	public static final int MATERIAL_TEXTURE_EXPERIENCE_ORB = 0x4F524233;
	/** Stable semantic texture identity for the copied vanilla beacon-beam sheet. */
	public static final int MATERIAL_TEXTURE_BEACON_BEAM = 0x4245414D;
	/** Stable semantic identity for vanilla entity-shadow coverage. */
	public static final int MATERIAL_TEXTURE_ENTITY_SHADOW = 0x53484457;
	/** Rust-owned generated white texture used with copied cloud-face colors. */
	public static final int MATERIAL_TEXTURE_GENERATED_WHITE = 0x4E2A16C1;
	public static final int MATERIAL_ID_OPAQUE_TEXTURED = 0x6A2FD335;
	public static final int MATERIAL_ID_CUTOUT_TEXTURED = 0x129B1B90;
	public static final int MATERIAL_ID_TRANSLUCENT_TEXTURED = 0x4D21A7C3;
	public static final int MATERIAL_ID_WATER_TRANSLUCENT = 0x39E0A7E4;
	public static final int MATERIAL_ID_BLOCK_MARKER_CUTOUT = 0x224A8659;
	public static final int MATERIAL_MODE_OPAQUE = 1;
	public static final int MATERIAL_MODE_CUTOUT = 2;
	public static final int MATERIAL_MODE_TRANSLUCENT = 3;
	/** Generic source-pack material family; never an Iris program ID. */
	public static final int MATERIAL_SOURCE_UNSPECIFIED = 0;
	public static final int MATERIAL_SOURCE_TEXTURED = 1;
	/** Source-derived weather pass family; semantic only, never an Iris program ID. */
	public static final int MATERIAL_SOURCE_WEATHER = 2;
	/** Semantic vanilla cloud-face family; source-plan admission remains explicit. */
	public static final int MATERIAL_SOURCE_CLOUDS = 3;
	/** UVs address the Rust-owned material texture. */
	public static final int MATERIAL_SOURCE_UV_LOCAL_TEXTURE = 0;
	/** UVs retain their semantic coordinates in the Minecraft block atlas. */
	public static final int MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS = 1;
	public static final int MESH_VERTEX_LAYOUT_V2 = 2;
	public static final int MESH_VERTEX_LAYOUT_V3 = 3;
	public static final int MESH_SECTION_ALL = -1;
	public static final int STRATUM_WORLD_TERRAIN = 60;
	private static final boolean DETERMINISTIC_TEMPORAL_PARITY =
		Boolean.getBoolean("mattmc.vulkan.deterministicTemporalParity");
	private static final long DETERMINISTIC_TEMPORAL_WORLD_TIME =
		Long.getLong("mattmc.vulkan.deterministicTemporalParity.worldTime", 6000L);
	private static final int DETERMINISTIC_TEMPORAL_FRAME_COUNTER =
		Integer.getInteger("mattmc.vulkan.deterministicTemporalParity.frameCounter", 0);
	private static final float DETERMINISTIC_TEMPORAL_FRAME_TIME_COUNTER = Float.parseFloat(
		System.getProperty("mattmc.vulkan.deterministicTemporalParity.frameTimeCounter", "0.0")
	);
	private static final float DETERMINISTIC_TEMPORAL_FRAME_TIME = Float.parseFloat(
		System.getProperty("mattmc.vulkan.deterministicTemporalParity.frameTime", "0.016666668")
	);
	private static final ResourceLocation MISSING_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("missingno");
	private static final ResourceLocation SKY_SUN_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
	private static final ResourceLocation SKY_MOON_PHASES_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
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
	// The coarse frame ABI is bounded independently from Rust's per-draw
	// shader payload. The frontend splits a large semantic material frame into
	// compatible draw-sized batches.
	private static final int MAX_RUST_WORLD_MATERIAL_QUADS = 65_536;
	private static final float CLOUD_CELL_WIDTH = 12.0F;
	private static final float CLOUD_CELL_HEIGHT = 4.0F;
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
	private static final ResourceLocation WEATHER_RAIN_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
	private static final ResourceLocation WEATHER_SNOW_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/snow.png");
	private static final ResourceLocation EXPERIENCE_ORB_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/experience_orb.png");
	private static final ResourceLocation BEACON_BEAM_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
	private static final ResourceLocation ENTITY_SHADOW_TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/shadow.png");
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
	// Capture-only provenance stays beside the coarse generic material records.
	// It never crosses the FFI boundary or changes material/GAL policy.
	private static int pendingEntityFlameQuadCount;
	private static final List<WorldTextSemanticCollector.WorldTextQuad> PENDING_TEXT_QUADS = new ArrayList<>();
	private static final Map<Long, WorldTextSemanticCollector.WorldTextImage> WORLD_TEXT_IMAGES = new LinkedHashMap<>();
	private static final Set<Long> DIRTY_WORLD_TEXT_IMAGES = new LinkedHashSet<>();
	private static long worldTextImageGeneration;
	private static long uploadedWorldTextImageGeneration;
	private static long attemptedWorldTextImageGeneration;
	private static WorldTextDiagnostic worldTextDiagnostic = WorldTextDiagnostic.empty();
	private static final List<VulkanicGalBridge.WorldMeshInstanceRecord> PENDING_MESH_INSTANCES = new ArrayList<>();
	private static final List<PendingMeshProducer> PENDING_MESH_PRODUCERS = new ArrayList<>();
	// First-person items have an explicit camera-space projection/depth domain.
	// They never join ordinary entity meshes, even though both reuse the same
	// copied indexed asset family.
	private static final List<VulkanicGalBridge.WorldMeshInstanceRecord> PENDING_FIRST_PERSON_MESH_INSTANCES = new ArrayList<>();
	private static final float[] PENDING_FIRST_PERSON_PROJECTION = new float[16];
	private static final float[] PENDING_FIRST_PERSON_MODEL_VIEW = new float[16];
	private static boolean pendingFirstPersonFrame;
	private static int pendingFirstPersonMainHandInstanceCount;
	// Coverage-only replay happens after the real submit collector. Retain the
	// exact semantic identity for this frame so it cannot mistake an already
	// queued Rust model for unsupported Java work.
	private static final Set<ModelMeshSemanticIdentity> PENDING_MODEL_MESH_SEMANTICS = new LinkedHashSet<>();
	private static final List<MovingMeshExecutionDiagnostic> MOVING_MESH_EXECUTION_DIAGNOSTICS = new ArrayList<>();
	private static final List<EntityFlameSemanticDiagnostic> ENTITY_FLAME_SEMANTIC_DIAGNOSTICS = new ArrayList<>();
	private static final List<EntityFlameExecutionDiagnostic> ENTITY_FLAME_EXECUTION_DIAGNOSTICS = new ArrayList<>();
	private static final List<EntityShadowSemanticDiagnostic> ENTITY_SHADOW_SEMANTIC_DIAGNOSTICS = new ArrayList<>();
	private static final List<EntityShadowExecutionDiagnostic> ENTITY_SHADOW_EXECUTION_DIAGNOSTICS = new ArrayList<>();
	private static final List<EntityLeashSemanticDiagnostic> ENTITY_LEASH_SEMANTIC_DIAGNOSTICS = new ArrayList<>();
	private static final List<EntityLeashExecutionDiagnostic> ENTITY_LEASH_EXECUTION_DIAGNOSTICS = new ArrayList<>();
	private static final Map<Long, VulkanicGalBridge.WorldMeshAssetRecord> WORLD_MESH_ASSETS = new LinkedHashMap<>();
	private static final Set<Long> DIRTY_WORLD_MESH_ASSETS = new LinkedHashSet<>();
	private static final Map<Long, VulkanicGalBridge.WorldMeshSortedIndexRecord> WORLD_MESH_SORTED_INDICES = new LinkedHashMap<>();
	private static final Set<Long> DIRTY_WORLD_MESH_SORTED_INDICES = new LinkedHashSet<>();
	private static final Map<Integer, VulkanicGalBridge.WorldMeshTextureAssetRecord> WORLD_MESH_TEXTURES = new LinkedHashMap<>();
	private static final Set<Integer> DIRTY_WORLD_MESH_TEXTURES = new LinkedHashSet<>();
	private static final Map<Long, Long> UPLOADED_WORLD_MESH_GENERATIONS = new LinkedHashMap<>();
	private static final Set<Integer> UPLOADED_WORLD_MESH_TEXTURES = new LinkedHashSet<>();
	private static long worldMeshAssetGeneration;
	private static long uploadedWorldMeshAssetGeneration;
	private static long attemptedWorldMeshAssetGeneration;
	private static long nextWorldMeshUploadGeneration;
	private static long lastWorldMeshAssetPayloadBytes;
	private static long lastWorldMeshAssetPayloadCount;
	private static final int MAX_WORLD_MESH_ASSETS_PER_UPLOAD = 8;
	private static final long MAX_WORLD_MESH_UPLOAD_BYTES = 1024L * 1024L;
	private static long worldMeshAssetUpdateFailures;
	private static final float[] MATERIAL_VERTEX_SCRATCH = new float[12];
	private static final Vector3f MATERIAL_BILLBOARD_VERTEX_SCRATCH = new Vector3f();
	private static final float[] MATERIAL_BILLBOARD_CORNERS = {1.0F, -1.0F, 1.0F, 1.0F, -1.0F, 1.0F, -1.0F, -1.0F};
	private static final List<BlockMarkerDiagnostic> BLOCK_MARKER_DIAGNOSTICS = new ArrayList<>();
	private static final List<TerrainParticleDiagnostic> TERRAIN_PARTICLE_DIAGNOSTICS = new ArrayList<>();
	private static final List<BlockDisplayDiagnostic> BLOCK_DISPLAY_DIAGNOSTICS = new ArrayList<>();
	private static final List<FallingBlockDiagnostic> FALLING_BLOCK_DIAGNOSTICS = new ArrayList<>();
	private static final List<FallingBlockRouteDecision> FALLING_BLOCK_ROUTE_DECISIONS = new ArrayList<>();
	private static final List<ArrowDiagnostic> ARROW_DIAGNOSTICS = new ArrayList<>();
	private static final List<ArrowRouteDecision> ARROW_ROUTE_DECISIONS = new ArrayList<>();
	private static final List<ItemEntityDiagnostic> ITEM_ENTITY_DIAGNOSTICS = new ArrayList<>();
	private static final List<ItemEntityRouteDecision> ITEM_ENTITY_ROUTE_DECISIONS = new ArrayList<>();
	private static final List<ModelMeshDiagnostic> MODEL_MESH_DIAGNOSTICS = new ArrayList<>();
	private static final List<ModelMeshRouteDecision> MODEL_MESH_ROUTE_DECISIONS = new ArrayList<>();
	private static final List<ModelPartMeshTraversalDiagnostic> MODEL_PART_MESH_TRAVERSAL_DIAGNOSTICS = new ArrayList<>();
	private static final List<MovingBlockDiagnostic> MOVING_BLOCK_DIAGNOSTICS = new ArrayList<>();
	private static final List<MovingBlockRouteDecision> MOVING_BLOCK_ROUTE_DECISIONS = new ArrayList<>();
	private static final List<MovingBlockShellScanDiagnostic> MOVING_BLOCK_SHELL_SCAN_DIAGNOSTICS = new ArrayList<>();
	private static final List<WeatherTraversalDiagnostic> WEATHER_TRAVERSAL_DIAGNOSTICS = new ArrayList<>();
	private static final List<WeatherSemanticDiagnostic> WEATHER_SEMANTIC_DIAGNOSTICS = new ArrayList<>();
	private static final List<WeatherExecutionDiagnostic> WEATHER_EXECUTION_DIAGNOSTICS = new ArrayList<>();
	private static final List<ExperienceOrbDiagnostic> EXPERIENCE_ORB_DIAGNOSTICS = new ArrayList<>();
	private static final List<ExperienceOrbRouteDecision> EXPERIENCE_ORB_ROUTE_DECISIONS = new ArrayList<>();
	private static final List<ExperienceOrbExecutionDiagnostic> EXPERIENCE_ORB_EXECUTION_DIAGNOSTICS = new ArrayList<>();
	private static final List<BeaconBeamDiagnostic> BEACON_BEAM_DIAGNOSTICS = new ArrayList<>();
	private static final List<BeaconBeamExecutionDiagnostic> BEACON_BEAM_EXECUTION_DIAGNOSTICS = new ArrayList<>();
	private static final List<CloudTraversalDiagnostic> CLOUD_TRAVERSAL_DIAGNOSTICS = new ArrayList<>();
	private static final List<CloudSemanticDiagnostic> CLOUD_SEMANTIC_DIAGNOSTICS = new ArrayList<>();
	private static final List<CloudExecutionDiagnostic> CLOUD_EXECUTION_DIAGNOSTICS = new ArrayList<>();
	private static final float[] PENDING_VIEW = new float[16];
	private static final float[] PENDING_PROJECTION = new float[16];
	// This advances at the one semantic-frame boundary before every whole-frame
	// world extraction. Capture screenshot indices are intentionally not used as
	// render-frame identities because one captured pose can span many renders.
	private static long semanticFrameSequence;
	private static ClientLevel voxelVolumeLevel;
	private static long voxelVolumeWorldGeneration = 1L;
	// This tracks the owned voxel-volume contract, not mutable mesh-cache
	// traffic. Terrain meshes are incremental occupancy inputs; rebuilding the
	// D3 volume for every section upload prevents its flood-fill history from
	// ever reaching a coherent generation during normal terrain streaming.
	private static long voxelVolumeResourceGeneration = 1L;
	private static VulkanicGalBridge.WorldVoxelVolumeFrameRecord pendingVoxelVolumeFrame =
		VulkanicGalBridge.WorldVoxelVolumeFrameRecord.disabled();
	private static VulkanicGalBridge.WorldShaderEnvironmentFrameRecord pendingShaderEnvironmentFrame =
		VulkanicGalBridge.WorldShaderEnvironmentFrameRecord.disabled();
	private static VulkanicGalBridge.WorldFeatureCoverageRecord pendingFeatureCoverage =
		VulkanicGalBridge.WorldFeatureCoverageRecord.empty();
	private static int shaderPackFrameCounter;
	private static float shaderPackFrameTimeSeconds;
	private static float shaderPackFrameTimeCounter;
	private static long shaderPackPreviousFrameStartNanos = Long.MIN_VALUE;
	private static float shaderPackFramePartialTick;
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
	// Capture-only origin paired with the camera-relative semantic frame matrices.
	private static double pendingDiagnosticCameraX;
	private static double pendingDiagnosticCameraY;
	private static double pendingDiagnosticCameraZ;
	private static boolean pendingDiagnosticCameraOriginValid;
	private static int blockOutlineProjectionDiagnosticLogs;
	private static int blockMarkerEnqueueDiagnosticLogs;
	private static int terrainParticleEnqueueDiagnosticLogs;
	private static int fallingBlockEnqueueDiagnosticLogs;
	private static int movingBlockEnqueueDiagnosticLogs;

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

	public static boolean shouldSuppressJavaWeatherRender() {
		return WorldRenderRoutePolicy.currentWeatherRoute().usesRustWholeFrameVulkan();
	}

	public static boolean shouldUseRustOpenGlMaterial() {
		return WorldRenderRoutePolicy.currentMaterialRoute().usesRustOpenGl()
			|| WorldRenderRoutePolicy.currentWeatherRoute().usesRustOpenGl();
	}

	private static boolean shouldUseRustOpenGlMeshInstances() {
		return WorldRenderRoutePolicy.currentBlockDisplayRoute().usesRustOpenGl()
			|| WorldRenderRoutePolicy.currentFallingBlockRoute().usesRustOpenGl()
			|| WorldRenderRoutePolicy.currentPistonMovingBlockRoute().usesRustOpenGl()
			|| WorldRenderRoutePolicy.currentPrimedTntRoute().usesRustOpenGl();
	}

	public static boolean shouldRouteTerrainParticle(BlockState blockState) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentMaterialRoute();
		return (route.usesRustOpenGl() || route.usesRustWholeFrameVulkan())
			&& terrainParticleTextureId(blockState) != 0;
	}

	public static boolean shouldUseRustOpenGlWorldPrimitives() {
		return shouldUseRustOpenGlOutline()
			|| shouldUseRustOpenGlCrack()
			|| shouldUseRustOpenGlMaterial()
			|| WorldRenderRoutePolicy.currentBlockDisplayRoute().usesRustOpenGl()
			|| WorldRenderRoutePolicy.currentFallingBlockRoute().usesRustOpenGl()
			|| WorldRenderRoutePolicy.currentPistonMovingBlockRoute().usesRustOpenGl()
			|| WorldRenderRoutePolicy.currentPrimedTntRoute().usesRustOpenGl();
	}

	/**
	 * Copies vanilla's extracted entity-fire feature into the existing cutout
	 * material-quad family. Java provides copied pose and atlas-UV semantics;
	 * Rust owns the atlas resource, batching, pipeline, and draw.
	 */
	public static int collectEntityFlameSemantics(
		List<SubmitNodeStorage.FlameSubmit> flameSubmits,
		net.minecraft.client.resources.model.AtlasManager atlasManager
	) {
		if (!WorldRenderRoutePolicy.currentEntityFlameRoute().usesRustWholeFrameVulkan()) {
			return 0;
		}
		if (flameSubmits == null || flameSubmits.isEmpty()) {
			return 0;
		}
		if (atlasManager == null) {
			throw new IllegalStateException("Rust whole-frame entity-fire route selected without atlas semantics");
		}
		net.minecraft.client.renderer.texture.TextureAtlasSprite fire0 = atlasManager.get(
			net.minecraft.client.resources.model.ModelBakery.FIRE_0
		);
		net.minecraft.client.renderer.texture.TextureAtlasSprite fire1 = atlasManager.get(
			net.minecraft.client.resources.model.ModelBakery.FIRE_1
		);
		int submittedQuads = 0;
		synchronized (LOCK) {
			if (pendingViewportWidth <= 0 || pendingViewportHeight <= 0) {
				throw new IllegalStateException("Rust whole-frame entity-fire route requires a seeded world primitive frame");
			}
			if (!WORLD_MESH_TEXTURES.containsKey(MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS)) {
				throw new IllegalStateException("Rust whole-frame entity-fire route selected before the copied terrain atlas was registered");
			}
			for (SubmitNodeStorage.FlameSubmit flameSubmit : flameSubmits) {
				EntityRenderState state = flameSubmit.entityRenderState();
				float scale = state.boundingBoxWidth * 1.4F;
				if (!Float.isFinite(scale) || scale <= 0.0F || !Float.isFinite(state.boundingBoxHeight)) {
					throw new IllegalStateException("Rust whole-frame entity-fire route received invalid entity bounds");
				}
				float remainingLayers = state.boundingBoxHeight / scale;
				Matrix4f transform = new Matrix4f(flameSubmit.pose().pose());
				transform.scale(scale, scale, scale);
				transform.rotate(flameSubmit.rotation());
				transform.translate(0.0F, 0.0F, 0.3F - (int)remainingLayers * 0.02F);
				float halfWidth = 0.5F;
				float verticalOffset = 0.0F;
				float depthOffset = 0.0F;
				for (int layer = 0; remainingLayers > 0.0F; layer++) {
					net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = (layer & 1) == 0 ? fire0 : fire1;
					float u0 = sprite.getU0();
					float v0 = sprite.getV0();
					float u1 = sprite.getU1();
					float v1 = sprite.getV1();
					if ((layer / 2 & 1) == 0) {
						float swap = u1;
						u1 = u0;
						u0 = swap;
					}
					float[] vertices = new float[12];
					transformMaterialVertex(transform, -halfWidth, -verticalOffset, depthOffset, vertices, 0);
					transformMaterialVertex(transform, halfWidth, -verticalOffset, depthOffset, vertices, 3);
					transformMaterialVertex(transform, halfWidth, 1.4F - verticalOffset, depthOffset, vertices, 6);
					transformMaterialVertex(transform, -halfWidth, 1.4F - verticalOffset, depthOffset, vertices, 9);
					PENDING_MATERIAL_QUADS.add(new VulkanicGalBridge.WorldMaterialQuadRecord(
						STRATUM_WORLD_MATERIAL, MATERIAL_ID_CUTOUT_TEXTURED, MATERIAL_TEXTURE_TERRAIN_BLOCK_ATLAS,
						MATERIAL_MODE_CUTOUT, DEPTH_POLICY_TEST_WRITE, CULL_NONE, WORLD_TOPOLOGY_TRIANGLES,
						WORLD_WINDING_CCW, 0xFFFFFFFF,
						vertices[0], vertices[1], vertices[2], vertices[3], vertices[4], vertices[5],
						vertices[6], vertices[7], vertices[8], vertices[9], vertices[10], vertices[11],
						u1, v1, u0, v1, u0, v0, u1, v0,
						pendingViewportWidth, pendingViewportHeight,
						MATERIAL_SOURCE_TEXTURED, MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS,
						0xFFFFFFFF, LightTexture.FULL_BRIGHT
					));
					submittedQuads++;
					remainingLayers -= 0.45F;
					verticalOffset -= 0.45F;
					halfWidth *= 0.9F;
					depthOffset -= 0.03F;
				}
			}
			pendingEntityFlameQuadCount += submittedQuads;
		}
		if (submittedQuads != 0) {
			recordEntityFlameSemanticDiagnostic(flameSubmits.size(), submittedQuads);
			DeterministicCameraCapture.recordSubmittedWorkIdentity(
				"entity-flame", "rust-vulkan-whole-frame:cutout-quads=" + submittedQuads
			);
		}
		return submittedQuads;
	}

	/**
	 * Copies the ordinary vanilla entity-shadow feature into the shared
	 * translucent material-quad family. This is the exact semantic expansion
	 * used by {@code ShadowFeatureRenderer}: Java supplies pose, receiver bounds,
	 * radius, and alpha; Rust owns the texture, batching, source program, and
	 * draw. No RenderType, VertexConsumer, or renderer callback crosses the
	 * boundary.
	 */
	public static int collectEntityShadowSemantics(List<SubmitNodeStorage.ShadowSubmit> shadowSubmits) {
		if (!WorldRenderRoutePolicy.currentEntityShadowRoute().usesRustWholeFrameVulkan()) {
			return 0;
		}
		if (shadowSubmits == null || shadowSubmits.isEmpty()) {
			return 0;
		}
		int submittedQuads = 0;
		synchronized (LOCK) {
			if (pendingViewportWidth <= 0 || pendingViewportHeight <= 0) {
				throw new IllegalStateException("Rust whole-frame entity-shadow route requires a seeded world primitive frame");
			}
			for (SubmitNodeStorage.ShadowSubmit shadowSubmit : shadowSubmits) {
				if (shadowSubmit.pose() == null || !Float.isFinite(shadowSubmit.radius()) || shadowSubmit.radius() <= 0.0F) {
					throw new IllegalStateException("Rust whole-frame entity-shadow route received an invalid copied shadow submit");
				}
				for (EntityRenderState.ShadowPiece shadowPiece : shadowSubmit.pieces()) {
					if (shadowPiece == null || shadowPiece.shapeBelow() == null || !Float.isFinite(shadowPiece.alpha())) {
						throw new IllegalStateException("Rust whole-frame entity-shadow route received invalid copied shadow-piece semantics");
					}
					AABB bounds = shadowPiece.shapeBelow().bounds();
					float x0 = shadowPiece.relativeX() + (float)bounds.minX;
					float x1 = shadowPiece.relativeX() + (float)bounds.maxX;
					float y = shadowPiece.relativeY() + (float)bounds.minY;
					float z0 = shadowPiece.relativeZ() + (float)bounds.minZ;
					float z1 = shadowPiece.relativeZ() + (float)bounds.maxZ;
					float radius = shadowSubmit.radius();
					float u0 = -x0 / (2.0F * radius) + 0.5F;
					float u1 = -x1 / (2.0F * radius) + 0.5F;
					float v0 = -z0 / (2.0F * radius) + 0.5F;
					float v1 = -z1 / (2.0F * radius) + 0.5F;
					float[] vertices = new float[12];
					transformMaterialVertex(shadowSubmit.pose(), x0, y, z0, vertices, 0);
					transformMaterialVertex(shadowSubmit.pose(), x0, y, z1, vertices, 3);
					transformMaterialVertex(shadowSubmit.pose(), x1, y, z1, vertices, 6);
					transformMaterialVertex(shadowSubmit.pose(), x1, y, z0, vertices, 9);
					int colorArgb = ARGB.white(shadowPiece.alpha());
					PENDING_MATERIAL_QUADS.add(new VulkanicGalBridge.WorldMaterialQuadRecord(
						STRATUM_WORLD_MATERIAL,
						MATERIAL_ID_TRANSLUCENT_TEXTURED,
						MATERIAL_TEXTURE_ENTITY_SHADOW,
						MATERIAL_MODE_TRANSLUCENT,
						DEPTH_POLICY_TEST_NO_WRITE,
						CULL_NONE,
						WORLD_TOPOLOGY_TRIANGLES,
						WORLD_WINDING_CCW,
						colorArgb,
						vertices[0], vertices[1], vertices[2], vertices[3], vertices[4], vertices[5],
						vertices[6], vertices[7], vertices[8], vertices[9], vertices[10], vertices[11],
						u0, v0, u0, v1, u1, v1, u1, v0,
						pendingViewportWidth, pendingViewportHeight,
						MATERIAL_SOURCE_TEXTURED, MATERIAL_SOURCE_UV_LOCAL_TEXTURE,
						colorArgb, LightTexture.FULL_BRIGHT
					));
					submittedQuads++;
				}
			}
		}
		if (submittedQuads != 0) {
			recordEntityShadowSemanticDiagnostic(shadowSubmits.size(), submittedQuads);
			DeterministicCameraCapture.recordSubmittedWorkIdentity(
				"entity-shadow", "rust-vulkan-whole-frame:translucent-quads=" + submittedQuads
			);
		}
		return submittedQuads;
	}

	/**
	 * Expands the copied vanilla leash endpoint state into the same 48 quads
	 * emitted by {@code LeashFeatureRenderer}. Each endpoint keeps its original
	 * color and packed light so the shared source-material stream interpolates
	 * rather than flattening the rope into a one-light approximation.
	 */
	public static int collectEntityLeashSemantics(List<SubmitNodeStorage.LeashSubmit> leashSubmits) {
		if (!WorldRenderRoutePolicy.currentEntityLeashRoute().usesRustWholeFrameVulkan()) {
			return 0;
		}
		if (leashSubmits == null || leashSubmits.isEmpty()) {
			return 0;
		}
		int submittedQuads = 0;
		synchronized (LOCK) {
			if (pendingViewportWidth <= 0 || pendingViewportHeight <= 0) {
				throw new IllegalStateException("Rust whole-frame entity-leash route requires a seeded world primitive frame");
			}
			for (SubmitNodeStorage.LeashSubmit leashSubmit : leashSubmits) {
				if (leashSubmit.pose() == null || leashSubmit.leashState() == null) {
					throw new IllegalStateException("Rust whole-frame entity-leash route received invalid copied leash semantics");
				}
				EntityRenderState.LeashState leash = leashSubmit.leashState();
				if (!isFiniteVec3(leash.start) || !isFiniteVec3(leash.end) || !isFiniteVec3(leash.offset)) {
					throw new IllegalStateException("Rust whole-frame entity-leash route received non-finite endpoint semantics");
				}
				float dx = (float)(leash.end.x - leash.start.x);
				float dy = (float)(leash.end.y - leash.start.y);
				float dz = (float)(leash.end.z - leash.start.z);
				float horizontalLengthSquared = dx * dx + dz * dz;
				if (!Float.isFinite(horizontalLengthSquared) || horizontalLengthSquared <= 0.0F) {
					throw new IllegalStateException("Rust whole-frame entity-leash route rejects zero-length horizontal rope semantics");
				}
				float widthScale = Mth.invSqrt(horizontalLengthSquared) * 0.025F;
				float sideZ = dz * widthScale;
				float sideX = dx * widthScale;
				Matrix4f transform = new Matrix4f(leashSubmit.pose()).translate(
					(float)leash.offset.x, (float)leash.offset.y, (float)leash.offset.z
				);
				submittedQuads += appendLeashStripLocked(transform, dx, dy, dz, sideZ, sideX, leash, 0, 24, false);
				submittedQuads += appendLeashStripLocked(transform, dx, dy, dz, sideZ, sideX, leash, 24, 0, true);
			}
		}
		if (submittedQuads != 0) {
			recordEntityLeashSemanticDiagnostic(leashSubmits.size(), submittedQuads);
			DeterministicCameraCapture.recordSubmittedWorkIdentity(
				"entity-leash", "rust-vulkan-whole-frame:vertex-modulated-quads=" + submittedQuads
			);
		}
		return submittedQuads;
	}

	private static int appendLeashStripLocked(
		Matrix4f transform, float dx, float dy, float dz, float sideZ, float sideX,
		EntityRenderState.LeashState leash, int startStep, int endStep, boolean reverse
	) {
		int direction = Integer.compare(endStep, startStep);
		int submitted = 0;
		for (int step = startStep; step != endStep; step += direction) {
			LeashVertex a = leashVertex(dx, dy, dz, sideZ, sideX, step, reverse, leash);
			LeashVertex b = leashVertex(dx, dy, dz, sideZ, sideX, step + direction, reverse, leash);
			float[] vertices = new float[12];
			transformMaterialVertex(transform, a.x0, a.y0, a.z0, vertices, 0);
			transformMaterialVertex(transform, a.x1, a.y1, a.z1, vertices, 3);
			transformMaterialVertex(transform, b.x0, b.y0, b.z0, vertices, 6);
			transformMaterialVertex(transform, b.x1, b.y1, b.z1, vertices, 9);
			PENDING_MATERIAL_QUADS.add(new VulkanicGalBridge.WorldMaterialQuadRecord(
				STRATUM_WORLD_MATERIAL,
				MATERIAL_ID_OPAQUE_TEXTURED,
				MATERIAL_TEXTURE_GENERATED_WHITE,
				MATERIAL_MODE_OPAQUE,
				DEPTH_POLICY_TEST_WRITE,
				CULL_NONE,
				WORLD_TOPOLOGY_TRIANGLES,
				WORLD_WINDING_CCW,
				a.colorArgb,
				vertices[0], vertices[1], vertices[2], vertices[3], vertices[4], vertices[5],
				vertices[6], vertices[7], vertices[8], vertices[9], vertices[10], vertices[11],
				0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F,
				pendingViewportWidth, pendingViewportHeight,
				MATERIAL_SOURCE_TEXTURED, MATERIAL_SOURCE_UV_LOCAL_TEXTURE,
				a.colorArgb, a.packedLight,
				a.colorArgb, a.colorArgb, b.colorArgb, b.colorArgb,
				a.packedLight, a.packedLight, b.packedLight, b.packedLight
			));
			submitted++;
		}
		return submitted;
	}

	private static LeashVertex leashVertex(
		float dx, float dy, float dz, float sideZ, float sideX, int step, boolean reverse, EntityRenderState.LeashState leash
	) {
		float progress = step / 24.0F;
		int blockLight = (int)Mth.lerp(progress, leash.startBlockLight, leash.endBlockLight);
		int skyLight = (int)Mth.lerp(progress, leash.startSkyLight, leash.endSkyLight);
		float shade = step % 2 == (reverse ? 1 : 0) ? 0.7F : 1.0F;
		float red = 0.5F * shade;
		float green = 0.4F * shade;
		float blue = 0.3F * shade;
		float y = leash.slack
			? (dy > 0.0F ? dy * progress * progress : dy - dy * (1.0F - progress) * (1.0F - progress))
			: dy * progress;
		float x = dx * progress;
		float z = dz * progress;
		return new LeashVertex(
			x - sideZ, y + 0.05F, z + sideX,
			x + sideZ, y, z - sideX,
			0xFF000000 | (Math.round(red * 255.0F) << 16) | (Math.round(green * 255.0F) << 8) | Math.round(blue * 255.0F),
			LightTexture.pack(blockLight, skyLight)
		);
	}

	private record LeashVertex(float x0, float y0, float z0, float x1, float y1, float z1, int colorArgb, int packedLight) {
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
				voxelVolumeResourceGeneration++;
				WORLD_MESH_ASSETS.clear();
				DIRTY_WORLD_MESH_ASSETS.clear();
				UPLOADED_WORLD_MESH_GENERATIONS.clear();
				WORLD_MESH_TEXTURES.clear();
				DIRTY_WORLD_MESH_TEXTURES.clear();
				UPLOADED_WORLD_MESH_TEXTURES.clear();
				registerWorldSkyTextureAssetsLocked(resourceManager);
				PENDING_MESH_INSTANCES.clear();
				PENDING_MESH_PRODUCERS.clear();
				PENDING_MODEL_MESH_SEMANTICS.clear();
				RustGalTerrainRenderer.invalidateForResourceReload();
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
			if (bridge == null || (DIRTY_WORLD_MESH_ASSETS.isEmpty()
				&& DIRTY_WORLD_MESH_TEXTURES.isEmpty()
				&& DIRTY_WORLD_MESH_SORTED_INDICES.isEmpty())) {
				return null;
			}
			long uploadGeneration = Math.max(worldMeshAssetGeneration, nextWorldMeshUploadGeneration + 1L);
			attemptedWorldMeshAssetGeneration = uploadGeneration;
			try {
				List<VulkanicGalBridge.WorldMeshAssetRecord> dirtyMeshes = dirtyWorldMeshAssetsLocked(
					MAX_WORLD_MESH_ASSETS_PER_UPLOAD,
					MAX_WORLD_MESH_UPLOAD_BYTES
				);
				List<VulkanicGalBridge.WorldMeshTextureAssetRecord> dirtyTextures = dirtyWorldMeshTextureAssetsLocked();
				List<VulkanicGalBridge.WorldMeshSortedIndexRecord> dirtySortedIndices = dirtyWorldMeshSortedIndicesLocked(dirtyMeshes);
					VulkanicGalBridge.Status status = bridge.updateWorldMeshAssets(
						uploadGeneration,
						dirtyMeshes,
						dirtyTextures,
						dirtySortedIndices
					);
					nextWorldMeshUploadGeneration = uploadGeneration;
					uploadedWorldMeshAssetGeneration = uploadGeneration;
					for (VulkanicGalBridge.WorldMeshAssetRecord mesh : dirtyMeshes) {
						UPLOADED_WORLD_MESH_GENERATIONS.put(mesh.meshKey(), mesh.meshGeneration());
						DIRTY_WORLD_MESH_ASSETS.remove(mesh.meshKey());
					}
					for (VulkanicGalBridge.WorldMeshSortedIndexRecord sortedIndex : dirtySortedIndices) {
						DIRTY_WORLD_MESH_SORTED_INDICES.remove(sortedIndex.meshKey());
					}
					for (VulkanicGalBridge.WorldMeshTextureAssetRecord texture : dirtyTextures) {
						UPLOADED_WORLD_MESH_TEXTURES.add(texture.textureId());
						DIRTY_WORLD_MESH_TEXTURES.remove(texture.textureId());
					}
					lastWorldMeshAssetPayloadCount = dirtyMeshes.size() + dirtyTextures.size() + dirtySortedIndices.size();
					lastWorldMeshAssetPayloadBytes = worldMeshAssetPayloadBytes(dirtyMeshes, dirtyTextures, dirtySortedIndices);
				auditMessage(
					"Rust VulkanicGAL world mesh asset update accepted"
						+ " generation=" + uploadGeneration
							+ " meshes=" + WORLD_MESH_ASSETS.size()
							+ " dirty_meshes=" + dirtyMeshes.size()
							+ " dirty_sorted_indices=" + dirtySortedIndices.size()
							+ " textures=" + WORLD_MESH_TEXTURES.size()
						+ " dirty_textures=" + dirtyTextures.size()
						+ " payload_bytes=" + lastWorldMeshAssetPayloadBytes
						+ " uploaded_generation=" + uploadedWorldMeshAssetGeneration
						+ " pending_meshes=" + DIRTY_WORLD_MESH_ASSETS.size()
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

	public static WorldMeshAssetMetrics worldMeshAssetMetrics() {
		synchronized (LOCK) {
			return new WorldMeshAssetMetrics(
				worldMeshAssetGeneration,
				uploadedWorldMeshAssetGeneration,
				lastWorldMeshAssetPayloadCount,
				lastWorldMeshAssetPayloadBytes,
				worldMeshAssetUpdateFailures,
				WORLD_MESH_ASSETS.size(),
				WORLD_MESH_TEXTURES.size(),
				DIRTY_WORLD_MESH_ASSETS.size(),
				DIRTY_WORLD_MESH_TEXTURES.size(),
				PENDING_MESH_INSTANCES.size(),
				UPLOADED_WORLD_MESH_GENERATIONS.size(),
				UPLOADED_WORLD_MESH_TEXTURES.size()
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
		WorldMaterialAssetCandidate[] candidates = new WorldMaterialAssetCandidate[11 + LIGHT_MARKER_LOCATIONS.length];
		candidates[0] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_STONE, STONE_TEXTURE_LOCATION);
		candidates[1] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_DIRT, DIRT_TEXTURE_LOCATION);
		candidates[2] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_OAK_LEAVES, OAK_LEAVES_TEXTURE_LOCATION);
		candidates[3] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_DEEPSLATE, DEEPSLATE_TEXTURE_LOCATION);
		candidates[4] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_WHITE_WOOL, WHITE_WOOL_TEXTURE_LOCATION);
		candidates[5] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER, BARRIER_MARKER_LOCATION);
		candidates[6] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_WEATHER_RAIN, WEATHER_RAIN_TEXTURE_LOCATION);
		candidates[7] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_WEATHER_SNOW, WEATHER_SNOW_TEXTURE_LOCATION);
		candidates[8] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_EXPERIENCE_ORB, EXPERIENCE_ORB_TEXTURE_LOCATION);
		candidates[9] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_BEACON_BEAM, BEACON_BEAM_TEXTURE_LOCATION);
		candidates[10] = new WorldMaterialAssetCandidate(MATERIAL_TEXTURE_ENTITY_SHADOW, ENTITY_SHADOW_TEXTURE_LOCATION);
		for (int i = 0; i < LIGHT_MARKER_LOCATIONS.length; i++) {
			candidates[i + 11] = new WorldMaterialAssetCandidate(LIGHT_MARKER_TEXTURE_IDS[i], LIGHT_MARKER_LOCATIONS[i]);
		}
		return candidates;
	}

	/**
	 * Registers only copied resource-pack bytes for the semantic sky assets.
	 * This runs after the mesh-asset generation reset so a successful reload
	 * cannot leave the next owned celestial pass sampling a texture from the
	 * previous pack generation. It does not select a shader program or draw.
	 */
	private static void registerWorldSkyTextureAssetsLocked(ResourceManager resourceManager) {
		if (resourceManager == null || !WorldRenderRoutePolicy.currentBackgroundRoute().usesRustWholeFrameVulkan()) {
			return;
		}
		VulkanicGalBridge.WorldMeshTextureAssetRecord sun = readWorldSkyTextureAsset(
			resourceManager,
			MATERIAL_TEXTURE_SKY_SUN,
			SKY_SUN_TEXTURE_LOCATION
		);
		VulkanicGalBridge.WorldMeshTextureAssetRecord moon = readWorldSkyTextureAsset(
			resourceManager,
			MATERIAL_TEXTURE_SKY_MOON_PHASES,
			SKY_MOON_PHASES_TEXTURE_LOCATION
		);
		if (sun == null || moon == null) {
			auditMessage(
				"Rust VulkanicGAL owned sky asset preparation unavailable"
					+ " sun=" + (sun != null)
					+ " moon=" + (moon != null)
					+ " route=unadmitted"
			);
			return;
		}
		WORLD_MESH_TEXTURES.put(sun.textureId(), sun);
		WORLD_MESH_TEXTURES.put(moon.textureId(), moon);
		DIRTY_WORLD_MESH_TEXTURES.add(sun.textureId());
		DIRTY_WORLD_MESH_TEXTURES.add(moon.textureId());
		auditMessage(
			"Rust VulkanicGAL owned sky texture assets registered"
				+ " textures=2"
				+ " source=resource-pack-copy"
				+ " route=unadmitted"
		);
	}

	private static VulkanicGalBridge.WorldMeshTextureAssetRecord readWorldSkyTextureAsset(
		ResourceManager resourceManager,
		int textureId,
		ResourceLocation location
	) {
		Optional<Resource> resource = resourceManager.getResource(location);
		if (resource.isEmpty()) {
			LOGGER.warn("Missing Rust VulkanicGAL semantic sky texture {}", location);
			return null;
		}
		try (InputStream input = resource.get().open()) {
			return new VulkanicGalBridge.WorldMeshTextureAssetRecord(textureId, input.readAllBytes());
		} catch (IOException error) {
			LOGGER.warn("Failed to copy Rust VulkanicGAL semantic sky texture {}", location, error);
			return null;
		}
	}

	private static MessageDigest sha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 digest is unavailable", error);
		}
	}

	public static void beginFrame(Matrix4f viewMatrix, Matrix4f projectionMatrix, int viewportWidth, int viewportHeight) {
		beginFrame(viewMatrix, projectionMatrix, viewportWidth, viewportHeight, null, null);
	}

	/**
	 * Refreshes the matrix/viewport portion of a borrowed OpenGL frame without
	 * discarding semantic work collected by earlier world passes. Frame-graph
	 * callbacks use this when their execution is deferred beyond extraction.
	 */
	public static void refreshBorrowedOpenGlFrameSeed(
		Matrix4f viewMatrix,
		Matrix4f projectionMatrix,
		int viewportWidth,
		int viewportHeight
	) {
		if (viewMatrix == null || projectionMatrix == null) {
			throw new IllegalArgumentException("Rust borrowed OpenGL frame seed requires view and projection matrices");
		}
		synchronized (LOCK) {
			// Deferred frame-graph callbacks can momentarily expose an incomplete
			// matrix snapshot while the early level-render seed is still valid. A
			// refresh must therefore be non-destructive: retain the known-good seed
			// rather than clearing the viewport and crashing a selected producer.
			float[] refreshedView = new float[16];
			float[] refreshedProjection = new float[16];
			viewMatrix.get(refreshedView);
			projectionMatrix.get(refreshedProjection);
			if (!isFinite(refreshedView) || !isFinite(refreshedProjection)
				|| viewportWidth <= 0 || viewportHeight <= 0) {
				return;
			}
			System.arraycopy(refreshedView, 0, PENDING_VIEW, 0, PENDING_VIEW.length);
			System.arraycopy(refreshedProjection, 0, PENDING_PROJECTION, 0, PENDING_PROJECTION.length);
			pendingViewportWidth = viewportWidth;
			pendingViewportHeight = viewportHeight;
		}
	}

	/**
	 * Advances explicit frame-time semantics once at the start of a client
	 * render frame. This deliberately duplicates no Iris renderer state: the
	 * record contains only clock values copied from the game loop.
	 */
	public static void beginShaderPackFrame(long frameStartNanos, float partialTick) {
		if (frameStartNanos < 0L) {
			throw new IllegalArgumentException("shader-pack frame start must be non-negative");
		}
		if (!Float.isFinite(partialTick) || partialTick < 0.0F || partialTick > 1.0F) {
			throw new IllegalArgumentException("shader-pack partial tick must be finite and within [0, 1]");
		}
		synchronized (LOCK) {
			shaderPackFramePartialTick = partialTick;
			if (DETERMINISTIC_TEMPORAL_PARITY) {
				shaderPackFrameCounter = Math.floorMod(DETERMINISTIC_TEMPORAL_FRAME_COUNTER, 720720);
				shaderPackFrameTimeSeconds = DETERMINISTIC_TEMPORAL_FRAME_TIME;
				shaderPackFrameTimeCounter = DETERMINISTIC_TEMPORAL_FRAME_TIME_COUNTER;
				shaderPackPreviousFrameStartNanos = frameStartNanos;
				return;
			}
			shaderPackFrameCounter = (shaderPackFrameCounter + 1) % 720720;
			long previous = shaderPackPreviousFrameStartNanos;
			long elapsedMillis = previous == Long.MIN_VALUE ? 0L : Math.max(0L, (frameStartNanos - previous) / 1_000_000L);
			shaderPackFrameTimeSeconds = elapsedMillis / 1000.0F;
			shaderPackFrameTimeCounter += shaderPackFrameTimeSeconds;
			if (shaderPackFrameTimeCounter >= 3600.0F) {
				shaderPackFrameTimeCounter = 0.0F;
			}
			shaderPackPreviousFrameStartNanos = frameStartNanos;
		}
	}

	/**
	 * Captures copied camera/world semantics for private Rust-owned volume
	 * preparation. This does not select a shader program or pass any renderer
	 * object through the semantic frame.
	 */
	public static void beginFrame(
		Matrix4f viewMatrix,
		Matrix4f projectionMatrix,
		int viewportWidth,
		int viewportHeight,
		ClientLevel level,
		Camera camera
	) {
		synchronized (LOCK) {
			semanticFrameSequence++;
			PENDING_SEGMENTS.clear();
			PENDING_CRACK_QUADS.clear();
			PENDING_BORDER_QUADS.clear();
			PENDING_MATERIAL_QUADS.clear();
			PENDING_TEXT_QUADS.clear();
			worldTextDiagnostic = WorldTextDiagnostic.empty(semanticFrameSequence);
			PENDING_MESH_INSTANCES.clear();
			PENDING_MESH_PRODUCERS.clear();
			PENDING_FIRST_PERSON_MESH_INSTANCES.clear();
			pendingFirstPersonFrame = false;
			pendingFirstPersonMainHandInstanceCount = 0;
			PENDING_MODEL_MESH_SEMANTICS.clear();
			pendingBackground = VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
			pendingVoxelVolumeFrame = VulkanicGalBridge.WorldVoxelVolumeFrameRecord.disabled();
			pendingShaderEnvironmentFrame = VulkanicGalBridge.WorldShaderEnvironmentFrameRecord.disabled();
			pendingFeatureCoverage = VulkanicGalBridge.WorldFeatureCoverageRecord.empty();
			seedFrameMatricesLocked(viewMatrix, projectionMatrix, viewportWidth, viewportHeight);
			seedDiagnosticCameraOriginLocked(camera);
			seedVoxelVolumeFrameLocked(level, camera);
			seedShaderEnvironmentFrameLocked(level, camera);
		}
	}

	/**
	 * Retains only the aggregate Java feature-family inventory for this semantic
	 * frame. It makes unported work explicit without retaining a Java model,
	 * render type, callback, Iris object, or native handle.
	 */
	public static void enqueueWorldFeatureCoverage(SubmitNodeCollection.WorldFeatureCoverageSnapshot coverage) {
		if (coverage == null) {
			return;
		}
		synchronized (LOCK) {
			// Whole-frame name tags were already collected from these same submit
			// lists into the explicit text stream. They are no longer an omitted
			// Java feature family, while every other non-zero count remains an
			// ownership gap that Rust must report rather than silently draw.
			int nameTagSubmits = WorldRenderRoutePolicy.currentWorldTextRoute().usesRustWholeFrameVulkan()
				? 0
				: coverage.nameTagSubmits();
			int textSubmits = WorldRenderRoutePolicy.currentWorldTextRoute().usesRustWholeFrameVulkan()
				? 0
				: coverage.textSubmits();
			int shadowSubmits = WorldRenderRoutePolicy.currentEntityShadowRoute().usesRustWholeFrameVulkan()
				? 0
				: coverage.shadowSubmits();
			int leashSubmits = WorldRenderRoutePolicy.currentEntityLeashRoute().usesRustWholeFrameVulkan()
				? 0
				: coverage.leashSubmits();
			pendingFeatureCoverage = new VulkanicGalBridge.WorldFeatureCoverageRecord(
				coverage.modelSubmits(), coverage.modelPartSubmits(), coverage.blockModelSubmits(),
				coverage.ordinaryBlockSubmits(), coverage.itemSubmits(), coverage.customGeometrySubmits(),
				shadowSubmits, coverage.flameSubmits(), nameTagSubmits,
					textSubmits, coverage.hitboxSubmits(), leashSubmits, coverage.particleGroupSubmits()
			);
		}
	}

	/**
	 * Stages copied name-tag semantics for the Rust-owned world-text pass.
	 * This method only accepts semantic glyph and atlas inputs and never retains
	 * a Java font renderer or an Iris render-state object.
	 */
	public static WorldTextSemanticCollector.Result collectWorldTextSemantics(
		NameTagFeatureRenderer.Storage.SemanticSnapshot snapshot, Font font
	) {
		WorldTextSemanticCollector.Result result = WorldTextSemanticCollector.collectNameTags(snapshot, font);
		recordWorldTextSemanticSnapshot(snapshot, result);
		stageWorldTextSemantics(result);
		return result;
	}

	/**
	 * Stages ordinary text through the same copied glyph contract as name tags.
	 * Outlined submits remain absent until the Rust frontend models their
	 * distinct stroke semantics.
	 */
	public static WorldTextSemanticCollector.Result collectWorldTextSemantics(
		List<SubmitNodeStorage.TextSubmit> submits, Font font
	) {
		WorldTextSemanticCollector.Result result = WorldTextSemanticCollector.collectTextSubmits(submits, font);
		recordWorldTextTextSnapshot(submits, result);
		stageWorldTextSemantics(result);
		return result;
	}

	private static void stageWorldTextSemantics(WorldTextSemanticCollector.Result result) {
		if (!result.fullySupported()) {
			return;
		}
		synchronized (LOCK) {
			PENDING_TEXT_QUADS.addAll(result.quads());
			boolean imageChanged = false;
			for (WorldTextSemanticCollector.WorldTextImage image : result.images()) {
				WorldTextSemanticCollector.WorldTextImage previous = WORLD_TEXT_IMAGES.put(image.assetId(), image);
				if (previous == null || !previous.matchesGeneration(image)) {
					DIRTY_WORLD_TEXT_IMAGES.add(image.assetId());
					imageChanged = true;
				}
			}
			if (imageChanged) {
				worldTextImageGeneration++;
			}
		}
	}

	/** Records bounded routing receipts for deterministic world-text capture. */
	public static void recordWorldTextTraversal(int visibleEntityStates, int nameTagCallbacks, int textCallbacks) {
		synchronized (LOCK) {
			worldTextDiagnostic = worldTextDiagnostic.withTraversal(
				semanticFrameSequence, visibleEntityStates, nameTagCallbacks, textCallbacks
			);
		}
	}

	private static void recordWorldTextSemanticSnapshot(
		NameTagFeatureRenderer.Storage.SemanticSnapshot snapshot,
		WorldTextSemanticCollector.Result result
	) {
		synchronized (LOCK) {
			worldTextDiagnostic = worldTextDiagnostic.withSemanticSnapshot(
				semanticFrameSequence,
				snapshot.normal().size(),
				snapshot.seeThrough().size(),
				0,
				result.quads().size(),
				result.images().size(),
				result.fullySupported()
			);
		}
	}

	/** Records ordinary text receipts separately from name tags without changing their glyph contract. */
	private static void recordWorldTextTextSnapshot(
		List<SubmitNodeStorage.TextSubmit> submits, WorldTextSemanticCollector.Result result
	) {
		int normal = 0;
		int seeThrough = 0;
		int polygonOffset = 0;
		for (SubmitNodeStorage.TextSubmit submit : submits) {
			switch (WorldTextSemanticCollector.textSubmitDepthPolicy(submit)) {
				case WorldTextSemanticCollector.DEPTH_NORMAL -> normal++;
				case WorldTextSemanticCollector.DEPTH_SEE_THROUGH -> seeThrough++;
				case WorldTextSemanticCollector.DEPTH_POLYGON_OFFSET -> polygonOffset++;
				default -> {
				}
			}
		}
		synchronized (LOCK) {
			worldTextDiagnostic = worldTextDiagnostic.withSemanticSnapshot(
				semanticFrameSequence,
				normal,
				seeThrough,
				polygonOffset,
				result.quads().size(),
				result.images().size(),
				result.fullySupported()
			);
		}
	}

	public static WorldTextDiagnostic worldTextDiagnostic() {
		synchronized (LOCK) {
			return worldTextDiagnostic;
		}
	}

	/** Publishes complete copied font-image generations before a frame references them. */
	public static VulkanicGalBridge.Status flushPendingWorldTextImages(VulkanicGalBridge bridge) {
		if (bridge == null) {
			return null;
		}
		synchronized (LOCK) {
			if (DIRTY_WORLD_TEXT_IMAGES.isEmpty()
				|| uploadedWorldTextImageGeneration >= worldTextImageGeneration
				|| attemptedWorldTextImageGeneration >= worldTextImageGeneration) {
				return null;
			}
			attemptedWorldTextImageGeneration = worldTextImageGeneration;
			List<WorldTextSemanticCollector.WorldTextImage> images = List.copyOf(WORLD_TEXT_IMAGES.values());
			VulkanicGalBridge.Status status = bridge.updateWorldTextImages(
				worldTextImageGeneration, encodeWorldTextImages(images)
			);
			uploadedWorldTextImageGeneration = worldTextImageGeneration;
			DIRTY_WORLD_TEXT_IMAGES.clear();
			return status;
		}
	}

	/** Converts copied world-text semantic records without exposing a font atlas object. */
	public static List<VulkanicGalBridge.WorldTextQuadRecord> encodeWorldTextQuads(
		List<WorldTextSemanticCollector.WorldTextQuad> quads
	) {
		Objects.requireNonNull(quads, "quads");
		return quads.stream().map(WorldTextSemanticCollector.WorldTextQuad::toBridgeRecord).toList();
	}

	/** Converts copied atlas pixels into the dedicated world-text asset stream. */
	public static List<VulkanicGalBridge.WorldTextImageAssetRecord> encodeWorldTextImages(
		List<WorldTextSemanticCollector.WorldTextImage> images
	) {
		Objects.requireNonNull(images, "images");
		return images.stream().map(image -> new VulkanicGalBridge.WorldTextImageAssetRecord(
			image.assetId(), image.atlasGeneration(), image.atlasRevision(), image.colored() ? 2 : 1,
			image.width(), image.height(), image.pixels()
		)).toList();
	}

	/**
	 * Bounded Java-only observability for a selected-source admission failure.
	 * The strings are emitted immediately and never become frame transport.
	 */
	public static void recordSelectedSourceCoverageDiagnostics(List<String> samples) {
		if (!selectedSourceDiagnosticsEnabled() || samples == null || samples.isEmpty()) {
			return;
		}
		auditMessage("Rust VulkanicGAL selected-source unsupported feature samples="
			+ String.join("|", samples));
	}

	/** Returns the active Rust whole-frame semantic extraction sequence. */
	public static long currentSemanticFrameSequence() {
		synchronized (LOCK) {
			return semanticFrameSequence;
		}
	}

	/**
	 * Refreshes the copied shader-environment record after a producer route has
	 * established a previously unavailable vanilla lightmap snapshot. The
	 * method copies semantic values only; it neither submits Java rendering nor
	 * exposes the Java lightmap resource to Rust.
	 */
	public static void refreshShaderEnvironmentLightmap() {
		synchronized (LOCK) {
			Minecraft minecraft = Minecraft.getInstance();
			seedShaderEnvironmentFrameLocked(minecraft.level, minecraft.gameRenderer.getMainCamera());
		}
	}

	/** Returns the copied lightmap generation currently staged in this frame's semantic record. */
	public static long pendingShaderEnvironmentLightmapGeneration() {
		synchronized (LOCK) {
			return pendingShaderEnvironmentFrame.lightmapEnabled()
				? pendingShaderEnvironmentFrame.lightmapGeneration()
				: 0L;
		}
	}

	private static void seedVoxelVolumeFrameLocked(ClientLevel level, Camera camera) {
		if (level == null || camera == null) {
			return;
		}
		if (voxelVolumeLevel != level) {
			voxelVolumeLevel = level;
			voxelVolumeWorldGeneration++;
			voxelVolumeResourceGeneration++;
		}
		Vec3 position = camera.getPosition();
		if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
			return;
		}
		pendingVoxelVolumeFrame = new VulkanicGalBridge.WorldVoxelVolumeFrameRecord(
			true,
			voxelVolumeWorldGeneration,
			Math.max(1L, voxelVolumeResourceGeneration),
			(float)position.x,
			(float)position.y,
			(float)position.z
		);
	}

	private static void seedShaderEnvironmentFrameLocked(ClientLevel level, Camera camera) {
		if (level == null) {
			return;
		}
		int skyColor = camera == null
			? 0
			: level.getSkyColor(camera.getPosition(), shaderPackFramePartialTick);
		Vector3f fogColor = shaderPackFogColor(level, camera);
		FogParameters fogParameters = shaderPackFogParameters();
		int biomePrecipitation = shaderPackBiomePrecipitation(level, camera);
		String biomeResourceLocation = shaderPackBiomeResourceLocation(level, camera);
		String mainHandItemModelResourceLocation = shaderPackHeldItemModelResourceLocation(
			Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem()
		);
		String offHandItemModelResourceLocation = shaderPackHeldItemModelResourceLocation(
			Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getOffhandItem()
		);
		int mainHandItemLightEmission = shaderPackHeldItemLightEmission(
			Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem()
		);
		int offHandItemLightEmission = shaderPackHeldItemLightEmission(
			Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getOffhandItem()
		);
		LightTexture.RustSemanticLightmapInputs lightmap = Minecraft.getInstance().gameRenderer.lightTexture()
			.ensureRustSemanticLightmapInputs(shaderPackFramePartialTick);
		Vec3 relativeEyePosition = shaderPackRelativeEyePosition(camera);
		int[] eyeBrightness = shaderPackEyeBrightness();
		pendingShaderEnvironmentFrame = new VulkanicGalBridge.WorldShaderEnvironmentFrameRecord(
			true,
			Math.max(1L, voxelVolumeWorldGeneration),
			shaderPackWorldTime(level),
			shaderPackFrameCounter,
			shaderPackFrameTimeSeconds,
			shaderPackFrameTimeCounter,
			shaderPackWorldDay(level),
			shaderPackMoonPhase(level),
			shaderPackTimeOfDay(level),
			clampUnit(level.getRainLevel(1.0F)),
			clampUnit(level.getThunderLevel(1.0F)),
			clampUnit(level.getSkyDarken(1.0F)),
			shaderPackEyeSubmersion(camera),
			(float)Math.max(0.0, Minecraft.getInstance().options.gamma().get()),
			Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F,
			(float)relativeEyePosition.x,
			(float)relativeEyePosition.y,
			(float)relativeEyePosition.z,
			ARGB.redFloat(skyColor),
			ARGB.greenFloat(skyColor),
			ARGB.blueFloat(skyColor),
			shaderPackDarknessLightFactor(),
			shaderPackNightVision(),
			fogColor.x,
			fogColor.y,
			fogColor.z,
			biomePrecipitation,
			biomeResourceLocation,
			mainHandItemModelResourceLocation,
			offHandItemModelResourceLocation,
			mainHandItemLightEmission,
			offHandItemLightEmission,
			lightmap != null,
			lightmap == null ? 0L : lightmap.generation(),
			lightmap == null ? 0.0F : lightmap.ambientLightFactor(),
			lightmap == null ? 0.0F : lightmap.skyFactor(),
			lightmap == null ? 0.0F : lightmap.blockFactor(),
			lightmap == null ? 0.0F : lightmap.nightVisionFactor(),
			lightmap == null ? 0.0F : lightmap.darknessScale(),
			lightmap == null ? 0.0F : lightmap.darkenWorldFactor(),
			lightmap == null ? 0.0F : lightmap.brightnessFactor(),
			lightmap == null ? 0.0F : lightmap.skyLightRed(),
			lightmap == null ? 0.0F : lightmap.skyLightGreen(),
			lightmap == null ? 0.0F : lightmap.skyLightBlue(),
			lightmap == null ? 0.0F : lightmap.ambientRed(),
			lightmap == null ? 0.0F : lightmap.ambientGreen(),
			lightmap == null ? 0.0F : lightmap.ambientBlue(),
			shaderPackBlindness(),
			shaderPackDarknessFactor(),
			eyeBrightness[0],
			eyeBrightness[1],
			fogParameters.red(),
			fogParameters.green(),
			fogParameters.blue(),
			fogParameters.alpha(),
			fogParameters.environmentalStart(),
			fogParameters.environmentalEnd(),
			fogParameters.renderStart(),
			fogParameters.renderEnd(),
			shaderPackDistantHorizonsRenderDistance()
		);
	}

	/**
	 * Complementary's {@code dhRenderDistance} uniform is measured in blocks.
	 *
	 * <p>The whole-frame Rust route intentionally does not initialize Iris's
	 * Distant Horizons renderer, so Iris's compatibility helper would fall back
	 * to Minecraft's chunk-count setting here. Read the public DH configuration
	 * as gameplay semantic state instead; no Iris renderer state crosses the
	 * boundary. The vanilla fallback is also converted to blocks so the uniform
	 * keeps one unit regardless of DH configuration readiness.</p>
	 */
	private static int shaderPackDistantHorizonsRenderDistance() {
		if (DhApi.Delayed.configs != null) {
			return Math.max(0, DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue() * 16);
		}
		return Math.max(0, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16);
	}

	/**
	 * Copies the game renderer's semantic fog range after vanilla has prepared
	 * it. This is the same gameplay-facing Sodium record Iris reads when it
	 * populates its fog uniforms, but no Iris object, GL state, or uniform
	 * location crosses into Rust.
	 */
	private static FogParameters shaderPackFogParameters() {
		// The Rust whole-frame route computes this frame's fog before semantic
		// extraction. Sodium's hook cache is populated by its separate terrain
		// setup path, which does not run here; reading it would instead retain a
		// stale DH-cancelled sentinel range. Keep the copied input tied to the
		// same fresh vanilla fog computation as the selected frame.
		return Minecraft.getInstance().gameRenderer.fogRenderer.sodium$getFogParameters();
	}

	/**
	 * Mirrors Iris's documented blindness semantic using only the camera
	 * entity's vanilla effect data. This is copied gameplay input, not an Iris
	 * uniform or renderer object.
	 */
	private static float shaderPackBlindness() {
		Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
		if (cameraEntity instanceof LivingEntity livingEntity) {
			MobEffectInstance blindness = livingEntity.getEffect(MobEffects.BLINDNESS);
			if (blindness != null) {
				return blindness.isInfiniteDuration()
					? 1.0F
					: Mth.clamp(blindness.getDuration() / 20.0F, 0.0F, 1.0F);
			}
		}
		return 0.0F;
	}

	/** Raw darkness-effect blend, intentionally distinct from lightmap pulse. */
	private static float shaderPackDarknessFactor() {
		Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
		if (cameraEntity instanceof LivingEntity livingEntity) {
			MobEffectInstance darkness = livingEntity.getEffect(MobEffects.DARKNESS);
			if (darkness != null) {
				return Mth.clamp(
					darkness.getBlendFactor(livingEntity, shaderPackFramePartialTick),
					0.0F,
					1.0F
				);
			}
		}
		return 0.0F;
	}

	/** Iris-compatible 16-step camera-cell block and sky light semantics. */
	private static int[] shaderPackEyeBrightness() {
		Minecraft minecraft = Minecraft.getInstance();
		Entity cameraEntity = minecraft.getCameraEntity();
		if (cameraEntity == null || minecraft.level == null) {
			return new int[] {0, 0};
		}
		Vec3 feet = cameraEntity.position();
		BlockPos eyeBlockPos = BlockPos.containing(feet.x, cameraEntity.getEyeY(), feet.z);
		return new int[] {
			minecraft.level.getBrightness(LightLayer.BLOCK, eyeBlockPos) * 16,
			minecraft.level.getBrightness(LightLayer.SKY, eyeBlockPos) * 16
		};
	}

	/**
	 * Copies only vanilla item-model identity. Rust resolves the selected
	 * shader pack's integer ID table; no Iris map, item renderer, or backend
	 * object crosses the semantic frame boundary.
	 */
	private static String shaderPackHeldItemModelResourceLocation(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		ResourceLocation itemModel = stack.get(DataComponents.ITEM_MODEL);
		if (itemModel == null) {
			itemModel = BuiltInRegistries.ITEM.getKey(stack.getItem());
		}
		return itemModel == null ? "" : itemModel.toString();
	}

	/**
	 * Copies one bounded vanilla gameplay scalar. Rust applies the selected
	 * pack's legacy main/off-hand composition rule, so Java never resolves a
	 * pack map or exposes an Iris renderer object across FFI.
	 */
	private static int shaderPackHeldItemLightEmission(ItemStack stack) {
		if (stack == null || stack.isEmpty() || Minecraft.getInstance().player == null) {
			return 0;
		}
		int emission = ((IrisItemLightProvider) stack.getItem()).getLightEmission(
			Minecraft.getInstance().player,
			stack
		);
		return Mth.clamp(emission, 0, 15);
	}

	/**
	 * Copies vanilla's camera-biome precipitation classification. Rust owns the
	 * shader-source smoothing and interpretation of this raw semantic.
	 */
	private static int shaderPackBiomePrecipitation(ClientLevel level, Camera camera) {
		if (camera == null) {
			return 0;
		}
		Biome.Precipitation precipitation = level.getBiome(camera.getBlockPosition()).value().getPrecipitationAt(
			camera.getBlockPosition(),
			level.getSeaLevel()
		);
		return switch (precipitation) {
			case NONE -> 0;
			case RAIN -> 1;
			case SNOW -> 2;
		};
	}

	/**
	 * Copies only the canonical camera-biome identity. Rust interprets selected
	 * shader-pack biome mappings and temporal behavior; no Iris registry object
	 * or integer mapping crosses the semantic boundary.
	 */
	private static String shaderPackBiomeResourceLocation(ClientLevel level, Camera camera) {
		if (camera == null) {
			return "";
		}
		return level.getBiome(camera.getBlockPosition())
			.unwrapKey()
			.map(key -> key.location().toString())
			.orElse("");
	}

	/**
	 * Evaluates GameRenderer's vanilla fog-color semantic before Iris merely
	 * captures it. The copied RGB value has no Iris renderer or GPU-state
	 * dependency.
	 */
	private static Vector3f shaderPackFogColor(ClientLevel level, Camera camera) {
		if (camera == null) {
			return new Vector3f();
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean worldFog = level.effects().isFoggyAt(
			camera.getBlockPosition().getX(),
			camera.getBlockPosition().getZ()
		) || minecraft.gui.getBossOverlay().shouldCreateWorldFog();
		Vector4f color = minecraft.gameRenderer.fogRenderer.computeFogColor(
			camera,
			shaderPackFramePartialTick,
			level,
			minecraft.options.getEffectiveRenderDistance(),
			minecraft.gameRenderer.getDarkenWorldAmount(shaderPackFramePartialTick),
			worldFog
		);
		return new Vector3f(color.x, color.y, color.z);
	}

	/**
	 * Copies the vanilla light-texture darkness calculation as a gameplay
	 * semantic. This deliberately does not read Iris's captured state: Iris
	 * records this exact value after the same calculation.
	 */
	private static float shaderPackDarknessLightFactor() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return 0.0F;
		}
		float effectScale = minecraft.options.darknessEffectScale().get().floatValue();
		float blend = minecraft.player.getEffectBlendFactor(MobEffects.DARKNESS, shaderPackFramePartialTick) * effectScale;
		float pulse = Math.max(0.0F, Mth.cos((minecraft.player.tickCount - shaderPackFramePartialTick) * (float)Math.PI * 0.025F) * 0.45F * blend);
		return pulse * effectScale;
	}

	/**
	 * Mirrors Iris's documented night-vision semantic from vanilla gameplay
	 * state. The camera entity is queried directly; no captured Iris tick or
	 * renderer state participates in the Rust-owned path.
	 */
	private static float shaderPackNightVision() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getCameraEntity() instanceof net.minecraft.world.entity.LivingEntity livingEntity) {
			float strength = GameRenderer.getNightVisionScale(livingEntity, shaderPackFramePartialTick);
			if (strength > 0.0F) {
				return Mth.clamp(strength, 0.0F, 1.0F);
			}
		}
		if (minecraft.player != null && minecraft.player.hasEffect(MobEffects.CONDUIT_POWER)) {
			return Mth.clamp(minecraft.player.getWaterVision(), 0.0F, 1.0F);
		}
		return 0.0F;
	}

	private static int shaderPackEyeSubmersion(Camera camera) {
		if (camera == null) {
			return 0;
		}
		return switch (camera.getFluidInCamera()) {
			case WATER -> 1;
			case LAVA -> 2;
			case POWDER_SNOW -> 3;
			default -> 0;
		};
	}

	private static Vec3 shaderPackRelativeEyePosition(Camera camera) {
		if (camera == null || camera.getEntity() == null) {
			return Vec3.ZERO;
		}
		return camera.getPosition().subtract(camera.getEntity().getEyePosition(shaderPackFramePartialTick));
	}

	/**
	 * Copies the semantic {@code worldTime} convention used by shader packs:
	 * the dimension's fixed time when one exists, otherwise the vanilla day
	 * cycle. This is game state, not an Iris uniform, program, or renderer
	 * dependency.
	 */
	private static long shaderPackWorldTime(ClientLevel level) {
		long sourceTime = DETERMINISTIC_TEMPORAL_PARITY
			? DETERMINISTIC_TEMPORAL_WORLD_TIME
			: level.getDayTime();
		return level.dimensionType().fixedTime().orElse(Math.floorMod(sourceTime, 24000L));
	}

	/**
	 * Keeps the angular day-cycle semantic coherent with {@link #shaderPackWorldTime}.
	 * Deterministic captures deliberately replace the source time, so sampling the
	 * live level clock here would otherwise feed the selected pack conflicting
	 * world-time and solar-angle inputs.
	 */
	private static float shaderPackTimeOfDay(ClientLevel level) {
		return clampUnit(level.dimensionType().timeOfDay(shaderPackWorldTime(level)));
	}

	private static int shaderPackWorldDay(ClientLevel level) {
		long sourceTime = DETERMINISTIC_TEMPORAL_PARITY
			? DETERMINISTIC_TEMPORAL_WORLD_TIME
			: level.getDayTime();
		return (int)Math.floorDiv(sourceTime, 24000L);
	}

	private static int shaderPackMoonPhase(ClientLevel level) {
		long sourceTime = DETERMINISTIC_TEMPORAL_PARITY
			? DETERMINISTIC_TEMPORAL_WORLD_TIME
			: level.getDayTime();
		return level.dimensionType().moonPhase(sourceTime);
	}

	private static float clampUnit(float value) {
		return Float.isFinite(value) ? Math.clamp(value, 0.0F, 1.0F) : 0.0F;
	}

	public static void reseedFrameMatrices(Matrix4f viewMatrix, Matrix4f projectionMatrix, int viewportWidth, int viewportHeight) {
		synchronized (LOCK) {
			seedFrameMatricesLocked(viewMatrix, projectionMatrix, viewportWidth, viewportHeight);
			pendingDiagnosticCameraOriginValid = false;
		}
	}

	private static void seedDiagnosticCameraOriginLocked(Camera camera) {
		pendingDiagnosticCameraOriginValid = false;
		if (camera == null) {
			return;
		}
		Vec3 position = camera.getPosition();
		if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)) {
			return;
		}
		pendingDiagnosticCameraX = position.x;
		pendingDiagnosticCameraY = position.y;
		pendingDiagnosticCameraZ = position.z;
		pendingDiagnosticCameraOriginValid = true;
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
				PENDING_TEXT_QUADS.clear();
				PENDING_MESH_INSTANCES.clear();
			PENDING_MESH_PRODUCERS.clear();
				PENDING_FIRST_PERSON_MESH_INSTANCES.clear();
				pendingFirstPersonFrame = false;
				pendingFirstPersonMainHandInstanceCount = 0;
				PENDING_MODEL_MESH_SEMANTICS.clear();
			pendingBackground = VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
			pendingVoxelVolumeFrame = VulkanicGalBridge.WorldVoxelVolumeFrameRecord.disabled();
			pendingShaderEnvironmentFrame = VulkanicGalBridge.WorldShaderEnvironmentFrameRecord.disabled();
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
			PENDING_TEXT_QUADS.clear();
			PENDING_MESH_INSTANCES.clear();
			PENDING_MESH_PRODUCERS.clear();
			PENDING_FIRST_PERSON_MESH_INSTANCES.clear();
			pendingFirstPersonFrame = false;
			pendingFirstPersonMainHandInstanceCount = 0;
			PENDING_MODEL_MESH_SEMANTICS.clear();
			pendingBackground = VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
			pendingVoxelVolumeFrame = VulkanicGalBridge.WorldVoxelVolumeFrameRecord.disabled();
			pendingShaderEnvironmentFrame = VulkanicGalBridge.WorldShaderEnvironmentFrameRecord.disabled();
			new Matrix4f().get(PENDING_VIEW);
			new Matrix4f().get(PENDING_PROJECTION);
			pendingViewportWidth = 0;
			pendingViewportHeight = 0;
			pendingDiagnosticCameraOriginValid = false;
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

	/**
	 * Captures the already-extracted vanilla sky state as one coarse semantic
	 * record for a future Rust-owned sky pass. This method never draws, changes
	 * Java's sky route, or exposes SkyRenderer/Iris/backend objects.
	 */
	public static void enqueueWorldSky(SkyRenderState state, boolean visible) {
		if (!WorldRenderRoutePolicy.currentBackgroundRoute().usesRustWholeFrameVulkan() || state == null) {
			return;
		}
		synchronized (LOCK) {
			if (!pendingBackground.enabled()) {
				return;
			}
			boolean skyVisible = visible && state.skyType != net.minecraft.client.renderer.DimensionSpecialEffects.SkyType.NONE;
			pendingBackground = pendingBackground.withSky(
				skyVisible,
				skyVisible && state.isSunriseOrSunset,
				skyVisible && state.shouldRenderDarkDisc,
				skyVisible ? state.sunAngle : 0.0F,
				skyVisible ? state.timeOfDay : 0.0F,
				skyVisible ? state.rainBrightness : 0.0F,
				skyVisible ? state.starBrightness : 0.0F,
				skyVisible ? state.sunriseAndSunsetColor : 0,
				skyVisible ? state.moonPhase : 0,
				skyVisible ? state.endFlashIntensity : 0.0F,
				skyVisible ? state.endFlashXAngle : 0.0F,
				skyVisible ? state.endFlashYAngle : 0.0F,
				skyVisible ? state.skyColor : 0
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
				viewportHeight,
				// The marker's copied PNG uses local UVs. The selected Rust source
				// writer binds that semantic material asset per compatible batch;
				// Java still supplies no atlas object or backend state.
				MATERIAL_SOURCE_TEXTURED,
				MATERIAL_SOURCE_UV_LOCAL_TEXTURE,
				colorArgb,
				LightTexture.FULL_BRIGHT
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

	/**
	 * Copies the final vanilla experience-orb billboard into the shared material
	 * family. Java contributes only transformed quad, sprite-cell UVs, color
	 * pulse, and packed light; Rust owns the texture, batching, pass, and draw.
	 */
	public static boolean enqueueExperienceOrb(
		PoseStack.Pose pose,
		ExperienceOrbRenderState state,
		float minU,
		float maxU,
		float minV,
		float maxV,
		int red,
		int blue
	) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentExperienceOrbRoute();
		if (!route.usesRustWholeFrameVulkan()) {
			return false;
		}
		if (pose == null || state == null || !Float.isFinite(minU) || !Float.isFinite(maxU)
			|| !Float.isFinite(minV) || !Float.isFinite(maxV)) {
			throw new IllegalArgumentException("Rust experience-orb route selected without finite copied billboard semantics");
		}
		GraphicsFrameBenchmark.beginPhase("world.experience-orb.java-extraction");
		try {
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) {
					throw new IllegalStateException("Rust VulkanicGAL ExperienceOrb requires a seeded world primitive frame");
				}
				float[] vertices = new float[12];
				transformMaterialVertex(pose.pose(), -0.5F, -0.25F, 0.0F, vertices, 0);
				transformMaterialVertex(pose.pose(), 0.5F, -0.25F, 0.0F, vertices, 3);
				transformMaterialVertex(pose.pose(), 0.5F, 0.75F, 0.0F, vertices, 6);
				transformMaterialVertex(pose.pose(), -0.5F, 0.75F, 0.0F, vertices, 9);
				int colorArgb = 0x80000000 | ((red & 0xff) << 16) | 0x0000ff00 | (blue & 0xff);
				PENDING_MATERIAL_QUADS.add(new VulkanicGalBridge.WorldMaterialQuadRecord(
					STRATUM_WORLD_MATERIAL,
					MATERIAL_ID_TRANSLUCENT_TEXTURED,
					MATERIAL_TEXTURE_EXPERIENCE_ORB,
					MATERIAL_MODE_TRANSLUCENT,
					DEPTH_POLICY_TEST_NO_WRITE,
					CULL_BACK,
					WORLD_TOPOLOGY_TRIANGLES,
					WORLD_WINDING_CCW,
					colorArgb,
					vertices[0], vertices[1], vertices[2],
					vertices[3], vertices[4], vertices[5],
					vertices[6], vertices[7], vertices[8],
					vertices[9], vertices[10], vertices[11],
					minU, maxV,
					maxU, maxV,
					maxU, minV,
					minU, minV,
					viewportWidth,
					viewportHeight,
					MATERIAL_SOURCE_TEXTURED,
					MATERIAL_SOURCE_UV_LOCAL_TEXTURE,
					colorArgb,
					state.lightCoords
				));
				ProjectedBounds projected = projectBounds(vertices, viewportWidth, viewportHeight);
				recordExperienceOrbDiagnostic(
					"rust-vulkan-whole-frame", colorArgb, state.lightCoords, minU, maxU, minV, maxV,
					viewportWidth, viewportHeight, projected
				);
				DeterministicCameraCapture.recordSubmittedWorkIdentity("experience-orb", "rust-vulkan-whole-frame:billboard");
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.experience-orb.java-extraction");
		}
		return true;
	}

	/**
	 * Copies the vanilla beacon beam's two material parts into the shared
	 * world-material request family. The producer has already resolved the
	 * animation, section height, colors, and model transforms; this method only
	 * expands that semantic data into copied quads. No Java render type, texture
	 * object, or backend state crosses the ABI.
	 */
	public static boolean enqueueBeaconBeam(
		ResourceLocation textureIdentity,
		Matrix4f solidTransform,
		Matrix4f glowTransform,
		float scroll,
		float beamScale,
		int startY,
		int endY,
		int colorArgb,
		float solidRadius,
		float glowRadius
	) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentBeaconBeamRoute();
		if (!route.usesRustWholeFrameVulkan()) {
			return false;
		}
		if (!BEACON_BEAM_TEXTURE_LOCATION.equals(textureIdentity)
			|| solidTransform == null
			|| glowTransform == null
			|| !Float.isFinite(scroll)
			|| !Float.isFinite(beamScale)
			|| !Float.isFinite(solidRadius)
			|| !Float.isFinite(glowRadius)
			|| solidRadius <= 0.0F
			|| glowRadius <= 0.0F
			|| endY < startY) {
			throw new IllegalArgumentException("Rust beacon-beam route selected without supported copied material semantics");
		}
		GraphicsFrameBenchmark.beginPhase("world.beacon-beam.java-extraction");
		try {
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) {
					throw new IllegalStateException("Rust VulkanicGAL BeaconBeam requires a seeded world primitive frame");
				}
				float solidMin = -solidRadius;
				float solidV0 = -1.0F + scroll;
				float solidV1 = (endY - startY) * beamScale * (0.5F / solidRadius) + solidV0;
				appendBeaconPartLocked(
					solidTransform, colorArgb, startY, endY,
					0.0F, solidRadius, solidRadius, 0.0F,
					solidMin, 0.0F, 0.0F, solidMin,
					0.0F, 1.0F, solidV1, solidV0,
					viewportWidth, viewportHeight
				);
				float glowMin = -glowRadius;
				float glowV0 = -1.0F + scroll;
				float glowV1 = (endY - startY) * beamScale + glowV0;
				appendBeaconPartLocked(
					glowTransform, ARGB.color(32, colorArgb), startY, endY,
					glowMin, glowMin, glowRadius, glowMin,
					glowMin, glowRadius, glowRadius, glowRadius,
					0.0F, 1.0F, glowV1, glowV0,
					viewportWidth, viewportHeight
				);
				float[] projectedVertices = new float[12];
				transformMaterialVertex(glowTransform, glowMin, endY, glowMin, projectedVertices, 0);
				transformMaterialVertex(glowTransform, glowMin, startY, glowMin, projectedVertices, 3);
				transformMaterialVertex(glowTransform, glowRadius, startY, glowRadius, projectedVertices, 6);
				transformMaterialVertex(glowTransform, glowRadius, endY, glowRadius, projectedVertices, 9);
				recordBeaconBeamDiagnostic(
					colorArgb, startY, endY, scroll, viewportWidth, viewportHeight,
					projectBounds(projectedVertices, viewportWidth, viewportHeight)
				);
				DeterministicCameraCapture.recordSubmittedWorkIdentity("beacon-beam", "rust-vulkan-whole-frame:translucent");
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.beacon-beam.java-extraction");
		}
		return true;
	}

	private static void appendBeaconPartLocked(
		Matrix4f transform,
		int colorArgb,
		int startY,
		int endY,
		float f,
		float g,
		float h,
		float l,
		float m,
		float n,
		float o,
		float p,
		float q,
		float r,
		float s,
		float t,
		int viewportWidth,
		int viewportHeight
	) {
		appendBeaconQuadLocked(transform, colorArgb, startY, endY, f, g, h, l, q, r, s, t, viewportWidth, viewportHeight);
		appendBeaconQuadLocked(transform, colorArgb, startY, endY, o, p, m, n, q, r, s, t, viewportWidth, viewportHeight);
		appendBeaconQuadLocked(transform, colorArgb, startY, endY, h, l, o, p, q, r, s, t, viewportWidth, viewportHeight);
		appendBeaconQuadLocked(transform, colorArgb, startY, endY, m, n, f, g, q, r, s, t, viewportWidth, viewportHeight);
	}

	private static void appendBeaconQuadLocked(
		Matrix4f transform,
		int colorArgb,
		int startY,
		int endY,
		float minX,
		float minZ,
		float maxX,
		float maxZ,
		float minU,
		float maxU,
		float minV,
		float maxV,
		int viewportWidth,
		int viewportHeight
	) {
		float[] vertices = new float[12];
		transformMaterialVertex(transform, minX, endY, minZ, vertices, 0);
		transformMaterialVertex(transform, minX, startY, minZ, vertices, 3);
		transformMaterialVertex(transform, maxX, startY, maxZ, vertices, 6);
		transformMaterialVertex(transform, maxX, endY, maxZ, vertices, 9);
		PENDING_MATERIAL_QUADS.add(new VulkanicGalBridge.WorldMaterialQuadRecord(
			STRATUM_WORLD_MATERIAL,
			MATERIAL_ID_TRANSLUCENT_TEXTURED,
			MATERIAL_TEXTURE_BEACON_BEAM,
			MATERIAL_MODE_TRANSLUCENT,
			DEPTH_POLICY_TEST_NO_WRITE,
			CULL_NONE,
			WORLD_TOPOLOGY_TRIANGLES,
			WORLD_WINDING_CCW,
			colorArgb,
			vertices[0], vertices[1], vertices[2],
			vertices[3], vertices[4], vertices[5],
			vertices[6], vertices[7], vertices[8],
			vertices[9], vertices[10], vertices[11],
			minU, minV,
			minU, maxV,
			maxU, maxV,
			maxU, minV,
			viewportWidth,
			viewportHeight,
			MATERIAL_SOURCE_TEXTURED,
			MATERIAL_SOURCE_UV_LOCAL_TEXTURE,
			colorArgb,
			LightTexture.FULL_BRIGHT
		));
	}

	private static void transformMaterialVertex(Matrix4f transform, float x, float y, float z, float[] output, int offset) {
		Vector3f transformed = transform.transformPosition(x, y, z, new Vector3f());
		output[offset] = transformed.x;
		output[offset + 1] = transformed.y;
		output[offset + 2] = transformed.z;
	}

	private static boolean isFiniteVec3(Vec3 value) {
		return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
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
			BlockPos tintPos = blockSubmit.tintPos() == null ? BlockPos.ZERO : blockSubmit.tintPos();
			BlockAndTintGetter tintGetter = Minecraft.getInstance().level == null
				? EmptyBlockAndTintGetter.INSTANCE
				: Minecraft.getInstance().level;
			extraction = extractBlockModelMesh(
				blockRenderDispatcher,
				blockState,
				tintPos,
				tintGetter,
				tintPos,
				material.materialId(),
				material.materialMode(),
				blockSubmit.lightCoords(),
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
				PENDING_MESH_PRODUCERS.add(PendingMeshProducer.BLOCK_DISPLAY);
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
				recordWorldMeshSubmittedWorkIdentity(
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

	/**
	 * Copies the bounded vanilla arrow model into the existing indexed-mesh
	 * asset family. The model and pose objects are consumed entirely on Java;
	 * Rust receives only copied vertex/index records, a local texture payload,
	 * and the normal per-frame transform/light instance semantics.
	 */
	public static boolean enqueueArrowModel(
		ModelPart modelRoot,
		ArrowRenderState arrowRenderState,
		PoseStack.Pose entityPose,
		ResourceLocation textureLocation,
		int packedLight
	) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentArrowRoute(
			isArrowMeshEligible(textureLocation, arrowRenderState)
		);
		if (!route.usesRustWholeFrameVulkan()) {
			return false;
		}
		if (modelRoot == null || arrowRenderState == null || entityPose == null
			|| !isArrowMeshEligible(textureLocation, arrowRenderState)) {
			throw new IllegalArgumentException("Rust Arrow route selected without eligible copied model semantics");
		}
		GraphicsFrameBenchmark.beginPhase("world.arrow.java-extraction");
		BlockMeshExtraction extraction;
		try {
				extraction = extractArrowModelMesh(
					modelRoot,
					textureLocation,
					BuiltInRegistries.ENTITY_TYPE.getKey(arrowRenderState.entityType).toString()
				);
		} finally {
			GraphicsFrameBenchmark.endPhase("world.arrow.java-extraction");
		}
		if (extraction == null) {
			throw new IllegalStateException("Rust Arrow route selected but copied ArrowModel extraction produced no mesh");
		}
		GraphicsFrameBenchmark.beginPhase("world.arrow.rust-enqueue");
		try {
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) {
					throw new IllegalStateException("Rust VulkanicGAL Arrow requires a seeded world primitive frame");
				}
				ensureMeshAssetLocked(extraction);
				VulkanicGalBridge.WorldMeshAssetRecord cachedAsset = WORLD_MESH_ASSETS.get(extraction.meshKey());
				long meshGeneration = cachedAsset == null ? extraction.meshGeneration() : cachedAsset.meshGeneration();
				float[] transform = new float[16];
				entityPose.pose().get(transform);
				PENDING_MESH_INSTANCES.add(new VulkanicGalBridge.WorldMeshInstanceRecord(
					STRATUM_WORLD_ENTITY_MESH,
					extraction.meshKey(),
					meshGeneration,
					MESH_SECTION_ALL,
					DEPTH_POLICY_TEST_WRITE,
					CULL_BACK,
					WORLD_WINDING_CCW,
					applyPackedLight(0xffffffff, packedLight),
					transform,
					viewportWidth,
					viewportHeight
				));
				PENDING_MESH_PRODUCERS.add(PendingMeshProducer.ARROW);
				recordArrowDiagnostic(
					"rust-vulkan-whole-frame",
					textureLocation,
					extraction.meshKey(),
					meshGeneration,
					extraction.asset(),
					packedLight,
					transform,
					viewportWidth,
					viewportHeight
				);
				recordWorldMeshSubmittedWorkIdentity(
					"arrow",
					"rust-vulkan-whole-frame:" + textureLocation
				);
				auditMessage("Rust VulkanicGAL Arrow mesh request"
					+ " mesh_key=" + extraction.meshKey()
					+ " mesh_generation=" + meshGeneration
					+ " texture=" + textureLocation
					+ " vertices=" + extraction.asset().vertices().size()
					+ " sections=" + extraction.asset().sections().size()
					+ " result=queued");
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.arrow.rust-enqueue");
		}
		return true;
	}

	/** Eligibility remains at the Java semantic boundary, before route selection. */
	public static boolean isArrowMeshEligible(ResourceLocation textureLocation, ArrowRenderState state) {
		if (textureLocation == null || !isVanillaArrowStateEligible(state)
			|| !"minecraft".equals(textureLocation.getNamespace())) {
			return false;
		}
		return switch (textureLocation.getPath()) {
			case "textures/entity/projectiles/arrow.png",
				"textures/entity/projectiles/tipped_arrow.png",
				"textures/entity/projectiles/spectral_arrow.png" -> true;
			default -> false;
		};
	}

	/** Shared eligibility for ArrowRenderer and the selected-source coverage pass. */
	public static boolean isVanillaArrowStateEligible(ArrowRenderState state) {
		return state != null
			&& (state.entityType == net.minecraft.world.entity.EntityType.ARROW
				|| state.entityType == net.minecraft.world.entity.EntityType.SPECTRAL_ARROW)
			&& state.outlineColor == 0
			&& !state.isInvisible
			&& !state.displayFireAnimation
			&& state.nameTag == null
			&& state.hitboxesRenderState == null
			&& (state.leashStates == null || state.leashStates.isEmpty())
			&& state.shadowPieces.isEmpty();
	}

	/**
	 * Checks whether a normal Java {@link Model} submit can be copied as the
	 * existing opaque/cutout indexed entity mesh. This is intentionally a narrow
	 * semantic boundary: translucent, outlined, crumbling, and unknown sprite
	 * states remain Java-owned before route selection.
	 */
	public static boolean isModelMeshEligible(
		Model<?> model,
		RenderType renderType,
		TextureAtlasSprite sprite,
		int overlayCoords,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		return modelMeshIneligibilityReason(model, renderType, sprite, overlayCoords, outlineColor, crumblingOverlay) == null;
	}

	/**
	 * Bounded semantic diagnostic for whole-frame source admission. It exposes
	 * only which Java-side eligibility predicate rejected a copied model; it
	 * does not alter routing or retain model, atlas, or renderer objects.
	 */
	public static String modelMeshIneligibilityReason(
		Model<?> model,
		RenderType renderType,
		TextureAtlasSprite sprite,
		int overlayCoords,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		if (model == null) return "model-null";
		if (!isSupportedModelMeshModel(model)) return "model-unsupported";
		if (renderType == null) return "render-type-null";
		if (modelMeshRenderSemantics(renderType) == null) return "render-semantics-unsupported";
		if (sprite == null) return "sprite-null";
		if (overlayCoords != OverlayTexture.NO_OVERLAY) return "overlay";
		if (outlineColor != 0) return "outline";
		if (crumblingOverlay != null) return "crumbling";
		if (renderType.pipeline().getBlendFunction().isPresent()) return "blend";
		if (sprite.contents().isAnimated()) return "sprite-animated";
		if (sprite.sodium$hasUnknownImageContents()) return "sprite-unknown-contents";
		if (sprite.contents().name() == null) return "sprite-identity-null";
		return null;
	}

	private static boolean isSupportedModelMeshModel(Model<?> model) {
		return model instanceof ChestModel
			|| model instanceof Model.Simple
			|| model instanceof BellModel
			|| model instanceof ChickenModel
			|| model instanceof CowModel
			|| model instanceof PigModel
			|| model instanceof RabbitModel
			|| model instanceof ZombieModel
			|| model instanceof ShulkerBoxRenderer.ShulkerBoxModel
			|| model instanceof LlamaSpitModel
			|| model instanceof EvokerFangsModel
			|| model instanceof SkullModel;
	}

	/**
	 * Eligibility for ordinary entity models whose Java submit carries a direct
	 * resource texture rather than a block-atlas sprite. The texture location is
	 * copied into Rust-owned assets; neither the RenderType nor its backing GPU
	 * state crosses the semantic boundary.
	 */
	public static boolean isStandaloneModelMeshEligible(
		Model<?> model,
		RenderType renderType,
		ResourceLocation textureIdentity,
		int overlayCoords,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		return model != null
			&& isSupportedModelMeshModel(model)
			&& modelMeshRenderSemantics(renderType) != null
			&& textureIdentity != null
			&& "minecraft".equals(textureIdentity.getNamespace())
			&& overlayCoords == OverlayTexture.NO_OVERLAY
			&& outlineColor == 0
			&& crumblingOverlay == null;
	}

	/**
	 * The first direct-texture living-model slice is deliberately narrow. Cow
	 * variants use the ordinary cutout-no-cull model path and provide all copied
	 * state required by the shared indexed-mesh frontend. Invisible, glowing, or
	 * overlay states retain their Java route before selection.
	 */
	public static boolean isVanillaCowModelMeshEligible(
		Model<?> model,
		CowRenderState state,
		RenderType renderType,
		ResourceLocation textureIdentity,
		int overlayCoords,
		int outlineColor,
		boolean bodyVisible,
		boolean translucentBody,
		boolean glowing
	) {
		return model instanceof CowModel
			&& state != null
			&& state.variant != null
			&& bodyVisible
			&& !translucentBody
			&& !glowing
			&& textureIdentity != null
			&& textureIdentity.getPath().startsWith("textures/entity/cow/")
			&& isStandaloneModelMeshEligible(model, renderType, textureIdentity, overlayCoords, outlineColor, null);
	}

	/**
	 * The first bird slice is deliberately limited to the adult temperate
	 * ChickenModel. Its animated ModelPart pose is copied as ordinary indexed
	 * mesh semantics; cold, warm, baby, feature-layer, overlay, and glowing
	 * states remain compatibility-owned before route selection.
	 */
	public static boolean isVanillaChickenModelMeshEligible(
		Model<?> model,
		ChickenRenderState state,
		RenderType renderType,
		ResourceLocation textureIdentity,
		int overlayCoords,
		int outlineColor,
		boolean bodyVisible,
		boolean translucentBody,
		boolean glowing
	) {
		return model != null
			&& model.getClass() == ChickenModel.class
			&& state != null
			&& state.variant != null
			&& !state.isBaby
			&& bodyVisible
			&& !translucentBody
			&& !glowing
			&& textureIdentity != null
			&& "minecraft".equals(textureIdentity.getNamespace())
			&& "textures/entity/chicken/temperate_chicken.png".equals(textureIdentity.getPath())
			&& isStandaloneModelMeshEligible(model, renderType, textureIdentity, overlayCoords, outlineColor, null);
	}

	/** Resolves the direct texture identity for an ordinary vanilla rabbit. */
	public static ResourceLocation vanillaRabbitTextureIdentity(RabbitRenderState state) {
		if (state == null || state.variant == null) {
			return null;
		}
		if (state.isToast) {
			return ResourceLocation.withDefaultNamespace("textures/entity/rabbit/toast.png");
		}
		return switch (state.variant) {
			case BROWN -> ResourceLocation.withDefaultNamespace("textures/entity/rabbit/brown.png");
			case WHITE -> ResourceLocation.withDefaultNamespace("textures/entity/rabbit/white.png");
			case BLACK -> ResourceLocation.withDefaultNamespace("textures/entity/rabbit/black.png");
			case GOLD -> ResourceLocation.withDefaultNamespace("textures/entity/rabbit/gold.png");
			case SALT -> ResourceLocation.withDefaultNamespace("textures/entity/rabbit/salt.png");
			case WHITE_SPLOTCHED -> ResourceLocation.withDefaultNamespace("textures/entity/rabbit/white_splotched.png");
			case EVIL -> ResourceLocation.withDefaultNamespace("textures/entity/rabbit/caerbannog.png");
		};
	}

	/** Adult vanilla rabbits use one direct-texture body model with no feature layer. */
	public static boolean isVanillaRabbitModelMeshEligible(
		Model<?> model,
		RabbitRenderState state,
		RenderType renderType,
		ResourceLocation textureIdentity,
		int overlayCoords,
		int outlineColor,
		boolean bodyVisible,
		boolean translucentBody,
		boolean glowing
	) {
		return model != null
			&& model.getClass() == RabbitModel.class
			&& state != null
			&& !state.isBaby
			&& bodyVisible
			&& !translucentBody
			&& !glowing
			&& Objects.equals(textureIdentity, vanillaRabbitTextureIdentity(state))
			&& isStandaloneModelMeshEligible(model, renderType, textureIdentity, overlayCoords, outlineColor, null);
	}

	/**
	 * Ordinary unsaddled vanilla pigs use the same direct-texture indexed-mesh
	 * contract as cows. Cold variants and saddle-layer work remain Java-owned
	 * until those distinct model or feature semantics are explicitly covered.
	 */
	public static boolean isVanillaPigModelMeshEligible(
		Model<?> model,
		PigRenderState state,
		RenderType renderType,
		ResourceLocation textureIdentity,
		int overlayCoords,
		int outlineColor,
		boolean bodyVisible,
		boolean translucentBody,
		boolean glowing
	) {
		return model != null
			&& model.getClass() == PigModel.class
			&& state != null
			&& state.variant != null
			&& state.saddle.isEmpty()
			&& bodyVisible
			&& !translucentBody
			&& !glowing
			&& textureIdentity != null
			&& "minecraft".equals(textureIdentity.getNamespace())
			&& ("textures/entity/pig/temperate_pig.png".equals(textureIdentity.getPath())
				|| "textures/entity/pig/warm_pig.png".equals(textureIdentity.getPath()))
			&& isStandaloneModelMeshEligible(model, renderType, textureIdentity, overlayCoords, outlineColor, null);
	}

	/**
	 * The first humanoid slice is the ordinary, unarmed adult zombie body. Its
	 * animated ModelPart hierarchy is already copied by the shared indexed-mesh
	 * extractor. Equipment, held items, overlays, movement-special states, and
	 * all variant models remain Java-owned before a route can be selected.
	 */
	public static boolean isVanillaZombieModelMeshEligible(
		Model<?> model,
		ZombieRenderState state,
		RenderType renderType,
		ResourceLocation textureIdentity,
		int overlayCoords,
		int outlineColor,
		boolean bodyVisible,
		boolean translucentBody,
		boolean glowing
	) {
		return model != null
			&& model.getClass() == ZombieModel.class
			&& state != null
			&& !state.isBaby
			&& !state.isInvisibleToPlayer
			&& !state.isPassenger
			&& !state.isUsingItem
			&& !state.isCrouching
			&& !state.isFallFlying
			&& !state.isVisuallySwimming
			&& !state.isInWater
			&& !state.isConverting
			&& !state.displayFireAnimation
			&& bodyVisible
			&& !translucentBody
			&& !glowing
			&& state.rightHandItem.isEmpty()
			&& state.leftHandItem.isEmpty()
			&& state.headItem.isEmpty()
			&& state.headEquipment.isEmpty()
			&& state.chestEquipment.isEmpty()
			&& state.legsEquipment.isEmpty()
			&& state.feetEquipment.isEmpty()
			&& ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png").equals(textureIdentity)
			&& isStandaloneModelMeshEligible(model, renderType, textureIdentity, overlayCoords, outlineColor, null);
	}

	/**
	 * Marks the real ItemEntity renderer's nested item submit. The scope lets
	 * the generic submit collector distinguish an ordinary dropped item from
	 * item models used by GUI, hands, frames, or block entities without passing
	 * an entity or renderer object through the semantic boundary.
	 */
	public static void beginItemEntitySubmission() {
		ITEM_ENTITY_SUBMISSION_DEPTH.set(ITEM_ENTITY_SUBMISSION_DEPTH.get() + 1);
	}

	public static void endItemEntitySubmission() {
		int depth = ITEM_ENTITY_SUBMISSION_DEPTH.get() - 1;
		if (depth <= 0) {
			ITEM_ENTITY_SUBMISSION_DEPTH.remove();
		} else {
			ITEM_ENTITY_SUBMISSION_DEPTH.set(depth);
		}
	}

	public static boolean isItemEntitySubmissionActive() {
		return ITEM_ENTITY_SUBMISSION_DEPTH.get() > 0;
	}

	/**
	 * Conservative pre-selection boundary for vanilla dropped-item quads. A
	 * special item renderer never reaches this method; foil, overlays, unknown
	 * FRAPI geometry, and unsupported texture payloads remain Java-owned.
	 */
	public static boolean isItemEntityMeshEligible(
		ItemDisplayContext displayContext,
		int packedLight,
		int overlayCoords,
		int outlineColor,
		int[] tintLayers,
		List<BakedQuad> quads,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	) {
		return itemEntityMeshIneligibility(
			displayContext, packedLight, overlayCoords, outlineColor, tintLayers, quads, renderType, foilType
		) == null;
	}

	/** Bounded diagnostic reason for a rejected dropped-item semantic request. */
	public static String itemEntityMeshIneligibility(
		ItemDisplayContext displayContext,
		int packedLight,
		int overlayCoords,
		int outlineColor,
		int[] tintLayers,
		List<BakedQuad> quads,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	) {
		if (displayContext != ItemDisplayContext.GROUND) return "display-context";
		if (overlayCoords != OverlayTexture.NO_OVERLAY) return "overlay";
		if (outlineColor != 0) return "outline";
		if (foilType != ItemStackRenderState.FoilType.NONE) return "foil";
		if (quads == null || quads.isEmpty()) return "empty-quads";
		if (quads.size() > 4096) return "quad-limit";
		if (renderType == null || modelMeshRenderSemantics(renderType) == null) return "render-type";
		for (BakedQuad bakedQuad : quads) {
			if (!(bakedQuad instanceof BakedQuadView quad)) {
				return "non-sodium-quad";
			}
			TextureAtlasSprite sprite = quad.getSprite();
			if (sprite == null || sprite.contents().name() == null) return "missing-sprite";
			if (sprite.contents().isAnimated()) return "animated-sprite";
			if (sprite.sodium$hasUnknownImageContents()) return "unknown-sprite-payload";
			if (readTexturePayload(sprite.contents().name()) == null) return "missing-texture-payload";
			if (bakedQuad.isTinted()
				&& (bakedQuad.tintIndex() < 0
					|| (tintLayers != null && tintLayers.length > 0 && bakedQuad.tintIndex() >= tintLayers.length))) {
				return "missing-tint";
			}
		}
		return null;
	}

	/**
	 * Copies ordinary ItemEntity baked quads through the shared indexed-mesh
	 * asset and instance ABI. The Java BakedQuad, RenderType, atlas wrapper, and
	 * item render state are consumed here and never cross FFI.
	 */
	public static boolean enqueueItemEntityMesh(
		PoseStack.Pose itemPose,
		ItemDisplayContext displayContext,
		int packedLight,
		int overlayCoords,
		int outlineColor,
		int[] tintLayers,
		List<BakedQuad> quads,
		RenderType renderType,
		ItemStackRenderState.FoilType foilType
	) {
		boolean eligible = isItemEntityMeshEligible(
			displayContext, packedLight, overlayCoords, outlineColor, tintLayers, quads, renderType, foilType
		);
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentItemEntityMeshRoute(eligible);
		if (!route.usesRustWholeFrameVulkan()) {
			return false;
		}
		if (itemPose == null) {
			throw new IllegalArgumentException("Rust item-entity route selected without an item transform");
		}
		ModelMeshRenderSemantics semantics = modelMeshRenderSemantics(renderType);
		if (semantics == null) {
			throw new IllegalArgumentException("Rust item-entity route selected without supported render semantics");
		}
		GraphicsFrameBenchmark.beginPhase("world.item-entity.java-extraction");
		BlockMeshExtraction extraction;
		try {
			extraction = extractItemQuadMesh(quads, tintLayers, packedLight, semantics, "minecraft:item_entity/ground");
		} finally {
			GraphicsFrameBenchmark.endPhase("world.item-entity.java-extraction");
		}
		if (extraction == null) {
			throw new IllegalStateException("Rust item-entity route selected but copied baked-quad extraction produced no mesh");
		}
		GraphicsFrameBenchmark.beginPhase("world.item-entity.rust-enqueue");
		try {
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) {
					throw new IllegalStateException("Rust VulkanicGAL item-entity mesh requires a seeded world primitive frame");
				}
				ensureMeshAssetLocked(extraction);
				VulkanicGalBridge.WorldMeshAssetRecord cachedAsset = WORLD_MESH_ASSETS.get(extraction.meshKey());
				long meshGeneration = cachedAsset == null ? extraction.meshGeneration() : cachedAsset.meshGeneration();
				float[] transform = new float[16];
				itemPose.pose().get(transform);
				PENDING_MESH_INSTANCES.add(new VulkanicGalBridge.WorldMeshInstanceRecord(
					STRATUM_WORLD_ENTITY_MESH,
					extraction.meshKey(),
					meshGeneration,
					MESH_SECTION_ALL,
					semantics.depthPolicy(),
					semantics.cullPolicy(),
					WORLD_WINDING_CCW,
					0xffffffff,
					transform,
					viewportWidth,
					viewportHeight
				));
				PENDING_MESH_PRODUCERS.add(PendingMeshProducer.ITEM_ENTITY);
				recordItemEntityDiagnostic(
					"rust-vulkan-whole-frame",
					itemEntityMaterialIdentity(quads),
					extraction.meshKey(),
					meshGeneration,
					extraction.asset(),
					packedLight,
					transform,
					viewportWidth,
					viewportHeight
				);
				recordWorldMeshSubmittedWorkIdentity("item-entity", "rust-vulkan-whole-frame:ground-baked-quad");
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.item-entity.rust-enqueue");
		}
		return true;
	}

	/**
	 * Seeds the copied first-person camera domain for one client frame. The
	 * matrices are semantic values produced by {@link GameRenderer}; neither a
	 * Java uniform buffer nor a backend object survives this boundary.
	 */
	public static void beginFirstPersonFrame(Matrix4f projectionMatrix, Matrix4f modelViewMatrix) {
		if (projectionMatrix == null || modelViewMatrix == null) {
			throw new IllegalArgumentException("Rust first-person frame requires projection and model-view matrices");
		}
		float[] projection = new float[16];
		float[] modelView = new float[16];
		projectionMatrix.get(projection);
		modelViewMatrix.get(modelView);
		if (!isFinite(projection) || !isFinite(modelView)) {
			throw new IllegalArgumentException("Rust first-person frame matrices must be finite");
		}
		synchronized (LOCK) {
			PENDING_FIRST_PERSON_MESH_INSTANCES.clear();
			pendingFirstPersonMainHandInstanceCount = 0;
			System.arraycopy(projection, 0, PENDING_FIRST_PERSON_PROJECTION, 0, projection.length);
			System.arraycopy(modelView, 0, PENDING_FIRST_PERSON_MODEL_VIEW, 0, modelView.length);
			pendingFirstPersonFrame = true;
		}
	}

	/**
	 * Copies a resolved ordinary first-person item-model layer into the shared
	 * indexed-mesh asset family. Unsupported hand states stay entirely on Java
	 * before the route decision; a selected Rust route either records all copied
	 * layers or reports a concrete failure rather than falling back after draw
	 * selection.
	 */
	public static boolean enqueueFirstPersonItemMesh(
		PoseStack.Pose outerPose,
		ItemStackRenderState itemState,
		ItemStack itemStack,
		int packedLight,
		boolean mainHand
	) {
		if (outerPose == null || itemState == null || itemStack == null) {
			return false;
		}
		String itemIdentity = shaderPackHeldItemModelResourceLocation(itemStack);
		String ineligibility = firstPersonItemMeshIneligibility(itemState, packedLight, itemIdentity);
		if (ineligibility != null
			|| !WorldRenderRoutePolicy.currentFirstPersonItemRoute(true).usesRustWholeFrameVulkan()) {
			return false;
		}
		List<ItemStackRenderState.SemanticLayer> layers = new ArrayList<>();
		itemState.forEachSemanticLayer(layers::add);
		List<FirstPersonMeshExtraction> extractions = new ArrayList<>(layers.size());
		GraphicsFrameBenchmark.beginPhase("world.first-person.java-extraction");
		try {
			for (ItemStackRenderState.SemanticLayer layer : layers) {
				ModelMeshRenderSemantics semantics = modelMeshRenderSemantics(layer.renderType());
				if (semantics == null) {
					throw new IllegalStateException("Rust first-person route selected without supported render semantics");
				}
				BlockMeshExtraction extraction = extractItemQuadMesh(
					layer.quads(), layer.tintLayers(), packedLight, semantics, itemIdentity
				);
				if (extraction == null) {
					throw new IllegalStateException("Rust first-person route selected but copied baked-quad extraction produced no mesh");
				}
				Matrix4f transform = new Matrix4f(outerPose.pose()).mul(new Matrix4f().set(layer.modelTransform()));
				extractions.add(new FirstPersonMeshExtraction(extraction, semantics, transform));
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.first-person.java-extraction");
		}
		GraphicsFrameBenchmark.beginPhase("world.first-person.rust-enqueue");
		try {
			synchronized (LOCK) {
				if (!pendingFirstPersonFrame) {
					throw new IllegalStateException("Rust first-person route selected without a seeded hand frame");
				}
				if (pendingViewportWidth <= 0 || pendingViewportHeight <= 0) {
					throw new IllegalStateException("Rust first-person route selected without a seeded world viewport");
				}
				int firstInstance = PENDING_FIRST_PERSON_MESH_INSTANCES.size();
				for (FirstPersonMeshExtraction prepared : extractions) {
					ensureMeshAssetLocked(prepared.extraction());
					VulkanicGalBridge.WorldMeshAssetRecord candidateAsset = WORLD_MESH_ASSETS.get(prepared.extraction().meshKey());
					long candidateGeneration = candidateAsset == null
						? prepared.extraction().meshGeneration()
						: candidateAsset.meshGeneration();
					if (!isWorldMeshGenerationAndTexturesUploadedLocked(prepared.extraction().meshKey(), candidateGeneration)) {
						// Resource residency is part of pre-selection eligibility. Java
						// retains this frame while Rust publishes the copied asset; once
						// resident, a selected Rust frame has no fallback or duplicate draw.
						return false;
					}
					VulkanicGalBridge.WorldMeshAssetRecord cachedAsset = WORLD_MESH_ASSETS.get(prepared.extraction().meshKey());
					long meshGeneration = cachedAsset == null ? prepared.extraction().meshGeneration() : cachedAsset.meshGeneration();
					float[] transform = new float[16];
					prepared.transform().get(transform);
					PENDING_FIRST_PERSON_MESH_INSTANCES.add(new VulkanicGalBridge.WorldMeshInstanceRecord(
						STRATUM_WORLD_ENTITY_MESH,
						prepared.extraction().meshKey(),
						meshGeneration,
						MESH_SECTION_ALL,
						prepared.semantics().depthPolicy(),
						prepared.semantics().cullPolicy(),
						WORLD_WINDING_CCW,
						0xffffffff,
						transform,
						pendingViewportWidth,
						pendingViewportHeight
					));
				}
				if (mainHand) {
					pendingFirstPersonMainHandInstanceCount += PENDING_FIRST_PERSON_MESH_INSTANCES.size() - firstInstance;
				}
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.first-person.rust-enqueue");
		}
		return true;
	}

	private static String firstPersonItemMeshIneligibility(
		ItemStackRenderState itemState,
		int packedLight,
		String itemIdentity
	) {
		if (!itemState.displayContext().firstPerson()) return "display-context";
		if (itemIdentity == null || itemIdentity.isBlank()) return "missing-item-identity";
		if (itemState.isAnimated()) return "animated-item";
		List<ItemStackRenderState.SemanticLayer> layers = new ArrayList<>();
		itemState.forEachSemanticLayer(layers::add);
		if (layers.isEmpty() || layers.size() > 64) return "layer-count";
		for (ItemStackRenderState.SemanticLayer layer : layers) {
			if (layer.hasSpecialRenderer()) return "special-renderer";
			if (layer.foilType() != ItemStackRenderState.FoilType.NONE) return "foil";
			if (layer.renderType() == null || layer.quads().isEmpty() || layer.quads().size() > 4096) return "quad-count-or-render-type";
			if (modelMeshRenderSemantics(layer.renderType()) == null) return "render-type";
			if (!isFinite(layer.modelTransform())) return "transform";
			for (BakedQuad quad : layer.quads()) {
				if (!(quad instanceof BakedQuadView view)) return "non-sodium-quad";
				TextureAtlasSprite sprite = view.getSprite();
				if (sprite == null || sprite.contents().name() == null) return "missing-sprite";
				if (sprite.contents().isAnimated()) return "animated-sprite";
				if (sprite.sodium$hasUnknownImageContents()) return "unknown-sprite-payload";
				if (readTexturePayload(sprite.contents().name()) == null) return "missing-texture-payload";
				if (quad.isTinted() && (quad.tintIndex() < 0 || quad.tintIndex() >= layer.tintLayers().length)) return "missing-tint";
			}
		}
		return null;
	}

	private static String itemEntityMaterialIdentity(List<BakedQuad> quads) {
		if (quads == null || quads.isEmpty() || !(quads.getFirst() instanceof BakedQuadView quad)
			|| quad.getSprite() == null || quad.getSprite().contents().name() == null) {
			return "missing";
		}
		return quad.getSprite().contents().name().toString();
	}

	/**
	 * Eligibility for copied {@link ModelPart} submissions. The selected slice
	 * is intentionally atlas-backed opaque/cutout work only: foil, sheeted,
	 * outlined, crumbling, animated, unknown, and blended Java semantics stay
	 * on their compatibility owner before a Rust route is selected.
	 */
	public static boolean isModelPartMeshEligible(
		ModelPart modelPart,
		RenderType renderType,
		TextureAtlasSprite sprite,
		int overlayCoords,
		boolean sheeted,
		boolean hasFoil,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		return "eligible".equals(modelPartMeshEligibilityReason(
			modelPart, renderType, sprite, overlayCoords, sheeted, hasFoil, outlineColor, crumblingOverlay
		));
	}

	/**
	 * Bounded Java-only classification for real {@code submitModelPart} calls.
	 * It makes a rejected source producer distinguishable from a producer that
	 * was never traversed; it is not a backend or route-policy input.
	 */
	public static String modelPartMeshEligibilityReason(
		ModelPart modelPart,
		RenderType renderType,
		TextureAtlasSprite sprite,
		int overlayCoords,
		boolean sheeted,
		boolean hasFoil,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		if (modelPart == null) return "missing-model-part";
		if (renderType == null) return "missing-render-type";
		if (renderType.pipeline().getBlendFunction().isPresent()) return "blended-render-type";
		if (modelMeshRenderSemantics(renderType) == null) return "unsupported-render-semantics";
		if (sprite == null) return "missing-sprite";
		if (sheeted) return "sheeted";
		if (hasFoil) return "foil";
		if (overlayCoords != OverlayTexture.NO_OVERLAY) return "overlay";
		// Vanilla's ordinary ModelPart submit uses -1 as the no-outline sentinel;
		// zero is also emitted by existing semantic callers. Positive values carry
		// an actual outline color and remain on the Java compatibility route.
		if (outlineColor > 0) return "outline";
		if (crumblingOverlay != null) return "crumbling";
		if (sprite.contents().isAnimated()) return "animated-sprite";
		if (sprite.sodium$hasUnknownImageContents()) return "unknown-sprite-image";
		if (sprite.contents().name() == null) return "missing-sprite-identity";
		return "eligible";
	}

	/**
	 * Copies an eligible Java {@code ModelPart} through the existing indexed
	 * entity-mesh family. Java owns the transient part, RenderType, and atlas
	 * wrapper only long enough to extract immutable semantic geometry and a
	 * resource location; Rust owns the resulting assets and draw grouping.
	 */
	public static boolean enqueueModelPartMesh(
		ModelPart modelPart,
		PoseStack.Pose entityPose,
		RenderType renderType,
		TextureAtlasSprite sprite,
		int packedLight,
		int overlayCoords,
		boolean sheeted,
		boolean hasFoil,
		int tintedColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		int outlineColor
	) {
		boolean eligible = isModelPartMeshEligible(
			modelPart, renderType, sprite, overlayCoords, sheeted, hasFoil, outlineColor, crumblingOverlay
		);
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentModelPartMeshRoute(eligible);
		if (!route.usesRustWholeFrameVulkan()) {
			return false;
		}
		if (!eligible || entityPose == null) {
			throw new IllegalArgumentException("Rust ModelPart route selected without eligible copied semantic inputs");
		}
		ModelMeshRenderSemantics semantics = modelMeshRenderSemantics(renderType);
		if (semantics == null) {
			throw new IllegalArgumentException("Rust ModelPart route selected without material semantics");
		}
		ResourceLocation textureIdentity = sprite.contents().name();
		GraphicsFrameBenchmark.beginPhase("world.model-part.java-extraction");
		BlockMeshExtraction extraction;
		try {
			extraction = extractModelPartMesh(
				modelPart,
				textureIdentity,
				modelPartEntityIdentity(textureIdentity),
				packedLight,
				semantics.materialId(),
				semantics.materialMode(),
				semantics.cullPolicy()
			);
		} finally {
			GraphicsFrameBenchmark.endPhase("world.model-part.java-extraction");
		}
		if (extraction == null) {
			throw new IllegalStateException("Rust ModelPart route selected but copied extraction produced no mesh");
		}
		GraphicsFrameBenchmark.beginPhase("world.model-part.rust-enqueue");
		try {
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) {
					throw new IllegalStateException("Rust VulkanicGAL ModelPart requires a seeded world primitive frame");
				}
				ensureMeshAssetLocked(extraction);
				VulkanicGalBridge.WorldMeshAssetRecord cachedAsset = WORLD_MESH_ASSETS.get(extraction.meshKey());
				long meshGeneration = cachedAsset == null ? extraction.meshGeneration() : cachedAsset.meshGeneration();
				float[] transform = new float[16];
				entityPose.pose().get(transform);
				PENDING_MESH_INSTANCES.add(new VulkanicGalBridge.WorldMeshInstanceRecord(
					STRATUM_WORLD_ENTITY_MESH,
					extraction.meshKey(),
					meshGeneration,
					MESH_SECTION_ALL,
					semantics.depthPolicy(),
					semantics.cullPolicy(),
					WORLD_WINDING_CCW,
					resolvedModelInstanceColor(tintedColor),
					transform,
					viewportWidth,
					viewportHeight
				));
				PENDING_MESH_PRODUCERS.add(PendingMeshProducer.MODEL_PART);
				recordModelMeshDiagnostic(
					textureIdentity,
					-1,
					extraction.meshKey(),
					meshGeneration,
					extraction.asset(),
					transform,
					viewportWidth,
					viewportHeight
				);
				recordWorldMeshSubmittedWorkIdentity(
					"model-part",
					"rust-vulkan-whole-frame:" + textureIdentity
				);
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.model-part.rust-enqueue");
		}
		return true;
	}

	/**
	 * {@link net.minecraft.client.renderer.OrderedSubmitNodeCollector}'s ordinary
	 * ModelPart overloads use zero as their explicit no-tint value. The copied
	 * mesh already owns opaque-white vertex colors, so preserve that semantic as
	 * a neutral instance modulation instead of forwarding transparent black.
	 */
	private static int resolvedModelInstanceColor(int tintedColor) {
		return tintedColor == 0 ? 0xffffffff : tintedColor;
	}

	/**
	 * The shared mesh ABI uses canonical resource-location text for entity-model
	 * identity. Preserve the source texture namespace inside the path instead of
	 * embedding a second colon-delimited location in it.
	 */
	private static String modelPartEntityIdentity(ResourceLocation textureIdentity) {
		return textureIdentity.getNamespace()
			+ ":model_part/"
			+ textureIdentity.getNamespace()
			+ "/"
			+ textureIdentity.getPath();
	}

	/**
	 * Resolves the bounded semantic material state that the normal Java model
	 * submit selected. The transient RenderType itself never leaves Java; the
	 * existing mesh ABI receives only material mode and cull policy.
	 */
	private static ModelMeshRenderSemantics modelMeshRenderSemantics(RenderType renderType) {
		if (renderType == null) {
			return null;
		}
		var blend = renderType.pipeline().getBlendFunction();
		if (blend.isPresent()) {
			if (!BlendFunction.TRANSLUCENT.equals(blend.get())) {
				return null;
			}
			return new ModelMeshRenderSemantics(
				MATERIAL_ID_TRANSLUCENT_TEXTURED,
				MATERIAL_MODE_TRANSLUCENT,
				DEPTH_POLICY_TEST_NO_WRITE,
				renderType.pipeline().isCull() ? CULL_BACK : CULL_NONE
			);
		}
		return new ModelMeshRenderSemantics(
			MATERIAL_ID_CUTOUT_TEXTURED,
			MATERIAL_MODE_CUTOUT,
			DEPTH_POLICY_TEST_WRITE,
			renderType.pipeline().isCull() ? CULL_BACK : CULL_NONE
		);
	}

	/**
	 * Copies one ordinary Java {@code ModelPart} submission into the shared
	 * indexed entity-mesh family. The transient model, state, sprite wrapper,
	 * and render type stop at this Java boundary; Rust receives only immutable
	 * geometry, a resource-location texture identity/payload, and an instance.
	 */
	public static <S> boolean enqueueModelMesh(
		Model<? super S> model,
		S state,
		PoseStack.Pose entityPose,
		RenderType renderType,
		TextureAtlasSprite sprite,
		int packedLight,
		int overlayCoords,
		int tintedColor
	) {
		if (!WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
			return false;
		}
		ModelMeshRenderSemantics semantics = modelMeshRenderSemantics(renderType);
		if (model == null || !isSupportedModelMeshModel(model) || entityPose == null || sprite == null
			|| overlayCoords != OverlayTexture.NO_OVERLAY || semantics == null) {
			throw new IllegalArgumentException("Rust model mesh route selected without eligible copied model semantics");
		}
		return enqueueEligibleModelMesh(
			model,
			state,
			entityPose,
			sprite.contents().name(),
			genericModelIdentity(model),
			packedLight,
			tintedColor,
			semantics
		);
	}

	/**
	 * Resolves the vanilla entity registry name while the render state is still
	 * Java semantic data. The returned value is copied into the mesh asset for
	 * Rust-owned shader-pack entity-ID resolution; it is not an Iris or backend
	 * object.
	 */
	public static ResourceLocation entityIdentity(EntityRenderState state) {
		if (state == null || state.entityType == null) {
			return null;
		}
		return BuiltInRegistries.ENTITY_TYPE.getKey(state.entityType);
	}

	/**
	 * Copies an eligible direct-texture entity model through the same indexed
	 * mesh family as atlas-backed ModelPart submissions. This method is only a
	 * Java semantic extraction boundary: the supplied texture identity is read
	 * into a Rust-owned asset before the coarse frame submit.
	 */
	public static <S> boolean enqueueStandaloneModelMesh(
		Model<? super S> model,
		S state,
		PoseStack.Pose entityPose,
		RenderType renderType,
		ResourceLocation textureIdentity,
		ResourceLocation entityIdentity,
		int packedLight,
		int overlayCoords,
		int tintedColor
	) {
		if (!WorldRenderRoutePolicy.currentModelMeshRoute(true).usesRustWholeFrameVulkan()) {
			return false;
		}
		ModelMeshRenderSemantics semantics = modelMeshRenderSemantics(renderType);
		if (!isStandaloneModelMeshEligible(model, renderType, textureIdentity, overlayCoords, 0, null)
			|| entityIdentity == null
			|| entityPose == null || semantics == null) {
			throw new IllegalArgumentException("Rust standalone model mesh route selected without eligible copied model semantics");
		}
		return enqueueEligibleModelMesh(
			model,
			state,
			entityPose,
			textureIdentity,
			entityIdentity.toString(),
			packedLight,
			tintedColor,
			semantics
		);
	}

	private static <S> boolean enqueueEligibleModelMesh(
		Model<? super S> model,
		S state,
		PoseStack.Pose entityPose,
		ResourceLocation textureIdentity,
		String entityIdentity,
		int packedLight,
		int tintedColor,
		ModelMeshRenderSemantics semantics
	) {
		GraphicsFrameBenchmark.beginPhase("world.model.java-extraction");
		BlockMeshExtraction extraction;
		try {
			model.setupAnim(state);
				extraction = extractModelPartMesh(
					model.root(),
					textureIdentity,
					entityIdentity,
					packedLight,
					semantics.materialId(),
					semantics.materialMode(),
					semantics.cullPolicy()
				);
		} finally {
			GraphicsFrameBenchmark.endPhase("world.model.java-extraction");
		}
		if (extraction == null) {
			throw new IllegalStateException("Rust model mesh route selected but copied ModelPart extraction produced no mesh");
		}
		GraphicsFrameBenchmark.beginPhase("world.model.rust-enqueue");
		try {
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) {
					throw new IllegalStateException("Rust VulkanicGAL model mesh requires a seeded world primitive frame");
				}
				ensureMeshAssetLocked(extraction);
				VulkanicGalBridge.WorldMeshAssetRecord cachedAsset = WORLD_MESH_ASSETS.get(extraction.meshKey());
				long meshGeneration = cachedAsset == null ? extraction.meshGeneration() : cachedAsset.meshGeneration();
				float[] transform = new float[16];
				entityPose.pose().get(transform);
				PENDING_MESH_INSTANCES.add(new VulkanicGalBridge.WorldMeshInstanceRecord(
					STRATUM_WORLD_ENTITY_MESH,
					extraction.meshKey(),
					meshGeneration,
					MESH_SECTION_ALL,
					semantics.depthPolicy(),
					semantics.cullPolicy(),
					WORLD_WINDING_CCW,
					resolvedModelInstanceColor(tintedColor),
					transform,
					viewportWidth,
					viewportHeight
				));
				PENDING_MESH_PRODUCERS.add(PendingMeshProducer.MODEL);
				PENDING_MODEL_MESH_SEMANTICS.add(new ModelMeshSemanticIdentity(
					model.getClass().getName(), textureIdentity
				));
				recordModelMeshDiagnostic(
					textureIdentity,
					state instanceof EntityRenderState entityRenderState ? entityRenderState.entityId : -1,
					extraction.meshKey(),
					meshGeneration,
					extraction.asset(),
					transform,
					viewportWidth,
					viewportHeight
				);
				recordWorldMeshSubmittedWorkIdentity(
					"model",
					"rust-vulkan-whole-frame:" + textureIdentity
				);
				auditMessage("Rust VulkanicGAL model mesh request"
					+ " mesh_key=" + extraction.meshKey()
					+ " mesh_generation=" + meshGeneration
					+ " texture=" + textureIdentity
					+ " vertices=" + extraction.asset().vertices().size()
					+ " sections=" + extraction.asset().sections().size()
					+ " result=queued");
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.model.rust-enqueue");
		}
		return true;
	}

	private static String genericModelIdentity(Model<?> model) {
		return "minecraft:generic_model/" + Long.toUnsignedString(fnv64(model.getClass().getName()), 16);
	}

	/**
	 * Routes only the explicitly marked ordinary primed-TNT block submit through
	 * the shared indexed baked-mesh family. Flashing overlay and outline states
	 * are intentionally never represented here: their Java producer does not
	 * mark them as {@code PRIMED_TNT}, so they remain compatibility-owned before
	 * a Rust route could be selected.
	 */
	public static boolean enqueuePrimedTntBlock(
		BlockRenderDispatcher blockRenderDispatcher,
		SubmitNodeStorage.BlockSubmit blockSubmit
	) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentPrimedTntRoute();
		if (!route.usesRustOpenGl() && !route.usesRustWholeFrameVulkan()) {
			return false;
		}
		if (!isPrimedTntMeshEligible(blockSubmit)) {
			throw new IllegalArgumentException("Primed TNT reached the Rust mesh route without eligible semantic inputs");
		}
		BlockState blockState = blockSubmit.state();
		MeshMaterial material = meshMaterialForChunkLayer(ItemBlockRenderTypes.getChunkRenderType(blockState));
		GraphicsFrameBenchmark.beginPhase("world.primed-tnt.java-extraction");
		BlockMeshExtraction extraction;
		try {
			BlockPos tintPos = BlockPos.ZERO;
			BlockAndTintGetter tintGetter = Minecraft.getInstance().level == null
				? EmptyBlockAndTintGetter.INSTANCE
				: Minecraft.getInstance().level;
			extraction = extractBlockModelMesh(
				blockRenderDispatcher,
				blockState,
				tintPos,
				tintGetter,
				tintPos,
				material.materialId(),
				material.materialMode(),
				blockSubmit.lightCoords(),
				"PrimedTnt"
			);
		} finally {
			GraphicsFrameBenchmark.endPhase("world.primed-tnt.java-extraction");
		}
		if (extraction == null) {
			throw new IllegalStateException("Rust VulkanicGAL Primed TNT extraction failed after Rust route selection");
		}
		GraphicsFrameBenchmark.beginPhase("world.primed-tnt.rust-enqueue");
		try {
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) {
					throw new IllegalStateException("Rust VulkanicGAL PrimedTnt requires a seeded world primitive frame");
				}
				ensureMeshAssetLocked(extraction);
				VulkanicGalBridge.WorldMeshAssetRecord cachedAsset = WORLD_MESH_ASSETS.get(extraction.meshKey());
				long meshGeneration = cachedAsset == null ? extraction.meshGeneration() : cachedAsset.meshGeneration();
				float[] transform = new float[16];
				blockSubmit.pose().pose().get(transform);
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
				PENDING_MESH_PRODUCERS.add(PendingMeshProducer.PRIMED_TNT);
				recordMovingBlockDiagnostic(
					route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl",
					"primed-tnt",
					blockState,
					extraction.meshKey(),
					meshGeneration,
					cachedAsset == null ? extraction.asset() : cachedAsset,
					transform,
					viewportWidth,
					viewportHeight
				);
				recordWorldMeshSubmittedWorkIdentity(
					"primed-tnt",
					(route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame:" : "rust-opengl:")
						+ blockState.getBlockHolder().getRegisteredName()
				);
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world.primed-tnt.rust-enqueue");
		}
		return true;
	}

	/**
	 * Eligibility is evaluated before route selection. The producer therefore
	 * leaves flashing, outlined, translucent, special-renderer, and non-model
	 * TNT entirely on Java rather than attempting a same-frame fallback after a
	 * Rust route has been chosen.
	 */
	public static boolean isPrimedTntMeshEligible(SubmitNodeStorage.BlockSubmit blockSubmit) {
		if (blockSubmit == null
			|| blockSubmit.source() != SubmitNodeStorage.BlockSubmitSource.PRIMED_TNT
			|| blockSubmit.overlayCoords() != 0
			|| blockSubmit.outlineColor() != 0) {
			return false;
		}
		BlockState blockState = blockSubmit.state();
		return blockState != null
			&& !blockState.isAir()
			&& blockState.getRenderShape() == RenderShape.MODEL
			&& !Minecraft.getInstance().getModelManager().specialBlockModelRenderer().get().hasRenderer(blockState.getBlock())
			&& meshMaterialForChunkLayer(ItemBlockRenderTypes.getChunkRenderType(blockState)) != null;
	}

	public static boolean enqueueFallingBlock(
		BlockRenderDispatcher blockRenderDispatcher,
		SubmitNodeStorage.MovingBlockSubmit movingBlockSubmit
	) {
		return enqueueMovingBlockMesh(
			blockRenderDispatcher,
			movingBlockSubmit,
			SubmitNodeStorage.MovingBlockSubmitSource.FALLING_BLOCK,
			WorldRenderRoutePolicy.currentFallingBlockRoute(),
			"falling-block",
			"FallingBlock"
		);
	}

	public static boolean enqueuePistonMovingBlock(
		BlockRenderDispatcher blockRenderDispatcher,
		SubmitNodeStorage.MovingBlockSubmit movingBlockSubmit
	) {
		return enqueueMovingBlockMesh(
			blockRenderDispatcher,
			movingBlockSubmit,
			SubmitNodeStorage.MovingBlockSubmitSource.PISTON,
			WorldRenderRoutePolicy.currentPistonMovingBlockRoute(),
			"piston",
			"PistonMovingBlock"
		);
	}

	private static boolean enqueueMovingBlockMesh(
		BlockRenderDispatcher blockRenderDispatcher,
		SubmitNodeStorage.MovingBlockSubmit movingBlockSubmit,
		SubmitNodeStorage.MovingBlockSubmitSource expectedSource,
		WorldRenderRoutePolicy.Route route,
		String family,
		String extractionLabel
	) {
		if (!route.usesRustOpenGl() && !route.usesRustWholeFrameVulkan()) {
			return false;
		}
		if (movingBlockSubmit.source() != expectedSource) {
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
		GraphicsFrameBenchmark.beginPhase("world." + family + ".java-extraction");
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
				LevelRenderer.getLightColor(
					LevelRenderer.BrightnessGetter.DEFAULT,
					movingBlockRenderState,
					blockState,
					movingBlockRenderState.blockPos
				),
				extractionLabel
			);
		} finally {
			GraphicsFrameBenchmark.endPhase("world." + family + ".java-extraction");
		}
		if (extraction == null) {
			return false;
		}
		GraphicsFrameBenchmark.beginPhase("world." + family + ".rust-enqueue");
		try {
			synchronized (LOCK) {
				int viewportWidth = pendingViewportWidth;
				int viewportHeight = pendingViewportHeight;
				if (viewportWidth <= 0 || viewportHeight <= 0) {
					throw new IllegalStateException("Rust VulkanicGAL " + extractionLabel + " requires a seeded world primitive frame");
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
					movingBlockLightColor(movingBlockRenderState, blockState, movingBlockRenderState.blockPos),
					transform,
					viewportWidth,
					viewportHeight
				));
				if (expectedSource == SubmitNodeStorage.MovingBlockSubmitSource.FALLING_BLOCK) {
					PENDING_MESH_PRODUCERS.add(PendingMeshProducer.FALLING_BLOCK);
				} else if (expectedSource == SubmitNodeStorage.MovingBlockSubmitSource.PISTON) {
					PENDING_MESH_PRODUCERS.add(PendingMeshProducer.PISTON);
				} else {
						PENDING_MESH_PRODUCERS.add(PendingMeshProducer.UNKNOWN);
					}
				recordMovingBlockDiagnostic(
					route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl",
					expectedSource.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'),
					blockState,
					extraction.meshKey(),
					meshGeneration,
					extraction.asset(),
					transform,
					viewportWidth,
					viewportHeight
				);
				recordWorldMeshSubmittedWorkIdentity(
					family,
					(route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame:" : "rust-opengl:") + blockState.getBlockHolder().getRegisteredName()
				);
				if (expectedSource == SubmitNodeStorage.MovingBlockSubmitSource.FALLING_BLOCK && fallingBlockEnqueueDiagnosticLogs < 24) {
					fallingBlockEnqueueDiagnosticLogs++;
				} else if (expectedSource == SubmitNodeStorage.MovingBlockSubmitSource.PISTON && movingBlockEnqueueDiagnosticLogs < 24) {
					movingBlockEnqueueDiagnosticLogs++;
				} else {
					return true;
				}
				auditMessage("Rust VulkanicGAL " + extractionLabel + " mesh request"
						+ " route=" + (route.usesRustWholeFrameVulkan() ? "rust-vulkan-whole-frame" : "rust-opengl")
						+ " provenance=" + expectedSource.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')
						+ " mesh_key=" + extraction.meshKey()
						+ " mesh_generation=" + meshGeneration
						+ " block=" + metricValue(blockState.getBlockHolder().getRegisteredName())
						+ " vertices=" + extraction.asset().vertices().size()
						+ " index_bytes=" + extraction.asset().indexBytes().length
						+ " sections=" + extraction.asset().sections().size()
						+ " viewport=" + viewportWidth + "x" + viewportHeight
						+ " result=queued");
			}
		} finally {
			GraphicsFrameBenchmark.endPhase("world." + family + ".rust-enqueue");
		}
		return true;
	}

	private static void ensureMeshAssetLocked(BlockMeshExtraction extraction) {
		if (WORLD_MESH_ASSETS.containsKey(extraction.meshKey())) {
			return;
		}
		WORLD_MESH_ASSETS.put(extraction.meshKey(), extraction.asset());
		DIRTY_WORLD_MESH_ASSETS.add(extraction.meshKey());
		for (VulkanicGalBridge.WorldMeshTextureAssetRecord texture : extraction.textures()) {
			if (!WORLD_MESH_TEXTURES.containsKey(texture.textureId())) {
				WORLD_MESH_TEXTURES.put(texture.textureId(), texture);
				DIRTY_WORLD_MESH_TEXTURES.add(texture.textureId());
			}
		}
		markWorldMeshAssetsChangedLocked();
	}

	public static void registerStaticTerrainMeshAsset(
		VulkanicGalBridge.WorldMeshAssetRecord asset,
		List<VulkanicGalBridge.WorldMeshTextureAssetRecord> textures
	) {
		if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()) {
			return;
		}
		synchronized (LOCK) {
			boolean changed = false;
			for (VulkanicGalBridge.WorldMeshTextureAssetRecord texture : textures) {
				WORLD_MESH_TEXTURES.put(texture.textureId(), texture);
				DIRTY_WORLD_MESH_TEXTURES.add(texture.textureId());
				changed = true;
			}
			VulkanicGalBridge.WorldMeshAssetRecord previous = WORLD_MESH_ASSETS.get(asset.meshKey());
			if (previous != null && previous.meshGeneration() == asset.meshGeneration()) {
				if (changed) {
					markWorldMeshAssetsChangedLocked();
				}
				return;
				}
				WORLD_MESH_ASSETS.put(asset.meshKey(), asset);
				WORLD_MESH_SORTED_INDICES.remove(asset.meshKey());
				DIRTY_WORLD_MESH_SORTED_INDICES.remove(asset.meshKey());
				DIRTY_WORLD_MESH_ASSETS.add(asset.meshKey());
			markWorldMeshAssetsChangedLocked();
			String sourceSemantics = selectedSourceDiagnosticsEnabled()
				? " source_semantics=" + staticTerrainSourceSemanticSummary(asset)
				: "";
			auditMessage(
				"Rust VulkanicGAL static terrain mesh asset registered"
					+ " mesh_key=" + asset.meshKey()
					+ " mesh_generation=" + asset.meshGeneration()
					+ " vertices=" + asset.vertices().size()
					+ " index_bytes=" + asset.indexBytes().length
					+ " sections=" + asset.sections().size()
					+ " textures=" + textures.size()
					+ sourceSemantics
					+ " route=rust-vulkan-whole-frame"
			);
		}
	}

	/**
	 * Whether this frame must prove that selected-source execution owns every
	 * visible semantic family. Normal whole-frame execution deliberately avoids
	 * walking unported Java producers solely to build this diagnostic inventory.
	 */
	public static boolean requiresSelectedSourceFeatureCoverage() {
		if (!System.getProperty(
			"mattmc.dev.deterministicCameraCapture.requiredRustSourceExecutionDir", ""
		).trim().isEmpty()) {
			return DeterministicCameraCapture.isSelectedSourceCoverageReady();
		}
		String value = System.getenv("MATTMC_RUST_SELECTED_SOURCE_EXECUTION");
		return value != null && (value.equals("1") || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes"));
	}

	private static boolean selectedSourceDiagnosticsEnabled() {
		return requiresSelectedSourceFeatureCoverage();
	}

	/**
	 * Bounded transport evidence for source-route admission. This intentionally
	 * reports only copied terrain semantics; it never observes an Iris object,
	 * renderer state, or backend handle.
	 */
	private static String staticTerrainSourceSemanticSummary(VulkanicGalBridge.WorldMeshAssetRecord asset) {
		int materialZero = 0;
		int materialOne = 0;
		int materialOther = 0;
		int firstBlockState = -1;
		for (VulkanicGalBridge.WorldMeshVertexRecord vertex : asset.vertices()) {
			if (firstBlockState < 0) {
				firstBlockState = vertex.shaderBlockId();
			}
			switch (vertex.shaderMaterialType()) {
				case 0 -> materialZero++;
				case 1 -> materialOne++;
				default -> materialOther++;
			}
		}
		return "first_block_state=" + firstBlockState
			+ ",material_0=" + materialZero
			+ ",material_1=" + materialOne
			+ ",material_other=" + materialOther;
	}

	public static void registerStaticTerrainAtlasTexture(VulkanicGalBridge.WorldMeshTextureAssetRecord texture) {
		registerWorldMeshTexture(texture, "static-terrain-atlas");
	}

	/**
	 * Registers a copied semantic world-mesh texture for any selected Rust
	 * whole-frame consumer. The payload remains resource data, never a native
	 * atlas or backend handle.
	 */
	public static void registerWorldMeshTexture(
		VulkanicGalBridge.WorldMeshTextureAssetRecord texture,
		String source
	) {
		boolean rustWholeFrame = WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()
			|| WorldRenderRoutePolicy.currentDistantHorizonsOpaqueRoute().usesRustWholeFrameVulkan();
		if (!rustWholeFrame || texture == null) {
			return;
		}
		synchronized (LOCK) {
			WORLD_MESH_TEXTURES.put(texture.textureId(), texture);
			DIRTY_WORLD_MESH_TEXTURES.add(texture.textureId());
			markWorldMeshAssetsChangedLocked();
			auditMessage(
				"Rust VulkanicGAL world mesh texture registered"
					+ " texture_id=" + texture.textureId()
					+ " payload_bytes=" + texture.pngBytes().length
					+ " source=" + (source == null || source.isBlank() ? "unknown" : source)
					+ " route=rust-vulkan-whole-frame"
			);
		}
	}

	public static void registerStaticTerrainSortedIndex(VulkanicGalBridge.WorldMeshSortedIndexRecord sortedIndex) {
		if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan() || sortedIndex == null) {
			return;
		}
		synchronized (LOCK) {
			VulkanicGalBridge.WorldMeshAssetRecord asset = WORLD_MESH_ASSETS.get(sortedIndex.meshKey());
			if (asset == null || asset.meshGeneration() != sortedIndex.meshGeneration()) {
				return;
			}
			VulkanicGalBridge.WorldMeshSortedIndexRecord previous = WORLD_MESH_SORTED_INDICES.get(sortedIndex.meshKey());
			if (previous != null && previous.indexGeneration() >= sortedIndex.indexGeneration()) {
				return;
			}
			WORLD_MESH_SORTED_INDICES.put(sortedIndex.meshKey(), sortedIndex);
			DIRTY_WORLD_MESH_SORTED_INDICES.add(sortedIndex.meshKey());
			RustGalTerrainRenderer.recordTranslucentSortCopyRegistered(sortedIndex);
			markWorldMeshAssetsChangedLocked();
			auditMessage(
				"Rust VulkanicGAL static terrain sorted index registered"
					+ " mesh_key=" + sortedIndex.meshKey()
					+ " mesh_generation=" + sortedIndex.meshGeneration()
					+ " index_generation=" + sortedIndex.indexGeneration()
					+ " index_bytes=" + sortedIndex.indexBytes().length
					+ " route=rust-vulkan-whole-frame"
			);
		}
	}

	public static StaticTerrainSortedIndexSnapshot staticTerrainSortedIndexSnapshot(long meshKey) {
		synchronized (LOCK) {
			VulkanicGalBridge.WorldMeshSortedIndexRecord sortedIndex = WORLD_MESH_SORTED_INDICES.get(meshKey);
			if (sortedIndex == null) {
				return null;
			}
			byte[] indexBytes = sortedIndex.indexBytes().clone();
			return new StaticTerrainSortedIndexSnapshot(
				sortedIndex.meshKey(),
				sortedIndex.meshGeneration(),
				sortedIndex.indexGeneration(),
				sortedIndex.indexType(),
				indexBytes.length,
				RustGalTerrainRenderer.sortedIndexHash(indexBytes)
			);
		}
	}

	public static void removeStaticTerrainMeshAsset(long meshKey) {
		if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()) {
			return;
		}
		synchronized (LOCK) {
				if (WORLD_MESH_ASSETS.remove(meshKey) == null) {
					return;
				}
				DIRTY_WORLD_MESH_ASSETS.remove(meshKey);
				UPLOADED_WORLD_MESH_GENERATIONS.remove(meshKey);
				WORLD_MESH_SORTED_INDICES.remove(meshKey);
				DIRTY_WORLD_MESH_SORTED_INDICES.remove(meshKey);
				PENDING_MESH_INSTANCES.removeIf(instance -> instance.meshKey() == meshKey);
			markWorldMeshAssetsChangedLocked();
			auditMessage(
				"Rust VulkanicGAL static terrain mesh asset removed"
					+ " mesh_key=" + meshKey
					+ " route=rust-vulkan-whole-frame"
			);
		}
	}

	public static boolean enqueueStaticTerrainMeshInstance(
		long meshKey,
		long meshGeneration,
		float[] transform,
		int viewportWidth,
		int viewportHeight
	) {
		return enqueueStaticTerrainMeshInstance(meshKey, meshGeneration, transform, viewportWidth, viewportHeight, DEPTH_POLICY_TEST_WRITE);
	}

	public static boolean enqueueStaticTerrainMeshInstance(
		long meshKey,
		long meshGeneration,
		float[] transform,
		int viewportWidth,
		int viewportHeight,
		int depthPolicy
	) {
		if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()) {
			return false;
		}
		synchronized (LOCK) {
			VulkanicGalBridge.WorldMeshAssetRecord asset = WORLD_MESH_ASSETS.get(meshKey);
			if (asset == null || asset.meshGeneration() != meshGeneration) {
				return false;
			}
			PENDING_MESH_INSTANCES.add(new VulkanicGalBridge.WorldMeshInstanceRecord(
				STRATUM_WORLD_TERRAIN,
				meshKey,
				meshGeneration,
				MESH_SECTION_ALL,
				depthPolicy,
				CULL_BACK,
				WORLD_WINDING_CCW,
				0xFFFFFFFF,
				transform,
				viewportWidth,
				viewportHeight
			));
			PENDING_MESH_PRODUCERS.add(PendingMeshProducer.STATIC_TERRAIN);
			return true;
		}
	}

	private static void markWorldMeshAssetsChangedLocked() {
		worldMeshAssetGeneration++;
		attemptedWorldMeshAssetGeneration = Math.min(attemptedWorldMeshAssetGeneration, uploadedWorldMeshAssetGeneration);
		lastWorldMeshAssetPayloadCount = DIRTY_WORLD_MESH_ASSETS.size() + DIRTY_WORLD_MESH_TEXTURES.size() + DIRTY_WORLD_MESH_SORTED_INDICES.size();
		lastWorldMeshAssetPayloadBytes = 0L;
		for (long meshKey : DIRTY_WORLD_MESH_ASSETS) {
			VulkanicGalBridge.WorldMeshAssetRecord mesh = WORLD_MESH_ASSETS.get(meshKey);
			if (mesh == null) {
				continue;
			}
			lastWorldMeshAssetPayloadBytes += mesh.indexBytes().length;
			lastWorldMeshAssetPayloadBytes += (long)mesh.vertices().size() * VulkanicGalBridge.Struct.WORLD_MESH_VERTEX.byteSize();
		}
		for (long meshKey : DIRTY_WORLD_MESH_SORTED_INDICES) {
			VulkanicGalBridge.WorldMeshSortedIndexRecord sortedIndex = WORLD_MESH_SORTED_INDICES.get(meshKey);
			if (sortedIndex == null) {
				continue;
			}
			lastWorldMeshAssetPayloadBytes += sortedIndex.indexBytes().length;
		}
		for (int textureId : DIRTY_WORLD_MESH_TEXTURES) {
			VulkanicGalBridge.WorldMeshTextureAssetRecord texture = WORLD_MESH_TEXTURES.get(textureId);
			if (texture == null) {
				continue;
			}
			lastWorldMeshAssetPayloadBytes += texture.pngBytes().length;
		}
	}

	private static List<VulkanicGalBridge.WorldMeshAssetRecord> dirtyWorldMeshAssetsLocked(int limit, long byteBudget) {
		if (DIRTY_WORLD_MESH_ASSETS.isEmpty()) {
			return List.of();
		}
		List<VulkanicGalBridge.WorldMeshAssetRecord> meshes = new ArrayList<>(Math.min(DIRTY_WORLD_MESH_ASSETS.size(), limit));
		long bytes = 0L;
		// Current-frame semantic work has to cross before background ingestion.
		// A transient visible mesh must also precede persistent terrain entries:
		// otherwise a full static-terrain upload window can discard an airborne
		// moving instance when this frame is frozen. The asset contract remains
		// shared; this is only admission ordering for the current semantic frame.
		for (VulkanicGalBridge.WorldMeshInstanceRecord instance : PENDING_MESH_INSTANCES) {
			if (instance.stratum() == STRATUM_WORLD_TERRAIN) {
				continue;
			}
			if (!DIRTY_WORLD_MESH_ASSETS.contains(instance.meshKey())) {
				continue;
			}
			VulkanicGalBridge.WorldMeshAssetRecord mesh = WORLD_MESH_ASSETS.get(instance.meshKey());
			if (mesh == null || mesh.meshGeneration() != instance.meshGeneration()) {
				continue;
			}
			if (containsWorldMeshAsset(meshes, mesh.meshKey())) {
				continue;
			}
			long meshBytes = worldMeshAssetPayloadBytes(mesh);
			if (!appendWorldMeshAssetWithinBudget(meshes, mesh, bytes, limit, byteBudget)) {
				break;
			}
			bytes += meshBytes;
		}
		for (VulkanicGalBridge.WorldMeshInstanceRecord instance : PENDING_MESH_INSTANCES) {
			if (instance.stratum() != STRATUM_WORLD_TERRAIN) {
				continue;
			}
			if (!DIRTY_WORLD_MESH_ASSETS.contains(instance.meshKey())) {
				continue;
			}
			VulkanicGalBridge.WorldMeshAssetRecord mesh = WORLD_MESH_ASSETS.get(instance.meshKey());
			if (mesh == null || mesh.meshGeneration() != instance.meshGeneration()) {
				continue;
			}
			if (containsWorldMeshAsset(meshes, mesh.meshKey())) {
				continue;
			}
			long meshBytes = worldMeshAssetPayloadBytes(mesh);
			if (!appendWorldMeshAssetWithinBudget(meshes, mesh, bytes, limit, byteBudget)) {
				break;
			}
			bytes += meshBytes;
		}
		for (long meshKey : DIRTY_WORLD_MESH_ASSETS) {
			if (containsWorldMeshAsset(meshes, meshKey)) {
				continue;
			}
			VulkanicGalBridge.WorldMeshAssetRecord mesh = WORLD_MESH_ASSETS.get(meshKey);
			if (mesh == null) {
				continue;
			}
			long meshBytes = worldMeshAssetPayloadBytes(mesh);
			if (!appendWorldMeshAssetWithinBudget(meshes, mesh, bytes, limit, byteBudget)) {
				break;
			}
			bytes += meshBytes;
		}
		return meshes;
	}

	private static boolean appendWorldMeshAssetWithinBudget(
		List<VulkanicGalBridge.WorldMeshAssetRecord> meshes,
		VulkanicGalBridge.WorldMeshAssetRecord mesh,
		long currentBytes,
		int limit,
		long byteBudget
	) {
		long meshBytes = worldMeshAssetPayloadBytes(mesh);
		if (!meshes.isEmpty() && (meshes.size() == limit || currentBytes + meshBytes > byteBudget)) {
			return false;
		}
		meshes.add(mesh);
		return true;
	}

	private static boolean containsWorldMeshAsset(List<VulkanicGalBridge.WorldMeshAssetRecord> meshes, long meshKey) {
		for (VulkanicGalBridge.WorldMeshAssetRecord mesh : meshes) {
			if (mesh.meshKey() == meshKey) {
				return true;
			}
		}
		return false;
	}

	private static long worldMeshAssetPayloadBytes(VulkanicGalBridge.WorldMeshAssetRecord mesh) {
		return mesh.indexBytes().length + (long)mesh.vertices().size() * VulkanicGalBridge.Struct.WORLD_MESH_VERTEX.byteSize();
	}

	private static long worldMeshAssetPayloadBytes(
		List<VulkanicGalBridge.WorldMeshAssetRecord> meshes,
		List<VulkanicGalBridge.WorldMeshTextureAssetRecord> textures,
		List<VulkanicGalBridge.WorldMeshSortedIndexRecord> sortedIndices
	) {
		long bytes = 0L;
		for (VulkanicGalBridge.WorldMeshAssetRecord mesh : meshes) {
			bytes += worldMeshAssetPayloadBytes(mesh);
		}
		for (VulkanicGalBridge.WorldMeshTextureAssetRecord texture : textures) {
			bytes += texture.pngBytes().length;
		}
		for (VulkanicGalBridge.WorldMeshSortedIndexRecord sortedIndex : sortedIndices) {
			bytes += sortedIndex.indexBytes().length;
		}
		return bytes;
	}

	private static List<VulkanicGalBridge.WorldMeshTextureAssetRecord> dirtyWorldMeshTextureAssetsLocked() {
		if (DIRTY_WORLD_MESH_TEXTURES.isEmpty()) {
			return List.of();
		}
		List<VulkanicGalBridge.WorldMeshTextureAssetRecord> textures = new ArrayList<>(DIRTY_WORLD_MESH_TEXTURES.size());
		for (int textureId : DIRTY_WORLD_MESH_TEXTURES) {
			VulkanicGalBridge.WorldMeshTextureAssetRecord texture = WORLD_MESH_TEXTURES.get(textureId);
			if (texture != null) {
				textures.add(texture);
			}
		}
		return textures;
	}

	private static List<VulkanicGalBridge.WorldMeshSortedIndexRecord> dirtyWorldMeshSortedIndicesLocked(
		List<VulkanicGalBridge.WorldMeshAssetRecord> uploadedMeshes
	) {
		if (DIRTY_WORLD_MESH_SORTED_INDICES.isEmpty()) {
			return List.of();
		}
		Set<Long> uploadedMeshKeys = new LinkedHashSet<>();
		for (VulkanicGalBridge.WorldMeshAssetRecord mesh : uploadedMeshes) {
			uploadedMeshKeys.add(mesh.meshKey());
		}
		List<VulkanicGalBridge.WorldMeshSortedIndexRecord> sortedIndices = new ArrayList<>(DIRTY_WORLD_MESH_SORTED_INDICES.size());
		for (long meshKey : DIRTY_WORLD_MESH_SORTED_INDICES) {
			VulkanicGalBridge.WorldMeshSortedIndexRecord sortedIndex = WORLD_MESH_SORTED_INDICES.get(meshKey);
			if (sortedIndex == null) {
				continue;
			}
			Long uploadedGeneration = UPLOADED_WORLD_MESH_GENERATIONS.get(meshKey);
			if (!uploadedMeshKeys.contains(meshKey)
				&& (uploadedGeneration == null || uploadedGeneration.longValue() != sortedIndex.meshGeneration())) {
				continue;
			}
			sortedIndices.add(sortedIndex);
		}
		return sortedIndices;
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
		int resolvedPackedLight,
		String diagnosticName
	) {
		try {
				BlockStateModel model = blockRenderDispatcher.getBlockModel(blockState);
				int shaderBlockId = stableBlockStateSemanticId(blockState);
				// This is the shader-pack material-class semantic, not the Rust
				// frontend's material-mode enum. The copied indexed-mesh ABI
				// carries the same 0/1 render-type convention used by terrain
				// source programs while the section retains opaque/cutout policy.
				int shaderMaterialType = materialMode == MATERIAL_MODE_CUTOUT ? 1 : 0;
				List<BlockModelPart> parts = model.collectParts(RandomSource.create(blockState.getSeed(randomSeedPos)));
			if (parts.isEmpty()) {
				return null;
			}
			List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = new ArrayList<>();
			List<VulkanicGalBridge.WorldMeshSectionRecord> sections = new ArrayList<>();
			List<VulkanicGalBridge.WorldMeshTextureAssetRecord> textures = new ArrayList<>();
				List<Integer> indices = new ArrayList<>();
				for (BlockModelPart part : parts) {
					for (Direction direction : Direction.values()) {
						appendBlockModelQuads(part.getQuads(direction), blockState, tintGetter, tintPos, materialId, materialMode, shaderBlockId, shaderMaterialType, resolvedPackedLight, vertices, indices, sections, textures);
					}
					appendBlockModelQuads(part.getQuads(null), blockState, tintGetter, tintPos, materialId, materialMode, shaderBlockId, shaderMaterialType, resolvedPackedLight, vertices, indices, sections, textures);
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
			long hash = meshContentHash(vertices, indexBytes, byteSections);
			long meshKey = hash == 0L ? 1L : hash;
			long meshGeneration = Math.max(1L, worldMeshAssetGeneration + 1L);
			return new BlockMeshExtraction(
				meshKey,
				meshGeneration,
				new VulkanicGalBridge.WorldMeshAssetRecord(
						meshKey,
						meshGeneration,
						MESH_VERTEX_LAYOUT_V2,
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

	/**
	 * Builds a reusable indexed mesh asset from the normal vanilla item baked
	 * quad list. Item tint is already resolved by Java's item model state; Rust
	 * receives only the copied per-vertex result plus atlas resource identity.
	 */
	private static BlockMeshExtraction extractItemQuadMesh(
		List<BakedQuad> quads,
		int[] tintLayers,
		int packedLight,
		ModelMeshRenderSemantics semantics,
		String semanticFamily
	) {
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = new ArrayList<>();
		List<Integer> indices = new ArrayList<>();
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections = new ArrayList<>();
		List<VulkanicGalBridge.WorldMeshTextureAssetRecord> textures = new ArrayList<>();
		for (BakedQuad bakedQuad : quads) {
			BakedQuadView quad = (BakedQuadView)(Object)bakedQuad;
			TextureAtlasSprite sprite = quad.getSprite();
			ResourceLocation spriteName = sprite.contents().name();
			byte[] payload = readTexturePayload(spriteName);
			if (payload == null) {
				throw new IllegalStateException("unsupported item texture asset " + spriteName);
			}
			int textureId = stableTextureId(spriteName);
			textures.add(new VulkanicGalBridge.WorldMeshTextureAssetRecord(textureId, payload));
			int tintColor = itemQuadTintColor(bakedQuad, tintLayers);
			int base = vertices.size();
			int firstIndex = indices.size();
			for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
				vertices.add(new VulkanicGalBridge.WorldMeshVertexRecord(
					quad.getX(vertexIndex),
					quad.getY(vertexIndex),
					quad.getZ(vertexIndex),
					spriteLocalU(sprite, quad.getTexU(vertexIndex)),
					spriteLocalV(sprite, quad.getTexV(vertexIndex)),
					quad.getTexU(vertexIndex),
					quad.getTexV(vertexIndex),
					0,
					MATERIAL_SOURCE_TEXTURED,
					shadedBakedVertexColor(quad.getColor(vertexIndex), tintColor, 1.0F),
					quad.getVertexNormal(vertexIndex),
					packedLight,
					0
				));
			}
			indices.add(base);
			indices.add(base + 1);
			indices.add(base + 2);
			indices.add(base + 2);
			indices.add(base + 3);
			indices.add(base);
			sections.add(new VulkanicGalBridge.WorldMeshSectionRecord(
				semantics.materialId(),
				textureId,
				semantics.materialMode(),
				semantics.cullPolicy(),
				blockDisplayQuadWinding(quad, bakedQuad.direction()),
				firstIndex,
				6
			));
		}
		if (vertices.isEmpty()) {
			return null;
		}
		int indexType = vertices.size() <= 0xffff ? VulkanicGalBridge.INDEX_U16 : VulkanicGalBridge.INDEX_U32;
		int indexStride = indexType == VulkanicGalBridge.INDEX_U16 ? 2 : 4;
		byte[] indexBytes = new byte[indices.size() * indexStride];
		for (int index = 0; index < indices.size(); index++) {
			int value = indices.get(index);
			for (int byteIndex = 0; byteIndex < indexStride; byteIndex++) {
				indexBytes[index * indexStride + byteIndex] = (byte)(value >>> (byteIndex * 8));
			}
		}
		List<VulkanicGalBridge.WorldMeshSectionRecord> byteSections = new ArrayList<>(sections.size());
		for (VulkanicGalBridge.WorldMeshSectionRecord section : sections) {
			byteSections.add(new VulkanicGalBridge.WorldMeshSectionRecord(
				section.materialId(), section.textureId(), section.materialMode(), section.cullPolicy(), section.winding(),
				Math.multiplyExact(section.indexOffset(), indexStride), section.indexCount()
			));
		}
		long meshKey = meshContentHash(vertices, indexBytes, byteSections, semanticFamily);
		long meshGeneration = Math.max(1L, worldMeshAssetGeneration + 1L);
		return new BlockMeshExtraction(
			meshKey,
			meshGeneration,
			new VulkanicGalBridge.WorldMeshAssetRecord(
				meshKey, meshGeneration, MESH_VERTEX_LAYOUT_V2, indexType, vertices, indexBytes, byteSections,
				semanticFamily
			),
			textures
		);
	}

	/**
	 * ItemStackRenderState deliberately keeps an empty tint array until a model
	 * supplies a tint layer. Vanilla renders an absent layer as white, so copied
	 * item semantics must retain that default rather than reject ordinary quads.
	 */
	private static int itemQuadTintColor(BakedQuad bakedQuad, int[] tintLayers) {
		if (!bakedQuad.isTinted() || tintLayers == null || tintLayers.length == 0) {
			return 0xffffffff;
		}
		int configuredTint = tintLayers[bakedQuad.tintIndex()];
		return configuredTint == -1 ? 0xffffffff : configuredTint;
	}

	private static BlockMeshExtraction extractArrowModelMesh(
		ModelPart modelRoot,
		ResourceLocation textureLocation,
		String entityIdentity
	) {
		byte[] texturePayload = readTexturePayloadForResource(textureLocation);
		if (texturePayload == null) {
			throw new IllegalStateException("unsupported arrow texture asset " + textureLocation);
		}
		int textureId = stableTextureId(textureLocation);
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = new ArrayList<>();
		List<Integer> indices = new ArrayList<>();
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections = new ArrayList<>();
		PoseStack modelPose = new PoseStack();
		modelRoot.visit(modelPose, (partPose, partPath, cubeIndex, cube) -> {
			for (ModelPart.Polygon polygon : cube.polygons) {
				if (polygon == null || polygon.vertices().length != 4) {
					throw new IllegalStateException("ArrowModel contains unsupported non-quad polygon at " + partPath + "/" + cubeIndex);
				}
				Vector3f transformedNormal = partPose.transformNormal(polygon.normal(), new Vector3f());
				int normalPacked = packWorldMeshNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
				int base = vertices.size();
				int firstIndex = indices.size();
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					Vector3f position = partPose.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f());
					vertices.add(new VulkanicGalBridge.WorldMeshVertexRecord(
						position.x, position.y, position.z,
						vertex.u(), vertex.v(), vertex.u(), vertex.v(),
						0, 1, 0xffffffff, normalPacked, 0, 0
					));
				}
				int winding = worldMeshWinding(vertices.get(base), vertices.get(base + 1), vertices.get(base + 2), transformedNormal);
				indices.add(base);
				indices.add(base + 1);
				indices.add(base + 2);
				indices.add(base + 2);
				indices.add(base + 3);
				indices.add(base);
				sections.add(new VulkanicGalBridge.WorldMeshSectionRecord(
					MATERIAL_ID_CUTOUT_TEXTURED,
					textureId,
					MATERIAL_MODE_CUTOUT,
					CULL_BACK,
					winding,
					firstIndex,
					6
				));
			}
		});
		if (vertices.isEmpty() || sections.isEmpty()) {
			return null;
		}
		byte[] indexBytes;
		int indexType;
		int indexStride;
		if (vertices.size() <= 0xffff) {
			indexType = VulkanicGalBridge.INDEX_U16;
			indexStride = 2;
			indexBytes = new byte[indices.size() * indexStride];
			for (int i = 0; i < indices.size(); i++) {
				int index = indices.get(i);
				indexBytes[i * indexStride] = (byte)(index & 0xff);
				indexBytes[i * indexStride + 1] = (byte)((index >>> 8) & 0xff);
			}
		} else {
			indexType = VulkanicGalBridge.INDEX_U32;
			indexStride = 4;
			indexBytes = new byte[indices.size() * indexStride];
			for (int i = 0; i < indices.size(); i++) {
				int index = indices.get(i);
				for (int byteIndex = 0; byteIndex < indexStride; byteIndex++) {
					indexBytes[i * indexStride + byteIndex] = (byte)(index >>> (byteIndex * 8));
				}
			}
		}
		List<VulkanicGalBridge.WorldMeshSectionRecord> byteSections = new ArrayList<>(sections.size());
		for (VulkanicGalBridge.WorldMeshSectionRecord section : sections) {
			byteSections.add(new VulkanicGalBridge.WorldMeshSectionRecord(
				section.materialId(), section.textureId(), section.materialMode(), section.cullPolicy(), section.winding(),
				Math.multiplyExact(section.indexOffset(), indexStride), section.indexCount()
			));
		}
		long meshKey = meshContentHash(vertices, indexBytes, byteSections, entityIdentity);
		long meshGeneration = Math.max(1L, worldMeshAssetGeneration + 1L);
		return new BlockMeshExtraction(
			meshKey,
			meshGeneration,
			new VulkanicGalBridge.WorldMeshAssetRecord(
				meshKey,
				meshGeneration,
				MESH_VERTEX_LAYOUT_V2,
				indexType,
				vertices,
				indexBytes,
				byteSections,
				entityIdentity
			),
			List.of(minecraftModelTextureAsset(textureId, texturePayload))
		);
	}

	/**
	 * Extracts the stable {@link ModelPart} polygon stream used by ordinary
	 * entity/block-entity models. Outer placement remains an instance transform;
	 * this method only copies model-local vertices, local sprite UVs, normals,
	 * packed light, and canonical indexed-quad topology.
	 */
	private static BlockMeshExtraction extractModelPartMesh(
		ModelPart modelRoot,
		ResourceLocation textureIdentity,
		String entityIdentity,
		int packedLight,
		int materialId,
		int materialMode,
		int cullPolicy
	) {
		byte[] texturePayload = readModelTexturePayload(textureIdentity);
		if (texturePayload == null) {
			throw new IllegalStateException("unsupported model texture asset " + textureIdentity);
		}
		int textureId = stableTextureId(textureIdentity);
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices = new ArrayList<>();
		List<Integer> indices = new ArrayList<>();
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections = new ArrayList<>();
		PoseStack modelPose = new PoseStack();
		modelRoot.visit(modelPose, (partPose, partPath, cubeIndex, cube) -> {
			for (ModelPart.Polygon polygon : cube.polygons) {
				if (polygon == null || polygon.vertices().length != 4) {
					throw new IllegalStateException("ModelPart contains unsupported non-quad polygon at " + partPath + "/" + cubeIndex);
				}
				Vector3f transformedNormal = partPose.transformNormal(polygon.normal(), new Vector3f());
				int normalPacked = packWorldMeshNormal(transformedNormal.x, transformedNormal.y, transformedNormal.z);
				int base = vertices.size();
				int firstIndex = indices.size();
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					Vector3f position = partPose.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f());
					vertices.add(new VulkanicGalBridge.WorldMeshVertexRecord(
						position.x, position.y, position.z,
						vertex.u(), vertex.v(), vertex.u(), vertex.v(),
						0, 1, 0xffffffff, normalPacked, packedLight, 0
					));
				}
				int winding = worldMeshWinding(vertices.get(base), vertices.get(base + 1), vertices.get(base + 2), transformedNormal);
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
						cullPolicy,
					winding,
					firstIndex,
					6
				));
			}
		});
		if (vertices.isEmpty() || sections.isEmpty()) {
			return null;
		}
		int indexType = vertices.size() <= 0xffff ? VulkanicGalBridge.INDEX_U16 : VulkanicGalBridge.INDEX_U32;
		int indexStride = indexType == VulkanicGalBridge.INDEX_U16 ? 2 : 4;
		byte[] indexBytes = new byte[indices.size() * indexStride];
		for (int index = 0; index < indices.size(); index++) {
			int value = indices.get(index);
			for (int byteIndex = 0; byteIndex < indexStride; byteIndex++) {
				indexBytes[index * indexStride + byteIndex] = (byte)(value >>> (byteIndex * 8));
			}
		}
		List<VulkanicGalBridge.WorldMeshSectionRecord> byteSections = new ArrayList<>(sections.size());
		for (VulkanicGalBridge.WorldMeshSectionRecord section : sections) {
			byteSections.add(new VulkanicGalBridge.WorldMeshSectionRecord(
				section.materialId(), section.textureId(), section.materialMode(), section.cullPolicy(), section.winding(),
				Math.multiplyExact(section.indexOffset(), indexStride), section.indexCount()
			));
		}
		long meshKey = meshContentHash(vertices, indexBytes, byteSections, entityIdentity);
		long meshGeneration = Math.max(1L, worldMeshAssetGeneration + 1L);
		return new BlockMeshExtraction(
			meshKey,
			meshGeneration,
			new VulkanicGalBridge.WorldMeshAssetRecord(
				meshKey,
				meshGeneration,
				MESH_VERTEX_LAYOUT_V2,
				indexType,
				vertices,
				indexBytes,
				byteSections,
				entityIdentity
			),
			List.of(minecraftModelTextureAsset(textureId, texturePayload))
		);
	}

	private static void appendBlockModelQuads(
		List<BakedQuad> quads,
		BlockState blockState,
		BlockAndTintGetter tintGetter,
		BlockPos tintPos,
		int materialId,
		int materialMode,
		int shaderBlockId,
		int shaderMaterialType,
		int resolvedPackedLight,
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
			float shade = tintGetter.getShade(bakedQuad.direction(), bakedQuad.shade());
			// Moving baked-quads omit their current world light. The caller supplies
			// it from the real moving-block level; other mesh producers retain their
			// copied baked-vertex light unchanged.
			int packedLight = resolvedPackedLight >= 0 ? resolvedPackedLight : quad.getLight(0);
			int winding = blockDisplayQuadWinding(quad, bakedQuad.direction());
			for (int i = 0; i < 4; i++) {
				int colorArgb = shadedBakedVertexColor(quad.getColor(i), tintColor, shade);
				vertices.add(new VulkanicGalBridge.WorldMeshVertexRecord(
					quad.getX(i),
					quad.getY(i),
						quad.getZ(i),
						spriteLocalU(sprite, quad.getTexU(i)),
						spriteLocalV(sprite, quad.getTexV(i)),
						quad.getTexU(i),
						quad.getTexV(i),
						shaderBlockId,
						shaderMaterialType,
						colorArgb,
					quad.getVertexNormal(i),
					LightTexture.lightCoordsWithEmission(
						resolvedPackedLight >= 0 ? packedLight : quad.getLight(i),
						bakedQuad.lightEmission()
					),
						0
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

	private static int movingBlockLightColor(BlockAndTintGetter level, BlockState blockState, BlockPos blockPos) {
		int packedLight = LevelRenderer.getLightColor(LevelRenderer.BrightnessGetter.DEFAULT, level, blockState, blockPos);
		float block = LightTexture.block(packedLight) / 15.0F;
		float sky = LightTexture.sky(packedLight) / 15.0F;
		float brightness = Mth.clamp(0.08F + Math.max(block, sky) * 0.92F, 0.0F, 1.0F);
		int channel = Mth.clamp(Math.round(brightness * 255.0F), 0, 255);
		return ARGB.color(255, channel, channel, channel);
	}

	private static int shadedBakedVertexColor(int bakedColor, int tintColor, float shade) {
		int bakedRed = bakedColor & 0xff;
		int bakedGreen = bakedColor >>> 8 & 0xff;
		int bakedBlue = bakedColor >>> 16 & 0xff;
		int bakedAlpha = bakedColor >>> 24 & 0xff;
		int tintRed = ARGB.red(tintColor);
		int tintGreen = ARGB.green(tintColor);
		int tintBlue = ARGB.blue(tintColor);
		int alpha = Mth.clamp(Math.round((bakedAlpha / 255.0F) * (ARGB.alpha(tintColor) / 255.0F) * 255.0F), 0, 255);
		int red = shadedChannel(bakedRed, tintRed, shade);
		int green = shadedChannel(bakedGreen, tintGreen, shade);
		int blue = shadedChannel(bakedBlue, tintBlue, shade);
		return ARGB.color(alpha, red, green, blue);
	}

	private static int shadedChannel(int baked, int tint, float shade) {
		return Mth.clamp(Math.round((baked / 255.0F) * (tint / 255.0F) * shade * 255.0F), 0, 255);
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

	private static int worldMeshWinding(
		VulkanicGalBridge.WorldMeshVertexRecord a,
		VulkanicGalBridge.WorldMeshVertexRecord b,
		VulkanicGalBridge.WorldMeshVertexRecord c,
		Vector3f expectedNormal
	) {
		float ax = b.x() - a.x();
		float ay = b.y() - a.y();
		float az = b.z() - a.z();
		float bx = c.x() - a.x();
		float by = c.y() - a.y();
		float bz = c.z() - a.z();
		float dot = (ay * bz - az * by) * expectedNormal.x
			+ (az * bx - ax * bz) * expectedNormal.y
			+ (ax * by - ay * bx) * expectedNormal.z;
		return dot >= 0.0F ? WORLD_WINDING_CCW : WORLD_WINDING_CW;
	}

	private static int packWorldMeshNormal(float x, float y, float z) {
		float length = (float)Math.sqrt(x * x + y * y + z * z);
		if (length <= 0.00001F) {
			return 0;
		}
		int ix = Math.round(x / length * 127.0F) & 0xff;
		int iy = Math.round(y / length * 127.0F) & 0xff;
		int iz = Math.round(z / length * 127.0F) & 0xff;
		return ix | iy << 8 | iz << 16;
	}

	private static byte[] readTexturePayload(ResourceLocation spriteName) {
		if (MISSING_TEXTURE_LOCATION.equals(spriteName)) {
			return missingTexturePayload();
		}
		ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath(
			spriteName.getNamespace(),
			"textures/" + spriteName.getPath() + ".png"
		);
		return readTexturePayloadForResource(textureLocation);
	}

	/**
	 * ModelPart producers can use either a block-atlas sprite identity or a
	 * standalone resource location. Keep that distinction at semantic asset
	 * extraction: both forms are copied into the same Rust-owned texture asset.
	 */
	private static byte[] readModelTexturePayload(ResourceLocation textureIdentity) {
		String path = textureIdentity.getPath();
		if (path.startsWith("textures/") && path.endsWith(".png")) {
			return readTexturePayloadForResource(textureIdentity);
		}
		return readTexturePayload(textureIdentity);
	}

	/**
	 * ModelPart and direct model resources use Minecraft's top-left PNG UV
	 * convention. Rust owns the later conversion into its sampler convention.
	 */
	private static VulkanicGalBridge.WorldMeshTextureAssetRecord minecraftModelTextureAsset(
		int textureId,
		byte[] payload
	) {
		return new VulkanicGalBridge.WorldMeshTextureAssetRecord(
			textureId,
			payload,
			0,
			0,
			1,
			1,
			0,
			0,
			0,
			List.of(),
			VulkanicGalBridge.WORLD_MESH_TEXTURE_COORDINATE_ORIGIN_MINECRAFT_TOP_LEFT
		);
	}

	private static byte[] readTexturePayloadForResource(ResourceLocation textureLocation) {
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

	/**
	 * Matches the copied runtime block-state table used by Rust's shader-pack
	 * contract. This is a canonical Minecraft semantic identity, not a Java
	 * object hash or an Iris material-map value; Rust resolves it against the
	 * selected pack's own block-property rules.
	 */
	static int stableBlockStateSemanticId(BlockState blockState) {
		int rawStateId = Block.getId(blockState);
		if (rawStateId < 0) {
			throw new IllegalArgumentException("block state has no raw registry identity: " + blockState);
		}
		return rawStateId;
	}

	private static int stableTextureId(ResourceLocation location) {
		long hash = fnv64(location.toString());
		int id = (int)(hash ^ (hash >>> 32));
		return id == 0 ? 1 : id;
	}

	private static long meshContentHash(
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices,
		byte[] indexBytes,
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections
	) {
		long hash = fnv64("world-mesh-content-v1");
		hash = fnv64Int(hash, vertices.size());
		for (VulkanicGalBridge.WorldMeshVertexRecord vertex : vertices) {
			hash = fnv64Float(hash, vertex.x());
			hash = fnv64Float(hash, vertex.y());
			hash = fnv64Float(hash, vertex.z());
			hash = fnv64Float(hash, vertex.u());
			hash = fnv64Float(hash, vertex.v());
			hash = fnv64Float(hash, vertex.atlasU());
			hash = fnv64Float(hash, vertex.atlasV());
			hash = fnv64Int(hash, vertex.shaderBlockId());
			hash = fnv64Int(hash, vertex.shaderMaterialType());
			hash = fnv64Int(hash, vertex.colorArgb());
			hash = fnv64Int(hash, vertex.normalPacked());
			hash = fnv64Int(hash, vertex.light());
		}
		hash = fnv64Bytes(hash, indexBytes);
		hash = fnv64Int(hash, sections.size());
		for (VulkanicGalBridge.WorldMeshSectionRecord section : sections) {
			hash = fnv64Int(hash, section.materialId());
			hash = fnv64Int(hash, section.textureId());
			hash = fnv64Int(hash, section.materialMode());
			hash = fnv64Int(hash, section.cullPolicy());
			hash = fnv64Int(hash, section.winding());
			hash = fnv64Int(hash, section.indexOffset());
			hash = fnv64Int(hash, section.indexCount());
		}
		return hash == 0L ? 1L : hash;
	}

	private static long meshContentHash(
		List<VulkanicGalBridge.WorldMeshVertexRecord> vertices,
		byte[] indexBytes,
		List<VulkanicGalBridge.WorldMeshSectionRecord> sections,
		String semanticIdentity
	) {
		long hash = meshContentHash(vertices, indexBytes, sections);
		hash = fnv64Int(hash, semanticIdentity.length());
		for (int index = 0; index < semanticIdentity.length(); index++) {
			hash = fnv64Int(hash, semanticIdentity.charAt(index));
		}
		return hash == 0L ? 1L : hash;
	}

	private static long fnv64(String value) {
		long hash = 0xcbf29ce484222325L;
		for (int i = 0; i < value.length(); i++) {
			hash ^= value.charAt(i);
			hash *= 0x100000001b3L;
		}
		return hash == 0L ? 1L : hash;
	}

	private static long fnv64Bytes(long hash, byte[] bytes) {
		hash = fnv64Int(hash, bytes.length);
		for (byte value : bytes) {
			hash ^= value & 0xffL;
			hash *= 0x100000001b3L;
		}
		return hash == 0L ? 1L : hash;
	}

	private static long fnv64Float(long hash, float value) {
		return fnv64Int(hash, Float.floatToRawIntBits(value));
	}

	private static long fnv64Int(long hash, int value) {
		for (int shift = 0; shift < 32; shift += 8) {
			hash ^= (value >>> shift) & 0xffL;
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

	private record FirstPersonMeshExtraction(
		BlockMeshExtraction extraction,
		ModelMeshRenderSemantics semantics,
		Matrix4f transform
	) {
	}

	private record MeshMaterial(int materialId, int materialMode) {
	}

	private record ModelMeshRenderSemantics(int materialId, int materialMode, int depthPolicy, int cullPolicy) {
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
				viewportHeight,
				MATERIAL_SOURCE_TEXTURED,
				MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS,
				colorArgb,
				packedLight
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

	/**
	 * One semantic enqueue is recorded in the performance and deterministic
	 * capture ledgers. Both are diagnostics only; neither participates in route
	 * selection or backend submission.
	 */
	private static void recordWorldMeshSubmittedWorkIdentity(String family, String identity) {
		GraphicsFrameBenchmark.recordSubmittedWorkIdentity(family, identity);
		DeterministicCameraCapture.recordSubmittedWorkIdentity(family, identity);
	}

	private static void recordArrowDiagnostic(
		String route,
		ResourceLocation textureLocation,
		long meshKey,
		long meshGeneration,
		VulkanicGalBridge.WorldMeshAssetRecord asset,
		int packedLight,
		float[] transform,
		int viewportWidth,
		int viewportHeight
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		if (ARROW_DIAGNOSTICS.size() >= 512) {
			ARROW_DIAGNOSTICS.remove(0);
		}
		ProjectedBounds projectedBounds = projectMeshBounds(asset.vertices(), transform, viewportWidth, viewportHeight);
		int materialMode = asset.sections().isEmpty() ? 0 : asset.sections().get(0).materialMode();
		ARROW_DIAGNOSTICS.add(new ArrowDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(),
			route,
			textureLocation.toString(),
			meshKey,
			meshGeneration,
			asset.vertexLayoutVersion(),
			asset.indexType(),
			asset.vertices().size(),
			asset.indexBytes().length,
			asset.sections().size(),
			materialMode,
			packedLight,
			viewportWidth,
			viewportHeight,
			projectedBounds.valid(),
			projectedBounds.left(),
			projectedBounds.top(),
			projectedBounds.right(),
			projectedBounds.bottom()
		));
	}

	public static List<ArrowDiagnostic> arrowDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(ARROW_DIAGNOSTICS);
		}
	}

	public static void recordArrowRouteDecision(
		String route,
		ResourceLocation textureLocation,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		synchronized (LOCK) {
			if (ARROW_ROUTE_DECISIONS.size() >= 512) {
				ARROW_ROUTE_DECISIONS.remove(0);
			}
			ARROW_ROUTE_DECISIONS.add(new ArrowRouteDecision(
				DeterministicCameraCapture.currentRenderedFrameIndex(),
				route,
				textureLocation == null ? "missing" : textureLocation.toString(),
				rustSelected,
				rustQueued,
				javaDrawn
			));
		}
	}

	public static List<ArrowRouteDecision> arrowRouteDecisions() {
		synchronized (LOCK) {
			return List.copyOf(ARROW_ROUTE_DECISIONS);
		}
	}

	private static void recordItemEntityDiagnostic(
		String route,
		String materialIdentity,
		long meshKey,
		long meshGeneration,
		VulkanicGalBridge.WorldMeshAssetRecord asset,
		int packedLight,
		float[] transform,
		int viewportWidth,
		int viewportHeight
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		if (ITEM_ENTITY_DIAGNOSTICS.size() >= 512) {
			ITEM_ENTITY_DIAGNOSTICS.remove(0);
		}
		ProjectedBounds projectedBounds = projectMeshBounds(asset.vertices(), transform, viewportWidth, viewportHeight);
		ITEM_ENTITY_DIAGNOSTICS.add(new ItemEntityDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(), route, materialIdentity, meshKey, meshGeneration,
			asset.vertexLayoutVersion(), asset.indexType(), asset.vertices().size(), asset.indexBytes().length,
			asset.sections().size(), packedLight, viewportWidth, viewportHeight,
			projectedBounds.valid(), projectedBounds.left(), projectedBounds.top(), projectedBounds.right(), projectedBounds.bottom()
		));
	}

	public static List<ItemEntityDiagnostic> itemEntityDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(ITEM_ENTITY_DIAGNOSTICS);
		}
	}

	public static void recordItemEntityRouteDecision(
		String route, boolean eligible, String ineligibility, boolean rustSelected, boolean rustQueued, boolean javaDrawn
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		synchronized (LOCK) {
			if (ITEM_ENTITY_ROUTE_DECISIONS.size() >= 512) {
				ITEM_ENTITY_ROUTE_DECISIONS.remove(0);
			}
			ITEM_ENTITY_ROUTE_DECISIONS.add(new ItemEntityRouteDecision(
				DeterministicCameraCapture.currentRenderedFrameIndex(), route, eligible, ineligibility,
				WorldRenderRoutePolicy.currentItemEntityMeshRoute(true).usesRustWholeFrameVulkan(), rustSelected, rustQueued, javaDrawn
			));
		}
	}

	public static List<ItemEntityRouteDecision> itemEntityRouteDecisions() {
		synchronized (LOCK) {
			return List.copyOf(ITEM_ENTITY_ROUTE_DECISIONS);
		}
	}

	private static void recordExperienceOrbDiagnostic(
		String route,
		int colorArgb,
		int packedLight,
		float minU,
		float maxU,
		float minV,
		float maxV,
		int viewportWidth,
		int viewportHeight,
		ProjectedBounds projectedBounds
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		if (EXPERIENCE_ORB_DIAGNOSTICS.size() >= 512) {
			EXPERIENCE_ORB_DIAGNOSTICS.remove(0);
		}
		EXPERIENCE_ORB_DIAGNOSTICS.add(new ExperienceOrbDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(), route, colorArgb, packedLight,
			minU, maxU, minV, maxV, viewportWidth, viewportHeight,
			projectedBounds.valid(), projectedBounds.left(), projectedBounds.top(), projectedBounds.right(), projectedBounds.bottom()
		));
	}

	public static List<ExperienceOrbDiagnostic> experienceOrbDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(EXPERIENCE_ORB_DIAGNOSTICS);
		}
	}

	private static void recordBeaconBeamDiagnostic(
		int colorArgb,
		int startY,
		int endY,
		float scroll,
		int viewportWidth,
		int viewportHeight,
		ProjectedBounds projectedBounds
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		if (BEACON_BEAM_DIAGNOSTICS.size() >= 128) {
			BEACON_BEAM_DIAGNOSTICS.remove(0);
		}
		BEACON_BEAM_DIAGNOSTICS.add(new BeaconBeamDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(), colorArgb, startY, endY, scroll,
			viewportWidth, viewportHeight, projectedBounds.valid(), projectedBounds.left(), projectedBounds.top(),
			projectedBounds.right(), projectedBounds.bottom()
		));
	}

	public static List<BeaconBeamDiagnostic> beaconBeamDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(BEACON_BEAM_DIAGNOSTICS);
		}
	}

	public static void recordExperienceOrbRouteDecision(
		String route, boolean rustSelected, boolean rustQueued, boolean javaDrawn
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		synchronized (LOCK) {
			if (EXPERIENCE_ORB_ROUTE_DECISIONS.size() >= 512) {
				EXPERIENCE_ORB_ROUTE_DECISIONS.remove(0);
			}
			EXPERIENCE_ORB_ROUTE_DECISIONS.add(new ExperienceOrbRouteDecision(
				DeterministicCameraCapture.currentRenderedFrameIndex(), route, rustSelected, rustQueued, javaDrawn
			));
		}
	}

	public static List<ExperienceOrbRouteDecision> experienceOrbRouteDecisions() {
		synchronized (LOCK) {
			return List.copyOf(EXPERIENCE_ORB_ROUTE_DECISIONS);
		}
	}

	private static void recordModelMeshDiagnostic(
		ResourceLocation textureIdentity,
		int entityId,
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
		if (MODEL_MESH_DIAGNOSTICS.size() >= 512) {
			MODEL_MESH_DIAGNOSTICS.remove(0);
		}
		ProjectedBounds projectedBounds = projectMeshBounds(asset.vertices(), transform, viewportWidth, viewportHeight);
		MODEL_MESH_DIAGNOSTICS.add(new ModelMeshDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(),
			"rust-vulkan-whole-frame",
			entityId,
			asset.entityIdentity(),
			textureIdentity.toString(),
			meshKey,
			meshGeneration,
			asset.vertexLayoutVersion(),
			asset.indexType(),
			asset.vertices().size(),
			asset.indexBytes().length,
			asset.sections().size(),
			uniformMeshSectionCullPolicy(asset.sections()),
			viewportWidth,
			viewportHeight,
			projectedBounds.valid(),
			projectedBounds.left(),
			projectedBounds.top(),
			projectedBounds.right(),
			projectedBounds.bottom()
		));
	}

	private static int uniformMeshSectionCullPolicy(List<VulkanicGalBridge.WorldMeshSectionRecord> sections) {
		if (sections.isEmpty()) {
			return -1;
		}
		int policy = sections.get(0).cullPolicy();
		for (int index = 1; index < sections.size(); index++) {
			if (sections.get(index).cullPolicy() != policy) {
				return -1;
			}
		}
		return policy;
	}

	public static List<ModelMeshDiagnostic> modelMeshDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(MODEL_MESH_DIAGNOSTICS);
		}
	}

	/** Bounded producer-route receipt for the copied ordinary {@code ModelPart} family. */
	public static void recordModelMeshRouteDecision(
		String route,
		ResourceLocation textureIdentity,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
		recordModelMeshRouteDecision(route, textureIdentity, "", -1, rustSelected, rustQueued, javaDrawn);
	}

	/**
	 * Records the semantic Java model class with a bounded route receipt. The
	 * class name is diagnostics-only: it lets selected-source coverage avoid
	 * reporting an already queued model as unavailable when its count-only
	 * replay intentionally has no sprite object.
	 */
	public static void recordModelMeshRouteDecision(
		String route,
		ResourceLocation textureIdentity,
		String modelClass,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
		recordModelMeshRouteDecision(route, textureIdentity, modelClass, -1, rustSelected, rustQueued, javaDrawn);
	}

	/** Diagnostics-only entity identity used to reject contaminated fixture work. */
	public static void recordModelMeshRouteDecision(
		String route,
		ResourceLocation textureIdentity,
		String modelClass,
		int entityId,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		synchronized (LOCK) {
			if (MODEL_MESH_ROUTE_DECISIONS.size() >= 512) {
				MODEL_MESH_ROUTE_DECISIONS.remove(0);
			}
			MODEL_MESH_ROUTE_DECISIONS.add(new ModelMeshRouteDecision(
				DeterministicCameraCapture.currentRenderedFrameIndex(),
				route,
				textureIdentity == null ? "missing" : textureIdentity.toString(),
				modelClass == null ? "" : modelClass,
				entityId,
				rustSelected,
				rustQueued,
				javaDrawn
			));
		}
	}

	public static List<ModelMeshRouteDecision> modelMeshRouteDecisions() {
		synchronized (LOCK) {
			return List.copyOf(MODEL_MESH_ROUTE_DECISIONS);
		}
	}

	/**
	 * Returns true only for a same-frame model submit that was selected and
	 * queued by the real Rust path. This deliberately does not infer routing
	 * from a model type alone.
	 */
	public static boolean hasCurrentFrameRustModelMeshDecision(Model<?> model, TextureAtlasSprite sprite) {
		if (model == null || sprite == null || sprite.contents().name() == null) {
			return false;
		}
		return hasCurrentFrameRustModelMeshDecision(model, sprite.contents().name());
	}

	/** Same-frame route proof for direct-texture model submits. */
	public static boolean hasCurrentFrameRustModelMeshDecision(Model<?> model, ResourceLocation textureIdentity) {
		if (model == null || textureIdentity == null) {
			return false;
		}
		synchronized (LOCK) {
			return PENDING_MODEL_MESH_SEMANTICS.contains(new ModelMeshSemanticIdentity(
				model.getClass().getName(), textureIdentity
			));
		}
	}

	/** Capture-only receipt emitted from the real ModelPart producer boundary. */
	public static void recordModelPartMeshTraversal(
		String route,
		String eligibility,
		ResourceLocation textureIdentity,
		String renderTypeIdentity
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		synchronized (LOCK) {
			if (MODEL_PART_MESH_TRAVERSAL_DIAGNOSTICS.size() >= 512) {
				MODEL_PART_MESH_TRAVERSAL_DIAGNOSTICS.remove(0);
			}
			MODEL_PART_MESH_TRAVERSAL_DIAGNOSTICS.add(new ModelPartMeshTraversalDiagnostic(
				DeterministicCameraCapture.currentRenderedFrameIndex(),
				route,
				eligibility,
				textureIdentity == null ? "missing" : textureIdentity.toString(),
				renderTypeIdentity == null ? "missing" : renderTypeIdentity
			));
		}
	}

	public static List<ModelPartMeshTraversalDiagnostic> modelPartMeshTraversalDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(MODEL_PART_MESH_TRAVERSAL_DIAGNOSTICS);
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
		recordMovingBlockDiagnostic(route, "falling-block", blockState, meshKey, meshGeneration, asset, transform, viewportWidth, viewportHeight);
	}

	private static void recordMovingBlockDiagnostic(
		String route,
		String provenance,
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
		ProjectedBounds projectedBounds = projectMeshBounds(asset.vertices(), transform, viewportWidth, viewportHeight);
		String blockId = blockState.getBlock().builtInRegistryHolder().key().location().toString();
		String textureIds = meshTextureIds(asset);
		int materialMode = asset.sections().isEmpty() ? 0 : asset.sections().get(0).materialMode();
		MovingBlockDiagnostic diagnostic = new MovingBlockDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(),
			route,
			provenance,
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
			projectedBounds.bottom(),
			transform[12],
			transform[13],
			transform[14]
		);
		if (MOVING_BLOCK_DIAGNOSTICS.size() >= 512) {
			MOVING_BLOCK_DIAGNOSTICS.remove(0);
		}
		MOVING_BLOCK_DIAGNOSTICS.add(diagnostic);
		if ("falling-block".equals(provenance)) {
			if (FALLING_BLOCK_DIAGNOSTICS.size() >= 512) {
				FALLING_BLOCK_DIAGNOSTICS.remove(0);
			}
			FALLING_BLOCK_DIAGNOSTICS.add(new FallingBlockDiagnostic(
				diagnostic.frameIndex(),
				diagnostic.route(),
				diagnostic.blockId(),
				diagnostic.meshKey(),
				diagnostic.meshGeneration(),
				diagnostic.vertexLayoutVersion(),
				diagnostic.indexType(),
				diagnostic.vertexCount(),
				diagnostic.indexBytes(),
				diagnostic.sectionCount(),
				diagnostic.textureIds(),
				diagnostic.materialMode(),
				diagnostic.viewportWidth(),
				diagnostic.viewportHeight(),
				diagnostic.projected(),
				diagnostic.screenLeft(),
				diagnostic.screenTop(),
				diagnostic.screenRight(),
				diagnostic.screenBottom()
			));
		}
	}

	public static List<FallingBlockDiagnostic> fallingBlockDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(FALLING_BLOCK_DIAGNOSTICS);
		}
	}

	public static List<MovingBlockDiagnostic> movingBlockDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(MOVING_BLOCK_DIAGNOSTICS);
		}
	}

	/**
	 * Capture-only receipt for moving mesh instances that reached one completed
	 * Rust whole-frame submission. Producer labels are kept alongside the
	 * semantic frame records and never cross the FFI boundary.
	 */
	public static void recordWholeFrameMovingMeshExecution(long frameId, long submissionId, PrimitiveFrame frame) {
		if (frame == null || frame.meshProducerLabels().isEmpty()) {
			return;
		}
		Map<String, Integer> instancesByProducer = new LinkedHashMap<>();
		int count = Math.min(frame.meshProducerLabels().size(), frame.meshInstances().size());
		for (int index = 0; index < count; index++) {
			VulkanicGalBridge.WorldMeshInstanceRecord instance = frame.meshInstances().get(index);
			String producer = frame.meshProducerLabels().get(index);
			boolean movingMesh = instance.stratum() == STRATUM_WORLD_MOVING_MESH
				&& ("falling-block".equals(producer)
					|| "piston".equals(producer)
					|| "primed-tnt".equals(producer));
			boolean entityMesh = instance.stratum() == STRATUM_WORLD_ENTITY_MESH
				&& ("arrow".equals(producer) || "item-entity".equals(producer)
					|| "model".equals(producer) || "model-part".equals(producer));
			if (!movingMesh && !entityMesh) {
				continue;
			}
			instancesByProducer.merge(producer, 1, Integer::sum);
		}
		// The coordinator calls this only after the owning Rust whole-frame
		// submission succeeds. Keep the bounded receipt even while a deterministic
		// capture is promoting its first ready frame; otherwise the producer gate
		// can wait for evidence that it discarded before becoming active.
		if (instancesByProducer.isEmpty()) {
			return;
		}
		synchronized (LOCK) {
			for (Map.Entry<String, Integer> entry : instancesByProducer.entrySet()) {
				if (MOVING_MESH_EXECUTION_DIAGNOSTICS.size() >= 128) {
					MOVING_MESH_EXECUTION_DIAGNOSTICS.remove(0);
				}
				MOVING_MESH_EXECUTION_DIAGNOSTICS.add(new MovingMeshExecutionDiagnostic(
					DeterministicCameraCapture.currentInProgressRenderedFrameIndex(),
					"rust-vulkan-whole-frame",
					entry.getKey(),
					frameId,
					submissionId,
					entry.getValue()
				));
			}
		}
	}

	public static List<MovingMeshExecutionDiagnostic> movingMeshExecutionDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(MOVING_MESH_EXECUTION_DIAGNOSTICS);
		}
	}

	private static void recordEntityFlameSemanticDiagnostic(int flameSubmits, int quads) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics()) {
			return;
		}
		synchronized (LOCK) {
			if (ENTITY_FLAME_SEMANTIC_DIAGNOSTICS.size() >= 128) {
				ENTITY_FLAME_SEMANTIC_DIAGNOSTICS.remove(0);
			}
			ENTITY_FLAME_SEMANTIC_DIAGNOSTICS.add(new EntityFlameSemanticDiagnostic(
				DeterministicCameraCapture.currentInProgressRenderedFrameIndex(),
				"rust-vulkan-whole-frame", flameSubmits, quads
			));
		}
	}

	/** Capture-only receipt for fire quads carried by a completed generic material submission. */
	public static void recordWholeFrameEntityFlameExecution(long frameId, long submissionId, int quads) {
		if (quads <= 0 || !DeterministicCameraCapture.isActiveForDiagnostics()) {
			return;
		}
		synchronized (LOCK) {
			if (ENTITY_FLAME_EXECUTION_DIAGNOSTICS.size() >= 128) {
				ENTITY_FLAME_EXECUTION_DIAGNOSTICS.remove(0);
			}
			ENTITY_FLAME_EXECUTION_DIAGNOSTICS.add(new EntityFlameExecutionDiagnostic(
				DeterministicCameraCapture.currentInProgressRenderedFrameIndex(),
				"rust-vulkan-whole-frame", frameId, submissionId, quads
			));
		}
	}

	public static List<EntityFlameSemanticDiagnostic> entityFlameSemanticDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(ENTITY_FLAME_SEMANTIC_DIAGNOSTICS);
		}
	}

	public static List<EntityFlameExecutionDiagnostic> entityFlameExecutionDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(ENTITY_FLAME_EXECUTION_DIAGNOSTICS);
		}
	}

	private static void recordEntityShadowSemanticDiagnostic(int shadowSubmits, int quads) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics()) {
			return;
		}
		synchronized (LOCK) {
			if (ENTITY_SHADOW_SEMANTIC_DIAGNOSTICS.size() >= 128) {
				ENTITY_SHADOW_SEMANTIC_DIAGNOSTICS.remove(0);
			}
			ENTITY_SHADOW_SEMANTIC_DIAGNOSTICS.add(new EntityShadowSemanticDiagnostic(
				DeterministicCameraCapture.currentInProgressRenderedFrameIndex(),
				"rust-vulkan-whole-frame", shadowSubmits, quads
			));
		}
	}

	/** Capture-only receipt for entity-shadow quads carried by the generic material submission. */
	public static void recordWholeFrameEntityShadowExecution(
		long frameId, long submissionId, List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads
	) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics() || materialQuads == null) {
			return;
		}
		int quads = 0;
		for (VulkanicGalBridge.WorldMaterialQuadRecord quad : materialQuads) {
			if (quad.textureId() == MATERIAL_TEXTURE_ENTITY_SHADOW
				&& quad.sourceProgram() == MATERIAL_SOURCE_TEXTURED
				&& quad.materialMode() == MATERIAL_MODE_TRANSLUCENT) {
				quads++;
			}
		}
		if (quads == 0) {
			return;
		}
		synchronized (LOCK) {
			if (ENTITY_SHADOW_EXECUTION_DIAGNOSTICS.size() >= 128) {
				ENTITY_SHADOW_EXECUTION_DIAGNOSTICS.remove(0);
			}
			ENTITY_SHADOW_EXECUTION_DIAGNOSTICS.add(new EntityShadowExecutionDiagnostic(
				DeterministicCameraCapture.currentInProgressRenderedFrameIndex(),
				"rust-vulkan-whole-frame", frameId, submissionId, quads
			));
		}
	}

	public static List<EntityShadowSemanticDiagnostic> entityShadowSemanticDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(ENTITY_SHADOW_SEMANTIC_DIAGNOSTICS);
		}
	}

	public static List<EntityShadowExecutionDiagnostic> entityShadowExecutionDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(ENTITY_SHADOW_EXECUTION_DIAGNOSTICS);
		}
	}

	private static void recordEntityLeashSemanticDiagnostic(int leashSubmits, int quads) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics()) {
			return;
		}
		synchronized (LOCK) {
			if (ENTITY_LEASH_SEMANTIC_DIAGNOSTICS.size() >= 128) {
				ENTITY_LEASH_SEMANTIC_DIAGNOSTICS.remove(0);
			}
			ENTITY_LEASH_SEMANTIC_DIAGNOSTICS.add(new EntityLeashSemanticDiagnostic(
				DeterministicCameraCapture.currentInProgressRenderedFrameIndex(),
				"rust-vulkan-whole-frame", leashSubmits, quads
			));
		}
	}

	/** Capture-only receipt for real leash quads carried by the generic material submission. */
	public static void recordWholeFrameEntityLeashExecution(
		long frameId, long submissionId, List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads
	) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics() || materialQuads == null) {
			return;
		}
		int quads = 0;
		for (VulkanicGalBridge.WorldMaterialQuadRecord quad : materialQuads) {
			if (quad.textureId() == MATERIAL_TEXTURE_GENERATED_WHITE
				&& quad.sourceProgram() == MATERIAL_SOURCE_TEXTURED
				&& quad.materialMode() == MATERIAL_MODE_OPAQUE
				&& quad.hasVertexModulation()) {
				quads++;
			}
		}
		if (quads == 0) {
			return;
		}
		synchronized (LOCK) {
			if (ENTITY_LEASH_EXECUTION_DIAGNOSTICS.size() >= 128) {
				ENTITY_LEASH_EXECUTION_DIAGNOSTICS.remove(0);
			}
			ENTITY_LEASH_EXECUTION_DIAGNOSTICS.add(new EntityLeashExecutionDiagnostic(
				DeterministicCameraCapture.currentInProgressRenderedFrameIndex(),
				"rust-vulkan-whole-frame", frameId, submissionId, quads
			));
		}
	}

	public static List<EntityLeashSemanticDiagnostic> entityLeashSemanticDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(ENTITY_LEASH_SEMANTIC_DIAGNOSTICS);
		}
	}

	public static List<EntityLeashExecutionDiagnostic> entityLeashExecutionDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(ENTITY_LEASH_EXECUTION_DIAGNOSTICS);
		}
	}

	public static void recordMovingBlockShellScan(
		String route,
		int visiblePistonStates,
		boolean fallbackUsed,
		int chunksScanned,
		int blockEntitiesInspected,
		int pistonEntitiesFound,
		int pistonStatesExtracted,
		long elapsedNanos
	) {
		if (!Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics")) {
			return;
		}
		synchronized (LOCK) {
			if (MOVING_BLOCK_SHELL_SCAN_DIAGNOSTICS.size() >= 256) {
				MOVING_BLOCK_SHELL_SCAN_DIAGNOSTICS.remove(0);
			}
			MOVING_BLOCK_SHELL_SCAN_DIAGNOSTICS.add(new MovingBlockShellScanDiagnostic(
				DeterministicCameraCapture.currentRenderedFrameIndex(),
				route,
				visiblePistonStates,
				fallbackUsed,
				chunksScanned,
				blockEntitiesInspected,
				pistonEntitiesFound,
				pistonStatesExtracted,
				elapsedNanos
			));
		}
		GraphicsFrameBenchmark.recordMovingBlockShellScan(
			route,
			visiblePistonStates,
			fallbackUsed,
			chunksScanned,
			blockEntitiesInspected,
			pistonEntitiesFound,
			pistonStatesExtracted,
			elapsedNanos
		);
	}

	public static List<MovingBlockShellScanDiagnostic> movingBlockShellScanDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(MOVING_BLOCK_SHELL_SCAN_DIAGNOSTICS);
		}
	}

	public static void recordFallingBlockRouteDecision(
		String route,
		BlockState blockState,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
		recordMovingBlockRouteDecision("falling-block", route, blockState, rustSelected, rustQueued, javaDrawn);
	}

	public static void recordMovingBlockRouteDecision(
		String provenance,
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
			if (MOVING_BLOCK_ROUTE_DECISIONS.size() >= 512) {
				MOVING_BLOCK_ROUTE_DECISIONS.remove(0);
			}
			MovingBlockRouteDecision decision = new MovingBlockRouteDecision(
				DeterministicCameraCapture.currentRenderedFrameIndex(),
				provenance,
				route,
				blockId,
				rustSelected,
				rustQueued,
				javaDrawn
			);
			MOVING_BLOCK_ROUTE_DECISIONS.add(decision);
			if ("falling-block".equals(provenance)) {
				if (FALLING_BLOCK_ROUTE_DECISIONS.size() >= 512) {
					FALLING_BLOCK_ROUTE_DECISIONS.remove(0);
				}
				FALLING_BLOCK_ROUTE_DECISIONS.add(new FallingBlockRouteDecision(
					decision.frameIndex(),
					decision.route(),
					decision.blockId(),
					decision.rustSelected(),
					decision.rustQueued(),
					decision.javaDrawn()
				));
			}
		}
	}

	public static List<FallingBlockRouteDecision> fallingBlockRouteDecisions() {
		synchronized (LOCK) {
			return List.copyOf(FALLING_BLOCK_ROUTE_DECISIONS);
		}
	}

	public static List<MovingBlockRouteDecision> movingBlockRouteDecisions() {
		synchronized (LOCK) {
			return List.copyOf(MOVING_BLOCK_ROUTE_DECISIONS);
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

	/**
	 * Collects vanilla's extracted rain/snow columns for the single Rust
	 * whole-frame semantic submission. This never draws and uses no Java/Iris
	 * renderer state; the later Rust frame route owns resources and execution.
	 */
	public static void enqueueWorldWeather(WeatherRenderState state, Vec3 cameraPos, boolean depthWrite) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentWeatherRoute();
		if (state != null) {
			recordWeatherTraversalDiagnostic(route, state.rainColumns.size(), state.snowColumns.size(), state.intensity);
		}
		if (!route.usesRustWholeFrameVulkan() || state == null || cameraPos == null) {
			return;
		}
		synchronized (LOCK) {
			int rainColumns = state.rainColumns.size();
			int snowColumns = state.snowColumns.size();
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0 || state.intensity <= 0.0F) {
				recordWeatherSemanticDiagnostic(rainColumns, snowColumns, 0, state.intensity, depthWrite);
				return;
			}
			List<VulkanicGalBridge.WorldMaterialQuadRecord> quads = weatherColumns(
				state,
				cameraPos,
				depthWrite,
				viewportWidth,
				viewportHeight
			);
			if (quads.isEmpty()) {
				recordWeatherSemanticDiagnostic(rainColumns, snowColumns, 0, state.intensity, depthWrite);
				return;
			}
			PENDING_MATERIAL_QUADS.addAll(quads);
			recordWeatherSemanticDiagnostic(rainColumns, snowColumns, quads.size(), state.intensity, depthWrite);
			DeterministicCameraCapture.recordSubmittedWorkIdentity("weather", "rust-vulkan-whole-frame:rain=" + rainColumns + ":snow=" + snowColumns);
			auditMessage("Rust VulkanicGAL weather semantic request"
				+ " route=rust-vulkan-whole-frame"
				+ " rain_columns=" + state.rainColumns.size()
				+ " snow_columns=" + state.snowColumns.size()
				+ " quads=" + quads.size()
				+ " depth_write=" + depthWrite
				+ " result=queued");
		}
	}

	/**
	 * Copies the decoded vanilla cloud-cell field into ordinary world-material
	 * faces. The cell field is semantic data only; Java-owned cloud buffers and
	 * pipelines never cross this boundary.
	 */
	public static void enqueueWorldCloudFaces(
		long[] cells,
		int textureWidth,
		int textureHeight,
		int cloudColorArgb,
		boolean fancy,
		int cameraRelation,
		int centerCellX,
		int centerCellZ,
		float offsetX,
		float verticalOffset,
		float offsetZ,
		int radius
	) {
		WorldRenderRoutePolicy.Route route = WorldRenderRoutePolicy.currentCloudRoute();
		recordCloudTraversalDiagnostic(route, cells == null ? 0 : cells.length, radius, fancy);
		if (!route.usesRustWholeFrameVulkan()) {
			return;
		}
		if (cells == null || textureWidth <= 0 || textureHeight <= 0 || cells.length != textureWidth * textureHeight) {
			throw new IllegalStateException("Rust VulkanicGAL cloud route selected with invalid copied cloud-cell semantics");
		}
		if (cameraRelation < 0 || cameraRelation > 2 || radius < 0
			|| !Float.isFinite(offsetX) || !Float.isFinite(verticalOffset) || !Float.isFinite(offsetZ)) {
			throw new IllegalStateException("Rust VulkanicGAL cloud route selected with invalid cloud frame semantics");
		}
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				throw new IllegalStateException("Rust VulkanicGAL cloud route requires a seeded world primitive frame");
			}
			List<VulkanicGalBridge.WorldMaterialQuadRecord> quads = new ArrayList<>();
			for (int ring = 0; ring <= radius * 2; ring++) {
				for (int dx = -ring; dx <= ring; dx++) {
					int dz = ring - Math.abs(dx);
					if (dz < 0 || dz > radius || dx * dx + dz * dz > radius * radius) {
						continue;
					}
					if (dz != 0) {
						appendCloudCellFaces(quads, cells, textureWidth, textureHeight, cloudColorArgb, fancy, cameraRelation, centerCellX, centerCellZ, dx, -dz, offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
					}
					appendCloudCellFaces(quads, cells, textureWidth, textureHeight, cloudColorArgb, fancy, cameraRelation, centerCellX, centerCellZ, dx, dz, offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
					if (quads.size() + PENDING_MATERIAL_QUADS.size() > MAX_RUST_WORLD_MATERIAL_QUADS) {
						throw new IllegalStateException(
							"Rust VulkanicGAL cloud route exceeds bounded material-quad frame capacity " + MAX_RUST_WORLD_MATERIAL_QUADS
								+ " faces=" + quads.size() + " radius=" + radius
						);
					}
				}
			}
			if (!quads.isEmpty()) {
				PENDING_MATERIAL_QUADS.addAll(quads);
				recordCloudSemanticDiagnostic(cells.length, radius, quads.size(), fancy);
				DeterministicCameraCapture.recordSubmittedWorkIdentity("clouds", "rust-vulkan-whole-frame:faces=" + quads.size());
				auditMessage("Rust VulkanicGAL cloud semantic request route=rust-vulkan-whole-frame cells="
					+ cells.length + " faces=" + quads.size() + " radius=" + radius + " result=queued");
			} else {
				recordCloudSemanticDiagnostic(cells.length, radius, 0, fancy);
			}
		}
	}

	private static void appendCloudCellFaces(
		List<VulkanicGalBridge.WorldMaterialQuadRecord> quads,
		long[] cells,
		int textureWidth,
		int textureHeight,
		int cloudColorArgb,
		boolean fancy,
		int cameraRelation,
		int centerCellX,
		int centerCellZ,
		int dx,
		int dz,
		float offsetX,
		float verticalOffset,
		float offsetZ,
		int viewportWidth,
		int viewportHeight
	) {
		long cell = cells[Math.floorMod(centerCellX + dx, textureWidth) + Math.floorMod(centerCellZ + dz, textureHeight) * textureWidth];
		if (cell == 0L) {
			return;
		}
		int colorArgb = ARGB.color(
			Mth.clamp(Math.round(ARGB.alpha(cloudColorArgb) * 0.8F), 0, 255),
			ARGB.red(cloudColorArgb),
			ARGB.green(cloudColorArgb),
			ARGB.blue(cloudColorArgb)
		);
		if (!fancy) {
			appendCloudFace(quads, dx, dz, 0, false, colorArgb, offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
			return;
		}
		if (cameraRelation != 2) {
			appendCloudFace(quads, dx, dz, 1, false, cloudFaceColor(colorArgb, 1.0F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
		}
		if (cameraRelation != 0) {
			appendCloudFace(quads, dx, dz, 0, false, cloudFaceColor(colorArgb, 0.7F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
		}
		if ((cell & 8L) != 0L && dz > 0) appendCloudFace(quads, dx, dz, 2, false, cloudFaceColor(colorArgb, 0.8F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
		if ((cell & 2L) != 0L && dz < 0) appendCloudFace(quads, dx, dz, 3, false, cloudFaceColor(colorArgb, 0.8F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
		if ((cell & 1L) != 0L && dx > 0) appendCloudFace(quads, dx, dz, 4, false, cloudFaceColor(colorArgb, 0.9F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
		if ((cell & 4L) != 0L && dx < 0) appendCloudFace(quads, dx, dz, 5, false, cloudFaceColor(colorArgb, 0.9F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
		if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
			appendCloudFace(quads, dx, dz, 0, true, cloudFaceColor(colorArgb, 0.7F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
			appendCloudFace(quads, dx, dz, 1, true, cloudFaceColor(colorArgb, 1.0F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
			appendCloudFace(quads, dx, dz, 2, true, cloudFaceColor(colorArgb, 0.8F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
			appendCloudFace(quads, dx, dz, 3, true, cloudFaceColor(colorArgb, 0.8F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
			appendCloudFace(quads, dx, dz, 4, true, cloudFaceColor(colorArgb, 0.9F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
			appendCloudFace(quads, dx, dz, 5, true, cloudFaceColor(colorArgb, 0.9F), offsetX, verticalOffset, offsetZ, viewportWidth, viewportHeight);
		}
	}

	private static int cloudFaceColor(int colorArgb, float brightness) {
		return ARGB.color(
			ARGB.alpha(colorArgb),
			Mth.clamp(Math.round(ARGB.red(colorArgb) * brightness), 0, 255),
			Mth.clamp(Math.round(ARGB.green(colorArgb) * brightness), 0, 255),
			Mth.clamp(Math.round(ARGB.blue(colorArgb) * brightness), 0, 255)
		);
	}

	private static void appendCloudFace(
		List<VulkanicGalBridge.WorldMaterialQuadRecord> quads,
		int cellX,
		int cellZ,
		int face,
		boolean inside,
		int colorArgb,
		float offsetX,
		float verticalOffset,
		float offsetZ,
		int viewportWidth,
		int viewportHeight
	) {
		float x = cellX * CLOUD_CELL_WIDTH - offsetX;
		float z = cellZ * CLOUD_CELL_WIDTH - offsetZ;
		float y = verticalOffset;
		float x1 = x + CLOUD_CELL_WIDTH;
		float y1 = y + CLOUD_CELL_HEIGHT;
		float z1 = z + CLOUD_CELL_WIDTH;
		float[] vertices = switch (face) {
			case 0 -> new float[] {x1, y, z, x1, y, z1, x, y, z1, x, y, z};
			case 1 -> new float[] {x, y1, z, x, y1, z1, x1, y1, z1, x1, y1, z};
			case 2 -> new float[] {x, y, z, x, y1, z, x1, y1, z, x1, y, z};
			case 3 -> new float[] {x1, y, z1, x1, y1, z1, x, y1, z1, x, y, z1};
			case 4 -> new float[] {x, y, z1, x, y1, z1, x, y1, z, x, y, z};
			case 5 -> new float[] {x1, y, z, x1, y1, z, x1, y1, z1, x1, y, z1};
			default -> throw new IllegalArgumentException("unknown cloud face " + face);
		};
		if (inside) {
			for (int left = 0, right = 9; left < right; left += 3, right -= 3) {
				float px = vertices[left];
				float py = vertices[left + 1];
				float pz = vertices[left + 2];
				vertices[left] = vertices[right];
				vertices[left + 1] = vertices[right + 1];
				vertices[left + 2] = vertices[right + 2];
				vertices[right] = px;
				vertices[right + 1] = py;
				vertices[right + 2] = pz;
			}
		}
		quads.add(new VulkanicGalBridge.WorldMaterialQuadRecord(
			STRATUM_WORLD_MATERIAL,
			MATERIAL_ID_TRANSLUCENT_TEXTURED,
			MATERIAL_TEXTURE_GENERATED_WHITE,
			MATERIAL_MODE_TRANSLUCENT,
			DEPTH_POLICY_TEST_NO_WRITE,
			face == 0 && !inside ? CULL_NONE : CULL_BACK,
			WORLD_TOPOLOGY_TRIANGLES,
			WORLD_WINDING_CCW,
			colorArgb,
			vertices[0], vertices[1], vertices[2], vertices[3], vertices[4], vertices[5],
			vertices[6], vertices[7], vertices[8], vertices[9], vertices[10], vertices[11],
			0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F,
			viewportWidth,
			viewportHeight,
			MATERIAL_SOURCE_CLOUDS,
			MATERIAL_SOURCE_UV_LOCAL_TEXTURE,
			colorArgb,
			LightTexture.FULL_BRIGHT
		));
	}

	/** Capture-only receipt for the already-selected whole-frame submission. */
	public static void recordWholeFrameWeatherExecution(long frameId, long submissionId, List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads) {
		if (materialQuads == null || materialQuads.isEmpty()) {
			return;
		}
		int weatherQuads = 0;
		for (VulkanicGalBridge.WorldMaterialQuadRecord quad : materialQuads) {
			if (quad.sourceProgram() == MATERIAL_SOURCE_WEATHER) {
				weatherQuads++;
			}
		}
		if (weatherQuads == 0) {
			return;
		}
		recordWeatherExecutionDiagnostic("rust_vulkan_whole_frame", frameId, submissionId, weatherQuads);
	}

	/** Capture-only receipt for copied experience-orb billboards in the selected submission. */
	public static void recordWholeFrameExperienceOrbExecution(
		long frameId, long submissionId, List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads
	) {
		if (materialQuads == null || materialQuads.isEmpty()) {
			return;
		}
		int quads = 0;
		for (VulkanicGalBridge.WorldMaterialQuadRecord quad : materialQuads) {
			if (quad.textureId() == MATERIAL_TEXTURE_EXPERIENCE_ORB
				&& quad.materialMode() == MATERIAL_MODE_TRANSLUCENT) {
				quads++;
			}
		}
		if (quads == 0 || !DeterministicCameraCapture.isActiveForDiagnostics()) {
			return;
		}
		synchronized (LOCK) {
			if (EXPERIENCE_ORB_EXECUTION_DIAGNOSTICS.size() >= 128) {
				EXPERIENCE_ORB_EXECUTION_DIAGNOSTICS.remove(0);
			}
			EXPERIENCE_ORB_EXECUTION_DIAGNOSTICS.add(new ExperienceOrbExecutionDiagnostic(
				DeterministicCameraCapture.currentInProgressRenderedFrameIndex(),
				"rust-vulkan-whole-frame", frameId, submissionId, quads
			));
		}
	}

	public static List<ExperienceOrbExecutionDiagnostic> experienceOrbExecutionDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(EXPERIENCE_ORB_EXECUTION_DIAGNOSTICS);
		}
	}

	/** Capture-only receipt for copied beacon-beam quads in the selected submission. */
	public static void recordWholeFrameBeaconBeamExecution(
		long frameId, long submissionId, List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads
	) {
		if (materialQuads == null || materialQuads.isEmpty() || !DeterministicCameraCapture.isActiveForDiagnostics()) {
			return;
		}
		int quads = 0;
		for (VulkanicGalBridge.WorldMaterialQuadRecord quad : materialQuads) {
			if (quad.textureId() == MATERIAL_TEXTURE_BEACON_BEAM
				&& quad.materialMode() == MATERIAL_MODE_TRANSLUCENT) {
				quads++;
			}
		}
		if (quads == 0) {
			return;
		}
		synchronized (LOCK) {
			if (BEACON_BEAM_EXECUTION_DIAGNOSTICS.size() >= 128) {
				BEACON_BEAM_EXECUTION_DIAGNOSTICS.remove(0);
			}
			BEACON_BEAM_EXECUTION_DIAGNOSTICS.add(new BeaconBeamExecutionDiagnostic(
				DeterministicCameraCapture.currentInProgressRenderedFrameIndex(),
				"rust-vulkan-whole-frame", frameId, submissionId, quads
			));
		}
	}

	public static List<BeaconBeamExecutionDiagnostic> beaconBeamExecutionDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(BEACON_BEAM_EXECUTION_DIAGNOSTICS);
		}
	}

	/** Capture-only receipt for copied cloud faces in the selected submission. */
	public static void recordWholeFrameCloudExecution(long frameId, long submissionId, List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads) {
		if (materialQuads == null || materialQuads.isEmpty()) {
			return;
		}
		int cloudQuads = 0;
		for (VulkanicGalBridge.WorldMaterialQuadRecord quad : materialQuads) {
			if (quad.sourceProgram() == MATERIAL_SOURCE_CLOUDS) {
				cloudQuads++;
			}
		}
		if (cloudQuads == 0) {
			return;
		}
		recordCloudExecutionDiagnostic("rust_vulkan_whole_frame", frameId, submissionId, cloudQuads);
	}

	public static List<CloudTraversalDiagnostic> cloudTraversalDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(CLOUD_TRAVERSAL_DIAGNOSTICS);
		}
	}

	public static List<CloudSemanticDiagnostic> cloudSemanticDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(CLOUD_SEMANTIC_DIAGNOSTICS);
		}
	}

	public static List<CloudExecutionDiagnostic> cloudExecutionDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(CLOUD_EXECUTION_DIAGNOSTICS);
		}
	}

	private static void recordCloudTraversalDiagnostic(WorldRenderRoutePolicy.Route route, int cells, int radius, boolean fancy) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics()) {
			return;
		}
		synchronized (LOCK) {
			if (CLOUD_TRAVERSAL_DIAGNOSTICS.size() >= 128) CLOUD_TRAVERSAL_DIAGNOSTICS.remove(0);
			CLOUD_TRAVERSAL_DIAGNOSTICS.add(new CloudTraversalDiagnostic(
				DeterministicCameraCapture.currentRenderedFrameIndex(), route.name().toLowerCase(Locale.ROOT), cells, radius, fancy
			));
		}
	}

	private static void recordCloudSemanticDiagnostic(int cells, int radius, int quads, boolean fancy) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics()) {
			return;
		}
		if (CLOUD_SEMANTIC_DIAGNOSTICS.size() >= 128) CLOUD_SEMANTIC_DIAGNOSTICS.remove(0);
		CLOUD_SEMANTIC_DIAGNOSTICS.add(new CloudSemanticDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(), cells, radius, quads, fancy
		));
	}

	private static void recordCloudExecutionDiagnostic(String route, long frameId, long submissionId, int quads) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics() || quads <= 0) {
			return;
		}
		synchronized (LOCK) {
			if (CLOUD_EXECUTION_DIAGNOSTICS.size() >= 128) CLOUD_EXECUTION_DIAGNOSTICS.remove(0);
			CLOUD_EXECUTION_DIAGNOSTICS.add(new CloudExecutionDiagnostic(
				DeterministicCameraCapture.currentInProgressRenderedFrameIndex(), route, frameId, submissionId, quads
			));
		}
	}

	public static List<WeatherSemanticDiagnostic> weatherSemanticDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(WEATHER_SEMANTIC_DIAGNOSTICS);
		}
	}

	public static List<WeatherTraversalDiagnostic> weatherTraversalDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(WEATHER_TRAVERSAL_DIAGNOSTICS);
		}
	}

	public static List<WeatherExecutionDiagnostic> weatherExecutionDiagnostics() {
		synchronized (LOCK) {
			return List.copyOf(WEATHER_EXECUTION_DIAGNOSTICS);
		}
	}

	private static void recordWeatherSemanticDiagnostic(int rainColumns, int snowColumns, int quads, float intensity, boolean depthWrite) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics()) {
			return;
		}
		if (WEATHER_SEMANTIC_DIAGNOSTICS.size() >= 512) {
			WEATHER_SEMANTIC_DIAGNOSTICS.remove(0);
		}
		WEATHER_SEMANTIC_DIAGNOSTICS.add(new WeatherSemanticDiagnostic(
			DeterministicCameraCapture.currentRenderedFrameIndex(), rainColumns, snowColumns, quads, intensity, depthWrite
		));
	}

	private static void recordWeatherTraversalDiagnostic(
		WorldRenderRoutePolicy.Route route,
		int rainColumns,
		int snowColumns,
		float intensity
	) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics()) {
			return;
		}
		synchronized (LOCK) {
			if (WEATHER_TRAVERSAL_DIAGNOSTICS.size() >= 512) {
				WEATHER_TRAVERSAL_DIAGNOSTICS.remove(0);
			}
			WEATHER_TRAVERSAL_DIAGNOSTICS.add(new WeatherTraversalDiagnostic(
				DeterministicCameraCapture.currentRenderedFrameIndex(),
				route.name().toLowerCase(Locale.ROOT),
				rainColumns,
				snowColumns,
				intensity
			));
		}
	}

	private static void recordWeatherExecutionDiagnostic(String route, long frameId, long submissionId, int quads) {
		if (!DeterministicCameraCapture.isActiveForDiagnostics() || quads <= 0) {
			return;
		}
		synchronized (LOCK) {
			if (WEATHER_EXECUTION_DIAGNOSTICS.size() >= 512) {
				WEATHER_EXECUTION_DIAGNOSTICS.remove(0);
			}
			WEATHER_EXECUTION_DIAGNOSTICS.add(new WeatherExecutionDiagnostic(
				DeterministicCameraCapture.currentInProgressRenderedFrameIndex(), route, frameId, submissionId, quads
			));
		}
		DeterministicCameraCapture.recordSubmittedWorkIdentity("weather", route + ":executed=" + quads);
	}

	/**
	 * Copies vanilla's extracted rain/snow columns into the existing coarse
	 * material-quad family for the explicitly selected borrowed OpenGL route.
	 * Java provides game semantics only; Rust owns texture resources and draw
	 * execution.
	 */
	public static boolean renderOpenGlWeather(
		Minecraft minecraft,
		WeatherRenderState state,
		Vec3 cameraPos,
		boolean depthWrite
	) {
		if (!WorldRenderRoutePolicy.currentWeatherRoute().usesRustOpenGl()) {
			return false;
		}
		if (minecraft == null || state == null || cameraPos == null) {
			throw new IllegalArgumentException("Rust VulkanicGAL weather requires Minecraft, weather state, and camera position");
		}
		PrimitiveFrame frame;
		synchronized (LOCK) {
			int viewportWidth = pendingViewportWidth;
			int viewportHeight = pendingViewportHeight;
			if (viewportWidth <= 0 || viewportHeight <= 0) {
				throw new IllegalStateException("Rust OpenGL weather requires a seeded world primitive frame");
			}
			if (state.intensity <= 0.0F || (state.rainColumns.isEmpty() && state.snowColumns.isEmpty())) {
				return false;
			}
			List<VulkanicGalBridge.WorldMaterialQuadRecord> quads = weatherColumns(
				state,
				cameraPos,
				depthWrite,
				viewportWidth,
				viewportHeight
			);
			if (quads.isEmpty()) {
				return false;
			}
			recordWeatherSemanticDiagnostic(
				state.rainColumns.size(), state.snowColumns.size(), quads.size(), state.intensity, depthWrite
			);
			frame = new PrimitiveFrame(
				viewportWidth,
				viewportHeight,
				PENDING_VIEW.clone(),
				PENDING_PROJECTION.clone(),
				VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback(),
				List.of(),
				List.of(),
				List.of(),
				List.copyOf(quads),
				List.of()
			);
		}
		auditMessage("Rust VulkanicGAL weather semantic request"
			+ " route=rust-opengl"
			+ " rain_columns=" + state.rainColumns.size()
			+ " snow_columns=" + state.snowColumns.size()
			+ " quads=" + frame.materialQuads().size()
			+ " depth_write=" + depthWrite
			+ " result=queued");
		if (!RustGalFrameCoordinator.executeWorldPrimitiveFrame(minecraft, frame, "minecraft.world.weather")) {
			throw new IllegalStateException("Rust VulkanicGAL weather submission failed after Rust route selection");
		}
		recordWeatherExecutionDiagnostic("rust_opengl_borrowed_context", 0L, 0L, frame.materialQuads().size());
		return true;
	}

	private static List<VulkanicGalBridge.WorldMaterialQuadRecord> weatherColumns(
		WeatherRenderState state,
		Vec3 cameraPos,
		boolean depthWrite,
		int viewportWidth,
		int viewportHeight
	) {
		List<VulkanicGalBridge.WorldMaterialQuadRecord> quads = new ArrayList<>(state.rainColumns.size() + state.snowColumns.size());
		appendWeatherColumns(quads, state.rainColumns, cameraPos, state.radius, state.intensity, 1.0F, MATERIAL_TEXTURE_WEATHER_RAIN, depthWrite, viewportWidth, viewportHeight);
		appendWeatherColumns(quads, state.snowColumns, cameraPos, state.radius, state.intensity, 0.8F, MATERIAL_TEXTURE_WEATHER_SNOW, depthWrite, viewportWidth, viewportHeight);
		return quads;
	}

	private static void appendWeatherColumns(
		List<VulkanicGalBridge.WorldMaterialQuadRecord> quads,
		List<WeatherEffectRenderer.ColumnInstance> columns,
		Vec3 cameraPos,
		int radius,
		float intensity,
		float innerIntensity,
		int textureId,
		boolean depthWrite,
		int viewportWidth,
		int viewportHeight
	) {
		if (radius <= 0) {
			return;
		}
		int cameraBlockX = Mth.floor(cameraPos.x);
		int cameraBlockZ = Mth.floor(cameraPos.z);
		for (WeatherEffectRenderer.ColumnInstance column : columns) {
			int offsetX = column.x() - cameraBlockX;
			int offsetZ = column.z() - cameraBlockZ;
			float radialLength = Mth.length(offsetX, offsetZ);
			if (!(radialLength > 0.0F)) {
				// Vanilla's precomputed direction is non-finite at the exact
				// camera column. It cannot produce valid copied geometry.
				continue;
			}
			float centerX = (float)(column.x() + 0.5 - cameraPos.x);
			float centerZ = (float)(column.z() + 0.5 - cameraPos.z);
			float distanceSquared = centerX * centerX + centerZ * centerZ;
			float alpha = Mth.lerp(distanceSquared / (radius * radius), innerIntensity, 0.5F) * intensity;
			int colorArgb = ARGB.white(alpha);
			float halfX = -offsetZ / radialLength / 2.0F;
			float halfZ = offsetX / radialLength / 2.0F;
			float leftX = centerX - halfX;
			float rightX = centerX + halfX;
			float topY = (float)(column.topY() - cameraPos.y);
			float bottomY = (float)(column.bottomY() - cameraPos.y);
			float nearZ = centerZ - halfZ;
			float farZ = centerZ + halfZ;
			float minU = column.uOffset();
			float maxU = column.uOffset() + 1.0F;
			float minV = column.bottomY() * 0.25F + column.vOffset();
			float maxV = column.topY() * 0.25F + column.vOffset();
			quads.add(new VulkanicGalBridge.WorldMaterialQuadRecord(
				STRATUM_WORLD_MATERIAL,
				MATERIAL_ID_TRANSLUCENT_TEXTURED,
				textureId,
				MATERIAL_MODE_TRANSLUCENT,
				depthWrite ? DEPTH_POLICY_TEST_WRITE : DEPTH_POLICY_TEST_NO_WRITE,
				CULL_NONE,
				WORLD_TOPOLOGY_TRIANGLES,
				WORLD_WINDING_CCW,
				colorArgb,
				leftX, topY, nearZ,
				rightX, topY, farZ,
				rightX, bottomY, farZ,
				leftX, bottomY, nearZ,
				minU, minV,
				maxU, minV,
				maxU, maxV,
				minU, maxV,
				viewportWidth,
				viewportHeight,
				MATERIAL_SOURCE_WEATHER,
				MATERIAL_SOURCE_UV_LOCAL_TEXTURE,
				colorArgb,
				column.lightCoords()
			));
		}
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
			List<VulkanicGalBridge.WorldMeshInstanceRecord> instances = List.copyOf(PENDING_MESH_INSTANCES);
			PENDING_MESH_INSTANCES.clear();
			PENDING_MESH_PRODUCERS.clear();
			PENDING_MODEL_MESH_SEMANTICS.clear();
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
				instances
				);
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

	/**
	 * Bounded capture-only projection of copied world coordinates through the
	 * camera-relative semantic frame matrices. This exposes no renderer or
	 * backend state: the deterministic harness uses it only to crop the final
	 * game window around a known world-space test target.
	 */
	public static WorldPointProjection projectWorldPointForDiagnostics(double x, double y, double z) {
		synchronized (LOCK) {
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
				|| pendingViewportWidth <= 0 || pendingViewportHeight <= 0
				|| !pendingDiagnosticCameraOriginValid) {
				return WorldPointProjection.invalid();
			}
			ProjectedEndpoint projected = projectEndpointColumnVector(
				(float)(x - pendingDiagnosticCameraX),
				(float)(y - pendingDiagnosticCameraY),
				(float)(z - pendingDiagnosticCameraZ),
				new Matrix4f().set(PENDING_VIEW),
				new Matrix4f().set(PENDING_PROJECTION),
				pendingViewportWidth,
				pendingViewportHeight
			);
			boolean insideViewport = projected.valid()
				&& projected.screenX() >= 0.0F && projected.screenX() < pendingViewportWidth
				&& projected.screenY() >= 0.0F && projected.screenY() < pendingViewportHeight;
			return new WorldPointProjection(
				projected.screenX(),
				projected.screenY(),
				projected.clipX(),
				projected.clipY(),
				projected.clipZ(),
				projected.clipW(),
				insideViewport
			);
		}
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

	/** Semantic projection receipt for deterministic final-frame validation. */
	public record WorldPointProjection(
		float screenX,
		float screenY,
		float clipX,
		float clipY,
		float clipZ,
		float clipW,
		boolean insideViewport
	) {
		private static WorldPointProjection invalid() {
			return new WorldPointProjection(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, false);
		}
	}

	/** Bounded receipt for the real name-tag producer through the Rust text frame. */
	public record WorldTextDiagnostic(
		long semanticFrame,
		int visibleEntityStates,
		int nameTagCallbacks,
		int textCallbacks,
		int normalSubmits,
		int seeThroughSubmits,
		int polygonOffsetSubmits,
		int emittedQuads,
		int emittedImages,
		boolean fullySupported,
		int consumedQuads
	) {
		private static WorldTextDiagnostic empty() {
			return new WorldTextDiagnostic(-1L, 0, 0, 0, 0, 0, 0, 0, 0, false, 0);
		}

		private static WorldTextDiagnostic empty(long frame) {
			return new WorldTextDiagnostic(frame, 0, 0, 0, 0, 0, 0, 0, 0, true, 0);
		}

		private WorldTextDiagnostic withTraversal(long frame, int entities, int nameTagCallbacks, int textCallbacks) {
			return new WorldTextDiagnostic(frame, entities, nameTagCallbacks, textCallbacks, normalSubmits, seeThroughSubmits, polygonOffsetSubmits, emittedQuads, emittedImages, fullySupported, consumedQuads);
		}

		private WorldTextDiagnostic withSemanticSnapshot(
			long frame, int normal, int seeThrough, int polygonOffset, int quads, int images, boolean supported
		) {
			return new WorldTextDiagnostic(
				frame, visibleEntityStates, nameTagCallbacks, textCallbacks,
				normalSubmits + normal, seeThroughSubmits + seeThrough, polygonOffsetSubmits + polygonOffset,
				emittedQuads + quads, emittedImages + images, fullySupported && supported, consumedQuads
			);
		}

		private WorldTextDiagnostic withConsumed(long frame, int quads) {
			return new WorldTextDiagnostic(frame, visibleEntityStates, nameTagCallbacks, textCallbacks, normalSubmits, seeThroughSubmits, polygonOffsetSubmits, emittedQuads, emittedImages, fullySupported, quads);
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

	public record ArrowDiagnostic(
		long frameIndex,
		String route,
		String textureId,
		long meshKey,
		long meshGeneration,
		int vertexLayoutVersion,
		int indexType,
		int vertexCount,
		int indexBytes,
		int sectionCount,
		int materialMode,
		int packedLight,
		int viewportWidth,
		int viewportHeight,
		boolean projected,
		float screenLeft,
		float screenTop,
		float screenRight,
		float screenBottom
	) {
	}

	public record ArrowRouteDecision(
		long frameIndex,
		String route,
		String textureId,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
	}

	public record ItemEntityDiagnostic(
		long frameIndex,
		String route,
		String materialIdentity,
		long meshKey,
		long meshGeneration,
		int vertexLayoutVersion,
		int indexType,
		int vertexCount,
		int indexBytes,
		int sectionCount,
		int packedLight,
		int viewportWidth,
		int viewportHeight,
		boolean projected,
		float screenLeft,
		float screenTop,
		float screenRight,
		float screenBottom
	) {
	}

	public record ItemEntityRouteDecision(
		long frameIndex,
		String route,
		boolean eligible,
		String ineligibility,
		boolean wholeFrameAvailable,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
	}

	public record ExperienceOrbDiagnostic(
		long frameIndex,
		String route,
		int colorArgb,
		int packedLight,
		float minU,
		float maxU,
		float minV,
		float maxV,
		int viewportWidth,
		int viewportHeight,
		boolean projected,
		float screenLeft,
		float screenTop,
		float screenRight,
		float screenBottom
	) {
	}

	public record ExperienceOrbRouteDecision(
		long frameIndex,
		String route,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
	}

	public record ExperienceOrbExecutionDiagnostic(
		long deterministicFrameIndex,
		String route,
		long gameplayFrameId,
		long submissionId,
		int quads
	) {
	}

	public record BeaconBeamDiagnostic(
		long frameIndex,
		int colorArgb,
		int startY,
		int endY,
		float scroll,
		int viewportWidth,
		int viewportHeight,
		boolean projected,
		float screenLeft,
		float screenTop,
		float screenRight,
		float screenBottom
	) {
	}

	public record BeaconBeamExecutionDiagnostic(
		long deterministicFrameIndex,
		String route,
		long gameplayFrameId,
		long submissionId,
		int quads
	) {
	}

	/** Capture-only receipt for a copied ordinary {@code ModelPart} submit. */
	public record ModelMeshDiagnostic(
		long frameIndex,
		String route,
		int entityId,
		String semanticModelIdentity,
		String textureId,
		long meshKey,
		long meshGeneration,
		int vertexLayoutVersion,
		int indexType,
		int vertexCount,
		int indexBytes,
		int sectionCount,
		int sectionCullPolicy,
		int viewportWidth,
		int viewportHeight,
		boolean projected,
		float screenLeft,
		float screenTop,
		float screenRight,
		float screenBottom
	) {
	}

	public record ModelMeshRouteDecision(
		long frameIndex,
		String route,
		String textureId,
		String modelClass,
		int entityId,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
	}

	public record ModelPartMeshTraversalDiagnostic(
		long frameIndex,
		String route,
		String eligibility,
		String textureId,
		String renderType
	) {
	}

	public record MovingBlockDiagnostic(
		long frameIndex,
		String route,
		String provenance,
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
		float screenBottom,
		float transformX,
		float transformY,
		float transformZ
	) {
	}

	public record MovingMeshExecutionDiagnostic(
		long deterministicFrameIndex,
		String route,
		String provenance,
		long gameplayFrameId,
		long submissionId,
		int instances
	) {
	}

	public record EntityFlameSemanticDiagnostic(long frameIndex, String route, int flameSubmits, int quads) {
	}

	public record EntityFlameExecutionDiagnostic(
		long deterministicFrameIndex, String route, long gameplayFrameId, long submissionId, int quads
	) {
	}

	public record EntityShadowSemanticDiagnostic(long frameIndex, String route, int shadowSubmits, int quads) {
	}

	public record EntityShadowExecutionDiagnostic(
		long deterministicFrameIndex, String route, long gameplayFrameId, long submissionId, int quads
	) {
	}

	public record EntityLeashSemanticDiagnostic(long frameIndex, String route, int leashSubmits, int quads) {
	}

	public record EntityLeashExecutionDiagnostic(
		long deterministicFrameIndex, String route, long gameplayFrameId, long submissionId, int quads
	) {
	}

	public record MovingBlockShellScanDiagnostic(
		long frameIndex,
		String route,
		int visiblePistonStates,
		boolean fallbackUsed,
		int chunksScanned,
		int blockEntitiesInspected,
		int pistonEntitiesFound,
		int pistonStatesExtracted,
		long elapsedNanos
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

	public record MovingBlockRouteDecision(
		long frameIndex,
		String provenance,
		String route,
		String blockId,
		boolean rustSelected,
		boolean rustQueued,
		boolean javaDrawn
	) {
	}

	public record WeatherSemanticDiagnostic(
		long frameIndex,
		int rainColumns,
		int snowColumns,
		int quads,
		float intensity,
		boolean depthWrite
	) {
	}

	public record WeatherTraversalDiagnostic(
		long frameIndex,
		String route,
		int rainColumns,
		int snowColumns,
		float intensity
	) {
	}

	public record WeatherExecutionDiagnostic(
		long deterministicFrameIndex,
		String route,
		long gameplayFrameId,
		long submissionId,
		int quads
	) {
	}

	public record CloudTraversalDiagnostic(long frameIndex, String route, int cells, int radius, boolean fancy) {
	}

	public record CloudSemanticDiagnostic(long frameIndex, int cells, int radius, int quads, boolean fancy) {
	}

	public record CloudExecutionDiagnostic(long deterministicFrameIndex, String route, long gameplayFrameId, long submissionId, int quads) {
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
			List<VulkanicGalBridge.WorldMeshInstanceRecord> admittedMeshInstances = new ArrayList<>(PENDING_MESH_INSTANCES.size());
			List<String> meshProducerLabels = new ArrayList<>(PENDING_MESH_INSTANCES.size());
			for (int index = 0; index < PENDING_MESH_INSTANCES.size(); index++) {
				VulkanicGalBridge.WorldMeshInstanceRecord instance = PENDING_MESH_INSTANCES.get(index);
				if (!isWorldMeshInstanceUploadedLocked(instance)) {
					continue;
				}
				admittedMeshInstances.add(instance);
				meshProducerLabels.add(index < PENDING_MESH_PRODUCERS.size()
					? PENDING_MESH_PRODUCERS.get(index).diagnosticLabel()
					: PendingMeshProducer.UNKNOWN.diagnosticLabel());
			}
			List<VulkanicGalBridge.WorldMeshInstanceRecord> admittedFirstPersonInstances = new ArrayList<>(PENDING_FIRST_PERSON_MESH_INSTANCES.size());
			int admittedFirstPersonMainHandInstanceCount = 0;
			for (int index = 0; index < PENDING_FIRST_PERSON_MESH_INSTANCES.size(); index++) {
				VulkanicGalBridge.WorldMeshInstanceRecord instance = PENDING_FIRST_PERSON_MESH_INSTANCES.get(index);
				if (!isWorldMeshInstanceUploadedLocked(instance)) {
					continue;
				}
				admittedFirstPersonInstances.add(instance);
				if (index < pendingFirstPersonMainHandInstanceCount) {
					admittedFirstPersonMainHandInstanceCount++;
				}
			}
			VulkanicGalBridge.WorldFirstPersonFrameRecord firstPersonFrame = pendingFirstPersonFrame
				&& !admittedFirstPersonInstances.isEmpty()
				? new VulkanicGalBridge.WorldFirstPersonFrameRecord(
					true,
					true,
					admittedFirstPersonMainHandInstanceCount,
					PENDING_FIRST_PERSON_PROJECTION,
					PENDING_FIRST_PERSON_MODEL_VIEW
				)
				: VulkanicGalBridge.WorldFirstPersonFrameRecord.disabled();
			List<VulkanicGalBridge.WorldMeshInstanceRecord> firstPersonInstances = List.copyOf(admittedFirstPersonInstances);
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
				List.copyOf(PENDING_TEXT_QUADS),
				List.copyOf(admittedMeshInstances),
				meshProducerLabels,
				pendingVoxelVolumeFrame,
				pendingShaderEnvironmentFrame,
			pendingFeatureCoverage,
			DistantHorizonsSemanticCollector.consumeVisibleSegments(),
			DistantHorizonsSemanticCollector.consumeRenderFrame(),
			pendingEntityFlameQuadCount,
			firstPersonFrame,
			firstPersonInstances
			);
			worldTextDiagnostic = worldTextDiagnostic.withConsumed(semanticFrameSequence, frame.textQuads().size());
			PENDING_SEGMENTS.clear();
			PENDING_CRACK_QUADS.clear();
					PENDING_BORDER_QUADS.clear();
					PENDING_MATERIAL_QUADS.clear();
					pendingEntityFlameQuadCount = 0;
					PENDING_TEXT_QUADS.clear();
					PENDING_MESH_INSTANCES.clear();
					PENDING_MESH_PRODUCERS.clear();
					PENDING_FIRST_PERSON_MESH_INSTANCES.clear();
					pendingFirstPersonFrame = false;
					pendingFirstPersonMainHandInstanceCount = 0;
					PENDING_MODEL_MESH_SEMANTICS.clear();
					pendingBackground = VulkanicGalBridge.WorldBackgroundRecord.diagnosticFallback();
					pendingVoxelVolumeFrame = VulkanicGalBridge.WorldVoxelVolumeFrameRecord.disabled();
					pendingShaderEnvironmentFrame = VulkanicGalBridge.WorldShaderEnvironmentFrameRecord.disabled();
					pendingFeatureCoverage = VulkanicGalBridge.WorldFeatureCoverageRecord.empty();
					return frame;
			}
		}

	public static PrimitiveFrame withViewport(PrimitiveFrame frame, int viewportWidth, int viewportHeight) {
		if (frame == null || frame.viewportWidth() == viewportWidth && frame.viewportHeight() == viewportHeight) {
			return frame;
		}
		List<VulkanicGalBridge.WorldLineSegmentRecord> segments = new ArrayList<>(frame.segments().size());
		for (VulkanicGalBridge.WorldLineSegmentRecord segment : frame.segments()) {
			segments.add(new VulkanicGalBridge.WorldLineSegmentRecord(
				segment.stratum(),
				segment.style(),
				segment.depthPolicy(),
				segment.colorArgb(),
				segment.lineWidth(),
				segment.startX(),
				segment.startY(),
				segment.startZ(),
				segment.endX(),
				segment.endY(),
				segment.endZ(),
				viewportWidth,
				viewportHeight
			));
		}
		List<VulkanicGalBridge.WorldCrackQuadRecord> crackQuads = new ArrayList<>(frame.crackQuads().size());
		for (VulkanicGalBridge.WorldCrackQuadRecord quad : frame.crackQuads()) {
			crackQuads.add(new VulkanicGalBridge.WorldCrackQuadRecord(
				quad.stratum(),
				quad.stage(),
				quad.depthPolicy(),
				quad.blendPolicy(),
				quad.cullPolicy(),
				quad.colorArgb(),
				quad.vertices(),
				viewportWidth,
				viewportHeight
			));
		}
		List<VulkanicGalBridge.WorldBorderQuadRecord> borderQuads = new ArrayList<>(frame.borderQuads().size());
		for (VulkanicGalBridge.WorldBorderQuadRecord quad : frame.borderQuads()) {
			borderQuads.add(new VulkanicGalBridge.WorldBorderQuadRecord(
				quad.stratum(),
				quad.textureId(),
				quad.depthPolicy(),
				quad.blendPolicy(),
				quad.cullPolicy(),
				quad.colorArgb(),
				quad.borderSize(),
				quad.distanceToBorder(),
				quad.scrollU(),
				quad.scrollV(),
				quad.uvU(),
				quad.uvV(),
				quad.uvWidth(),
				quad.uvHeight(),
				quad.vertices(),
				viewportWidth,
				viewportHeight
			));
		}
		List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads = new ArrayList<>(frame.materialQuads().size());
		for (VulkanicGalBridge.WorldMaterialQuadRecord quad : frame.materialQuads()) {
			materialQuads.add(new VulkanicGalBridge.WorldMaterialQuadRecord(
				quad.stratum(),
				quad.materialId(),
				quad.textureId(),
				quad.materialMode(),
				quad.depthPolicy(),
				quad.cullPolicy(),
				quad.topology(),
				quad.winding(),
				quad.colorArgb(),
				quad.p0X(),
				quad.p0Y(),
				quad.p0Z(),
				quad.p1X(),
				quad.p1Y(),
				quad.p1Z(),
				quad.p2X(),
				quad.p2Y(),
				quad.p2Z(),
				quad.p3X(),
				quad.p3Y(),
				quad.p3Z(),
				quad.uv0U(),
				quad.uv0V(),
				quad.uv1U(),
				quad.uv1V(),
				quad.uv2U(),
				quad.uv2V(),
				quad.uv3U(),
				quad.uv3V(),
				viewportWidth,
				viewportHeight,
				quad.sourceProgram(),
				quad.sourceUvSpace(),
				quad.sourceColorArgb(),
				quad.packedLight(),
				quad.vertex0ColorArgb(),
				quad.vertex1ColorArgb(),
				quad.vertex2ColorArgb(),
				quad.vertex3ColorArgb(),
				quad.vertex0PackedLight(),
				quad.vertex1PackedLight(),
				quad.vertex2PackedLight(),
				quad.vertex3PackedLight()
			));
		}
		List<VulkanicGalBridge.WorldMeshInstanceRecord> meshInstances = new ArrayList<>(frame.meshInstances().size());
		for (VulkanicGalBridge.WorldMeshInstanceRecord instance : frame.meshInstances()) {
			meshInstances.add(new VulkanicGalBridge.WorldMeshInstanceRecord(
				instance.stratum(),
				instance.meshKey(),
				instance.meshGeneration(),
				instance.meshSectionIndex(),
				instance.depthPolicy(),
				instance.cullPolicy(),
				instance.winding(),
				instance.colorArgb(),
				instance.transform(),
				viewportWidth,
				viewportHeight,
				instance.entityId(),
				instance.entityColorArgb()
			));
		}
		VulkanicGalBridge.WorldBackgroundRecord background = frame.background();
		VulkanicGalBridge.WorldBackgroundRecord normalizedBackground = new VulkanicGalBridge.WorldBackgroundRecord(
			background.enabled(),
			background.skyType(),
			background.loadIntent(),
			background.storeIntent(),
			background.colorArgb(),
			viewportWidth,
			viewportHeight,
			background.skyVisible(),
			background.skySunriseOrSunset(),
			background.skyDarkDisc(),
			background.skySunAngle(),
			background.skyTimeOfDay(),
			background.skyRainBrightness(),
			background.skyStarBrightness(),
			background.skySunriseAndSunsetColorArgb(),
			background.skyMoonPhase(),
			background.skyEndFlashIntensity(),
			background.skyEndFlashXAngle(),
			background.skyEndFlashYAngle(),
			background.skyColorArgb()
		);
		return new PrimitiveFrame(
			viewportWidth,
			viewportHeight,
			frame.viewMatrix(),
			frame.projectionMatrix(),
			normalizedBackground,
			List.copyOf(segments),
			List.copyOf(crackQuads),
			List.copyOf(borderQuads),
			List.copyOf(materialQuads),
			List.copyOf(frame.textQuads()),
			List.copyOf(meshInstances),
			List.copyOf(frame.meshProducerLabels()),
			frame.voxelVolumeFrame(),
			frame.shaderEnvironmentFrame(),
			frame.featureCoverage(),
			frame.lodInstances(),
			frame.lodRenderFrame(),
			frame.entityFlameQuadCount(),
			frame.firstPersonFrame(),
			frame.firstPersonMeshInstances()
		);
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
		List<WorldTextSemanticCollector.WorldTextQuad> textQuads,
		List<VulkanicGalBridge.WorldMeshInstanceRecord> meshInstances,
		List<String> meshProducerLabels,
		VulkanicGalBridge.WorldVoxelVolumeFrameRecord voxelVolumeFrame,
		VulkanicGalBridge.WorldShaderEnvironmentFrameRecord shaderEnvironmentFrame,
		VulkanicGalBridge.WorldFeatureCoverageRecord featureCoverage,
		List<VulkanicGalBridge.WorldLodColumnInstanceRecord> lodInstances,
		VulkanicGalBridge.WorldLodRenderFrameRecord lodRenderFrame,
		int entityFlameQuadCount,
		VulkanicGalBridge.WorldFirstPersonFrameRecord firstPersonFrame,
		List<VulkanicGalBridge.WorldMeshInstanceRecord> firstPersonMeshInstances
	) {
		public PrimitiveFrame(
			int viewportWidth,
			int viewportHeight,
			float[] viewMatrix,
			float[] projectionMatrix,
			VulkanicGalBridge.WorldBackgroundRecord background,
			List<VulkanicGalBridge.WorldLineSegmentRecord> segments,
			List<VulkanicGalBridge.WorldCrackQuadRecord> crackQuads,
			List<VulkanicGalBridge.WorldBorderQuadRecord> borderQuads,
			List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads,
			List<WorldTextSemanticCollector.WorldTextQuad> textQuads,
			List<VulkanicGalBridge.WorldMeshInstanceRecord> meshInstances,
			List<String> meshProducerLabels,
			VulkanicGalBridge.WorldVoxelVolumeFrameRecord voxelVolumeFrame,
			VulkanicGalBridge.WorldShaderEnvironmentFrameRecord shaderEnvironmentFrame,
			VulkanicGalBridge.WorldFeatureCoverageRecord featureCoverage,
			List<VulkanicGalBridge.WorldLodColumnInstanceRecord> lodInstances,
			VulkanicGalBridge.WorldLodRenderFrameRecord lodRenderFrame
		) {
			this(
				viewportWidth, viewportHeight, viewMatrix, projectionMatrix, background, segments, crackQuads, borderQuads,
				materialQuads, textQuads, meshInstances, meshProducerLabels, voxelVolumeFrame, shaderEnvironmentFrame,
				featureCoverage, lodInstances, lodRenderFrame, 0,
				VulkanicGalBridge.WorldFirstPersonFrameRecord.disabled(), List.of()
			);
		}

		public PrimitiveFrame(
			int viewportWidth,
			int viewportHeight,
			float[] viewMatrix,
			float[] projectionMatrix,
			VulkanicGalBridge.WorldBackgroundRecord background,
			List<VulkanicGalBridge.WorldLineSegmentRecord> segments,
			List<VulkanicGalBridge.WorldCrackQuadRecord> crackQuads,
			List<VulkanicGalBridge.WorldBorderQuadRecord> borderQuads,
			List<VulkanicGalBridge.WorldMaterialQuadRecord> materialQuads,
			List<VulkanicGalBridge.WorldMeshInstanceRecord> meshInstances,
			List<String> meshProducerLabels,
			VulkanicGalBridge.WorldVoxelVolumeFrameRecord voxelVolumeFrame,
			VulkanicGalBridge.WorldShaderEnvironmentFrameRecord shaderEnvironmentFrame,
			VulkanicGalBridge.WorldFeatureCoverageRecord featureCoverage,
			List<VulkanicGalBridge.WorldLodColumnInstanceRecord> lodInstances,
			VulkanicGalBridge.WorldLodRenderFrameRecord lodRenderFrame
		) {
			this(
				viewportWidth, viewportHeight, viewMatrix, projectionMatrix, background, segments, crackQuads, borderQuads,
				materialQuads, List.of(), meshInstances, meshProducerLabels, voxelVolumeFrame, shaderEnvironmentFrame,
				featureCoverage, lodInstances, lodRenderFrame, 0,
				VulkanicGalBridge.WorldFirstPersonFrameRecord.disabled(), List.of()
			);
		}

		public PrimitiveFrame(
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
			this(
				viewportWidth,
				viewportHeight,
				viewMatrix,
				projectionMatrix,
				background,
				segments,
				crackQuads,
				borderQuads,
			materialQuads,
			List.of(),
			meshInstances,
			List.of(),
				VulkanicGalBridge.WorldVoxelVolumeFrameRecord.disabled(),
				VulkanicGalBridge.WorldShaderEnvironmentFrameRecord.disabled(),
				VulkanicGalBridge.WorldFeatureCoverageRecord.empty(),
				List.of(),
				VulkanicGalBridge.WorldLodRenderFrameRecord.disabled(),
				0,
				VulkanicGalBridge.WorldFirstPersonFrameRecord.disabled(),
				List.of()
			);
		}
	}

	private static boolean isWorldMeshInstanceUploadedLocked(VulkanicGalBridge.WorldMeshInstanceRecord instance) {
		return isWorldMeshGenerationAndTexturesUploadedLocked(instance.meshKey(), instance.meshGeneration());
	}

	private static boolean isWorldMeshGenerationAndTexturesUploadedLocked(long meshKey, long meshGeneration) {
		Long uploadedGeneration = UPLOADED_WORLD_MESH_GENERATIONS.get(meshKey);
		if (uploadedGeneration == null || uploadedGeneration.longValue() != meshGeneration) {
			return false;
		}
		VulkanicGalBridge.WorldMeshAssetRecord asset = WORLD_MESH_ASSETS.get(meshKey);
		if (asset == null || asset.meshGeneration() != meshGeneration) {
			return false;
		}
		for (VulkanicGalBridge.WorldMeshSectionRecord section : asset.sections()) {
			if (section.textureId() != 0
				&& (DIRTY_WORLD_MESH_TEXTURES.contains(section.textureId())
					|| !UPLOADED_WORLD_MESH_TEXTURES.contains(section.textureId()))) {
				return false;
			}
		}
		return true;
	}

	private record ModelMeshSemanticIdentity(String modelClass, ResourceLocation textureIdentity) {}

	private enum PendingMeshProducer {
		BLOCK_DISPLAY,
		FALLING_BLOCK,
		PISTON,
		PRIMED_TNT,
		ARROW,
		ITEM_ENTITY,
		MODEL,
		MODEL_PART,
		STATIC_TERRAIN,
		UNKNOWN;

		private String diagnosticLabel() {
			return switch (this) {
				case BLOCK_DISPLAY -> "block-display";
				case FALLING_BLOCK -> "falling-block";
				case PISTON -> "piston";
			case PRIMED_TNT -> "primed-tnt";
			case ARROW -> "arrow";
			case ITEM_ENTITY -> "item-entity";
			case MODEL -> "model";
			case MODEL_PART -> "model-part";
				case STATIC_TERRAIN -> "static-terrain";
				case UNKNOWN -> "unknown";
			};
		}
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

	public record WorldMeshAssetMetrics(
		long generation,
		long uploadedGeneration,
		long payloadCount,
		long payloadBytes,
		long failures,
		int cachedMeshes,
		int cachedTextures,
		int dirtyMeshes,
		int dirtyTextures,
		int pendingInstances,
		int uploadedMeshes,
		int uploadedTextures
	) {
	}

	public record StaticTerrainSortedIndexSnapshot(
		long meshKey,
		long meshGeneration,
		long indexGeneration,
		int indexType,
		int indexBytes,
		long indexHash
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
