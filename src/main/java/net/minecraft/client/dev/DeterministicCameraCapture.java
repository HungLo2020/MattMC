package net.minecraft.client.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.sodium.client.render.StaticTerrainParityDiagnostics;
import net.vulkanic.VulkanPerfAudit;
import org.joml.Vector4f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Frozen-repo benchmark hook used only for current-vs-Frozen OpenGL regression
 * measurements. It is inert unless mattmc.dev.deterministicCameraCapture=true.
 */
public final class DeterministicCameraCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("MattMC-FrozenDeterministicCapture");
    /**
     * Opt-in, bounded lifecycle evidence for a failed capture harness. This
     * observes only whether the existing hook ran and why it returned; it is
     * never consulted by Frozen's renderer or normal capture behavior.
     */
    private static final boolean HOOK_TRACE = Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.hookTrace");
    private static final AtomicInteger HOOK_TRACE_EVENTS = new AtomicInteger();
    private static final int HOOK_TRACE_MAX_EVENTS = 32;
    private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.deterministicCameraCapture");
    private static final int FRAMES_PER_POSE = Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.framesPerPose", 8));
    private static final int ACK_TIMEOUT_FRAMES = Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.ackTimeoutFrames", 600));
    private static final int POSE_COUNT = Math.max(1, Math.min(7, Integer.getInteger("mattmc.dev.deterministicCameraCapture.poseCount", 4)));
    private static final float YAW_DELTA = Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.yawDelta", "35.0"));
    private static final boolean STOP_AFTER_COMPLETE = Boolean.parseBoolean(System.getProperty("mattmc.dev.deterministicCameraCapture.stopAfterComplete", "true"));
    /** Capture-only shared-fixture clock; normal Frozen rendering never sets it. */
    private static final long FIXED_CAPTURE_TIME = Long.getLong(
        "mattmc.dev.deterministicCameraCapture.fixedTime",
        Long.MIN_VALUE
    );
    /** Capture-only cloud scroll phase shared with Current; NaN preserves vanilla timing. */
    private static final float FIXED_CLOUD_TIME = Float.parseFloat(
        System.getProperty("mattmc.dev.deterministicCameraCapture.fixedCloudTime", "NaN")
    );
    private static final boolean WAIT_FOR_STATIC_TERRAIN_PARITY =
        Boolean.getBoolean("mattmc.dev.staticTerrainParityDiagnostics.waitForStable");
    private static final int STATIC_TERRAIN_PARITY_READY_FRAMES = Math.max(
        1,
        Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.readyFrames", 3)
    );
    private static final int STATIC_TERRAIN_PARITY_MIN_SECTIONS = Math.max(
        1,
        Integer.getInteger("mattmc.dev.staticTerrainParityDiagnostics.minSections", 1)
    );
    private static final boolean PERFORMANCE_MODE = Boolean.parseBoolean(System.getProperty("mattmc.dev.deterministicCameraCapture.performanceMode", "false"));
    private static final int PERFORMANCE_WARMUP_FRAMES = Math.max(0, Integer.getInteger("mattmc.dev.deterministicCameraCapture.performanceWarmupFrames", 120));
    private static final int PERFORMANCE_MEASURE_FRAMES = Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.performanceMeasureFrames", 300));
    private static final String FORCED_CAMERA_TYPE = System.getProperty("mattmc.dev.deterministicCameraCapture.cameraType", "").trim();
    private static final String FORCED_GAME_MODE = System.getProperty("mattmc.dev.deterministicCameraCapture.gameMode", "").trim();
    private static final int FORCED_SELECTED_HOTBAR_SLOT = Integer.getInteger("mattmc.dev.deterministicCameraCapture.selectedHotbarSlot", 0);
    /** Capture-fixture input only: keeps Frozen's hotbar state equivalent to Current. */
    private static final boolean FORCE_EMPTY_SELECTED_HAND = Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.emptySelectedHand");
    /** Optional capture-only skin selection shared with Current parity fixtures. */
    private static final String FORCED_SELECTED_SKIN =
        System.getProperty("mattmc.dev.deterministicCameraCapture.selectedSkin", "").trim();
    /**
     * Capture-only inventory fixture shared with Current. It only supplies
     * identical semantic gameplay inputs to the two renderers; Frozen's Java
     * OpenGL rendering behavior remains the baseline under observation.
     */
    private static final String HOTBAR_ITEM_FIXTURE =
        System.getProperty("mattmc.dev.deterministicCameraCapture.hotbarItemFixture", "").trim().toLowerCase(Locale.ROOT);
    private static final int FORCED_ARMOR_VALUE = Integer.getInteger("mattmc.dev.deterministicCameraCapture.armorValue", -1);
    private static final boolean HIDE_CHAT = Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.hideChat");
    private static final String WORLD_NAME = System.getProperty("mattmc.dev.deterministicCameraCapture.world", "Origin");
    private static final String CAMERA_PATH_ID = System.getProperty("mattmc.dev.deterministicCameraCapture.cameraPathId", "origin-fixed-sweep-v1");
    private static final String WEATHER_SCENARIO = System.getProperty("mattmc.dev.rustGalWeather.scenario", "").trim().toLowerCase(Locale.ROOT);
    /** Capture-only cloud fixture selector; inert for ordinary Frozen runs. */
    private static final String CLOUD_SCENARIO = System.getProperty("mattmc.dev.rustGalClouds.scenario", "").trim().toLowerCase(Locale.ROOT);
    /** Capture-only gameplay setting shared with Current's bounded cloud fixture. */
    private static final int FORCED_CLOUD_RANGE_CHUNKS = Math.max(
        -1,
        Integer.getInteger("mattmc.dev.deterministicCameraCapture.cloudRangeChunks", -1)
    );

    /** The capture fixture owns only the temporary Minecraft quality setting. */
    private static CloudStatus forcedCloudStatus() {
        return switch (CLOUD_SCENARIO) {
            case "bounded" -> CloudStatus.FANCY;
            case "fast" -> CloudStatus.FAST;
            default -> null;
        };
    }
    /**
     * A copied-world/camera fixture requested by the cross-repository parity
     * harness. It is deliberately separate from every Frozen rendering
     * setting: this class only supplies equal gameplay inputs to the two
     * renderers while Frozen OpenGL remains the observed baseline.
     */
    private static final String STATIC_TERRAIN_FIXTURE = System.getProperty(
        "mattmc.dev.deterministicCameraCapture.staticTerrainFixture", ""
    ).trim().toLowerCase(Locale.ROOT);
    /** Capture-only input selector; Frozen OpenGL never reads it as render policy. */
    private static final String STATIC_TERRAIN_SCENARIO = System.getProperty(
        "mattmc.dev.rustGalStaticTerrain.scenario", ""
    ).trim().toLowerCase(Locale.ROOT);
    /**
     * Harness-only vanilla entity fixture. It supplies the same copied-world
     * cow as Current's model parity run; it is inert outside that explicit
     * capture property and never participates in Frozen renderer selection.
     */
    private static final String MODEL_MESH_SCENARIO = System.getProperty(
        "mattmc.dev.rustGalWorldMesh.modelScenario", ""
    ).trim().toLowerCase(Locale.ROOT);
    private static final double FIXED_X = doubleProperty("fixedX", 150.5);
    private static final double FIXED_Y = doubleProperty("fixedY", 100.0);
    private static final double FIXED_Z = doubleProperty("fixedZ", 530.5);
    private static final float FIXED_YAW = (float) doubleProperty("fixedYaw", 150.0);
    private static final float FIXED_PITCH = (float) doubleProperty("fixedPitch", 10.0);
    private static final Path METADATA_PATH = Path.of(System.getProperty(
        "mattmc.dev.deterministicCameraCapture.metadata",
        "run/deterministic_camera_capture.json"
    ));
    private static final Path SCREENSHOT_DIR = Path.of(System.getProperty(
        "mattmc.dev.deterministicCameraCapture.screenshotDir",
        "run/deterministic_camera_capture"
    ));
    private static final Path PERFORMANCE_STATUS_PATH = Path.of(System.getProperty(
        "mattmc.dev.deterministicCameraCapture.performanceStatus",
        "run/deterministic_performance_capture.json"
    ));
    private static final String BENCHMARK_FINGERPRINT_SCHEMA_VERSION = "2";
    private static final String HARNESS_VERSION = "rundevcapture-perf-matrix-v2";

    private static boolean initialized;
    private static boolean complete;
    private static boolean failed;
    private static boolean stopIssued;
    private static int renderedFramesAtPose;
    private static int poseIndex;
    private static int performanceFrames;
    private static long renderedFrameIndex;
    private static FrozenModelEmission lastCowModelEmission;

    /** Passive capture provenance from the live renderer device. */
    private static int terrainStagePixelObservations;
    private static int observedTerrainFramebuffer;
    private static int celestialStagePixelObservations;

    /** Capture-only, bounded observations; never selects or changes a draw state. */
    public static void observeCelestialStagePixels(String stage) {
        if (!ENABLED || complete || failed || !"opengl".equals(observedBackend())
            || !Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.celestialStagePixels")
            || renderedFrameIndex < 128 || renderedFrameIndex % 64 != 0
            || celestialStagePixelObservations >= 48) return;
        celestialStagePixelObservations++;
        var target = Minecraft.getInstance().getMainRenderTarget();
        if (!(target.getColorTexture() instanceof net.blaze3d.opengl.GlTexture texture)
            || org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING) != 0
            || org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_SKIP_PIXELS) != 0
            || org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_PACK_SKIP_ROWS) != 0
            || org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL12.GL_PACK_SKIP_IMAGES) != 0) {
            LOGGER.info("FrozenCelestialStagePixel unavailable stage={} reason=direct-texture-read-preconditions", stage);
            return;
        }
        int width = target.width;
        int height = target.height;
        int previousReadFramebuffer = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_READ_BUFFER);
        int probeFramebuffer = org.lwjgl.opengl.GL30.glGenFramebuffers();
        try {
            // This private diagnostic attachment survives pass-close detach.
            // Only READ_FRAMEBUFFER is touched; the draw binding is unchanged.
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, probeFramebuffer);
            org.lwjgl.opengl.GL30.glFramebufferTexture2D(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER,
                org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                net.vulkanic.VulkanicCoreAPI.textureId(texture), 0);
            org.lwjgl.opengl.GL11.glReadBuffer(org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0);
            if (org.lwjgl.opengl.GL30.glCheckFramebufferStatus(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER)
                != org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE) {
                LOGGER.info("FrozenCelestialStagePixel unavailable stage={} reason=incomplete-private-read-attachment", stage);
                return;
            }
            for (int[] point : new int[][] {{640, 238}, {640, 310}, {640, 360}, {500, 500}}) {
                int x = point[0] * width / 1280;
                int y = point[1] * height / 720;
                var pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
                org.lwjgl.opengl.GL11.glReadPixels(x, height - y - 1, 1, 1,
                    org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, pixel);
                LOGGER.info("FrozenCelestialStagePixel frame={} stage={} screen=({}, {}) texture={} rgba=({},{},{},{}) post={}",
                    renderedFrameIndex + 1, stage, x, y, net.vulkanic.VulkanicCoreAPI.textureId(texture),
                    pixel.get(0) & 255, pixel.get(1) & 255, pixel.get(2) & 255, pixel.get(3) & 255,
                    Minecraft.getInstance().gameRenderer.currentPostEffect());
            }
        } finally {
            org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            org.lwjgl.opengl.GL11.glReadBuffer(previousReadBuffer);
            org.lwjgl.opengl.GL30.glDeleteFramebuffers(probeFramebuffer);
        }
    }

    /** Bounded read-only samples from the terrain pass's current color attachment. */
    public static void observeTerrainStagePixels(String stage) {
        if (!ENABLED || complete || failed || !"opengl".equals(observedBackend())
            || !Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.terrainStagePixels")
            || renderedFrameIndex < 500 || renderedFrameIndex % 128 != 0
            || terrainStagePixelObservations >= 128) return;
        if (stage.startsWith("main-") && observedTerrainFramebuffer == 0) return;
        terrainStagePixelObservations++;
        int width = Minecraft.getInstance().getWindow().getWidth();
        int height = Minecraft.getInstance().getWindow().getHeight();
        for (int sampleX : new int[] {200, 400, 600}) {
            int x = sampleX * width / 1280;
            int y = 600 * height / 720;
            var probe = stage.startsWith("main-")
                ? net.vulkanic.VulkanicAPI.readFramebufferColorProbe(observedTerrainFramebuffer, x, height - y - 1)
                : net.vulkanic.VulkanicAPI.readDrawFramebufferAttachmentProbe(x, height - y - 1, 1);
            if ("after-solid".equals(stage)) observedTerrainFramebuffer = probe.drawFramebuffer();
            for (var attachment : probe.attachments()) {
                LOGGER.info("FrozenTerrainStagePixel frame={} stage={} screen=({}, {}) framebuffer={} texture={} buffer={} rgba=({},{},{},{})",
                    renderedFrameIndex + 1, stage, x, y, probe.drawFramebuffer(),
                    attachment.textureId(), attachment.drawBuffer(),
                    attachment.red(), attachment.green(), attachment.blue(), attachment.alpha());
            }
        }
    }

    private static String observedBackend() {
        // Capture provenance must describe the live device, not a requested JVM property.
        return net.vulkanic.VulkanicAPI.tryGetDevice() instanceof net.blaze3d.opengl.GlDevice
            ? "opengl" : "unknown";
    }

    /** Passive receipt after vanilla model emission; no renderer state is changed. */
    public static void observeModelEmission(Object model, Object state) {
        if (!ENABLED || complete || failed || !"cow".equals(MODEL_MESH_SCENARIO)
            || !(model instanceof net.minecraft.client.model.CowModel)
            || !(state instanceof net.minecraft.client.renderer.entity.state.CowRenderState cowState)) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Entity fixture = level == null ? null : level.getEntity(cowModelFixtureEntityId);
        if (!(fixture instanceof Cow) || cowState.entityType != EntityType.COW
            || Math.abs(cowState.x - fixture.getX()) > 0.0001
            || Math.abs(cowState.y - fixture.getY()) > 0.0001
            || Math.abs(cowState.z - fixture.getZ()) > 0.0001 || cowState.variant == null) {
            return;
        }
        lastCowModelEmission = new FrozenModelEmission(renderedFrameIndex + 1,
            fixture.getId(), cowState.variant.modelAndTexture().asset().texturePath().toString(),
            cowState.x, cowState.y, cowState.z);
    }

    private record FrozenModelEmission(long frameIndex, int entityId, String textureId, double x, double y, double z) {
        private String json() {
            return "{\"stage\":\"model-render-to-buffer-returned\",\"frameIndex\":" + frameIndex
                + ",\"fixtureEntityId\":" + entityId + ",\"textureId\":\"" + escape(textureId)
                + "\",\"entityType\":\"minecraft:cow\",\"position\":[" + format(x) + "," + format(y) + "," + format(z) + "]}";
        }
    }
    /** Last vanilla weather tick copied by the normal OpenGL render path; diagnostics only. */
    private static volatile int lastWeatherRendererTicks = Integer.MIN_VALUE;
    private static volatile float lastWeatherRendererPartialTick = Float.NaN;
    private static volatile String lastWeatherSemanticFingerprint = "";
    private static volatile String lastLightmapSemanticFingerprint = "";

    /** Records the vanilla weather animation input without influencing rendering. */
    public static void recordWeatherRendererTicks(int ticks) {
        if (ENABLED) {
            lastWeatherRendererTicks = ticks;
        }
    }

    /** Records the copied vanilla weather interpolation input without influencing rendering. */
    public static void recordWeatherRendererPartialTick(float partialTick) {
        if (ENABLED) {
            lastWeatherRendererPartialTick = partialTick;
        }
    }

    /** Records bounded pre-raster weather semantics for cross-repository diagnostics. */
    public static void recordWeatherSemanticFingerprint(String fingerprint) {
        if (ENABLED) {
            lastWeatherSemanticFingerprint = fingerprint == null ? "" : fingerprint;
        }
    }

    /** Records scalar lightmap semantics only; never a Java GPU resource. */
    public static void recordLightmapSemanticFingerprint(String fingerprint) {
        if (ENABLED) {
            lastLightmapSemanticFingerprint = fingerprint == null ? "" : fingerprint;
        }
    }
    private static Vec3 initialPosition;
    private static Pose initialPose;
    private static String initialDimension;
    private static int windowWidth;
    private static int windowHeight;
    private static CameraType originalCameraType;
    private static GameType originalGameMode;
    private static GameType originalPreviousGameMode;
    private static int originalSelectedHotbarSlot = -1;
    private static List<ItemStack> originalHotbarItems = List.of();
    private static int originalArmorValueOverride = -1;
    private static ChatVisiblity originalChatVisibility;
    private static CloudStatus originalCloudStatus;
    private static int originalCloudRangeChunks = -1;
    private static boolean awaitingScreenshotAck;
    private static volatile boolean cowModelFixtureSpawnQueued;
    private static volatile int cowModelFixtureEntityId = -1;
    private static int framesAwaitingAck;
    private static Path currentScreenshotPath;
    private static Path currentAckPath;
    private static final List<PoseCapture> CAPTURES = new ArrayList<>();

    private DeterministicCameraCapture() {
    }

    /**
     * Allows the harness benchmark to defer shutdown until its externally
     * acknowledged deterministic screenshot has completed.
     */
    public static boolean isAwaitingCompletion() {
        return ENABLED && !complete && !failed;
    }

    /** Capture-only scalar receipt; it observes Frozen's prepared sky state
     * and never participates in pass selection or rendering. */
    public static void recordFrozenSkyColor(int skyColor) {
        if (!ENABLED || complete || failed) {
            return;
        }
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Files.writeString(
                SCREENSHOT_DIR.resolve("frozen-sky-last.json"),
                "{\"sky_color_argb\":" + Integer.toUnsignedLong(skyColor)
                    + ",\"rendered_frame_index\":" + renderedFrameIndex + "}\n",
                StandardCharsets.UTF_8
            );
        } catch (IOException ignored) {
            // Diagnostics must never perturb Frozen's authoritative render.
        }
    }

    /** Capture-only receipt of the exact matrices already prepared for the
     * Frozen OpenGL world frame. This observes CPU values only and never
     * changes Frozen rendering or GPU state. */
    public static void recordFrozenSkyMatrices(Matrix4f view, Matrix4f projection) {
        if (!ENABLED || complete || failed || view == null || projection == null) return;
        float[] viewValues = view.get(new float[16]);
        float[] projectionValues = projection.get(new float[16]);
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Files.writeString(
                SCREENSHOT_DIR.resolve("frozen-sky-matrices-last.json"),
                "{\"view\":" + java.util.Arrays.toString(viewValues)
                    + ",\"projection\":" + java.util.Arrays.toString(projectionValues)
                    + ",\"rendered_frame_index\":" + renderedFrameIndex + "}\n",
                StandardCharsets.UTF_8
            );
        } catch (IOException ignored) {
            // Diagnostics must never perturb Frozen's authoritative render.
        }
    }

    /** Capture-only receipt of the CPU model-view matrix Frozen binds at the
     * sky-disc draw. This observes no GPU state and has no rendering effect. */
    public static void recordFrozenSkyDiscModelView(Matrix4f modelView) {
        if (!ENABLED || complete || failed || modelView == null) return;
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Files.writeString(
                SCREENSHOT_DIR.resolve("frozen-sky-disc-model-view-last.json"),
                "{\"model_view\":" + java.util.Arrays.toString(modelView.get(new float[16]))
                    + ",\"rendered_frame_index\":" + renderedFrameIndex + "}\n",
                StandardCharsets.UTF_8
            );
        } catch (IOException ignored) {
        }
    }

    /**
     * Capture-only receipt of the vanilla fog inputs that Frozen's OpenGL sky
     * path already consumed. This observes immutable gameplay values after the
     * frame; it neither allocates GPU state nor changes Frozen rendering.
     */
    private static void recordFrozenSkyFog(Minecraft minecraft) {
        if (!ENABLED || complete || failed || minecraft.level == null) return;
        Vector4f fog = minecraft.gameRenderer.fogRenderer.computeFogColor(
            minecraft.gameRenderer.getMainCamera(),
            1.0F,
            minecraft.level,
            minecraft.options.getEffectiveRenderDistance(),
            minecraft.gameRenderer.getDarkenWorldAmount(1.0F),
            false
        );
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Files.writeString(
                SCREENSHOT_DIR.resolve("frozen-sky-fog-last.json"),
                "{\"fog_color\":[" + fog.x + "," + fog.y + "," + fog.z + "," + fog.w + "]"
                    + ",\"sky_darken\":" + minecraft.level.getSkyDarken(1.0F)
                    + ",\"rendered_frame_index\":" + renderedFrameIndex + "}\n",
                StandardCharsets.UTF_8
            );
        } catch (IOException ignored) {
            // Diagnostics must never perturb Frozen's authoritative render.
        }
    }

    /**
     * Capture-only receipt of the exact CPU values packed into Frozen's WORLD
     * fog UBO. This observes the values after hooks have run and before their
     * mapped upload is consumed; it does not inspect GPU memory or influence
     * rendering.
     */
    public static void recordFrozenWorldFogUbo(
        Vector4f color,
        float environmentalStart,
        float environmentalEnd,
        float renderDistanceStart,
        float renderDistanceEnd,
        float skyEnd,
        float cloudsEnd
    ) {
        if (!ENABLED || complete || failed || color == null) return;
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Files.writeString(
                SCREENSHOT_DIR.resolve("frozen-world-fog-ubo-last.json"),
                "{\"color\":[" + color.x + "," + color.y + "," + color.z + "," + color.w + "]"
                    + ",\"environmental_start\":" + environmentalStart
                    + ",\"environmental_end\":" + environmentalEnd
                    + ",\"render_distance_start\":" + renderDistanceStart
                    + ",\"render_distance_end\":" + renderDistanceEnd
                    + ",\"sky_end\":" + skyEnd
                    + ",\"clouds_end\":" + cloudsEnd
                    + ",\"rendered_frame_index\":" + renderedFrameIndex + "}\n",
                StandardCharsets.UTF_8
            );
        } catch (IOException ignored) {
            // Diagnostics must never perturb Frozen's authoritative render.
        }
    }

    /**
     * Capture-only receipt of the cloud mesh Frozen already prepared for its
     * OpenGL draw.  It observes CPU-side mesh state only; it neither selects a
     * pipeline nor changes renderer or GPU state.
     */
    public static void recordFrozenCloudMesh(
		int quadCount,
		String relativeCameraPos,
		CloudStatus cloudStatus,
		int cloudColorArgb,
		float cloudHeightOffset,
        int cellX,
        int cellZ,
		float cellOffsetX,
		float cellOffsetZ,
		boolean rebuilt,
		long meshFingerprint
    ) {
        if (!ENABLED || complete || failed) return;
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Files.writeString(
                SCREENSHOT_DIR.resolve("frozen-cloud-mesh-last.json"),
                "{\"quad_count\":" + quadCount
                    + ",\"relative_camera_pos\":\"" + relativeCameraPos + "\""
					+ ",\"cloud_status\":\"" + cloudStatus.getSerializedName() + "\""
					+ ",\"cloud_color_argb\":\"0x" + String.format(java.util.Locale.ROOT, "%08x", cloudColorArgb) + "\""
                    + ",\"cloud_height_offset\":" + cloudHeightOffset
                    + ",\"cell_x\":" + cellX
                    + ",\"cell_z\":" + cellZ
                    + ",\"cell_offset_x\":" + cellOffsetX
					+ ",\"cell_offset_z\":" + cellOffsetZ
					+ ",\"rebuilt\":" + rebuilt
					+ ",\"mesh_fingerprint\":\"" + Long.toUnsignedString(meshFingerprint) + "\""
                    + ",\"rendered_frame_index\":" + renderedFrameIndex + "}\n",
                StandardCharsets.UTF_8
            );
        } catch (IOException ignored) {
        }
    }

    /** Capture-only receipt of the live vanilla GUI vignette state.  This
     * observes Frozen without altering its tick or rendering behavior. */
    private static void recordFrozenVignetteBrightness(Minecraft minecraft) {
        if (!ENABLED || complete || failed) return;
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Files.writeString(
                SCREENSHOT_DIR.resolve("frozen-vignette-last.json"),
                "{\"vignette_brightness\":" + minecraft.gui.vignetteBrightness
                    + ",\"rendered_frame_index\":" + renderedFrameIndex + "}\n",
                StandardCharsets.UTF_8
            );
        } catch (IOException ignored) {
            // Diagnostics must never perturb Frozen's authoritative render.
        }
    }

	public static void beforeTick(Minecraft minecraft) {
        if (ENABLED && failed && STOP_AFTER_COMPLETE && !stopIssued) {
            stopIssued = true;
            minecraft.stop();
            return;
        }
        if (ENABLED && complete && STOP_AFTER_COMPLETE && !stopIssued) {
            // A correctness screenshot is complete, but the independent
            // benchmark must still collect its configured samples. This is
            // harness-only lifecycle coordination; it never changes Frozen's
            // rendering path or its captured frame.
            if (GraphicsFrameBenchmark.isAwaitingCompletion()) {
                return;
            }
            stopIssued = true;
            minecraft.stop();
            return;
        }
        if (!ENABLED || complete || failed || minecraft.player == null) {
            return;
        }
        applyFixedCaptureTime(minecraft);
        LocalPlayer player = minecraft.player;
        player.input.keyPresses = Input.EMPTY;
        player.xxa = 0.0F;
        player.zza = 0.0F;
        player.setSprinting(false);
        player.setShiftKeyDown(false);
        player.setDeltaMovement(Vec3.ZERO);
        applyRuntimeOverrides(minecraft, player);
        if (initialized && initialPosition != null && initialPose != null) {
            player.setPos(initialPosition);
            player.setOldPosAndRot(initialPosition, initialPose.yaw(), initialPose.pitch());
        }
    }

    public static void beforeRender(Minecraft minecraft) {
        boolean ready = ENABLED && !complete && !failed && ensureInitialized(minecraft);
        traceRenderHook("before", minecraft, ready);
        if (!ready) {
            return;
        }
        applyFixedCaptureTime(minecraft);
        if (PERFORMANCE_MODE) {
            VulkanPerfAudit.setDeterministicMeasurementFrameActive(isPerformanceMeasurementFrame());
        }
        applyPose(minecraft.player, currentPose());
    }

    private static java.lang.ref.WeakReference<net.minecraft.server.MinecraftServer> fixedCaptureClockServer = new java.lang.ref.WeakReference<>(null);

    private static void applyFixedCaptureTime(Minecraft minecraft) {
        if (FIXED_CAPTURE_TIME != Long.MIN_VALUE && minecraft.level != null) {
            var server = minecraft.getSingleplayerServer();
            if (server != null && fixedCaptureClockServer.get() != server) {
                fixedCaptureClockServer = new java.lang.ref.WeakReference<>(server);
                // Capture-only copied-world fixture: keep server time packets
                // consistent with the fixed client clock between ordinary ticks.
                server.execute(() -> {
                    long previousDayTime = server.overworld().getDayTime();
                    server.overworld().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
                    server.overworld().setDayTime(FIXED_CAPTURE_TIME);
                    LOGGER.info("Capture fixture server clock: previousDayTime={} fixedDayTime={}", previousDayTime, FIXED_CAPTURE_TIME);
                });
            }
            minecraft.level.setTimeFromServer(FIXED_CAPTURE_TIME, FIXED_CAPTURE_TIME, false);
        }
    }

    /** Returns the capture fixture's cloud phase without affecting Frozen rendering. */
    public static float cloudTimeForCapture(float vanillaCloudTime) {
        return ENABLED && Float.isFinite(FIXED_CLOUD_TIME) ? FIXED_CLOUD_TIME : vanillaCloudTime;
    }

    /** Read-only frame identity for observations made during the current draw. */
    public static long currentRenderingFrameIndexForDiagnostics() {
        return ENABLED && initialized && !complete && !failed ? renderedFrameIndex + 1 : -1;
    }

    public static void afterRender(Minecraft minecraft) {
        boolean ready = ENABLED && !complete && !failed && ensureInitialized(minecraft);
        traceRenderHook("after", minecraft, ready);
        if (!ready) {
            return;
        }
        applyPose(minecraft.player, currentPose());
        renderedFrameIndex++;
        if (minecraft.level != null) {
            recordFrozenSkyColor(minecraft.level.getSkyColor(
                minecraft.gameRenderer.getMainCamera().getPosition(), 1.0F
            ));
            recordFrozenSkyFog(minecraft);
        }
        recordFrozenVignetteBrightness(minecraft);
        if (!PERFORMANCE_MODE) {
            if (awaitingScreenshotAck) {
                if (checkScreenshotAck(minecraft)) {
                    return;
                }
                framesAwaitingAck++;
                if (framesAwaitingAck > ACK_TIMEOUT_FRAMES) {
                    fail("timed out waiting for deterministic screenshot ack: " + currentAckPath);
                }
                return;
            }
        }
        renderedFramesAtPose++;
		if (
			WAIT_FOR_STATIC_TERRAIN_PARITY
                && !StaticTerrainParityDiagnostics.isSolidVisibleListStable(
                    STATIC_TERRAIN_PARITY_READY_FRAMES,
                    STATIC_TERRAIN_PARITY_MIN_SECTIONS
                )
        ) {
            if ((renderedFrameIndex % 30L) == 0L) {
                writeMetadata(minecraft, "waiting_for_static_terrain_parity");
            }
			return;
		}
		if (!minecraft.gui.vignetteBrightnessSettledForDeterministicCapture(minecraft.getCameraEntity())) {
			renderedFramesAtPose = 0;
			return;
		}
		if (GraphicsAuditWorldMenuFixture.afterRender(minecraft)) {
			renderedFramesAtPose = 0;
			return;
		}
		// Only a terrain-appearance fixture may make the optional lightmap
		// export a completion prerequisite. A trace destination can be inherited
		// by an unrelated capture, where it remains observational and must not
		// prevent the normal deterministic screenshot from completing.
		if (Boolean.getBoolean("mattmc.dev.staticTerrainParityDiagnostics.requireLightmapCapture")
			&& !net.minecraft.client.renderer.LightTexture.hasStaticTerrainParityLightmapCapture()) {
			if ((renderedFrameIndex % 30L) == 0L) {
				writeMetadata(minecraft, "waiting_for_static_terrain_lightmap_diagnostic");
			}
			return;
		}
		if (renderedFramesAtPose >= FRAMES_PER_POSE) {
            if (!PERFORMANCE_MODE) {
                requestCurrentPoseScreenshot(minecraft);
                return;
            }
            renderedFramesAtPose = 0;
            poseIndex = (poseIndex + 1) % POSE_COUNT;
            writeMetadata(minecraft, "running");
        }
    }

    private static void traceRenderHook(String phase, Minecraft minecraft, boolean ready) {
        if (!HOOK_TRACE || HOOK_TRACE_EVENTS.getAndIncrement() >= HOOK_TRACE_MAX_EVENTS) {
            return;
        }
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Files.writeString(
                SCREENSHOT_DIR.resolve("frozen-capture-hook-trace.jsonl"),
                "{\"phase\":\"" + phase + "\",\"ready\":" + ready
                    + ",\"enabled\":" + ENABLED + ",\"complete\":" + complete
                    + ",\"failed\":" + failed + ",\"initialized\":" + initialized
                    + ",\"noRender\":" + minecraft.noRender + ",\"hasLevel\":" + (minecraft.level != null)
                    + ",\"hasPlayer\":" + (minecraft.player != null)
                    + ",\"hasScreen\":" + (minecraft.screen != null)
                    + ",\"hasOverlay\":" + (minecraft.getOverlay() != null)
                    + ",\"renderedFrameIndex\":" + renderedFrameIndex + "}\n",
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // Capture diagnostics must never alter Frozen's authoritative path.
        }
    }

    public static void recordPerformanceFrame(Minecraft minecraft, long frameNanos) {
        if (!ENABLED || !PERFORMANCE_MODE || !initialized || complete || failed) {
            return;
        }
        boolean measurementFrame = isPerformanceMeasurementFrame();
        VulkanPerfAudit.recordDeterministicFrame(frameNanos, measurementFrame);
        VulkanPerfAudit.setDeterministicMeasurementFrameActive(false);
        performanceFrames++;
        if ((performanceFrames % 30) == 0) {
            writePerformanceStatus(minecraft, "running");
        }
        if (performanceFrames >= PERFORMANCE_WARMUP_FRAMES + PERFORMANCE_MEASURE_FRAMES) {
            writePerformanceStatus(minecraft, "complete");
            VulkanPerfAudit.flush();
            finish(minecraft);
        }
    }

    private static boolean isPerformanceMeasurementFrame() {
        return performanceFrames >= PERFORMANCE_WARMUP_FRAMES
            && performanceFrames < PERFORMANCE_WARMUP_FRAMES + PERFORMANCE_MEASURE_FRAMES;
    }

    private static boolean ensureInitialized(Minecraft minecraft) {
        if (initialized) {
            return true;
        }
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.getConnection() == null || minecraft.screen != null || minecraft.getOverlay() != null) {
            return false;
        }
        try {
            Path metadataParent = METADATA_PATH.getParent();
            if (metadataParent != null) {
                Files.createDirectories(metadataParent);
            }
            Path statusParent = PERFORMANCE_STATUS_PATH.getParent();
            if (statusParent != null) {
                Files.createDirectories(statusParent);
            }
            Files.createDirectories(SCREENSHOT_DIR);
        } catch (IOException exception) {
            fail("failed to create deterministic capture directories: " + exception.getMessage());
            return false;
        }
        originalCameraType = minecraft.options.getCameraType();
        originalGameMode = minecraft.gameMode == null ? null : minecraft.gameMode.getPlayerMode();
        originalPreviousGameMode = minecraft.gameMode == null ? null : minecraft.gameMode.getPreviousPlayerMode();
        originalSelectedHotbarSlot = player.getInventory().getSelectedSlot();
        if (!HOTBAR_ITEM_FIXTURE.isEmpty()) {
            originalHotbarItems = new ArrayList<>(9);
            for (int slot = 0; slot < 9; slot++) {
                originalHotbarItems.add(player.getInventory().getItem(slot).copy());
            }
        }
        originalArmorValueOverride = player.getArmorValueForDeterministicCapture();
        originalChatVisibility = minecraft.options.chatVisibility().get();
        originalCloudStatus = minecraft.options.cloudStatus().get();
		originalCloudRangeChunks = minecraft.options.cloudRange().get();
        applyRuntimeOverrides(minecraft, player);
        double weatherY = "rain".equals(WEATHER_SCENARIO) ? Math.max(FIXED_Y, 160.0D) : FIXED_Y;
        initialPosition = new Vec3(FIXED_X, weatherY, FIXED_Z);
        if ("rain".equals(WEATHER_SCENARIO)) {
            // Capture-only fixture state: keep Frozen's Java OpenGL renderer
            // unchanged while matching the Current weather column setup.
            level.setRainLevel(1.0F);
            level.setThunderLevel(0.0F);
        }
        initialPose = new Pose("initial", FIXED_YAW, FIXED_PITCH);
        initialDimension = level.dimension().location().toString();
        player.setPos(initialPosition);
        player.setDeltaMovement(Vec3.ZERO);
        applyPose(player, initialPose);
        GraphicsAuditBlockDisplayFixture.install(minecraft);
        if ("translucent-mixed".equals(STATIC_TERRAIN_SCENARIO)) {
            BlockPos target = staticTerrainFixtureTarget();
            if (target == null || !GraphicsAuditMixedFluidFixture.install(minecraft, target, player.getDirection())) {
                return false;
            }
            minecraft.levelRenderer.allChanged();
        }
        if ("translucent-overlap".equals(STATIC_TERRAIN_SCENARIO)) {
            if (!setupTranslucentOverlapFixture(minecraft, level, player)) {
                return false;
            }
        }
        if ("texture-palette".equals(STATIC_TERRAIN_FIXTURE)) {
            if (!setupTexturePaletteFixture(minecraft, level, player)) {
                return false;
            }
        }
        if (!setupCowModelFixture(minecraft, level, player)) {
            return false;
        }
        applyPose(player, initialPose);
        player.setOldPosAndRot(initialPosition, initialPose.yaw(), initialPose.pitch());
        windowWidth = minecraft.getWindow().getWidth();
        windowHeight = minecraft.getWindow().getHeight();
        initialized = true;
        LOGGER.info(
            "Frozen deterministic benchmark started dimension={} pos={} yaw={} pitch={} warmup={} measure={}",
            initialDimension,
            initialPosition,
            initialPose.yaw(),
            initialPose.pitch(),
            PERFORMANCE_WARMUP_FRAMES,
            PERFORMANCE_MEASURE_FRAMES
        );
        writeMetadata(minecraft, "running");
        if (PERFORMANCE_MODE) {
            writePerformanceStatus(minecraft, "running");
        }
        return true;
    }

    /**
     * Spawn exactly one ordinary vanilla cow through the integrated server for
     * the model parity fixture. This is test setup only: Frozen's unmodified
     * EntityRenderDispatcher performs the later OpenGL render, so it remains
     * the correctness baseline instead of acquiring a Rust route or policy.
     */
    private static boolean setupCowModelFixture(Minecraft minecraft, ClientLevel clientLevel, LocalPlayer player) {
        if (!"cow".equals(MODEL_MESH_SCENARIO)) {
            return true;
        }
        if (cowModelFixtureEntityId >= 0) {
            Entity entity = clientLevel.getEntity(cowModelFixtureEntityId);
            return entity instanceof Cow;
        }
        if (cowModelFixtureSpawnQueued || minecraft.getSingleplayerServer() == null) {
            return false;
        }
        Vec3 forward = player.getLookAngle();
        if (forward.lengthSqr() < 1.0e-4) {
            forward = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 origin = player.getEyePosition().add(forward.normalize().scale(4.0)).add(0.0, -0.25, 0.0);
        MinecraftServer server = minecraft.getSingleplayerServer();
        var dimension = clientLevel.dimension();
        Vec3 eyePosition = player.getEyePosition();
        cowModelFixtureSpawnQueued = true;
        server.execute(() -> {
            ServerLevel serverLevel = server.getLevel(dimension);
            if (serverLevel == null) {
                cowModelFixtureSpawnQueued = false;
                return;
            }
            Cow cow = new Cow(EntityType.COW, serverLevel);
            Vec3 cowPosition = origin.add(0.0, -1.1, 0.0);
            prepareCowModelFixtureSite(serverLevel, eyePosition, cowPosition);
            cow.setPos(origin.x, origin.y - 1.1, origin.z);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.setDeltaMovement(Vec3.ZERO);
            serverLevel.addFreshEntity(cow);
            cowModelFixtureEntityId = cow.getId();
        });
        return false;
    }

    /** Mirrors Current's capture-only cow platform and clear sightline. */
    private static void prepareCowModelFixtureSite(ServerLevel serverLevel, Vec3 eyePosition, Vec3 cowPosition) {
        BlockPos base = BlockPos.containing(cowPosition);
        for (int localX = -1; localX <= 1; localX++) {
            for (int localZ = -1; localZ <= 1; localZ++) {
                serverLevel.setBlock(base.offset(localX, -1, localZ), Blocks.STONE.defaultBlockState(), 3);
                for (int localY = 0; localY <= 2; localY++) {
                    serverLevel.setBlock(base.offset(localX, localY, localZ), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        Vec3 ray = cowPosition.subtract(eyePosition);
        int steps = Math.max(1, (int)Math.ceil(ray.length() * 4.0));
        for (int step = 1; step <= steps; step++) {
            BlockPos position = BlockPos.containing(eyePosition.add(ray.scale((double)step / steps)));
            serverLevel.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
            serverLevel.setBlock(position.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        placeShadowGlassFixture(serverLevel, eyePosition, cowPosition);
    }

    private static void placeShadowGlassFixture(ServerLevel serverLevel, Vec3 eyePosition, Vec3 entityPosition) {
        // Test-world setup only; no renderer state or renderer behavior changes.
        if (!Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.shadowGlass")) return;
        Vec3 towardEye = eyePosition.subtract(entityPosition);
        int dx = Math.abs(towardEye.x) >= Math.abs(towardEye.z) ? (towardEye.x > 0 ? 1 : -1) : 0;
        int dz = dx == 0 ? (towardEye.z > 0 ? 1 : -1) : 0;
        BlockPos origin = BlockPos.containing(entityPosition).offset(dx * 2, 0, dz * 2);
        for (int across = -1; across <= 1; across++) {
            for (int up = 0; up <= 1; up++) {
                serverLevel.setBlock(origin.offset(-dz * across, up, dx * across),
                    Blocks.RED_STAINED_GLASS.defaultBlockState(), 3);
            }
        }
    }

    private static Pose currentPose() {
        if (initialPose == null) {
            return new Pose("initial", FIXED_YAW, FIXED_PITCH);
        }
        if ("translucent-overlap".equals(STATIC_TERRAIN_SCENARIO)) {
            net.minecraft.core.Direction forward = net.minecraft.core.Direction.fromYRot(initialPose.yaw());
            net.minecraft.core.Direction right = forward.getClockWise();
            return switch (poseIndex) {
                case 0 -> new Pose("translucent-front", initialPose.yaw(), initialPose.pitch());
                case 1 -> new Pose("translucent-lateral", initialPose.yaw(), initialPose.pitch(),
                    right.getStepX() * 4.0, 0.0, right.getStepZ() * 4.0);
                case 2 -> new Pose("translucent-orbit-left", initialPose.yaw() - 55.0F, initialPose.pitch());
                case 3 -> new Pose("translucent-cross-opposite", initialPose.yaw() + 180.0F, initialPose.pitch(),
                    forward.getStepX() * 10.0, 0.0, forward.getStepZ() * 10.0);
                case 4 -> new Pose("translucent-above", initialPose.yaw(), initialPose.pitch(),
                    forward.getStepX() * 4.0, 5.0, forward.getStepZ() * 4.0);
                case 5 -> new Pose("translucent-below", initialPose.yaw(), initialPose.pitch(),
                    forward.getStepX() * 4.0, -4.0, forward.getStepZ() * 4.0);
                default -> new Pose("translucent-return", initialPose.yaw(), initialPose.pitch());
            };
        }
        return switch (poseIndex) {
            case 1 -> new Pose("right", initialPose.yaw() + YAW_DELTA, initialPose.pitch());
            case 2 -> new Pose("left", initialPose.yaw() - YAW_DELTA, initialPose.pitch());
            case 3 -> new Pose("return", initialPose.yaw(), initialPose.pitch());
            default -> new Pose("initial", initialPose.yaw(), initialPose.pitch());
        };
    }

    private static String currentPoseName() {
        if ("texture-palette".equals(STATIC_TERRAIN_FIXTURE)) {
            return "texture-palette";
        }
        return currentPose().name();
    }

    private static void applyPose(LocalPlayer player, Pose pose) {
        if (player == null) {
            return;
        }
        if (initialPosition != null) {
            Vec3 position = initialPosition.add(pose.offsetX(), pose.offsetY(), pose.offsetZ());
            player.setPos(position);
            player.setOldPosAndRot(position, pose.yaw(), pose.pitch());
        }
        player.setYRot(pose.yaw());
        player.setXRot(pose.pitch());
        player.yRotO = pose.yaw();
        player.xRotO = pose.pitch();
        player.yHeadRot = pose.yaw();
        player.yHeadRotO = pose.yaw();
        player.yBodyRot = pose.yaw();
        player.yBodyRotO = pose.yaw();
        net.minecraft.client.particle.GraphicsAuditTerrainParticleFixture.install(Minecraft.getInstance());
    }

    /**
     * Builds the same four-block material palette and camera framing used by
     * Current's capture-only fixture. Both authoritative server and local
     * client copies are updated before rendering begins, then only their
     * affected sections are invalidated. Normal Frozen execution never enters
     * this method because the parity-only property is absent.
     */
    private static boolean setupTexturePaletteFixture(Minecraft minecraft, ClientLevel clientLevel, LocalPlayer player) {
        if (minecraft.getSingleplayerServer() == null) {
            return false;
        }
        ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(clientLevel.dimension());
        if (serverLevel == null) {
            return false;
        }
        Direction forward = player.getDirection();
        Direction right = forward.getClockWise();
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        BlockPos target = staticTerrainFixtureTarget();
        if (target != null && (!serverLevel.isLoaded(target) || !clientLevel.isLoaded(target)
            || !serverLevel.getBlockState(target).isAir() || !clientLevel.getBlockState(target).isAir()
            || !serverLevel.getFluidState(target).isEmpty() || !clientLevel.getFluidState(target).isEmpty())) {
            return false;
        }
        for (int distance = 4; distance <= 10 && target == null; distance++) {
            for (int vertical = -2; vertical <= 1 && target == null; vertical++) {
                for (int lateral = -2; lateral <= 1; lateral++) {
                    BlockPos candidate = eye.relative(forward, distance).relative(right, lateral).above(vertical);
                    boolean available = true;
                    for (int offset = 0; offset < 4; offset++) {
                        BlockPos position = candidate.relative(right, offset);
                        if (!serverLevel.isLoaded(position) || !clientLevel.isLoaded(position)
                            || !serverLevel.getBlockState(position).isAir() || !clientLevel.getBlockState(position).isAir()) {
                            available = false;
                            break;
                        }
                    }
                    if (available) {
                        target = candidate;
                        break;
                    }
                }
            }
        }
        if (target == null) {
            return false;
        }
        BlockState[] states = {
            Blocks.GRASS_BLOCK.defaultBlockState(),
            Blocks.REDSTONE_ORE.defaultBlockState(),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
            Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)
        };
        for (int offset = 0; offset < states.length; offset++) {
            BlockPos position = target.relative(right, offset);
            serverLevel.setBlock(position, states[offset], 3);
            clientLevel.setBlock(position, states[offset], 3);
        }
        minecraft.levelRenderer.setBlocksDirty(
            target.getX() - 1, target.getY() - 1, target.getZ() - 1,
            target.relative(right, 3).getX() + 1, target.getY() + 1, target.relative(right, 3).getZ() + 1
        );
        Vec3 paletteCenter = Vec3.atCenterOf(target).add(right.getStepX() * 1.5D, 0.0D, right.getStepZ() * 1.5D);
        Vec3 delta = paletteCenter.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.001D) {
            return false;
        }
        initialPose = new Pose(
            "texture-palette",
            (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D),
            (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)))
        );
        return true;
    }

    private static BlockPos staticTerrainFixtureTarget() {
        String[] parts = System.getProperty("mattmc.dev.deterministicCameraCapture.staticTerrainFixtureTarget", "").split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Installs the same bounded pane arrangement used by Current's
     * translucent-overlap capture. This is an isolated copied-world fixture:
     * it changes neither Frozen's OpenGL renderer nor its render route.
     */
    private static boolean setupTranslucentOverlapFixture(Minecraft minecraft, ClientLevel clientLevel, LocalPlayer player) {
        if (minecraft.getSingleplayerServer() == null) {
            return false;
        }
        ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(clientLevel.dimension());
        if (serverLevel == null) {
            return false;
        }
        Direction forward = player.getDirection();
        Direction right = forward.getClockWise();
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        // The shared parity harness supplies one canonical world coordinate.
        // Honor it for this pane fixture just as Current does: choosing the
        // first locally available air block makes otherwise identical camera
        // runs compare different world geometry rather than renderer output.
        BlockPos target = staticTerrainFixtureTarget();
        if (target != null && (!serverLevel.isLoaded(target) || !clientLevel.isLoaded(target)
            || !serverLevel.getBlockState(target).isAir() || !clientLevel.getBlockState(target).isAir()
            || !serverLevel.getFluidState(target).isEmpty() || !clientLevel.getFluidState(target).isEmpty())) {
            return false;
        }
        for (int distance = 4; distance <= 10 && target == null; distance++) {
            for (int vertical = -2; vertical <= 2 && target == null; vertical++) {
                for (int lateral = -2; lateral <= 2; lateral++) {
                    BlockPos candidate = eye.relative(forward, distance).relative(right, lateral).above(vertical);
                    if (serverLevel.isLoaded(candidate) && clientLevel.isLoaded(candidate)
                        && serverLevel.getBlockState(candidate).isAir()
                        && clientLevel.getBlockState(candidate).isAir()
                        && serverLevel.getFluidState(candidate).isEmpty()
                        && clientLevel.getFluidState(candidate).isEmpty()) {
                        target = candidate;
                        break;
                    }
                }
            }
        }
        if (target == null) {
            return false;
        }
        net.minecraft.world.level.block.state.BlockState[] layers = {
            Blocks.RED_STAINED_GLASS.defaultBlockState(),
            Blocks.GREEN_STAINED_GLASS.defaultBlockState(),
            Blocks.BLUE_STAINED_GLASS.defaultBlockState()
        };
        for (int depth = 0; depth < layers.length; depth++) {
            BlockPos planeBase = target.relative(forward, depth * 2);
            for (int lateral = -1; lateral <= 1; lateral++) {
                for (int vertical = 0; vertical <= 2; vertical++) {
                    setFixtureBlock(serverLevel, clientLevel, planeBase.relative(right, lateral).above(vertical), layers[depth]);
                }
            }
        }
        net.minecraft.world.level.block.state.BlockState crossing = Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
        BlockPos crossingBase = target.relative(forward, 1).relative(right, 2);
        for (int depth = 0; depth <= 4; depth++) {
            for (int vertical = 0; vertical <= 2; vertical++) {
                setFixtureBlock(serverLevel, clientLevel, crossingBase.relative(forward, depth).above(vertical), crossing);
            }
        }
        minecraft.levelRenderer.allChanged();
        return true;
    }

    private static void setFixtureBlock(
        ServerLevel serverLevel,
        ClientLevel clientLevel,
        BlockPos position,
        net.minecraft.world.level.block.state.BlockState state
    ) {
        if (serverLevel.isLoaded(position) && clientLevel.isLoaded(position)) {
            serverLevel.setBlock(position, state, 3);
            clientLevel.setBlock(position, state, 3);
        }
    }

    private static void applyRuntimeOverrides(Minecraft minecraft, LocalPlayer player) {
        if (!FORCED_SELECTED_SKIN.isEmpty() && !FORCED_SELECTED_SKIN.equals(minecraft.options.selectedSkin)) {
            minecraft.options.selectedSkin = FORCED_SELECTED_SKIN;
        }
        CameraType cameraType = forcedCameraType();
        if (cameraType != null && minecraft.options.getCameraType() != cameraType) {
            minecraft.options.setCameraType(cameraType);
            minecraft.gameRenderer.checkEntityPostEffect(cameraType.isFirstPerson() ? minecraft.getCameraEntity() : null);
        }
        GameType gameType = forcedGameMode();
        if (gameType != null && minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() != gameType) {
            minecraft.gameMode.setLocalMode(gameType, minecraft.gameMode.getPreviousPlayerMode());
        }
        if (FORCED_SELECTED_HOTBAR_SLOT > 0) {
            int selectedSlot = Math.max(1, Math.min(9, FORCED_SELECTED_HOTBAR_SLOT)) - 1;
            if (player.getInventory().getSelectedSlot() != selectedSlot) {
                player.getInventory().setSelectedSlot(selectedSlot);
            }
        }
        applyHotbarItemFixture(player);
        if (FORCE_EMPTY_SELECTED_HAND) {
            player.getInventory().setItem(player.getInventory().getSelectedSlot(), ItemStack.EMPTY);
        }
        if (FORCED_ARMOR_VALUE >= 0) {
            player.setArmorValueForDeterministicCapture(Math.min(20, FORCED_ARMOR_VALUE));
        }
        if (HIDE_CHAT && minecraft.options.chatVisibility().get() != ChatVisiblity.HIDDEN) {
            minecraft.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        }
        CloudStatus forcedCloudStatus = forcedCloudStatus();
        if (forcedCloudStatus != null && minecraft.options.cloudStatus().get() != forcedCloudStatus) {
            minecraft.options.cloudStatus().set(forcedCloudStatus);
        }
        if (FORCED_CLOUD_RANGE_CHUNKS >= 0
            && minecraft.options.cloudRange().get() != FORCED_CLOUD_RANGE_CHUNKS) {
            minecraft.options.cloudRange().set(FORCED_CLOUD_RANGE_CHUNKS);
        }
    }

    private static void restoreRuntimeOverrides(Minecraft minecraft) {
        if (minecraft.player != null && FORCED_SELECTED_HOTBAR_SLOT > 0 && originalSelectedHotbarSlot >= 0 && originalSelectedHotbarSlot < 9) {
            minecraft.player.getInventory().setSelectedSlot(originalSelectedHotbarSlot);
        }
        if (minecraft.player != null && !originalHotbarItems.isEmpty()) {
            for (int slot = 0; slot < originalHotbarItems.size(); slot++) {
                minecraft.player.getInventory().setItem(slot, originalHotbarItems.get(slot).copy());
            }
            originalHotbarItems = List.of();
        }
        if (minecraft.player != null && FORCED_ARMOR_VALUE >= 0) {
            minecraft.player.setArmorValueForDeterministicCapture(originalArmorValueOverride);
        }
        if (!FORCED_CAMERA_TYPE.isBlank() && originalCameraType != null && minecraft.options.getCameraType() != originalCameraType) {
            minecraft.options.setCameraType(originalCameraType);
            minecraft.gameRenderer.checkEntityPostEffect(originalCameraType.isFirstPerson() ? minecraft.getCameraEntity() : null);
        }
        if (!FORCED_GAME_MODE.isBlank() && minecraft.gameMode != null && originalGameMode != null) {
            minecraft.gameMode.setLocalMode(originalGameMode, originalPreviousGameMode);
        }
        if (HIDE_CHAT && originalChatVisibility != null) {
            minecraft.options.chatVisibility().set(originalChatVisibility);
        }
        if (forcedCloudStatus() != null && originalCloudStatus != null) {
            minecraft.options.cloudStatus().set(originalCloudStatus);
        }
        if (FORCED_CLOUD_RANGE_CHUNKS >= 0 && originalCloudRangeChunks >= 0) {
            minecraft.options.cloudRange().set(originalCloudRangeChunks);
        }
    }

    private static void applyHotbarItemFixture(LocalPlayer player) {
        if (HOTBAR_ITEM_FIXTURE.isEmpty()) {
            return;
        }
        List<ItemStack> items = switch (HOTBAR_ITEM_FIXTURE) {
            case "animated-block" -> GraphicsAuditAnimatedItemFixture.items();
            case "standard-3d" -> List.of(
                new ItemStack(Blocks.STONE), new ItemStack(Blocks.GRASS_BLOCK), new ItemStack(Blocks.REDSTONE_ORE),
                new ItemStack(Blocks.OAK_LEAVES), new ItemStack(Blocks.OAK_SLAB), new ItemStack(Blocks.OAK_TRAPDOOR),
                new ItemStack(Blocks.WHITE_WOOL), new ItemStack(Blocks.CRAFTING_TABLE), new ItemStack(Blocks.DIRT)
            );
            default -> throw new IllegalStateException("unknown deterministic hotbar fixture: " + HOTBAR_ITEM_FIXTURE);
        };
        for (int slot = 0; slot < items.size(); slot++) {
            player.getInventory().setItem(slot, items.get(slot));
        }
    }

    private static String blockAnimationAtCapture = "null";
    private static Path pendingPresentedRequest;
    private static String pendingPresentedRequestJson;
    private static long blockAnimationPresentedFrame = -1;

    /** Publish only after the normal window swap, then hold that completed frame
     * for the external screenshot. No renderer, animation, or presenter state
     * is changed; this private diagnostic handshake only delays the next loop. */
    public static void afterPresent(Minecraft minecraft) {
        if (pendingPresentedRequest == null) return;
        Path request = pendingPresentedRequest;
        String json = pendingPresentedRequestJson;
        pendingPresentedRequest = null;
        pendingPresentedRequestJson = null;
        blockAnimationPresentedFrame = renderedFrameIndex;
        writeString(request, json);
        writeMetadata(minecraft, "waiting_for_presented_screenshot");
        try {
            GraphicsAuditPresentedFrameWait.await(() -> checkScreenshotAck(minecraft), System::nanoTime, () -> {
                java.util.concurrent.locks.LockSupport.parkNanos(10_000_000L);
                if (Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("presented diagnostic capture interrupted");
                }
            }, 10_000_000_000L);
        } catch (IllegalStateException timeout) {
            fail(timeout.getMessage());
        }
    }

    private static void requestCurrentPoseScreenshot(Minecraft minecraft) {
        try {
            if (!GraphicsAuditBlockDisplayFixture.readyForCapture(minecraft)) return;
        } catch (IllegalStateException phaseTimeout) {
            fail(phaseTimeout.getMessage());
            return;
        }
        blockAnimationAtCapture = GraphicsAuditBlockDisplayFixture.animationObservation(minecraft);
        // Preserve the real Java draw observation that produced this settled
        // pose. This is diagnostics-only and runs after the renderer completed
        // its frame; it neither feeds a renderer nor changes capture timing.
        StaticTerrainParityDiagnostics.recordJavaDrawCaptureCoverage(renderedFrameIndex);
        StaticTerrainParityDiagnostics.recordOpenGlCapturedFrameBinding(renderedFrameIndex);
        Pose pose = currentPose();
        String poseName = currentPoseName();
        int captureIndex = poseIndex + 1;
        String fileName = String.format(Locale.ROOT, "%02d_%s.png", captureIndex, poseName);
        String requestName = String.format(Locale.ROOT, "capture_request_%02d_%s.json", captureIndex, poseName);
        String ackName = String.format(Locale.ROOT, "capture_request_%02d_%s.ack.json", captureIndex, poseName);
        currentScreenshotPath = SCREENSHOT_DIR.resolve(fileName);
        currentAckPath = SCREENSHOT_DIR.resolve(ackName);
        Path requestPath = SCREENSHOT_DIR.resolve(requestName);

        String json = "{\n"
            + "  \"index\": " + captureIndex + ",\n"
            + field("poseName", poseName) + ",\n"
            + field("screenshot", currentScreenshotPath.toAbsolutePath().toString()) + ",\n"
            + field("ack", currentAckPath.toAbsolutePath().toString()) + ",\n"
            + field("dimension", minecraft.level == null ? "missing" : minecraft.level.dimension().location().toString()) + ",\n"
            + vec3("position", minecraft.player == null ? Vec3.ZERO : minecraft.player.position()) + ",\n"
            + "  \"requestedYaw\": " + format(pose.yaw()) + ",\n"
            + "  \"requestedPitch\": " + format(pose.pitch()) + ",\n"
            + "  \"observedYaw\": " + format(minecraft.player == null ? 0.0F : minecraft.player.getYRot()) + ",\n"
            + "  \"observedPitch\": " + format(minecraft.player == null ? 0.0F : minecraft.player.getXRot()) + ",\n"
            + "  \"renderedFrameIndex\": " + renderedFrameIndex + ",\n"
            + "  \"gameTime\": " + (minecraft.level == null ? -1L : minecraft.level.getGameTime()) + ",\n"
            + "  \"vignetteBrightness\": " + format(minecraft.gui.vignetteBrightnessForDeterministicCapture()) + "\n"
            + "}\n";
        // Every external diagnostic screenshot must represent the observed
        // completed frame, including later poses of a model fixture.
        pendingPresentedRequest = requestPath;
        pendingPresentedRequestJson = json;
        awaitingScreenshotAck = true;
        framesAwaitingAck = 0;
        writeMetadata(minecraft, "waiting_for_screenshot");
        LOGGER.info("Frozen deterministic capture requested screenshot index={} pose={} path={} ack={}", captureIndex, poseName, currentScreenshotPath, currentAckPath);
    }

    private static boolean checkScreenshotAck(Minecraft minecraft) {
        if (currentAckPath == null || currentScreenshotPath == null || !Files.isRegularFile(currentAckPath)) {
            return false;
        }
        if (!Files.isRegularFile(currentScreenshotPath)) {
            fail("deterministic screenshot ack existed but screenshot was missing: " + currentScreenshotPath);
            return true;
        }
        Pose pose = currentPose();
        CAPTURES.add(new PoseCapture(
            poseIndex + 1,
            currentPoseName(),
            currentScreenshotPath.toAbsolutePath().toString(),
            minecraft.level == null ? "missing" : minecraft.level.dimension().location().toString(),
            minecraft.player == null ? Vec3.ZERO : minecraft.player.position(),
            pose.yaw(),
            pose.pitch(),
            minecraft.player == null ? 0.0F : minecraft.player.getYRot(),
            minecraft.player == null ? 0.0F : minecraft.player.getXRot(),
            renderedFrameIndex,
            lastWeatherRendererTicks,
            lastWeatherRendererPartialTick,
            lastWeatherSemanticFingerprint,
            lastLightmapSemanticFingerprint,
            minecraft.level == null ? -1L : minecraft.level.getGameTime(),
            lastCowModelEmission
        ));
        awaitingScreenshotAck = false;
        framesAwaitingAck = 0;
        currentAckPath = null;
        currentScreenshotPath = null;
        renderedFramesAtPose = 0;
        poseIndex++;
        if (poseIndex >= POSE_COUNT) {
            finish(minecraft);
        } else {
            writeMetadata(minecraft, "running");
        }
        return true;
    }

    private static CameraType forcedCameraType() {
        if (FORCED_CAMERA_TYPE.isBlank()) {
            return null;
        }
        try {
            return CameraType.valueOf(FORCED_CAMERA_TYPE.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            fail("invalid deterministic camera type override: " + FORCED_CAMERA_TYPE);
            return null;
        }
    }

    private static GameType forcedGameMode() {
        if (FORCED_GAME_MODE.isBlank()) {
            return null;
        }
        GameType gameType = GameType.byName(FORCED_GAME_MODE, null);
        if (gameType == null) {
            throw new IllegalArgumentException("unknown deterministic capture game mode: " + FORCED_GAME_MODE);
        }
        return gameType;
    }

    private static void writeMetadata(Minecraft minecraft, String status) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        String dimension = level == null ? "missing" : level.dimension().location().toString();
        String json = "{\n"
            + field("status", status) + ",\n"
            + field("backend", observedBackend()) + ",\n"
            + "  \"blockDisplayScenario\": \"" + System.getProperty("mattmc.dev.rustGalWorldMesh.blockDisplayScenario", "") + "\",\n"
            + "  \"blockDisplayFixture\": " + GraphicsAuditBlockDisplayFixture.receipt(minecraft) + ",\n"
            + "  \"terrainParticleFixture\": " + net.minecraft.client.particle.GraphicsAuditTerrainParticleFixture.receipt(minecraft) + ",\n"
            + "  \"blockDisplayAnimationObservation\": " + GraphicsAuditBlockDisplayFixture.animationObservation(minecraft) + ",\n"
            + "  \"blockDisplayAnimationAtCapture\": " + blockAnimationAtCapture + ",\n"
            + "  \"blockDisplayAnimationPresentedFrame\": " + blockAnimationPresentedFrame + ",\n"
            + "  \"worldMenuFixture\": " + GraphicsAuditWorldMenuFixture.receipt(minecraft) + ",\n"
            + "  \"hotbarItemFixture\": \"" + HOTBAR_ITEM_FIXTURE + "\",\n"
            + "  \"animatedItemFixture\": " + ("animated-block".equals(HOTBAR_ITEM_FIXTURE)
                ? GraphicsAuditAnimatedItemFixture.receipt(minecraft) : "null") + ",\n"
            + "  \"staticTerrainFixtureScenario\": \"" + STATIC_TERRAIN_SCENARIO + "\",\n"
            + "  \"mixedFluidFixture\": " + ("translucent-mixed".equals(STATIC_TERRAIN_SCENARIO)
                ? GraphicsAuditMixedFluidFixture.receipt(minecraft) : "null") + ",\n"
            + field("shadowReceiverFixture", Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.shadowGlass") ? "red-glass-v1" : "none") + ",\n"
            + field("shaderEnabled", System.getProperty("mattmc.dev.deterministicCameraCapture.shaderEnabled", "unknown")) + ",\n"
            + field("shaderPack", System.getProperty("mattmc.dev.deterministicCameraCapture.shaderPack", "unknown")) + ",\n"
            + field("gitCommit", property("gitCommit", "unknown")) + ",\n"
            + "  \"window\": { \"width\": " + windowWidth + ", \"height\": " + windowHeight + " },\n"
            + field("dimension", dimension) + ",\n"
            + vec3("initialPosition", initialPosition == null ? Vec3.ZERO : initialPosition) + ",\n"
            + "  \"initialPose\": { \"yaw\": " + format(initialPose == null ? FIXED_YAW : initialPose.yaw()) + ", \"pitch\": " + format(initialPose == null ? FIXED_PITCH : initialPose.pitch()) + " },\n"
            + "  \"yawDelta\": " + format(YAW_DELTA) + ",\n"
            + "  \"poseSequence\": [\"initial\", \"right\", \"left\", \"return\"],\n"
            + capturesJson(dimension) + ",\n"
            + "  \"yaw\": " + format(player == null ? 0.0F : player.getYRot()) + ",\n"
            + "  \"pitch\": " + format(player == null ? 0.0F : player.getXRot()) + ",\n"
            + benchmarkFingerprintJson(dimension) + ",\n"
            + field("benchmarkFingerprintHash", benchmarkFingerprintHash(dimension)) + ",\n"
            + "  \"armorValue\": " + (player == null ? -1 : player.getArmorValue()) + ",\n"
            + "  \"armorValueOverride\": " + FORCED_ARMOR_VALUE + ",\n"
            + "  \"renderedFrameIndex\": " + renderedFrameIndex + ",\n"
            + field("staticTerrainParityVisibleList", StaticTerrainParityDiagnostics.solidVisibleListSummary()) + ",\n"
            + "  \"staticTerrainParityWaitEnabled\": " + WAIT_FOR_STATIC_TERRAIN_PARITY + ",\n"
            + "  \"staticTerrainParityReady\": " + StaticTerrainParityDiagnostics.isSolidVisibleListStable(
                STATIC_TERRAIN_PARITY_READY_FRAMES,
                STATIC_TERRAIN_PARITY_MIN_SECTIONS
            ) + ",\n"
            + "  \"performanceMode\": " + PERFORMANCE_MODE + ",\n"
            + "  \"performanceWarmupFrames\": " + PERFORMANCE_WARMUP_FRAMES + ",\n"
            + "  \"performanceMeasureFrames\": " + PERFORMANCE_MEASURE_FRAMES + "\n"
            + "}\n";
        writeString(METADATA_PATH, json);
    }

    private static String capturesJson(String dimension) {
        StringBuilder json = new StringBuilder();
        json.append("  \"captures\": [");
        for (int i = 0; i < CAPTURES.size(); i++) {
            PoseCapture capture = CAPTURES.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append("\n    {\n")
                .append("      \"index\": ").append(capture.index()).append(",\n")
                .append(field("poseName", capture.poseName(), 6)).append(",\n")
                .append(field("screenshot", capture.screenshot(), 6)).append(",\n")
                .append(field("backend", observedBackend(), 6)).append(",\n")
                .append(field("shaderEnabled", property("shaderEnabled", "unknown"), 6)).append(",\n")
                .append(field("shaderPack", property("shaderPack", "unknown"), 6)).append(",\n")
                .append(field("gitCommit", property("gitCommit", "unknown"), 6)).append(",\n")
                .append("      \"window\": { \"width\": ").append(windowWidth).append(", \"height\": ").append(windowHeight).append(" },\n")
                .append(field("dimension", dimension, 6)).append(",\n")
                .append("      \"position\": { \"x\": ").append(format(capture.position().x)).append(", \"y\": ").append(format(capture.position().y)).append(", \"z\": ").append(format(capture.position().z)).append(" },\n")
                .append("      \"requestedYaw\": ").append(format(capture.requestedYaw())).append(",\n")
                .append("      \"requestedPitch\": ").append(format(capture.requestedPitch())).append(",\n")
                .append("      \"observedYaw\": ").append(format(capture.observedYaw())).append(",\n")
                .append("      \"observedPitch\": ").append(format(capture.observedPitch())).append(",\n")
                .append("      \"renderedFrameIndex\": ").append(capture.renderedFrameIndex()).append(",\n")
                .append("      \"weatherRendererTicks\": ").append(capture.weatherRendererTicks()).append(",\n")
                .append("      \"weatherRendererPartialTick\": ").append(format(capture.weatherRendererPartialTick())).append(",\n")
                .append("      \"weatherSemanticFingerprint\": \"").append(escape(capture.weatherSemanticFingerprint())).append("\",\n")
                .append("      \"lightmapSemanticFingerprint\": \"").append(escape(capture.lightmapSemanticFingerprint())).append("\",\n")
                .append("      \"gameTime\": ").append(capture.gameTime()).append(",\n")
                .append("      \"frozenModelProducer\": ").append(capture.modelEmission() == null ? "null" : capture.modelEmission().json()).append("\n")
                .append("    }");
        }
        if (!CAPTURES.isEmpty()) {
            json.append("\n  ");
        }
        json.append("]");
        return json.toString();
    }

    private static void writePerformanceStatus(Minecraft minecraft, String status) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        String dimension = level == null ? "missing" : level.dimension().location().toString();
        int measuredFrames = Math.max(0, performanceFrames - PERFORMANCE_WARMUP_FRAMES);
        measuredFrames = Math.min(measuredFrames, PERFORMANCE_MEASURE_FRAMES);
        String json = "{\n"
            + field("status", status) + ",\n"
            + field("backend", observedBackend()) + ",\n"
            + field("shaderEnabled", System.getProperty("mattmc.dev.deterministicCameraCapture.shaderEnabled", "unknown")) + ",\n"
            + field("shaderPack", System.getProperty("mattmc.dev.deterministicCameraCapture.shaderPack", "unknown")) + ",\n"
            + "  \"window\": { \"width\": " + windowWidth + ", \"height\": " + windowHeight + " },\n"
            + field("dimension", dimension) + ",\n"
            + vec3("position", player == null ? Vec3.ZERO : player.position()) + ",\n"
            + "  \"yaw\": " + format(player == null ? 0.0F : player.getYRot()) + ",\n"
            + "  \"pitch\": " + format(player == null ? 0.0F : player.getXRot()) + ",\n"
            + "  \"warmupFramesRequested\": " + PERFORMANCE_WARMUP_FRAMES + ",\n"
            + "  \"measureFramesRequested\": " + PERFORMANCE_MEASURE_FRAMES + ",\n"
            + "  \"measuredFrames\": " + measuredFrames + ",\n"
            + field("benchmarkFingerprintHash", benchmarkFingerprintHash(dimension)) + "\n"
            + "}\n";
        writeString(PERFORMANCE_STATUS_PATH, json);
    }

    private static String benchmarkFingerprintHash(String dimension) {
        String fingerprint = benchmarkFingerprintCanonical(dimension);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(fingerprint.hashCode());
        }
    }

    private static String benchmarkFingerprintJson(String dimension) {
        return "  \"benchmarkFingerprint\": {\n"
            + field("schemaVersion", BENCHMARK_FINGERPRINT_SCHEMA_VERSION, 4) + ",\n"
            + field("repositoryIdentity", property("repositoryIdentity", "frozen-java"), 4) + ",\n"
            + field("repositoryCommit", property("gitCommit", "unknown"), 4) + ",\n"
            + field("repositoryWorktree", property("repositoryWorktree", "unknown"), 4) + ",\n"
            + field("backend", observedBackend(), 4) + ",\n"
            + field("shaderEnabled", property("shaderEnabled", "unknown"), 4) + ",\n"
            + field("shaderPack", property("shaderPack", "unknown"), 4) + ",\n"
            + field("resolution", windowWidth + "x" + windowHeight, 4) + ",\n"
            + field("world", WORLD_NAME, 4) + ",\n"
            + field("dimension", dimension, 4) + ",\n"
            + "    \"distantHorizonsActive\": " + isDistantHorizonsActive() + ",\n"
            + field("distantHorizonsConfig", property("dhConfigFingerprint", "unknown"), 4) + ",\n"
            + field("cameraPath", CAMERA_PATH_ID, 4) + ",\n"
            + "    \"position\": { \"x\": " + format(FIXED_X) + ", \"y\": " + format(FIXED_Y) + ", \"z\": " + format(FIXED_Z) + " },\n"
            + "    \"yaw\": " + format(FIXED_YAW) + ",\n"
            + "    \"pitch\": " + format(FIXED_PITCH) + ",\n"
            + "    \"yawDelta\": " + format(YAW_DELTA) + ",\n"
            + "    \"poseCount\": " + POSE_COUNT + ",\n"
            + "    \"framesPerPose\": " + FRAMES_PER_POSE + ",\n"
            + "    \"warmupFrames\": " + PERFORMANCE_WARMUP_FRAMES + ",\n"
            + "    \"measureFrames\": " + PERFORMANCE_MEASURE_FRAMES + ",\n"
            + "    \"settledReadyFrames\": 0,\n"
            + "    \"settledReadyMaxWaitFrames\": 2400,\n"
            + field("settledReadyFamilies", "sodium-terrain,distant-horizons", 4) + ",\n"
            + field("graphicsSettings", property("graphicsSettingsFingerprint", "unknown"), 4) + ",\n"
            + field("jvm", property("jvmFingerprint", "unknown"), 4) + ",\n"
            + field("harness", HARNESS_VERSION, 4) + ",\n"
            + field("profilerFlags", profilerFlags(), 4) + ",\n"
            + field("galContractVersion", optionalStaticString("net.vulkanic.VulkanicGalExecutionRequest", "CONTRACT_VERSION", "contractVersion"), 4) + ",\n"
            + field("galContractFingerprint", optionalStaticString("net.vulkanic.VulkanicGalExecutionRequest", "CONTRACT_SCHEMA_FINGERPRINT", "contractSchemaFingerprint"), 4) + ",\n"
            + field("galV2ContractVersion", optionalStaticString("net.vulkanic.VulkanicGalV2", "CONTRACT_VERSION", "contractVersion"), 4) + ",\n"
            + field("galV2ContractFingerprint", optionalStaticString("net.vulkanic.VulkanicGalV2", "CONTRACT_SCHEMA_FINGERPRINT", "contractSchemaFingerprint"), 4) + "\n"
            + "  }";
    }

    private static String benchmarkFingerprintCanonical(String dimension) {
        return "schemaVersion=" + BENCHMARK_FINGERPRINT_SCHEMA_VERSION + "\n"
            + "repositoryIdentity=" + property("repositoryIdentity", "frozen-java") + "\n"
            + "repositoryCommit=" + property("gitCommit", "unknown") + "\n"
            + "repositoryWorktree=" + property("repositoryWorktree", "unknown") + "\n"
            + "backend=" + observedBackend() + "\n"
            + "shaderEnabled=" + property("shaderEnabled", "unknown") + "\n"
            + "shaderPack=" + property("shaderPack", "unknown") + "\n"
            + "resolution=" + windowWidth + "x" + windowHeight + "\n"
            + "world=" + WORLD_NAME + "\n"
            + "dimension=" + dimension + "\n"
            + "distantHorizonsActive=" + isDistantHorizonsActive() + "\n"
            + "distantHorizonsConfig=" + property("dhConfigFingerprint", "unknown") + "\n"
            + "cameraPath=" + CAMERA_PATH_ID + "\n"
            + "position=" + format(FIXED_X) + "," + format(FIXED_Y) + "," + format(FIXED_Z) + "\n"
            + "yaw=" + format(FIXED_YAW) + "\n"
            + "pitch=" + format(FIXED_PITCH) + "\n"
            + "yawDelta=" + format(YAW_DELTA) + "\n"
            + "poseCount=" + POSE_COUNT + "\n"
            + "framesPerPose=" + FRAMES_PER_POSE + "\n"
            + "warmupFrames=" + PERFORMANCE_WARMUP_FRAMES + "\n"
            + "measureFrames=" + PERFORMANCE_MEASURE_FRAMES + "\n"
            + "settledReadyFrames=0\n"
            + "settledReadyMaxWaitFrames=2400\n"
            + "settledReadyFamilies=sodium-terrain,distant-horizons\n"
            + "graphicsSettings=" + property("graphicsSettingsFingerprint", "unknown") + "\n"
            + "jvm=" + property("jvmFingerprint", "unknown") + "\n"
            + "harness=" + HARNESS_VERSION + "\n"
            + "profilerFlags=" + profilerFlags() + "\n"
            + "galContractVersion=" + optionalStaticString("net.vulkanic.VulkanicGalExecutionRequest", "CONTRACT_VERSION", "contractVersion") + "\n"
            + "galContractFingerprint=" + optionalStaticString("net.vulkanic.VulkanicGalExecutionRequest", "CONTRACT_SCHEMA_FINGERPRINT", "contractSchemaFingerprint") + "\n"
            + "galV2ContractVersion=" + optionalStaticString("net.vulkanic.VulkanicGalV2", "CONTRACT_VERSION", "contractVersion") + "\n"
            + "galV2ContractFingerprint=" + optionalStaticString("net.vulkanic.VulkanicGalV2", "CONTRACT_SCHEMA_FINGERPRINT", "contractSchemaFingerprint") + "\n";
    }

    private static boolean isDistantHorizonsActive() {
        try {
            Class.forName("com.seibel.distanthorizons.core.render.renderer.LodRenderer", false, DeterministicCameraCapture.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private static String property(String key, String fallback) {
        return System.getProperty("mattmc.dev.deterministicCameraCapture." + key, fallback);
    }

    private static String profilerFlags() {
        return "perfAudit=" + Boolean.getBoolean("mattmc.perfAudit")
            + ";vulkanPerfAudit=" + Boolean.getBoolean("mattmc.vulkan.perfAudit")
            + ";legacyGraphicsLowering=" + Boolean.getBoolean("mattmc.perfAudit.legacyGraphicsLowering")
            + ";resourcePlanBreakdown=" + Boolean.getBoolean("mattmc.perfAudit.resourcePlanBreakdown")
            + ";maxFrameSamples=" + Integer.getInteger("mattmc.perfAudit.maxFrameSamples", 4096);
    }

    private static String optionalStaticString(String className, String fieldName, String methodName) {
        try {
            Class<?> type = Class.forName(className, false, DeterministicCameraCapture.class.getClassLoader());
            try {
                return String.valueOf(type.getField(fieldName).get(null));
            } catch (NoSuchFieldException ignored) {
                return String.valueOf(type.getMethod(methodName).invoke(null));
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            return "unavailable";
        }
    }

    private static void finish(Minecraft minecraft) {
        if (complete || failed) {
            return;
        }
        complete = true;
        writeMetadata(minecraft, "complete");
        restoreRuntimeOverrides(minecraft);
        LOGGER.info("Frozen deterministic benchmark complete metadata={}", METADATA_PATH);
    }

    private static void fail(String reason) {
        if (failed || complete) {
            return;
        }
        failed = true;
        LOGGER.error("Frozen deterministic benchmark failed: {}", reason);
        writeString(METADATA_PATH, "{\n  \"status\": \"failed\",\n" + field("reason", reason) + "\n}\n");
    }

    private static void writeString(Path path, String value) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.error("Unable to write deterministic benchmark file {}", path, exception);
        }
    }

    private static String field(String name, String value) {
        return field(name, value, 2);
    }

    private static String field(String name, String value, int indent) {
        return " ".repeat(indent) + "\"" + escape(name) + "\": \"" + escape(value) + "\"";
    }

    private static String vec3(String name, Vec3 value) {
        return "  \"" + escape(name) + "\": { \"x\": " + format(value.x) + ", \"y\": " + format(value.y) + ", \"z\": " + format(value.z) + " }";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static double doubleProperty(String suffix, double fallback) {
        String value = System.getProperty("mattmc.dev.deterministicCameraCapture." + suffix);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Double.parseDouble(value);
    }

    private record Pose(String name, float yaw, float pitch, double offsetX, double offsetY, double offsetZ) {
        private Pose(String name, float yaw, float pitch) {
            this(name, yaw, pitch, 0.0, 0.0, 0.0);
        }
    }

    private record PoseCapture(
        int index,
        String poseName,
        String screenshot,
        String dimension,
        Vec3 position,
        float requestedYaw,
        float requestedPitch,
        float observedYaw,
        float observedPitch,
        long renderedFrameIndex,
        int weatherRendererTicks,
        float weatherRendererPartialTick,
        String weatherSemanticFingerprint,
        String lightmapSemanticFingerprint,
        long gameTime,
        FrozenModelEmission modelEmission
    ) {
    }
}
