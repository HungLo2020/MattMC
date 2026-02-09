package net.sodium.client.gl.attribute;

/**
 * An enumeration over the supported data types that can be used for vertex attributes.
 */
public record GlVertexAttributeFormat(int typeId, int size) {
    public static final GlVertexAttributeFormat FLOAT = new GlVertexAttributeFormat(5126, 4);
    public static final GlVertexAttributeFormat INT = new GlVertexAttributeFormat(5124, 4);
    public static final GlVertexAttributeFormat SHORT = new GlVertexAttributeFormat(5122, 2);
    public static final GlVertexAttributeFormat BYTE = new GlVertexAttributeFormat(5120, 1);
    public static final GlVertexAttributeFormat UNSIGNED_SHORT = new GlVertexAttributeFormat(5123, 2);
    public static final GlVertexAttributeFormat UNSIGNED_BYTE = new GlVertexAttributeFormat(5121, 1);
    public static final GlVertexAttributeFormat UNSIGNED_INT = new GlVertexAttributeFormat(5125, 4);
}
