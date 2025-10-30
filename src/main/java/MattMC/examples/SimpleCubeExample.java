package MattMC.examples;

import static org.lwjgl.opengl.GL11.*;

/**
 * Simple example showing how to draw a cube in OpenGL.
 * This is a reference implementation that can be used as a template.
 * 
 * To use this in a Screen implementation:
 * 1. Set up perspective projection
 * 2. Position your camera
 * 3. Call drawCube()
 */
public final class SimpleCubeExample {

    /**
     * Draws a simple colored cube centered at the origin.
     * Note: Caller should enable GL_DEPTH_TEST before calling and disable after.
     * 
     * @param size The size of the cube (edge length)
     */
    public static void drawCube(float size) {
        float h = size / 2f;  // Half-size for vertices relative to center
        
        // Front face (+Z) - Yellow
        glColor3f(1.0f, 1.0f, 0.0f);
        glBegin(GL_QUADS);
        glVertex3f(-h, -h, +h);  // Bottom-left
        glVertex3f(+h, -h, +h);  // Bottom-right
        glVertex3f(+h, +h, +h);  // Top-right
        glVertex3f(-h, +h, +h);  // Top-left
        glEnd();
        
        // Back face (-Z) - Cyan
        glColor3f(0.0f, 1.0f, 1.0f);
        glBegin(GL_QUADS);
        glVertex3f(+h, -h, -h);
        glVertex3f(-h, -h, -h);
        glVertex3f(-h, +h, -h);
        glVertex3f(+h, +h, -h);
        glEnd();
        
        // Left face (-X) - Magenta
        glColor3f(1.0f, 0.0f, 1.0f);
        glBegin(GL_QUADS);
        glVertex3f(-h, -h, -h);
        glVertex3f(-h, -h, +h);
        glVertex3f(-h, +h, +h);
        glVertex3f(-h, +h, -h);
        glEnd();
        
        // Right face (+X) - Green
        glColor3f(0.0f, 1.0f, 0.0f);
        glBegin(GL_QUADS);
        glVertex3f(+h, -h, +h);
        glVertex3f(+h, -h, -h);
        glVertex3f(+h, +h, -h);
        glVertex3f(+h, +h, +h);
        glEnd();
        
        // Top face (+Y) - Blue
        glColor3f(0.0f, 0.0f, 1.0f);
        glBegin(GL_QUADS);
        glVertex3f(-h, +h, +h);
        glVertex3f(+h, +h, +h);
        glVertex3f(+h, +h, -h);
        glVertex3f(-h, +h, -h);
        glEnd();
        
        // Bottom face (-Y) - Red
        glColor3f(1.0f, 0.0f, 0.0f);
        glBegin(GL_QUADS);
        glVertex3f(-h, -h, -h);
        glVertex3f(+h, -h, -h);
        glVertex3f(+h, -h, +h);
        glVertex3f(-h, -h, +h);
        glEnd();
    }
    
    /**
     * Draws a cube with edge lines for better visibility.
     * Manages depth testing internally.
     * 
     * @param size The size of the cube (edge length)
     */
    public static void drawCubeWithEdges(float size) {
        glEnable(GL_DEPTH_TEST);
        
        // Draw the colored faces
        drawCube(size);
        
        // Draw black edge lines
        float h = size / 2f;
        glColor3f(0.0f, 0.0f, 0.0f);
        glBegin(GL_LINES);
        
        // Bottom square
        glVertex3f(-h, -h, -h); glVertex3f(+h, -h, -h);
        glVertex3f(+h, -h, -h); glVertex3f(+h, -h, +h);
        glVertex3f(+h, -h, +h); glVertex3f(-h, -h, +h);
        glVertex3f(-h, -h, +h); glVertex3f(-h, -h, -h);
        
        // Top square
        glVertex3f(-h, +h, -h); glVertex3f(+h, +h, -h);
        glVertex3f(+h, +h, -h); glVertex3f(+h, +h, +h);
        glVertex3f(+h, +h, +h); glVertex3f(-h, +h, +h);
        glVertex3f(-h, +h, +h); glVertex3f(-h, +h, -h);
        
        // Vertical edges
        glVertex3f(-h, -h, -h); glVertex3f(-h, +h, -h);
        glVertex3f(+h, -h, -h); glVertex3f(+h, +h, -h);
        glVertex3f(+h, -h, +h); glVertex3f(+h, +h, +h);
        glVertex3f(-h, -h, +h); glVertex3f(-h, +h, +h);
        
        glEnd();
        
        glDisable(GL_DEPTH_TEST);
    }
    
    /**
     * Sets up a basic perspective projection.
     * Call this before drawing the cube.
     * 
     * @param width Viewport width
     * @param height Viewport height
     * @param fov Field of view in degrees
     */
    public static void setupPerspective(int width, int height, float fov) {
        float aspect = Math.max(1f, (float) width / Math.max(1, height));
        float zNear = 0.1f;
        float zFar = 100.0f;
        
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        
        float top = (float) (Math.tan(Math.toRadians(fov * 0.5)) * zNear);
        float bottom = -top;
        float right = top * aspect;
        float left = -right;
        
        glFrustum(left, right, bottom, top, zNear, zFar);
        
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }
    
    /**
     * Example usage in a render method:
     * 
     * <pre>
     * {@code
     * public void render() {
     *     // Clear screen
     *     glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
     *     
     *     // Setup perspective
     *     SimpleCubeExample.setupPerspective(width, height, 60f);
     *     
     *     // Position camera
     *     glTranslatef(0f, 0f, -5f);  // Move back to see cube
     *     glRotatef(angle, 0f, 1f, 0f);  // Rotate for animation
     *     
     *     // Draw cube (manages depth testing internally)
     *     SimpleCubeExample.drawCubeWithEdges(2.0f);
     *     
     *     // Or manually manage depth testing:
     *     // glEnable(GL_DEPTH_TEST);
     *     // SimpleCubeExample.drawCube(2.0f);
     *     // glDisable(GL_DEPTH_TEST);
     * }
     * }
     * </pre>
     */
    private SimpleCubeExample() {
        // Utility class - prevent instantiation
    }
}
