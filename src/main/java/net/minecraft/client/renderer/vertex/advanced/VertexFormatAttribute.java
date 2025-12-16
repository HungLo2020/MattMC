package net.minecraft.client.renderer.vertex.advanced;

import net.minecraft.client.renderer.gl.advanced.attribute.GlVertexAttributeFormat;

public record VertexFormatAttribute(String name, GlVertexAttributeFormat format, int count, boolean normalized, boolean intType) {

}
