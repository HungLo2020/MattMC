package mattmc.client.renderer;

import mattmc.client.renderer.chunk.RegionRenderer;

import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.client.renderer.chunk.ChunkRenderer;
import mattmc.world.level.Level;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders all loaded chunks in an infinite world.
 * Similar to RegionRenderer but works with the Level system.
 */
public class LevelRenderer {
    private final ChunkRenderer chunkRenderer;
    
    public LevelRenderer() {
        this.chunkRenderer = new ChunkRenderer();
    }
    
    /**
     * Render all loaded chunks in the world.
     */
    public void render(Level world, float playerX, float playerY, float playerZ) {
        glPushMatrix();
        
        // Render each loaded chunk
        for (LevelChunk chunk : world.getLoadedChunks()) {
            // Calculate chunk world position
            int chunkWorldX = chunk.chunkX() * LevelChunk.WIDTH;
            int chunkWorldZ = chunk.chunkZ() * LevelChunk.DEPTH;
            
            glPushMatrix();
            glTranslatef(chunkWorldX, 0, chunkWorldZ);
            chunkRenderer.renderChunk(chunk);
            glPopMatrix();
        }
        
        glPopMatrix();
    }
}
