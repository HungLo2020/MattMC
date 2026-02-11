package net.sodium.client.gl.array;

import net.sodium.client.gl.GlObject;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

/**
 * Provides Vertex Array functionality on supported platforms.
 */
public class GlVertexArray extends GlObject {
    private static final CommandContext CTX = VulkanicAPI.getImmediateContext();
    public static final int NULL_ARRAY_ID = 0;

    public GlVertexArray() {
        this.setHandle(VulkanicAPI.createVertexArrayObject(CTX));
    }
}
