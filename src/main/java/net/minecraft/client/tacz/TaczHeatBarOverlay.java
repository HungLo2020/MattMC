package net.minecraft.client.tacz;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

public final class TaczHeatBarOverlay {
	private static final ResourceLocation ID = ResourceLocation.withDefaultNamespace("tacz_heat_bar");
	private static final ResourceLocation HEAT_BASE = ResourceLocation.withDefaultNamespace("textures/hud/heat_base.png");
	private static final ResourceLocation HEAT_BAR = ResourceLocation.withDefaultNamespace("textures/hud/heat_bar.png");
	private static float heatAmount;
	private static float heatMax = 1.0F;
	private static boolean locked;

	private TaczHeatBarOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, ID, TaczHeatBarOverlay::render);
	}

	public static void updateHeat(float amount, float max, boolean isLocked) {
		heatAmount = Math.max(0.0F, amount);
		heatMax = Math.max(1.0F, max);
		locked = isLocked;
	}

	private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || minecraft.options.hideGui || !player.getMainHandItem().is(Items.TACZ_GLOCK_17)) {
			return;
		}

		if (heatAmount <= 0.0F && !locked) {
			return;
		}

		int width = guiGraphics.guiWidth();
		int height = guiGraphics.guiHeight();
		int x = width - 118;
		int y = height - 56;
		int fillWidth = Mth.clamp(Math.round(39.0F * heatAmount / heatMax), 0, 39);
		int color = locked ? 0xFFFF5555 : 0xFFFFFFFF;
		guiGraphics.nextStratum();
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, HEAT_BASE, x, y, 0.0F, 0.0F, 40, 3, 40, 3, 0xFFFFFFFF);
		if (fillWidth > 0) {
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, HEAT_BAR, x, y, 0.0F, 0.0F, fillWidth, 3, 40, 3, color);
		}
	}
}
