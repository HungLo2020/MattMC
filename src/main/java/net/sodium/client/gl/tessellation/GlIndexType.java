package net.sodium.client.gl.tessellation;

import net.vulkanic.VulkanicIndexType;

public enum GlIndexType {
    UNSIGNED_BYTE(5121, 1),
    UNSIGNED_SHORT(5123, 2),
    UNSIGNED_INT(5125, 4);

    private final int id;
    private final int stride;

    GlIndexType(int id, int stride) {
        this.id = id;
        this.stride = stride;
    }

    public int getFormatId() {
        return this.id;
    }

    public int getStride() {
        return this.stride;
    }

    public VulkanicIndexType toVulkanicIndexType() {
        return switch (this) {
            case UNSIGNED_BYTE -> VulkanicIndexType.BYTE;
            case UNSIGNED_SHORT -> VulkanicIndexType.SHORT;
            case UNSIGNED_INT -> VulkanicIndexType.INT;
        };
    }

    public static GlIndexType fromVulkanicIndexType(VulkanicIndexType type) {
        return switch (type) {
            case BYTE -> UNSIGNED_BYTE;
            case SHORT -> UNSIGNED_SHORT;
            case INT -> UNSIGNED_INT;
        };
    }
}
