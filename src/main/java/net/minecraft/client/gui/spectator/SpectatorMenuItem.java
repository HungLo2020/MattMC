package net.minecraft.client.gui.spectator;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public interface SpectatorMenuItem {
	void selectItem(SpectatorMenu spectatorMenu);

	Component getName();

	void renderIcon(GuiGraphics guiGraphics, float f, float g);

	boolean isEnabled();
}
