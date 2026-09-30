package net.sodium.client.model.quad.properties;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;

/**
 * Defines the orientation of vertices in a model quad. This information be used to re-orient the quad's vertices to a
 * consistent order, eliminating a number of shading issues caused by anisotropy problems.
 */
public enum ModelQuadOrientation {
    NORMAL(new int[] { 0, 1, 2, 3 }),
    FLIP(new int[] { 1, 2, 3, 0 });

    private final int[] indices;

    ModelQuadOrientation(int[] indices) {
        this.indices = indices;
    }

    /**
     * @return The re-oriented index of the vertex {@param idx}
     */
    public int getVertexIndex(int idx) {
        return this.indices[idx];
    }

}
