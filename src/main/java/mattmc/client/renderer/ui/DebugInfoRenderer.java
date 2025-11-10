package mattmc.client.renderer.ui;

import mattmc.client.gui.components.TextRenderer;
import static org.lwjgl.opengl.GL11.*;

/**
 * Renders debug information overlay showing FPS, position, chunk info, etc.
 */
public class DebugInfoRenderer {
    
    /**
     * Render debug information at the top-left of the screen.
     */
    public void render(DebugInfo info, int screenWidth, int screenHeight) {
        if (info == null) return;
        
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        int x = 10;
        int y = 10;
        int lineHeight = 20;
        float textScale = 1.0f;
        
        // FPS
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        TextRenderer.drawText(String.format("FPS: %.1f", info.fps), x, y, textScale);
        y += lineHeight;
        
        // Position
        TextRenderer.drawText(String.format("XYZ: %.2f / %.2f / %.2f", info.playerX, info.playerY, info.playerZ), x, y, textScale);
        y += lineHeight;
        
        // Chunk position
        TextRenderer.drawText(String.format("Chunk: %d, %d", info.chunkX, info.chunkZ), x, y, textScale);
        y += lineHeight;
        
        // Block position
        TextRenderer.drawText(String.format("Block: %d, %d, %d", info.blockX, info.blockY, info.blockZ), x, y, textScale);
        y += lineHeight;
        
        // Facing direction
        TextRenderer.drawText(String.format("Facing: %s (%.1f, %.1f)", info.facing, info.yaw, info.pitch), x, y, textScale);
        y += lineHeight;
        
        // Loaded chunks
        TextRenderer.drawText(String.format("Chunks loaded: %d", info.chunksLoaded), x, y, textScale);
        y += lineHeight;
        
        // Render distance
        TextRenderer.drawText(String.format("Render distance: %d chunks", info.renderDistance), x, y, textScale);
        
        glDisable(GL_BLEND);
    }
    
    /**
     * Container for debug information to display.
     */
    public static class DebugInfo {
        public double fps;
        public double playerX, playerY, playerZ;
        public int chunkX, chunkZ;
        public int blockX, blockY, blockZ;
        public String facing;
        public float yaw, pitch;
        public int chunksLoaded;
        public int renderDistance;
        
        public DebugInfo(double fps, double playerX, double playerY, double playerZ,
                         int chunkX, int chunkZ, int blockX, int blockY, int blockZ,
                         String facing, float yaw, float pitch, int chunksLoaded, int renderDistance) {
            this.fps = fps;
            this.playerX = playerX;
            this.playerY = playerY;
            this.playerZ = playerZ;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.facing = facing;
            this.yaw = yaw;
            this.pitch = pitch;
            this.chunksLoaded = chunksLoaded;
            this.renderDistance = renderDistance;
        }
    }
}
