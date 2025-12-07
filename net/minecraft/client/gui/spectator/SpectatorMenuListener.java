package net.minecraft.client.gui.spectator;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface SpectatorMenuListener {
	void onSpectatorMenuClosed(SpectatorMenu spectatorMenu);
}
