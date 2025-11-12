package mattmc.client.renderer;

import mattmc.client.gui.components.TextRenderer;
import mattmc.world.level.block.Block;

import static org.lwjgl.opengl.GL11.*;

/**
 * Displays the name of the block the player is looking at in the top-left corner.
 * Uses AbstractBlurBox to create a blurred background behind the text.
 */
public class BlockNameDisplay extends AbstractBlurBox {
    private static final int PADDING = 10;
    private static final int MARGIN = 10;
    
    // Blue outline parameters matching tooltip style
    private static final float CORNER_RADIUS = 9f;
    private static final float BORDER_WIDTH = 6f;
    private static final float BORDER_R = 0.3f;
    private static final float BORDER_G = 0.5f;
    private static final float BORDER_B = 1.0f;
    private static final float BORDER_ALPHA = 1.0f;
    
    /**
     * Render the block name display in the top-left corner.
     * @param screenWidth The width of the screen
     * @param screenHeight The height of the screen
     * @param block The block to display the name of (can be null)
     */
    public void render(int screenWidth, int screenHeight, Block block) {
        if (block == null || block.isAir()) {
            return; // Don't display anything if no block is targeted
        }
        
        String blockName = getDisplayName(block);
        if (blockName == null || blockName.isEmpty()) {
            return;
        }
        
        // Text scale
        float textScale = 1.5f;
        
        // Measure text dimensions
        int textWidth = (int) TextRenderer.getTextWidth(blockName, textScale);
        int textHeight = (int) TextRenderer.getTextHeight(blockName, textScale);
        
        // Calculate box dimensions
        int boxWidth = textWidth + (PADDING * 2);
        int boxHeight = textHeight + (PADDING * 2);
        
        // Position in top-left corner with margin
        int x = MARGIN;
        int y = MARGIN;
        
        // Apply blur effect to the background
        applyRegionalBlur(x, y, boxWidth, boxHeight, screenWidth, screenHeight);
        
        // Switch to 2D orthographic projection for drawing
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Enable blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw blue rounded border
        drawRoundedRectBorder(x, y, boxWidth, boxHeight, CORNER_RADIUS, 
                             BORDER_WIDTH, BORDER_R, BORDER_G, BORDER_B, BORDER_ALPHA);
        
        // Draw the block name text in white
        glColor4f(1f, 1f, 1f, 1f);
        TextRenderer.drawText(blockName, x + PADDING, y + PADDING, textScale);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Convert the block identifier to a display name.
     * Converts "mattmc:stone" to "Stone", "mattmc:grass_block" to "Grass Block", etc.
     */
    private String getDisplayName(Block block) {
        String identifier = block.getIdentifier();
        if (identifier == null) {
            return null;
        }
        
        // Extract block name from identifier (e.g., "mattmc:stone" -> "stone")
        String blockName = identifier.contains(":") 
            ? identifier.substring(identifier.indexOf(':') + 1) 
            : identifier;
        
        // Convert to title case and replace underscores with spaces
        // "grass_block" -> "Grass Block"
        String[] words = blockName.split("_");
        StringBuilder displayName = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                // Capitalize first letter, lowercase the rest
                displayName.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    displayName.append(word.substring(1).toLowerCase());
                }
                
                // Add space between words (but not after the last word)
                if (i < words.length - 1) {
                    displayName.append(" ");
                }
            }
        }
        
        return displayName.toString();
    }
}
