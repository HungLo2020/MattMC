package net.voxelmap;

import net.voxelmap.persistent.ThreadManager;
import net.voxelmap.util.BiomeRepository;
import net.voxelmap.util.CommandUtils;
import net.blaze3d.vertex.PoseStack;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.Font;
import net.voxelmap.textures.Sprite;
import net.voxelmap.util.Waypoint;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public final class VoxelConstants {
    private static final Logger LOGGER = LogManager.getLogger("VoxelMap");
    private static final VoxelMap VOXELMAP_INSTANCE = new VoxelMap();
    private static int elapsedTicks;
    private static final ResourceLocation OPTIONS_BACKGROUND_TEXTURE = ResourceLocation.parse("textures/block/dirt.png");
    public static final boolean DEBUG = false;
    private static boolean initialized;
    private static boolean initializationAttempted;
    private static Events events;
    private static PacketBridge packetBridge;
    private static ModApiBridge modApiBridge;

    private VoxelConstants() {}

    @NotNull
    public static Minecraft getMinecraft() { return Minecraft.getInstance(); }

    public static boolean isSinglePlayer() { return getMinecraft().isLocalServer(); }

    @NotNull
    public static Logger getLogger() { return LOGGER; }

    @NotNull
    public static Optional<IntegratedServer> getIntegratedServer() { return Optional.ofNullable(getMinecraft().getSingleplayerServer()); }

    @NotNull
    public static Optional<Level> getWorldByKey(ResourceKey<Level> key) { return getIntegratedServer().map(integratedServer -> integratedServer.getLevel(key)); }

    @NotNull
    public static ClientLevel getClientWorld() { return (ClientLevel) getPlayer().level(); }

    @NotNull
    public static LocalPlayer getPlayer() {
        LocalPlayer player = getMinecraft().player;

        if (player == null) {
            String error = "Attempted to fetch player entity while not in-game!";

            getLogger().fatal(error);
            throw new IllegalStateException(error);
        }

        return player;
    }

    @NotNull
    public static VoxelMap getVoxelMapInstance() { return VOXELMAP_INSTANCE; }

    static void tick() { elapsedTicks = elapsedTicks == Integer.MAX_VALUE ? 1 : elapsedTicks + 1; }

    public static int getElapsedTicks() { return elapsedTicks; }

    static { elapsedTicks = 0; }

    public static ResourceLocation getOptionsBackgroundTexture() {
        return OPTIONS_BACKGROUND_TEXTURE;
    }

    public static synchronized void lateInit() {
        if (initialized && VoxelConstants.getVoxelMapInstance().getMap() != null) {
            return;
        }
        if (events == null) {
            // Stripped client boots can omit the Fabric initializer. Install
            // the same bridge once so the semantic VoxelMap producer remains
            // reachable; atlas construction is CPU-only on Rust Vulkan.
            events = new net.voxelmap.fabric.FabricEvents();
        }
        if (initializationAttempted) return;
        initializationAttempted = true;
        try {
            VoxelConstants.getVoxelMapInstance().lateInit(true, false);
            // Publish initialized only after Map construction completed. The
            // Rust semantic HUD callsite must never observe a half-built
            // VoxelMap instance and turn that into a Java fallback.
            initialized = VoxelConstants.getVoxelMapInstance().getMap() != null;
        } catch (RuntimeException error) {
            initialized = false;
            LOGGER.warn("VoxelMap semantic initialization deferred", error);
        }
    }

    public static void clientTick() {
        if (!initialized) {
            lateInit();
        }

        if (initialized) {
            VoxelConstants.getVoxelMapInstance().onTick();
        }

    }

    public static void renderOverlay(GuiGraphics guiGraphics) {
        if (!initialized) {
            lateInit();
        }

        if (!initialized) {
            return; // Failed to initialize, skip rendering
        }

        if (getVoxelMapInstance().getMap() == null) {
            return;
        }
        getVoxelMapInstance().onTickSemantic();
        if (!getVoxelMapInstance().getMap().renderRustSemanticOverlay(guiGraphics)) {
            throw new IllegalStateException("Rust whole-frame Vulkan VoxelMap overlay is unavailable for the current waypoint/settings state");
        }
        return;

    }

    public static boolean onSendChatMessage(String message) {
        if (message.startsWith("newWaypoint")) {
            CommandUtils.waypointClicked(message);
            return false;
        } else if (message.startsWith("ztp")) {
            CommandUtils.teleport(message);
            return false;
        } else {
            return true;
        }
    }

    /** Lifecycle hook retained for callers that validate VoxelMap ownership. */
    public static void assertRustWholeFrameWaypointsSupported() {
        if (!initialized || getVoxelMapInstance().getMap() == null) {
            return;
        }
        // Beacon-only and sign/icon/label semantics are copied by the explicit
        // producers below; no Java renderer is admitted on this route.
        getVoxelMapInstance().getWaypointManager();
    }

    /** Submits copied VoxelMap 3D icon/background/text semantics before Rust world-text extraction. */
    public static void submitRustWaypointSemantics(SubmitNodeCollector collector, PoseStack poseStack, Camera camera) {
		if (!initialized || collector == null || camera == null) return;
        WaypointManager manager = getVoxelMapInstance().getWaypointManager();
        if (!manager.options.waypointsAllowed || !manager.options.showWaypoints) return;
        net.minecraft.world.phys.Vec3 cameraPos = camera.getPosition();
        for (Waypoint waypoint : manager.getWaypoints()) {
            if (waypoint == null || !waypoint.isActive()) continue;
            double distance = Math.sqrt(waypoint.getDistanceSqToCamera(camera));
            if (manager.options.maxWaypointDisplayDistance >= 0 && distance >= manager.options.maxWaypointDisplayDistance) continue;
            String name = waypoint.name == null ? "" : waypoint.name;
            if (name.isEmpty()) continue;
            float scale = ((float)distance * 0.1F + 1.0F) * 0.0266F * manager.options.waypointSignScale;
            poseStack.pushPose();
            poseStack.translate((float)(waypoint.getX() + 0.5 - cameraPos.x), (float)(waypoint.getY() + 1.62 - cameraPos.y), (float)(waypoint.getZ() + 0.5 - cameraPos.z));
            poseStack.mulPose(net.math.Axis.YP.rotationDegrees(-camera.getYRot()));
            poseStack.mulPose(net.math.Axis.XP.rotationDegrees(camera.getXRot()));
            poseStack.scale(-scale, -scale, -scale);
			Sprite icon = manager.getTextureAtlas().getAtlasSprite("voxelmap:images/waypoints/waypoint" + waypoint.imageSuffix + ".png");
			if (icon == manager.getTextureAtlas().getMissingImage()) icon = manager.getTextureAtlas().getAtlasSprite("voxelmap:images/waypoints/waypoint.png");
			float[] iconVertices = {-10, -10, 0, -10, 10, 0, 10, 10, 0, 10, -10, 0};
			// Submit the source PNG as a semantic asset rather than the mod-owned
			// stitched atlas. This preserves the icon pixels while avoiding any
			// dependency on VoxelMap's Java atlas texture or native handle.
			String iconPath = "images/waypoints/waypoint" + waypoint.imageSuffix + ".png";
			if (icon == manager.getTextureAtlas().getMissingImage()) iconPath = "images/waypoints/waypoint.png";
			ResourceLocation iconSource = ResourceLocation.fromNamespaceAndPath("voxelmap", iconPath);
			float[] iconUvs = {0, 0, 0, 1, 1, 1, 1, 0};
			if (!collector.submitTranslucentTexturedQuadSemantic(poseStack, (RenderType)null, iconSource, iconVertices, iconUvs, waypoint.getUnifiedColor(), 0x00F000F0)) {
                throw new IllegalStateException("Rust whole-frame waypoint icon route rejected semantic quad (mode="
					+ true
					+ ", route=" + net.vulkanic.world.WorldRenderRoutePolicy.currentTexturedBillboardRoute() + ")");
            }
            int halfWidth = Minecraft.getInstance().font.width(name) / 2;
            float[] backgroundVertices = {-halfWidth - 2, 8, 0, -halfWidth - 2, 19, 0, halfWidth + 2, 19, 0, halfWidth + 2, 8, 0};
            float[] backgroundUvs = {0, 0, 0, 1, 1, 1, 1, 0};
            int background = (0x99 << 24) | ((int)(waypoint.red * 255) << 16) | ((int)(waypoint.green * 255) << 8) | (int)(waypoint.blue * 255);
            if (!collector.submitColoredQuadsSemantic(poseStack, (RenderType)null, backgroundVertices, backgroundUvs,
				new int[] {background}, 0x00F000F0)) {
                throw new IllegalStateException("Rust whole-frame waypoint label route rejected semantic background");
            }
            collector.submitTextSemantic(
                poseStack, -halfWidth, 10, Component.literal(name).getVisualOrderText(), false,
                Font.DisplayMode.SEE_THROUGH, 0xFFFFFFFF, 0, 0x00F000F0, 0
            );
            poseStack.popPose();
        }
    }

    public static void onShutDown() {
        VoxelConstants.getLogger().info("Saving all world maps");
        VoxelConstants.getVoxelMapInstance().getPersistentMap().purgeCachedRegions();
        VoxelConstants.getVoxelMapInstance().getMapOptions().saveAll();
        BiomeRepository.saveBiomeColors();
        long shutdownTime = System.currentTimeMillis();

        while (ThreadManager.executorService.getQueue().size() + ThreadManager.executorService.getActiveCount() > 0 && System.currentTimeMillis() - shutdownTime < 10000L) {
            Thread.onSpinWait();
        }
    }

    public static void playerRunTeleportCommand(double x, double y, double z) {
        MapSettingsManager mapSettingsManager = VoxelConstants.getVoxelMapInstance().getMapOptions();
        String cmd = mapSettingsManager.serverTeleportCommand == null ? mapSettingsManager.teleportCommand : mapSettingsManager.serverTeleportCommand;
        cmd = cmd.replace("%p", VoxelConstants.getPlayer().getName().getString()).replace("%x", String.valueOf(x + 0.5)).replace("%y", String.valueOf(y)).replace("%z", String.valueOf(z + 0.5));
        VoxelConstants.getPlayer().connection.sendCommand(cmd);
    }

    public static int moveScoreboard(int bottomX, int entriesHeight) {
        double unscaledHeight = Map.getMinTablistOffset(); // / scaleFactor;
        if (VoxelMap.mapOptions.hide || !VoxelMap.mapOptions.minimapAllowed || VoxelMap.mapOptions.mapCorner != 1 || !VoxelMap.mapOptions.moveScoreBoardDown || !Double.isFinite(unscaledHeight)) {
            return bottomX;
        }
        double scaleFactor = Minecraft.getInstance().getWindow().getGuiScale(); // 1x 2x 3x, ...
        double mapHeightScaled = unscaledHeight * 1.37 / scaleFactor; // * 1.37 because unscaledHeight is just the map without the text around it

        int fontHeight = Minecraft.getInstance().font.lineHeight; // height of the title line
        float statusIconOffset = Map.getStatusIconOffset();
        int statusIconOffsetInt = Float.isFinite(statusIconOffset) ? (int) statusIconOffset : 0;
        int minBottom = (int) (mapHeightScaled + entriesHeight + fontHeight + statusIconOffsetInt);

        return Math.max(bottomX, minBottom);
    }

    public static void setEvents(Events events) {
        VoxelConstants.events = events;
    }

    public static Events getEvents() {
        return events;
    }

    public static PacketBridge getPacketBridge() {
        return packetBridge;
    }

    public static void setPacketBridge(PacketBridge packetBridge) {
        VoxelConstants.packetBridge = packetBridge;
    }

    public static void setModApiBride(ModApiBridge modApiBridge) {
        VoxelConstants.modApiBridge = modApiBridge;
    }

}
