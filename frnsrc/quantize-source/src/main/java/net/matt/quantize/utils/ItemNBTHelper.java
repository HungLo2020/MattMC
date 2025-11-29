package net.matt.quantize.utils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class ItemNBTHelper {

    public static long getLong(ItemStack stack, String key, long defaultValue) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(key) ? tag.getLong(key) : defaultValue;
    }

    public static void setLong(ItemStack stack, String key, long value) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(key, value);
    }

    public static CompoundTag getCompound(ItemStack stack, String key, boolean createIfAbsent) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(key)) {
            return tag.getCompound(key);
        }
        return createIfAbsent ? new CompoundTag() : null;
    }

    public static void setCompound(ItemStack stack, String key, CompoundTag value) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(key, value);
    }
}