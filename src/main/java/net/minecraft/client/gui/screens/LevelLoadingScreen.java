package net.minecraft.client.gui.screens;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class LevelLoadingScreen extends Screen {
	private static final Component DOWNLOADING_TERRAIN_TEXT = Component.translatable("multiplayer.downloadingTerrain");
	private static final Component READY_TO_PLAY_TEXT = Component.translatable("narrator.ready_to_play");
	private static final long NARRATION_DELAY_MS = 2000L;
	private static final int PROGRESS_BAR_WIDTH = 200;
	private LevelLoadTracker loadTracker;
	private float smoothedProgress;
	private long lastNarration = -1L;
	private LevelLoadingScreen.Reason reason;
	private String currentTip;
	@Nullable
	private TextureAtlasSprite cachedNetherPortalSprite;
	private static final Object2IntMap<ChunkStatus> COLORS = (Object2IntMap<ChunkStatus>)Util.make(new Object2IntOpenHashMap(), object2IntOpenHashMap -> {
		object2IntOpenHashMap.defaultReturnValue(0);
		object2IntOpenHashMap.put(ChunkStatus.EMPTY, 5526612);
		object2IntOpenHashMap.put(ChunkStatus.STRUCTURE_STARTS, 10066329);
		object2IntOpenHashMap.put(ChunkStatus.STRUCTURE_REFERENCES, 6250897);
		object2IntOpenHashMap.put(ChunkStatus.BIOMES, 8434258);
		object2IntOpenHashMap.put(ChunkStatus.NOISE, 13750737);
		object2IntOpenHashMap.put(ChunkStatus.SURFACE, 7497737);
		object2IntOpenHashMap.put(ChunkStatus.CARVERS, 3159410);
		object2IntOpenHashMap.put(ChunkStatus.FEATURES, 2213376);
		object2IntOpenHashMap.put(ChunkStatus.INITIALIZE_LIGHT, 13421772);
		object2IntOpenHashMap.put(ChunkStatus.LIGHT, 16769184);
		object2IntOpenHashMap.put(ChunkStatus.SPAWN, 15884384);
		object2IntOpenHashMap.put(ChunkStatus.FULL, 16777215);
	});
	/**
	 * Opt-in capture-only hold used by the cross-repository parity harness. It
	 * never selects a renderer or affects an ordinary level load.
	 */
	private static final boolean LEVEL_LOADING_SCREEN_CAPTURE =
		Boolean.getBoolean("mattmc.dev.levelLoadingScreenCapture");
	private static final int LEVEL_LOADING_SCREEN_CAPTURE_WARMUP_FRAMES = 3;
	private int levelLoadingScreenCaptureFrames;
	@Nullable
	private Path levelLoadingScreenCaptureAck;
	private boolean levelLoadingScreenCaptureRequested;
	@Nullable
	private int[] levelLoadingScreenCaptureGridColors;
	private int levelLoadingScreenCaptureGridSize;

	public LevelLoadingScreen(LevelLoadTracker levelLoadTracker, LevelLoadingScreen.Reason reason) {
		super(Component.empty());
		this.loadTracker = levelLoadTracker;
		this.reason = reason;
		this.currentTip = Minecraft.getInstance().getTipsManager().getRandomTip();
	}

	public void update(LevelLoadTracker levelLoadTracker, LevelLoadingScreen.Reason reason) {
		this.loadTracker = levelLoadTracker;
		this.reason = reason;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected boolean shouldNarrateNavigation() {
		return false;
	}

	@Override
	protected void updateNarratedWidget(NarrationElementOutput narrationElementOutput) {
		if (this.loadTracker.hasProgress()) {
			narrationElementOutput.add(
				NarratedElementType.TITLE, Component.translatable("loading.progress", new Object[]{Mth.floor(this.loadTracker.serverProgress() * 100.0F)})
			);
		}
	}

	@Override
	public void tick() {
		super.tick();
		this.smoothedProgress = this.smoothedProgress + (this.loadTracker.serverProgress() - this.smoothedProgress) * 0.2F;
		if (this.loadTracker.isLevelReady() && !this.awaitingLevelLoadingScreenCapture()) {
			this.onClose();
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		long l = Util.getMillis();
		if (l - this.lastNarration > 2000L) {
			this.lastNarration = l;
			this.triggerImmediateNarration(true);
		}

		int k = this.width / 2;
		int m = this.height / 2;
		ChunkLoadStatusView chunkLoadStatusView = this.loadTracker.statusView();
		int o;
		if (this.awaitingLevelLoadingScreenCapture() && this.levelLoadingScreenCaptureGridColors != null) {
			renderCapturedChunks(guiGraphics, k, m, 2, 0, this.levelLoadingScreenCaptureGridSize, this.levelLoadingScreenCaptureGridColors);
			o = m - (this.levelLoadingScreenCaptureGridSize - 1) - 9 * 3;
		} else if (chunkLoadStatusView != null) {
			int n = 2;
			renderChunks(guiGraphics, k, m, 2, 0, chunkLoadStatusView);
			o = m - chunkLoadStatusView.radius() * 2 - 9 * 3;
		} else {
			o = m - 50;
		}

		guiGraphics.drawCenteredString(this.font, DOWNLOADING_TERRAIN_TEXT, k, o, -1);
		if (this.loadTracker.hasProgress()) {
			this.drawProgressBar(guiGraphics, k - 100, o + 9 + 3, 200, 2, this.smoothedProgress);
		}

		// Display tip in bottom left corner
		if (this.currentTip != null) {
			guiGraphics.drawString(this.font, this.currentTip, 5, this.height - 15, -1);
		}
		this.requestLevelLoadingScreenCapture(chunkLoadStatusView, k, m);
	}

	private boolean awaitingLevelLoadingScreenCapture() {
		if (!LEVEL_LOADING_SCREEN_CAPTURE || !this.levelLoadingScreenCaptureRequested) return false;
		return this.levelLoadingScreenCaptureAck == null || !Files.isRegularFile(this.levelLoadingScreenCaptureAck);
	}

	/** Writes one bounded external presentation-capture request after real screen traversal. */
	private void requestLevelLoadingScreenCapture(@Nullable ChunkLoadStatusView statusView, int centerX, int centerY) {
		// LoadingOverlay invokes this screen while it fades, then paints the
		// Mojang logo above it.  That is a valid transition frame, but it cannot
		// prove the independently presented level-loading UI.  Wait for the
		// overlay to retire and then settle three actual screen frames before
		// asking the external harness to capture the final presentation.
		if (!LEVEL_LOADING_SCREEN_CAPTURE || this.levelLoadingScreenCaptureRequested
			|| Minecraft.getInstance().getOverlay() != null
			|| ++this.levelLoadingScreenCaptureFrames < LEVEL_LOADING_SCREEN_CAPTURE_WARMUP_FRAMES) return;
		String screenshotDirectory = System.getProperty("mattmc.dev.deterministicCameraCapture.screenshotDir", "").trim();
		if (screenshotDirectory.isEmpty()) return;
		Path directory = Path.of(screenshotDirectory);
		Path screenshot = directory.resolve("level_loading_screen.png");
		Path request = directory.resolve("capture_request_level_loading_screen.json");
		Path acknowledgement = directory.resolve("capture_request_level_loading_screen.ack.json");
		int gridSize = statusView == null ? 0 : statusView.radius() * 2 + 1;
		int gridChecksum = 1;
		int[] gridColors = new int[gridSize * gridSize];
		if (statusView != null) for (int x = 0; x < gridSize; x++) for (int z = 0; z < gridSize; z++) {
			int color = COLORS.getInt(statusView.get(x, z));
			gridColors[x * gridSize + z] = color;
			gridChecksum = 31 * gridChecksum + color;
		}
		String json = "{\n"
			+ "  \"captureKind\": \"level-loading-screen\",\n"
			+ "  \"screenshot\": \"" + escapeCapturePath(screenshot) + "\",\n"
			+ "  \"ack\": \"" + escapeCapturePath(acknowledgement) + "\",\n"
			+ "  \"reason\": \"" + this.reason.name() + "\",\n"
			+ "  \"screenWidth\": " + this.width + ",\n"
			+ "  \"screenHeight\": " + this.height + ",\n"
			+ "  \"centerX\": " + centerX + ",\n"
			+ "  \"centerY\": " + centerY + ",\n"
			+ "  \"gridSize\": " + gridSize + ",\n"
			+ "  \"gridChecksum\": " + gridChecksum + ",\n"
			+ "  \"overlayPresent\": false,\n"
			+ "  \"progressMilli\": " + Math.round(this.smoothedProgress * 1000.0F) + "\n"
			+ "}\n";
		try {
			Files.createDirectories(directory);
			Files.writeString(request, json, StandardCharsets.UTF_8);
			// The external desktop capture occurs after this render callback. Keep
			// the actual emitted status snapshot stable only while this opt-in
			// receipt is outstanding, rather than racing a completed tracker into
			// a visually empty diagnostic frame.
			this.levelLoadingScreenCaptureGridColors = gridColors;
			this.levelLoadingScreenCaptureGridSize = gridSize;
			this.levelLoadingScreenCaptureAck = acknowledgement;
			this.levelLoadingScreenCaptureRequested = true;
		} catch (IOException exception) {
			throw new IllegalStateException("failed to request level-loading-screen diagnostic capture", exception);
		}
	}

	/** Replays the capture receipt's copied status cells without consulting a later tracker state. */
	private static void renderCapturedChunks(GuiGraphics guiGraphics, int centerX, int centerY, int cellSize, int gap, int gridSize, int[] colors) {
		int stride = cellSize + gap;
		int extent = gridSize * stride - gap;
		int originX = centerX - extent / 2;
		int originY = centerY - extent / 2;
		for (int x = 0; x < gridSize; x++) for (int z = 0; z < gridSize; z++) {
			int left = originX + x * stride;
			int top = originY + z * stride;
			guiGraphics.fill(left, top, left + cellSize, top + cellSize, ARGB.opaque(colors[x * gridSize + z]));
		}
	}

	private static String escapeCapturePath(Path path) {
		return path.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private void drawProgressBar(GuiGraphics guiGraphics, int i, int j, int k, int l, float f) {
		guiGraphics.fill(i, j, i + k, j + l, -16777216);
		guiGraphics.fill(i, j, i + Math.round(f * k), j + l, -16711936);
	}

	public static void renderChunks(GuiGraphics guiGraphics, int i, int j, int k, int l, ChunkLoadStatusView chunkLoadStatusView) {
		int m = k + l;
		int n = chunkLoadStatusView.radius() * 2 + 1;
		int o = n * m - l;
		int p = i - o / 2;
		int q = j - o / 2;
		if (SharedConstants.DEBUG_CHUNKS) {
			int r = m / 2 + 1;
			guiGraphics.fill(i - r, j - r, i + r, j + r, -65536);
		}

		for (int r = 0; r < n; r++) {
			for (int s = 0; s < n; s++) {
				ChunkStatus chunkStatus = chunkLoadStatusView.get(r, s);
				int t = p + r * m;
				int u = q + s * m;
				guiGraphics.fill(t, u, t + k, u + k, ARGB.opaque(COLORS.getInt(chunkStatus)));
			}
		}
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
		switch (this.reason) {
			case NETHER_PORTAL:
				guiGraphics.blitSprite(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, this.getNetherPortalSprite(), 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight());
				break;
			case END_PORTAL:
				TextureManager textureManager = Minecraft.getInstance().getTextureManager();
				TextureSetup textureSetup = TextureSetup.doubleTexture(
					textureManager.getTexture(AbstractEndPortalRenderer.END_SKY_LOCATION).getTextureView(),
					textureManager.getTexture(AbstractEndPortalRenderer.END_PORTAL_LOCATION).getTextureView()
				);
				guiGraphics.fill(RenderPipelines.END_PORTAL, textureSetup, 0, 0, this.width, this.height);
				break;
			case OTHER:
				this.renderPanorama(guiGraphics, f);
				this.renderBlurredBackground(guiGraphics);
				this.renderMenuBackground(guiGraphics);
		}
	}

	private TextureAtlasSprite getNetherPortalSprite() {
		if (this.cachedNetherPortalSprite != null) {
			return this.cachedNetherPortalSprite;
		} else {
			this.cachedNetherPortalSprite = this.minecraft.getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.NETHER_PORTAL.defaultBlockState());
			return this.cachedNetherPortalSprite;
		}
	}

	@Override
	public void onClose() {
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Environment(EnvType.CLIENT)
	public static enum Reason {
		NETHER_PORTAL,
		END_PORTAL,
		OTHER;
	}
}
