package net.fabricmc.fabric.api.renderer.v1.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * Fabric extension interface for LayerRenderState.
 */
public interface FabricLayerRenderState {
    
    /**
     * Gets the mutable mesh for this layer render state.
     */
    MutableMesh fabric_getMutableMesh();
    
    /**
     * Gets the quad emitter for this layer render state.
     */
    default QuadEmitter emitter() {
        return fabric_getMutableMesh().emitter();
    }
}
