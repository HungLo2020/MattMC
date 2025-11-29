package net.matt.quantize.item.item;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.matt.quantize.item.utils.CustomTabBehavior;

public class ItemInventoryOnly extends Item implements CustomTabBehavior {

    public ItemInventoryOnly(Properties properties) {
        super(properties);
    }

    @Override
    public void fillItemCategory(CreativeModeTab.Output contents) {

    }
}
