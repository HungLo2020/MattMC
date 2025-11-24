package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.CommandBuffer;

import mattmc.client.renderer.UIRenderLogic;

import mattmc.client.renderer.backend.DrawCommand;

import mattmc.client.renderer.backend.RenderBackend;

import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.Region;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders debug information in the top-left corner.
 * Shows version, FPS, player position, chunk position, region position, and culling stats.
 */
public class DebugInfoRenderer {
    
    // Backend support (Stage 4)
    private RenderBackend backend = null;
    
    /**
     * Set the render backend to use for rendering (Stage 4).
     * 
     * @param backend the backend to use, or null to use legacy rendering
     */
    public void setBackend(RenderBackend backend) {
        this.backend = backend;
    }
    
    /**
     * Draw debug information in the top-left corner.
     */
    public void render(int screenWidth, int screenHeight, float playerX, float playerY, float playerZ, 
                       float yaw, float pitch, float roll, double fps, 
                       int loadedChunks, int pendingChunks, int activeWorkers, int renderedChunks, int culledChunks) {
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        // Use backend if available (Stage 4)
        if (backend != null) {
            backend.beginFrame();
            
            // Build and submit commands via backend
            UIRenderLogic logic = new UIRenderLogic();
            CommandBuffer buffer = new CommandBuffer();
            
            // Clear text registry for this frame
            UIRenderLogic.clearTextRegistry();
            
            // Build debug info commands
            logic.buildDebugInfoCommands(screenWidth, screenHeight, 
                playerX, playerY, playerZ, yaw, pitch, roll, fps,
                loadedChunks, pendingChunks, activeWorkers, renderedChunks, culledChunks,
                buffer);
            
            // Submit to backend
            for (DrawCommand cmd : buffer.getCommands()) {
                backend.submit(cmd);
            }
            
            backend.endFrame();
        } else {
            // Legacy rendering path
            renderLegacy(screenWidth, screenHeight, playerX, playerY, playerZ,
                yaw, pitch, roll, fps, loadedChunks, pendingChunks, activeWorkers,
                renderedChunks, culledChunks);
        }
        
        UIRenderHelper.restore2DProjection();
    }
    
    /**
     * Legacy rendering path (before backend).
     */
    private void renderLegacy(int screenWidth, int screenHeight, float playerX, float playerY, float playerZ,
                             float yaw, float pitch, float roll, double fps,
                             int loadedChunks, int pendingChunks, int activeWorkers,
                             int renderedChunks, int culledChunks) {
        // Calculate chunk position from player position
        int chunkX = Math.floorDiv((int)playerX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv((int)playerZ, LevelChunk.DEPTH);
        
        // Calculate region position from chunk position
        int regionX = Math.floorDiv(chunkX, Region.REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, Region.REGION_SIZE);
        
        // Draw debug text in top-left corner
        float x = 10f;
        float y = 10f;
        float lineHeight = 20f;
        float scale = 1.5f;
        
        // Header line with version
        String headerText = "MattMC: " + mattmc.client.main.Main.VERSION + ": Debug Screen";
        UIRenderHelper.drawText(headerText, x, y, scale, 0xFFFFFF);
        
        // FPS display
        String fpsText = String.format("FPS: %.0f", fps);
        UIRenderHelper.drawText(fpsText, x, y + lineHeight, scale, 0xFFFFFF);
        
        // Normalize yaw to 0-360 range for display
        float normalizedYaw = ((yaw % 360) + 360) % 360;
        
        // Calculate cardinal direction from normalized yaw
        String direction = getCardinalDirection(normalizedYaw);
        
        // Format position values to 2 decimal places for readability
        String posText = String.format("Position: %.2f, %.2f, %.2f (Facing: %s)", 
                                        playerX, playerY, playerZ, direction);
        String rotationText = String.format("Yaw: %.2f, Pitch: %.2f, Roll: %.2f", normalizedYaw, pitch, roll);
        String chunkText = String.format("LevelChunk: %d, %d", chunkX, chunkZ);
        String regionText = String.format("Region: %d, %d", regionX, regionZ);
        
        UIRenderHelper.drawText(posText, x, y + lineHeight * 2, scale, 0xFFFFFF);
        UIRenderHelper.drawText(rotationText, x, y + lineHeight * 3, scale, 0xFFFFFF);
        UIRenderHelper.drawText(chunkText, x, y + lineHeight * 4, scale, 0xFFFFFF);
        UIRenderHelper.drawText(regionText, x, y + lineHeight * 5, scale, 0xFFFFFF);
        
        // Chunk loading stats
        String loadedText = String.format("Loaded Chunks: %d", loadedChunks);
        String pendingText = String.format("Pending: %d | Workers: %d", pendingChunks, activeWorkers);
        
        UIRenderHelper.drawText(loadedText, x, y + lineHeight * 6, scale, 0xFFFFFF);
        UIRenderHelper.drawText(pendingText, x, y + lineHeight * 7, scale, 0xFFFFFF);
        
        // Frustum culling stats
        String renderText = String.format("Rendered: %d | Culled: %d", renderedChunks, culledChunks);
        UIRenderHelper.drawText(renderText, x, y + lineHeight * 8, scale, 0xFFFFFF);
    }
    
    /**
     * Get cardinal direction from yaw angle.
     * Minecraft's yaw: 0 = South, 90 = West, 180 = North, 270 = East
     * @param normalizedYaw The yaw angle in degrees (should be normalized to 0-360 range)
     * @return Cardinal direction (N, S, E, W, NE, NW, SE, SW)
     */
    private String getCardinalDirection(float normalizedYaw) {
        // Determine direction based on yaw ranges
        // In Minecraft: South = 0, West = 90, North = 180, East = 270
        if (normalizedYaw >= 337.5 || normalizedYaw < 22.5) {
            return "South";
        } else if (normalizedYaw >= 22.5 && normalizedYaw < 67.5) {
            return "South-West";
        } else if (normalizedYaw >= 67.5 && normalizedYaw < 112.5) {
            return "West";
        } else if (normalizedYaw >= 112.5 && normalizedYaw < 157.5) {
            return "North-West";
        } else if (normalizedYaw >= 157.5 && normalizedYaw < 202.5) {
            return "North";
        } else if (normalizedYaw >= 202.5 && normalizedYaw < 247.5) {
            return "North-East";
        } else if (normalizedYaw >= 247.5 && normalizedYaw < 292.5) {
            return "East";
        } else {
            return "South-East";
        }
    }
}
