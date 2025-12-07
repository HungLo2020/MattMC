package net.minecraft.client.gui.screens.worldselection;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface WorldCreationContextMapper {
	WorldCreationContext apply(
		ReloadableServerResources reloadableServerResources, LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess, DataPackReloadCookie dataPackReloadCookie
	);
}
