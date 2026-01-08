package net.fabricmc.fabric.api.renderer.v1.mesh;

import java.util.function.Consumer;

/**
 * A mutable mesh that can be built and then converted to an immutable Mesh.
 */
public interface MutableMesh extends MeshView {
    
    /**
     * Gets the quad emitter for adding quads to this mesh.
     */
    QuadEmitter emitter();
    
    /**
     * Builds and returns an immutable copy of this mesh.
     * The mutable mesh is cleared after this call.
     */
    default Mesh build() {
        Mesh mesh = immutableCopy();
        clear();
        return mesh;
    }
    
    /**
     * Returns an immutable copy without clearing this mesh.
     */
    Mesh immutableCopy();
    
    /**
     * Clears all quads from this mesh.
     */
    void clear();
    
    /**
     * Iterates over all quads in this mesh with mutable access.
     */
    void forEachMutable(Consumer<? super MutableQuadView> action);
}
