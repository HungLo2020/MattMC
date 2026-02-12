package net.sodium.client.gl.tessellation;

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
}
