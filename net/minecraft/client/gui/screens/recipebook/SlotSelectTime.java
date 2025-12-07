package net.minecraft.client.gui.screens.recipebook;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface SlotSelectTime {
	int currentIndex();
}
