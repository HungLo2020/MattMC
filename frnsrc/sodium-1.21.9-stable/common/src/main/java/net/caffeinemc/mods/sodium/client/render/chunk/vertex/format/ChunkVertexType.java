package frnsrc.sodium;

import net.caffeinemc.mods.sodium.client.gl.attribute.GlVertexFormat;

public interface ChunkVertexType {
    GlVertexFormat getVertexFormat();

    ChunkVertexEncoder getEncoder();
}
