package mattmc.client.renderer;

import mattmc.client.gui.components.TextRenderer;
import mattmc.world.entity.player.LocalPlayer;
import mattmc.world.level.LevelAccessor;
import mattmc.world.level.block.Block;
import mattmc.world.level.chunk.LevelChunk;

import static org.lwjgl.opengl.GL11.*;

/**
 * HUD element that displays the name of the block the player is looking at.
 * Renders in the top-left corner of the screen with a blurred background.
 * Uses raycasting up to 10 blocks to detect the targeted block.
 */
public class BlockNameHUD extends AbstractBlurBox {
    private static final float HUD_X = 10f;
    private static final float HUD_Y = 10f;
    private static final float PADDING = 10f;
    private static final float TEXT_SCALE = 1.5f;
    private static final float MAX_RAYCAST_DISTANCE = 10.0f;
    
    /**
     * Render the block name HUD if the player is looking at a block.
     * 
     * @param player The local player
     * @param world The world/level accessor
     * @param screenWidth Screen width for rendering
     * @param screenHeight Screen height for rendering
     */
    public void render(LocalPlayer player, LevelAccessor world, int screenWidth, int screenHeight) {
        BlockHitResult hit = raycastBlock(player, world);
        if (hit == null) {
            return; // Not looking at any block
        }
        
        Block block = world.getBlock(hit.x, hit.y, hit.z);
        if (block == null || block.isAir()) {
            return; // No valid block
        }
        
        // Get block name from identifier (e.g., "mattmc:dirt" -> "Dirt")
        String blockName = getBlockDisplayName(block);
        
        // Calculate HUD size based on text
        float textWidth = TextRenderer.getTextWidth(blockName, TEXT_SCALE);
        float textHeight = TextRenderer.getTextHeight(blockName, TEXT_SCALE);
        float hudWidth = textWidth + PADDING * 2;
        float hudHeight = textHeight + PADDING * 2;
        
        // Save current viewport to restore after blur
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        
        // Save comprehensive GL state before rendering (but NOT matrices - those are already set up correctly)
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        
        try {
            // Apply blur to the background region
            applyRegionalBlur(HUD_X, HUD_Y, hudWidth, hudHeight, screenWidth, screenHeight);
            
            // Ensure viewport is correctly restored after blur
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            
            // 2D projection is already set up by DevplayScreen - no need to set it again
            // Just render the text
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(1f, 1f, 1f, 1f);
            
            TextRenderer.drawText(blockName, HUD_X + PADDING, HUD_Y + PADDING, TEXT_SCALE);
        } finally {
            // Restore all GL state
            glPopAttrib();
            
            // Restore viewport one more time to be absolutely sure
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
    
    /**
     * Get a display-friendly name for a block.
     * Converts identifier like "mattmc:dirt" to "Dirt".
     */
    private String getBlockDisplayName(Block block) {
        String identifier = block.getIdentifier();
        if (identifier == null) {
            return "Unknown";
        }
        
        // Extract block name from identifier (e.g., "mattmc:dirt" -> "dirt")
        String blockName = identifier.contains(":") 
            ? identifier.substring(identifier.indexOf(':') + 1) 
            : identifier;
        
        // Convert to title case (dirt -> Dirt, grass_block -> Grass Block)
        return toTitleCase(blockName);
    }
    
    /**
     * Convert snake_case to Title Case.
     * Examples: "dirt" -> "Dirt", "grass_block" -> "Grass Block"
     */
    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String[] parts = input.split("_");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            String part = parts[i];
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Perform ray casting to find the block the player is looking at.
     * Similar to BlockInteraction.raycastBlock() but with extended range (10 blocks).
     * Returns null if no block is found within maxDistance.
     */
    private BlockHitResult raycastBlock(LocalPlayer player, LevelAccessor world) {
        float[] dir = player.getForwardVector();
        float dirX = dir[0];
        float dirY = dir[1];
        float dirZ = dir[2];
        
        // Start ray at player's eyes (camera position), not feet
        float rayX = player.getX();
        float rayY = player.getEyeY();
        float rayZ = player.getZ();
        
        // DDA algorithm for voxel traversal
        float stepSize = 0.1f;
        int steps = (int) (MAX_RAYCAST_DISTANCE / stepSize);
        
        for (int i = 0; i < steps; i++) {
            rayX += dirX * stepSize;
            rayY += dirY * stepSize;
            rayZ += dirZ * stepSize;
            
            int blockX = (int) Math.floor(rayX);
            int blockY = (int) Math.floor(rayY);
            int blockZ = (int) Math.floor(rayZ);
            
            // Convert world Y to chunk Y
            int chunkY = LevelChunk.worldYToChunkY(blockY);
            
            // Check if Y is valid
            if (chunkY >= 0 && chunkY < LevelChunk.HEIGHT) {
                Block block = world.getBlock(blockX, chunkY, blockZ);
                if (!block.isAir()) {
                    // Found a solid block
                    return new BlockHitResult(blockX, chunkY, blockZ);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Simple data class to hold ray cast hit result.
     */
    private static class BlockHitResult {
        public final int x, y, z;
        
        public BlockHitResult(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
