package net.sodium.client.gl.tessellation;

import net.vulkanic.VulkanicPrimitiveMode;

public enum GlPrimitiveType {
    POINTS(0),
    LINES(1),
    TRIANGLES(4),
    PATCHES(14);

    private final int id;

    GlPrimitiveType(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public VulkanicPrimitiveMode toVulkanicPrimitiveMode() {
        return switch (this) {
            case LINES -> VulkanicPrimitiveMode.LINES;
            case TRIANGLES -> VulkanicPrimitiveMode.TRIANGLES;
            case PATCHES -> VulkanicPrimitiveMode.PATCHES;
            case POINTS -> throw new IllegalArgumentException("POINTS is not represented by VulkanicPrimitiveMode");
        };
    }

    public static GlPrimitiveType fromVulkanicPrimitiveMode(VulkanicPrimitiveMode mode) {
        return switch (mode) {
            case LINES -> LINES;
            case TRIANGLES -> TRIANGLES;
            case PATCHES -> PATCHES;
            case TRIANGLE_STRIP -> throw new IllegalArgumentException("TRIANGLE_STRIP is not supported by Sodium tessellation");
            case TRIANGLE_FAN -> throw new IllegalArgumentException("TRIANGLE_FAN is not supported by Sodium tessellation");
        };
    }
}
