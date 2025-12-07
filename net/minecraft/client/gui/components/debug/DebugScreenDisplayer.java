package net.minecraft.client.gui.components.debug;

import java.util.Collection;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public interface DebugScreenDisplayer {
	void addPriorityLine(String string);

	void addLine(String string);

	void addToGroup(ResourceLocation resourceLocation, Collection<String> collection);

	void addToGroup(ResourceLocation resourceLocation, String string);
}
