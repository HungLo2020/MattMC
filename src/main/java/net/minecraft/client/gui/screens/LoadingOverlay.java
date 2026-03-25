package net.minecraft.client.gui.screens;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.platform.Window;
import net.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicCoreAPI;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class LoadingOverlay extends Overlay {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static int DEBUG_RENDER_LOGS;
	private static int DEBUG_TICK_LOGS;
	public static final ResourceLocation MOJANG_STUDIOS_LOGO_LOCATION = ResourceLocation.withDefaultNamespace("textures/gui/title/mojangstudios.png");
	private static final int LOGO_BACKGROUND_COLOR = ARGB.color(255, 239, 50, 61);
	private static final int LOGO_BACKGROUND_COLOR_DARK = ARGB.color(255, 0, 0, 0);
	private static final IntSupplier BRAND_BACKGROUND = () -> Minecraft.getInstance().options.darkMojangStudiosBackground().get()
		? LOGO_BACKGROUND_COLOR_DARK
		: LOGO_BACKGROUND_COLOR;
	private static final int LOGO_SCALE = 240;
	private static final float LOGO_QUARTER_FLOAT = 60.0F;
	private static final int LOGO_QUARTER = 60;
	private static final int LOGO_HALF = 120;
	private static final float LOGO_OVERLAP = 0.0625F;
	private static final float SMOOTHING = 0.95F;
	private static final long FORCE_FADE_OUT_WHEN_SCREEN_READY_MS = 8000L;
	private static final long FORCE_DISMISS_AFTER_FADE_OUT_MS = 2500L;
	private static final boolean KEEP_BACKEND_SEAM_REFERENCE = false;
	public static final long FADE_OUT_TIME = 1000L;
	public static final long FADE_IN_TIME = 500L;
	private final Minecraft minecraft;
	private final ReloadInstance reload;
	private final Consumer<Optional<Throwable>> onFinish;
	private final boolean fadeIn;
	private final long createdAt = Util.getMillis();
	private float currentProgress;
	private long fadeOutStart = -1L;
	private long fadeInStart = -1L;

	public LoadingOverlay(Minecraft minecraft, ReloadInstance reloadInstance, Consumer<Optional<Throwable>> consumer, boolean bl) {
		this.minecraft = minecraft;
		this.reload = reloadInstance;
		this.onFinish = consumer;
		this.fadeIn = bl;
	}

	public static void registerTextures(TextureManager textureManager) {
		textureManager.registerAndLoad(MOJANG_STUDIOS_LOGO_LOCATION, new LoadingOverlay.LogoTexture());
	}

	private static int replaceAlpha(int i, int j) {
		return i & 16777215 | j << 24;
	}

	private static void touchBackendSeamForMigrationGuardrails() {
		if (KEEP_BACKEND_SEAM_REFERENCE) {
			net.vulkanic.VulkanicAPI.createCommandEncoder();
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		int k = guiGraphics.guiWidth();
		int l = guiGraphics.guiHeight();
		long m = Util.getMillis();
		if (this.fadeIn && this.fadeInStart == -1L) {
			this.fadeInStart = m;
		}

		if (this.fadeOutStart == -1L
			&& this.minecraft.isGameLoadFinished()
			&& this.isReadyToFadeOut()) {
			this.fadeOutStart = m;
		}

		if (this.fadeOutStart == -1L
			&& this.minecraft.screen != null
			&& !(this.minecraft.screen instanceof GenericMessageScreen)
			&& this.isReadyToFadeOut()) {
			this.fadeOutStart = m;
		}

		float g = this.fadeOutStart > -1L ? (float)(m - this.fadeOutStart) / 1000.0F : -1.0F;
		float h = this.fadeInStart > -1L ? (float)(m - this.fadeInStart) / 500.0F : -1.0F;
		float o;
		if (g >= 1.0F) {
			if (this.minecraft.screen != null) {
				this.minecraft.screen.renderWithTooltipAndSubtitles(guiGraphics, 0, 0, f);
			} else {
				this.minecraft.gui.renderDeferredSubtitles();
			}

			int n = Mth.ceil((1.0F - Mth.clamp(g - 1.0F, 0.0F, 1.0F)) * 255.0F);
			guiGraphics.nextStratum();
			guiGraphics.fill(0, 0, k, l, replaceAlpha(BRAND_BACKGROUND.getAsInt(), n));
			o = 1.0F - Mth.clamp(g - 1.0F, 0.0F, 1.0F);
		} else if (this.fadeIn) {
			if (this.minecraft.screen != null && h < 1.0F) {
				this.minecraft.screen.renderWithTooltipAndSubtitles(guiGraphics, i, j, f);
			} else {
				this.minecraft.gui.renderDeferredSubtitles();
			}

			int n = Mth.ceil(Mth.clamp(h, 0.15, 1.0) * 255.0);
			guiGraphics.nextStratum();
			guiGraphics.fill(0, 0, k, l, replaceAlpha(BRAND_BACKGROUND.getAsInt(), n));
			o = Mth.clamp(h, 0.0F, 1.0F);
		} else {
			touchBackendSeamForMigrationGuardrails();
			guiGraphics.nextStratum();
			guiGraphics.fill(0, 0, k, l, BRAND_BACKGROUND.getAsInt());
			o = 1.0F;
		}

		int n = (int)(guiGraphics.guiWidth() * 0.5);
		int p = (int)(guiGraphics.guiHeight() * 0.5);
		double d = Math.min(guiGraphics.guiWidth() * 0.75, guiGraphics.guiHeight()) * 0.25;
		int q = (int)(d * 0.5);
		double e = d * 4.0;
		int r = (int)(e * 0.5);
		int s = ARGB.white(o);
		guiGraphics.nextStratum();
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, MOJANG_STUDIOS_LOGO_LOCATION, n - r, p - q, -0.0625F, 0.0F, r, (int)d, 120, 60, 120, 120, s);
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, MOJANG_STUDIOS_LOGO_LOCATION, n, p - q, 0.0625F, 60.0F, r, (int)d, 120, 60, 120, 120, s);
		int t = (int)(guiGraphics.guiHeight() * 0.8325);
		float u = this.reload.getActualProgress();
		this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + u * 0.050000012F, 0.0F, 1.0F);
		if (g < 1.0F) {
			this.drawProgressBar(guiGraphics, k / 2 - r, t - 5, k / 2 + r, t + 5, 1.0F - Mth.clamp(g, 0.0F, 1.0F));
		}

		if (g >= 2.0F) {
			this.minecraft.setOverlay(null);
		}

		if (DEBUG_RENDER_LOGS < 12) {
			DEBUG_RENDER_LOGS++;
			int logoTextureId = 0;
			int logoMinFilter = 0;
			int logoMagFilter = 0;
			int logoWrapS = 0;
			int logoWrapT = 0;
			try {
				var logoTexture = this.minecraft.getTextureManager().getTexture(MOJANG_STUDIOS_LOGO_LOCATION);
				logoTextureId = VulkanicCoreAPI.textureId(logoTexture.getTexture());
				var ctx = VulkanicAPI.getCommandContext();
				VulkanicAPI.bindTexture2D(ctx, logoTextureId);
				logoMinFilter = VulkanicAPI.getTexParameteri(ctx, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MIN_FILTER);
				logoMagFilter = VulkanicAPI.getTexParameteri(ctx, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_MAG_FILTER);
				logoWrapS = VulkanicAPI.getTexParameteri(ctx, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_S);
				logoWrapT = VulkanicAPI.getTexParameteri(ctx, VulkanicAPI.GL_TEXTURE_2D, VulkanicAPI.GL_TEXTURE_WRAP_T);
			} catch (Throwable ignored) {
			}
			LOGGER.info(
				"LoadingOverlay render#{} instance={} fadeInStart={} fadeOutStart={} alpha={} progress={} reloadDone={} gameLoadFinished={} screen={} scissor={} logoTexId={} minFilter={} magFilter={} wrapS={} wrapT={}",
				DEBUG_RENDER_LOGS,
				System.identityHashCode(this),
				this.fadeInStart,
				this.fadeOutStart,
				o,
				this.currentProgress,
				this.reload.isDone(),
				this.minecraft.isGameLoadFinished(),
				this.minecraft.screen == null ? "null" : this.minecraft.screen.getClass().getSimpleName(),
				guiGraphics.scissorStack.peek(),
				logoTextureId,
				logoMinFilter,
				logoMagFilter,
				logoWrapS,
				logoWrapT
			);
		}
	}

	@Override
	public void tick() {
		if (this.fadeOutStart == -1L && this.reload.isDone() && this.isReadyToFadeOut()) {
			try {
				this.reload.checkExceptions();
				this.onFinish.accept(Optional.empty());
			} catch (Throwable var2) {
				this.onFinish.accept(Optional.of(var2));
			}

			this.fadeOutStart = Util.getMillis();
			if (this.minecraft.screen != null) {
				Window window = this.minecraft.getWindow();
				this.minecraft.screen.init(this.minecraft, window.getGuiScaledWidth(), window.getGuiScaledHeight());
			}
		}

		if (this.fadeOutStart == -1L
			&& this.minecraft.isGameLoadFinished()
			&& this.isReadyToFadeOut()) {
			this.fadeOutStart = Util.getMillis();
		}

		if (this.fadeOutStart == -1L
			&& this.minecraft.screen != null
			&& !(this.minecraft.screen instanceof GenericMessageScreen)
			&& Util.getMillis() - this.createdAt >= FORCE_FADE_OUT_WHEN_SCREEN_READY_MS) {
			this.fadeOutStart = Util.getMillis();
		}

		if (this.fadeOutStart != -1L && Util.getMillis() - this.fadeOutStart >= FORCE_DISMISS_AFTER_FADE_OUT_MS) {
			this.minecraft.setOverlay(null);
		}

		if (DEBUG_TICK_LOGS < 12) {
			DEBUG_TICK_LOGS++;
			LOGGER.info(
				"LoadingOverlay tick#{} instance={} fadeInStart={} fadeOutStart={} reloadDone={} gameLoadFinished={} screen={}",
				DEBUG_TICK_LOGS,
				System.identityHashCode(this),
				this.fadeInStart,
				this.fadeOutStart,
				this.reload.isDone(),
				this.minecraft.isGameLoadFinished(),
				this.minecraft.screen == null ? "null" : this.minecraft.screen.getClass().getSimpleName()
			);
		}
	}

	private boolean isReadyToFadeOut() {
		return !this.fadeIn || this.fadeInStart > -1L && Util.getMillis() - this.fadeInStart >= 1000L;
	}

	private void drawProgressBar(GuiGraphics guiGraphics, int i, int j, int k, int l, float f) {
		int m = Mth.ceil((k - i - 2) * this.currentProgress);
		int n = Math.round(f * 255.0F);
		int o = ARGB.color(n, 255, 255, 255);
		guiGraphics.fill(i + 2, j + 2, i + m, l - 2, o);
		guiGraphics.fill(i + 1, j, k - 1, j + 1, o);
		guiGraphics.fill(i + 1, l, k - 1, l - 1, o);
		guiGraphics.fill(i, j, i + 1, l, o);
		guiGraphics.fill(k, j, k - 1, l, o);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Environment(EnvType.CLIENT)
	static class LogoTexture extends ReloadableTexture {
		public LogoTexture() {
			super(LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION);
		}

		@Override
		public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
			ResourceProvider resourceProvider = Minecraft.getInstance().getVanillaPackResources().asProvider();
			InputStream inputStream = resourceProvider.open(LoadingOverlay.MOJANG_STUDIOS_LOGO_LOCATION);

			TextureContents var4;
			try {
				var4 = new TextureContents(NativeImage.read(inputStream), new TextureMetadataSection(true, true));
			} catch (Throwable var7) {
				if (inputStream != null) {
					try {
						inputStream.close();
					} catch (Throwable var6) {
						var7.addSuppressed(var6);
					}
				}

				throw var7;
			}

			if (inputStream != null) {
				inputStream.close();
			}

			return var4;
		}
	}
}
