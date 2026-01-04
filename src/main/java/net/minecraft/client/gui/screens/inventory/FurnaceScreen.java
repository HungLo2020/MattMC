package net.minecraft.client.gui.screens.inventory;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.FurnaceMenu;

@Environment(EnvType.CLIENT)
public class FurnaceScreen extends AbstractFurnaceScreen<FurnaceMenu> {
	private static final ResourceLocation LIT_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
	private static final ResourceLocation BURN_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");

	public FurnaceScreen(FurnaceMenu furnaceMenu, Inventory inventory, Component component) {
		super(furnaceMenu, inventory, component, TEXTURE, LIT_PROGRESS_SPRITE, BURN_PROGRESS_SPRITE);
	}
}
