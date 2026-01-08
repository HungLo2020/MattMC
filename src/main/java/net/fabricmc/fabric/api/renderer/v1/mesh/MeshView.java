package net.fabricmc.fabric.api.renderer.v1.mesh;

import java.util.function.Consumer;

/**
 * Read-only view of a Mesh. Used to iterate over quad data.
 */
public interface MeshView {
    
    /**
     * Returns the number of quads in this mesh.
     */
    int size();
    
    /**
     * Iterates over all quads in this mesh, passing each to the consumer.
     */
    void forEach(Consumer<? super QuadView> consumer);
    
    /**
     * Outputs all quads from this mesh to the given emitter.
     */
    void outputTo(QuadEmitter emitter);
}
