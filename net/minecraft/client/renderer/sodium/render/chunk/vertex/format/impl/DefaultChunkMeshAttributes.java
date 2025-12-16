package net.minecraft.client.renderer.sodium.render.chunk.vertex.format.impl;

import net.minecraft.client.renderer.gl.advanced.attribute.GlVertexAttributeFormat;
import net.minecraft.client.renderer.vertex.advanced.VertexFormatAttribute;

public class DefaultChunkMeshAttributes {
    public static final VertexFormatAttribute POSITION = new VertexFormatAttribute("POSITION", GlVertexAttributeFormat.UNSIGNED_INT, 2, false, true);
    public static final VertexFormatAttribute COLOR = new VertexFormatAttribute("COLOR", GlVertexAttributeFormat.UNSIGNED_BYTE, 4, true, false);
    public static final VertexFormatAttribute TEXTURE = new VertexFormatAttribute("TEXTURE", GlVertexAttributeFormat.UNSIGNED_SHORT, 2, false, true);
    public static final VertexFormatAttribute LIGHT_MATERIAL_INDEX = new VertexFormatAttribute("LIGHT_MATERIAL_INDEX", GlVertexAttributeFormat.UNSIGNED_BYTE, 4, false, true);
}
