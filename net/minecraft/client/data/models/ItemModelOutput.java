package net.minecraft.client.data.models;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.world.item.Item;

@Environment(EnvType.CLIENT)
public interface ItemModelOutput {
	void accept(Item item, ItemModel.Unbaked unbaked);

	void copy(Item item, Item item2);
}
