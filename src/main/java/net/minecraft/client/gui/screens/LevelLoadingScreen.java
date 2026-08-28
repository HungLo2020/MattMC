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
	private static int rustGalLoadingGridProducerDiagnostics;

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
		if (this.loadTracker.isLevelReady()) {
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
		if (chunkLoadStatusView != null) {
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
	}

	private void drawProgressBar(GuiGraphics guiGraphics, int i, int j, int k, int l, float f) {
		if (net.vulkanic.gui.RustGalGuiRenderer.currentExecutionRoute().usesRustGui()) {
			// Keep the loading bar on the same explicit Rust GUI route as the
			// chunk-status grid.  Constructing the rectangle state here is semantic
			// input only; it is not submitted to Java's renderer.
			int guiWidth = guiGraphics.guiWidth();
			int guiHeight = guiGraphics.guiHeight();
			java.util.List<net.vulkanic.gui.RustGalGuiElementRenderState> background =
				net.vulkanic.gui.RustGalGuiRenderer.tryEnqueueUniformRectangle(
					new net.minecraft.client.gui.render.state.ColoredRectangleRenderState(
						RenderPipelines.GUI, TextureSetup.noTexture(), new org.joml.Matrix3x2f(guiGraphics.pose()),
						i, j, i + k, j + l, -16777216, -16777216, null
					), guiWidth, guiHeight
				);
			int filledWidth = Math.round(Mth.clamp(f, 0.0F, 1.0F) * k);
			java.util.List<net.vulkanic.gui.RustGalGuiElementRenderState> fill = filledWidth == 0
				? java.util.List.of()
				: net.vulkanic.gui.RustGalGuiRenderer.tryEnqueueUniformRectangle(
					new net.minecraft.client.gui.render.state.ColoredRectangleRenderState(
						RenderPipelines.GUI, TextureSetup.noTexture(), new org.joml.Matrix3x2f(guiGraphics.pose()),
						i, j, i + filledWidth, j + l, -16711936, -16711936, null
					), guiWidth, guiHeight
				);
			if (background == null || fill == null) {
				throw new IllegalStateException("Rust OpenGL loading progress semantic admission failed");
			}
			for (net.vulkanic.gui.RustGalGuiElementRenderState element : background) guiGraphics.guiRenderState.submitGuiElement(element);
			for (net.vulkanic.gui.RustGalGuiElementRenderState element : fill) guiGraphics.guiRenderState.submitGuiElement(element);
			return;
		}
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
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
			|| Boolean.getBoolean("mattmc.dev.rustGalVulkanWholeFrame")) {
			if (Boolean.getBoolean("mattmc.dev.graphicsAuditSliceMetrics") && rustGalLoadingGridProducerDiagnostics++ < 4) {
				System.out.println("[MattMC graphics audit] loading-grid semantic producer grid=" + n + " stride=" + m);
			}
			int[] semanticColors = new int[n * n];
			for (int r = 0; r < n; r++) for (int s = 0; s < n; s++) semanticColors[r * n + s] = COLORS.getInt(chunkLoadStatusView.get(r, s));
			guiGraphics.guiRenderState.submitGuiElement(new net.vulkanic.gui.RustGalLoadingGridRenderState(semanticColors, n, p, q, k, m, guiGraphics.guiWidth(), guiGraphics.guiHeight()));
			return;
		}
		int[] colors = new int[n * n];
		for (int r = 0; r < n; r++) {
			for (int s = 0; s < n; s++) {
				colors[r * n + s] = COLORS.getInt(chunkLoadStatusView.get(r, s));
			}
		}
		var rustGrid = net.vulkanic.gui.RustGalGuiRenderer.tryEnqueueLoadingGrid(
			colors, n, p, q, k, m, guiGraphics.guiWidth(), guiGraphics.guiHeight());
		if (rustGrid != null) {
			for (var element : rustGrid) guiGraphics.guiRenderState.submitGuiElement(element);
			return;
		}

		for (int r = 0; r < n; r++) {
			if (m != k) {
				for (int s = 0; s < n; s++) {
					ChunkStatus chunkStatus = chunkLoadStatusView.get(r, s);
					int t = p + r * m;
					int u = q + s * m;
					guiGraphics.fill(t, u, t + k, u + k, ARGB.opaque(COLORS.getInt(chunkStatus)));
				}
				continue;
			}
			int s = 0;
			while (s < n) {
				int color = ARGB.opaque(COLORS.getInt(chunkLoadStatusView.get(r, s)));
				int runEnd = s + 1;
				while (runEnd < n
					&& ARGB.opaque(COLORS.getInt(chunkLoadStatusView.get(r, runEnd))) == color) {
					runEnd++;
				}
				int t = p + r * m;
				int u = q + s * m;
				guiGraphics.fill(t, u, t + (runEnd - s) * m, u + k, color);
				s = runEnd;
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
				if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()
					|| net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
					float gameTime = Minecraft.getInstance().level == null
						? 0.0F
						: (float)Minecraft.getInstance().level.getGameTime() + f;
					guiGraphics.submitRustEndPortal(gameTime);
					break;
				}
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
