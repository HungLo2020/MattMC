package MattMC.renderer;

import MattMC.world.Chunk;
import MattMC.world.World;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders all loaded chunks in an infinite world.
 * Similar to RegionRenderer but works with the World system.
 */
public class WorldRenderer {
    private final ChunkRenderer chunkRenderer;
    
    public WorldRenderer() {
        this.chunkRenderer = new ChunkRenderer();
    }
    
    /**
     * Render all loaded chunks in the world.
     */
    public void render(World world, float playerX, float playerY, float playerZ) {
        glPushMatrix();
        
        // Render each loaded chunk
        for (Chunk chunk : world.getLoadedChunks()) {
            // Calculate chunk world position
            int chunkWorldX = chunk.chunkX() * Chunk.WIDTH;
            int chunkWorldZ = chunk.chunkZ() * Chunk.DEPTH;
            
            glPushMatrix();
            glTranslatef(chunkWorldX, 0, chunkWorldZ);
            chunkRenderer.renderChunk(chunk);
            glPopMatrix();
        }
        
        glPopMatrix();
    }
}
