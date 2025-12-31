package net.minecraft.client.gui.spectator;

import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public interface SpectatorMenuCategory {
	List<SpectatorMenuItem> getItems();

	Component getPrompt();
}
