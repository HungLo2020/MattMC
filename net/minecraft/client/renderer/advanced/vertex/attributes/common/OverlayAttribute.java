package net.minecraft.client.renderer.advanced.vertex.attributes.common;

import org.jetbrains.annotations.ApiStatus;

import org.lwjgl.system.MemoryUtil;

public class OverlayAttribute {
    public static void set(long ptr, int overlay) {
        MemoryUtil.memPutInt(ptr + 0, overlay);
    }

    public static int get(long ptr) {
        return MemoryUtil.memGetInt(ptr);
    }
}
