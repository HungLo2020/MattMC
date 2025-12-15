package net.minecraft.client.renderer.advanced.vertex.attributes.common;

import org.jetbrains.annotations.ApiStatus;

import org.lwjgl.system.MemoryUtil;

public class NormalAttribute {
    public static void set(long ptr, int normal) {
        MemoryUtil.memPutInt(ptr, normal);
    }

    public static int get(long ptr) {
        return MemoryUtil.memGetInt(ptr);
    }
}
