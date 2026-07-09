package net.sodium.client.render.chunk.vertex.format;

public record NativeChunkVertexFormat(
        int stride,
        int blockIdOffset,
        int normalOffset,
        int tangentOffset,
        int midUvOffset,
        int midBlockOffset
) {
    public NativeChunkVertexFormat {
        if (stride < 20) {
            throw new IllegalArgumentException("Chunk vertex stride must be at least 20 bytes");
        }
    }

    public static NativeChunkVertexFormat compact() {
        return new NativeChunkVertexFormat(20, 0, 0, 0, 0, 0);
    }
}
