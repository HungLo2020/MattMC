package net.vulkanic;

/**
 * Index data type for indexed draw calls.
 *
 * <p>Used with {@link VulkanicRenderPass#setIndexBuffer(VulkanicBuffer, VulkanicIndexType)}
 * to specify how vertex indices are packed in the index buffer.
 *
 * <p>In OpenGL these map to GL_UNSIGNED_BYTE/SHORT/INT.
 * In Vulkan these map to VK_INDEX_TYPE_UINT8_EXT/UINT16/UINT32.
 */
public enum VulkanicIndexType {

    /** 8-bit unsigned integer indices. */
    BYTE(1),

    /** 16-bit unsigned integer indices. */
    SHORT(2),

    /** 32-bit unsigned integer indices (most common). */
    INT(4);

    private final int bytesPerIndex;

    VulkanicIndexType(int bytesPerIndex) {
        this.bytesPerIndex = bytesPerIndex;
    }

    /**
     * Returns the number of bytes consumed by a single index value.
     */
    public int bytesPerIndex() {
        return bytesPerIndex;
    }

    /**
     * Returns the corresponding Vulkanic/OpenGL unsigned integer type constant.
     * The returned value matches VulkanicAPI.GL_UNSIGNED_BYTE/SHORT/INT.
     */
    public int toGlTypeConstant() {
        return switch (this) {
            case BYTE  -> 0x1401; // VulkanicAPI.GL_UNSIGNED_BYTE
            case SHORT -> 0x1403; // VulkanicAPI.GL_UNSIGNED_SHORT
            case INT   -> 0x1405; // VulkanicAPI.GL_UNSIGNED_INT
        };
    }
}
