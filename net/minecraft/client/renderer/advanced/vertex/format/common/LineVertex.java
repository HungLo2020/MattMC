package net.minecraft.client.renderer.advanced.vertex.format.common;

import org.jetbrains.annotations.ApiStatus;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.advanced.vertex.attributes.common.ColorAttribute;
import net.minecraft.client.renderer.advanced.vertex.attributes.common.NormalAttribute;
import net.minecraft.client.renderer.advanced.vertex.attributes.common.PositionAttribute;

public final class LineVertex  {
    public static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_COLOR_NORMAL;

    public static final int STRIDE = 20;

    private static final int OFFSET_POSITION = 0;
    private static final int OFFSET_COLOR = 12;
    private static final int OFFSET_NORMAL = 16;

    public static void put(long ptr,
                           float x, float y, float z, int color, int normal) {
        PositionAttribute.put(ptr + OFFSET_POSITION, x, y, z);
        ColorAttribute.set(ptr + OFFSET_COLOR, color);
        NormalAttribute.set(ptr + OFFSET_NORMAL, normal);
    }
}
