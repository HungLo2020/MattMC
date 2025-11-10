package mattmc.client.renderer.ui;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders the crosshair in the center of the screen.
 */
public class CrosshairRenderer {
    private static final int CROSSHAIR_SIZE = 15;
    private static final int CROSSHAIR_THICKNESS = 3;
    
    /**
     * Render the crosshair centered on the screen.
     */
    public void render(int screenWidth, int screenHeight) {
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int half = CROSSHAIR_SIZE / 2;
        
        glDisable(GL_TEXTURE_2D);
        glColor3f(1.0f, 1.0f, 1.0f); // White crosshair
        
        glBegin(GL_QUADS);
        // Horizontal line
        glVertex2i(centerX - half, centerY - CROSSHAIR_THICKNESS / 2);
        glVertex2i(centerX + half, centerY - CROSSHAIR_THICKNESS / 2);
        glVertex2i(centerX + half, centerY + CROSSHAIR_THICKNESS / 2);
        glVertex2i(centerX - half, centerY + CROSSHAIR_THICKNESS / 2);
        
        // Vertical line
        glVertex2i(centerX - CROSSHAIR_THICKNESS / 2, centerY - half);
        glVertex2i(centerX + CROSSHAIR_THICKNESS / 2, centerY - half);
        glVertex2i(centerX + CROSSHAIR_THICKNESS / 2, centerY + half);
        glVertex2i(centerX - CROSSHAIR_THICKNESS / 2, centerY + half);
        glEnd();
    }
}
