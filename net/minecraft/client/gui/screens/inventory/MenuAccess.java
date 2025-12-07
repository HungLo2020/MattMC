package net.minecraft.client.gui.screens.inventory;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.inventory.AbstractContainerMenu;

@Environment(EnvType.CLIENT)
public interface MenuAccess<T extends AbstractContainerMenu> {
	T getMenu();
}
