package net.minecraft.client.renderer.sodium.render.viewport.frustum;

public interface Frustum {
    boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);

    int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);
}
