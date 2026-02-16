package net.sodium.client.gl.array;

import net.sodium.client.gl.GlObject;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

/**
 * Provides Vertex Array functionality on supported platforms.
 */
public class GlVertexArray extends GlObject {
    public static final int NULL_ARRAY_ID = 0;

    public GlVertexArray() {
        CommandContext ctx = VulkanicAPI.getImmediateContext();
        this.setHandle(VulkanicAPI.createVertexArray(ctx));
    }
}
