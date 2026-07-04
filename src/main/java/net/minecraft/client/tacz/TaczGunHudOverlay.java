package net.minecraft.client.tacz;

import java.text.DecimalFormat;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.SharedConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczMvpGunItem;

public final class TaczGunHudOverlay {
	private static final ResourceLocation ID = ResourceLocation.withDefaultNamespace("tacz_gun_hud_overlay");
	private static final ResourceLocation FIRE_MODE_SEMI = ResourceLocation.withDefaultNamespace("textures/hud/fire_mode_semi.png");
	private static final DecimalFormat CURRENT_AMMO_FORMAT = new DecimalFormat("000");
	private static final DecimalFormat INVENTORY_AMMO_FORMAT = new DecimalFormat("0000");
	private static final int MAX_AMMO_COUNT = 9999;
	private static long checkAmmoTimestamp = -1L;
	private static int cachedInventoryAmmoCount;

	private TaczGunHudOverlay() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, ID, TaczGunHudOverlay::render);
		TaczHeatBarOverlay.register();
		TaczKillAmountOverlay.register();
	}

	private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || minecraft.options.hideGui) {
			return;
		}

		ItemStack gunStack = player.getMainHandItem();
		if (!(gunStack.getItem() instanceof TaczMvpGunItem gunItem)) {
			return;
		}

		handleCacheCount(player, gunItem);
		int width = guiGraphics.guiWidth();
		int height = guiGraphics.guiHeight();
		int maxAmmo = TaczMvpGunItem.getMagazineSize(gunStack);
		int currentAmmo = Math.min(TaczMvpGunItem.getAmmo(gunStack), MAX_AMMO_COUNT);
		int reserveAmmo = player.hasInfiniteMaterials() ? MAX_AMMO_COUNT : Math.min(cachedInventoryAmmoCount, MAX_AMMO_COUNT);
		boolean empty = currentAmmo <= 0;
		int ammoColor = empty || currentAmmo < Math.min(10, maxAmmo * 0.25F) ? 0xFFFF5555 : 0xFFFFFFFF;
		int reserveColor = player.hasInfiniteMaterials() ? 0xFF55FFFF : 0xFFAAAAAA;
		String currentAmmoText = CURRENT_AMMO_FORMAT.format(currentAmmo);
		String reserveAmmoText = INVENTORY_AMMO_FORMAT.format(reserveAmmo);
		Font font = minecraft.font;

		guiGraphics.nextStratum();
		guiGraphics.fill(width - 75, height - 43, width - 74, height - 25, 0xFFFFFFFF);

		guiGraphics.pose().pushMatrix();
		guiGraphics.pose().scale(1.5F, 1.5F);
		guiGraphics.drawString(font, currentAmmoText, Math.round((width - 70) / 1.5F), Math.round((height - 43) / 1.5F), ammoColor, false);
		guiGraphics.pose().popMatrix();

		guiGraphics.pose().pushMatrix();
		guiGraphics.pose().scale(0.8F, 0.8F);
		int reserveX = Math.round((width - 68 + font.width(currentAmmoText) * 1.5F) / 0.8F);
		int reserveY = Math.round((height - 43) / 0.8F);
		guiGraphics.drawString(font, reserveAmmoText, reserveX, reserveY, reserveColor, false);
		guiGraphics.pose().popMatrix();

		String debugInfo = SharedConstants.getCurrentVersion().name() + "-TACZ";
		guiGraphics.pose().pushMatrix();
		guiGraphics.pose().scale(0.5F, 0.5F);
		guiGraphics.drawString(font, debugInfo, Math.round((width - 70) / 0.5F), Math.round((height - 29.0F) / 0.5F), 0xFFAAAAAA, false);
		guiGraphics.pose().popMatrix();

		int gunIconColor = empty ? 0xFFFF4D4D : 0xFFFFFFFF;
		guiGraphics.blit(
			RenderPipelines.GUI_TEXTURED,
			ResourceLocation.withDefaultNamespace("textures/gun/hud/" + gunItem.gunId() + ".png"),
			width - 117,
			height - 44,
			0.0F,
			0.0F,
			39,
			13,
			39,
			13,
			gunIconColor
		);
		guiGraphics.blit(
			RenderPipelines.GUI_TEXTURED,
			FIRE_MODE_SEMI,
			(int)(width - 68.5F + font.width(currentAmmoText) * 1.5F),
			height - 38,
			0.0F,
			0.0F,
			10,
			10,
			10,
			10,
			0xFFFFFFFF
		);
	}

	private static void handleCacheCount(LocalPlayer player, TaczMvpGunItem gunItem) {
		if (System.currentTimeMillis() - checkAmmoTimestamp <= 50L) {
			return;
		}

		checkAmmoTimestamp = System.currentTimeMillis();
		cachedInventoryAmmoCount = Math.min(gunItem.countReserveAmmo(player), MAX_AMMO_COUNT);
	}
}
