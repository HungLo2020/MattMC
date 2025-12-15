package net.minecraft.client.renderer.advanced.vertex.attributes.common;

import org.jetbrains.annotations.ApiStatus;

import org.lwjgl.system.MemoryUtil;

public class ColorAttribute {
    public static void set(long ptr, int color) {
        MemoryUtil.memPutInt(ptr, color);
    }

    public static int get(long ptr) {
        return MemoryUtil.memGetInt(ptr);
    }
}
