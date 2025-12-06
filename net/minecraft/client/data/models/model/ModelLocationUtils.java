package net.minecraft.client.data.models.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
public class ModelLocationUtils {
	@Deprecated
	public static ResourceLocation decorateBlockModelLocation(String string) {
		return ResourceLocation.withDefaultNamespace("block/" + string);
	}

	public static ResourceLocation decorateItemModelLocation(String string) {
		return ResourceLocation.withDefaultNamespace("item/" + string);
	}

	public static ResourceLocation getModelLocation(Block block, String string) {
		ResourceLocation resourceLocation = BuiltInRegistries.BLOCK.getKey(block);
		return resourceLocation.withPath(string2 -> "block/" + string2 + string);
	}

	public static ResourceLocation getModelLocation(Block block) {
		ResourceLocation resourceLocation = BuiltInRegistries.BLOCK.getKey(block);
		return resourceLocation.withPrefix("block/");
	}

	public static ResourceLocation getModelLocation(Item item) {
		ResourceLocation resourceLocation = BuiltInRegistries.ITEM.getKey(item);
		return resourceLocation.withPrefix("item/");
	}

	public static ResourceLocation getModelLocation(Item item, String string) {
		ResourceLocation resourceLocation = BuiltInRegistries.ITEM.getKey(item);
		return resourceLocation.withPath(string2 -> "item/" + string2 + string);
	}
}
