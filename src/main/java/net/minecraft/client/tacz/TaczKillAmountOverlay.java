package net.minecraft.client.tacz;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.TaczMvpGunItem;

public final class TaczKillAmountOverlay {
	private static final ResourceLocation ID = ResourceLocation.withDefaultNamespace("tacz_kill_amount_overlay");
	private static final long KILL_DISPLAY_TIME_MS = 3000L;
	private static final long KILL_STACK_TIME_MS = 1500L;
	private static long killTimestamp = -1L;
	private static int killAmount;

	private TaczKillAmountOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, ID, TaczKillAmountOverlay::render);
	}

	public static void mark(int amount) {
		long now = System.currentTimeMillis();
		if (now - killTimestamp > KILL_STACK_TIME_MS) {
			killAmount = 0;
		}

		killAmount = Math.min(99, killAmount + Math.max(1, amount));
		killTimestamp = now;
	}

	private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || minecraft.options.hideGui || !(player.getMainHandItem().getItem() instanceof TaczMvpGunItem)) {
			return;
		}

		long elapsed = System.currentTimeMillis() - killTimestamp;
		if (killTimestamp < 0L || elapsed > KILL_DISPLAY_TIME_MS || killAmount <= 0) {
			return;
		}

		int alpha = 255;
		long fadeStart = KILL_DISPLAY_TIME_MS * 2L / 3L;
		if (elapsed > fadeStart) {
			alpha = Mth.clamp((int)(255L * (KILL_DISPLAY_TIME_MS - elapsed) / (KILL_DISPLAY_TIME_MS - fadeStart)), 0, 255);
		}

		Font font = minecraft.font;
		String text = "\u2620 x " + killAmount;
		int width = guiGraphics.guiWidth();
		int height = guiGraphics.guiHeight();
		int color = Mth.hsvToArgb((killAmount % 10) / 10.0F, 0.7F, 1.0F, alpha);
		int x = width - 18 - font.width(text);
		int y = height - 62;
		guiGraphics.nextStratum();
		guiGraphics.drawString(font, text, x, y, color, true);
	}
}
