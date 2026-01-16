package net.fabricmc.fabric.api.renderer.v1.mesh;

import net.minecraft.client.renderer.block.model.BakedQuad;

/**
 * Interface for building quads and emitting them to a mesh.
 */
public interface QuadEmitter extends MutableQuadView {
    
    @Override
    QuadEmitter pos(int vertexIndex, float x, float y, float z);
    
    @Override
    QuadEmitter color(int vertexIndex, int color);
    
    @Override
    QuadEmitter uv(int vertexIndex, float u, float v);
    
    @Override
    QuadEmitter lightmap(int vertexIndex, int lightmap);
    
    @Override
    QuadEmitter normal(int vertexIndex, float x, float y, float z);
    
    /**
     * Emits the current quad and resets state for the next quad.
     */
    QuadEmitter emit();
    
    /**
     * Copies data from a BakedQuad to this emitter.
     */
    QuadEmitter fromBakedQuad(BakedQuad quad);
}
