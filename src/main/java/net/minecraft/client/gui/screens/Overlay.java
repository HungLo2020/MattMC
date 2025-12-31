package net.minecraft.client.gui.screens;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.components.Renderable;

@Environment(EnvType.CLIENT)
public abstract class Overlay implements Renderable {
	public boolean isPauseScreen() {
		return true;
	}

	public void tick() {
	}
}
