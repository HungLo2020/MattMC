package net.sodium.api.vertex.attributes.common;

import org.lwjgl.system.MemoryUtil;

public class OverlayAttribute {
    public static void set(long ptr, int overlay) {
        MemoryUtil.memPutInt(ptr, overlay);
    }

    public static int get(long ptr) {
        return MemoryUtil.memGetInt(ptr);
    }
}
