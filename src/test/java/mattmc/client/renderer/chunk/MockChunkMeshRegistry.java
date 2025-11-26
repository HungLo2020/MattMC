package mattmc.client.renderer.chunk;

import mattmc.world.level.chunk.LevelChunk;

/**
 * Mock implementation of ChunkMeshRegistry for testing without OpenGL context.
 * 
 * <p>This allows unit tests to run without requiring LWJGL/OpenGL initialization,
 * which would crash in headless CI environments.
 */
public class MockChunkMeshRegistry implements ChunkMeshRegistry {
    
    @Override
    public boolean hasChunkMesh(LevelChunk chunk) {
        return false; // No meshes in mock
    }
    
    @Override
    public int getMeshIdForChunk(LevelChunk chunk) {
        return -1; // No meshes
    }
    
    @Override
    public int getDefaultMaterialId() {
        return 0;
    }
}
