package MattMC.gfx;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/** Renders a panorama (cubemap skybox) with optional blur effect. */
public final class PanoramaRenderer {
    private static final float BLUR_OVERLAY_ALPHA = 0.25f; // Semi-transparent overlay for blur depth
    
    private final CubeMap sky;
    private float yawDeg = 0f;
    private float pitchDeg = 5f;
    private final float yawSpeedDegPerSec = 2.0f;
    private double lastFrameTimeSec = System.nanoTime() * 1e-9;

    public PanoramaRenderer(CubeMap sky) {
        this.sky = sky;
    }

    /** Update the panorama rotation based on elapsed time since last update. */
    public void update() {
        double now = System.nanoTime() * 1e-9;
        double frameDt = now - lastFrameTimeSec;
        lastFrameTimeSec = now;
        if (frameDt < 0) frameDt = 0;
        if (frameDt > 0.25) frameDt = 0.25; // clamp huge pauses
        
        yawDeg += yawSpeedDegPerSec * (float)frameDt;
        if (yawDeg >= 360f) yawDeg -= 360f;
        if (yawDeg < 0f) yawDeg += 360f;
    }

    /**
     * Render the panorama skybox.
     * @param width viewport width
     * @param height viewport height
     * @param blurred if true, applies a blur effect (dimming + slight transparency overlay)
     */
    public void render(int width, int height, boolean blurred) {
        float aspect = Math.max(1f, (float)width / Math.max(1, height));

        // Clear and set perspective
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        // Simple perspective via glFrustum
        float fov = 70f, zn = 0.1f, zf = 10f;
        float top = (float)(Math.tan(Math.toRadians(fov * 0.5)) * zn);
        float bottom = -top;
        float right = top * aspect;
        float left = -right;
        glFrustum(left, right, bottom, top, zn, zf);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Camera at origin, rotate the skybox
        glRotatef(pitchDeg, 1f, 0f, 0f);
        glRotatef(yawDeg, 0f, 1f, 0f);

        // Draw a cube of size 2 centered at origin with cubemap lookup
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_CUBE_MAP);
        glBindTexture(GL_TEXTURE_CUBE_MAP, sky.id);
        
        // Apply texture filtering for blur effect
        if (blurred) {
            // Use linear filtering with reduced quality for blur appearance
            glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
        
        glColor4f(1f, 1f, 1f, 1f);

        glBegin(GL_QUADS);
        // +X (right)
        glTexCoord3f(+1, -1, -1); glVertex3f(+1, -1, -1);
        glTexCoord3f(+1, -1, +1); glVertex3f(+1, -1, +1);
        glTexCoord3f(+1, +1, +1); glVertex3f(+1, +1, +1);
        glTexCoord3f(+1, +1, -1); glVertex3f(+1, +1, -1);

        // -X (left)
        glTexCoord3f(-1, -1, +1); glVertex3f(-1, -1, +1);
        glTexCoord3f(-1, -1, -1); glVertex3f(-1, -1, -1);
        glTexCoord3f(-1, +1, -1); glVertex3f(-1, +1, -1);
        glTexCoord3f(-1, +1, +1); glVertex3f(-1, +1, +1);

        // +Y (top)
        glTexCoord3f(-1, +1, -1); glVertex3f(-1, +1, -1);
        glTexCoord3f(+1, +1, -1); glVertex3f(+1, +1, -1);
        glTexCoord3f(+1, +1, +1); glVertex3f(+1, +1, +1);
        glTexCoord3f(-1, +1, +1); glVertex3f(-1, +1, +1);

        // -Y (bottom)
        glTexCoord3f(-1, -1, +1); glVertex3f(-1, -1, +1);
        glTexCoord3f(+1, -1, +1); glVertex3f(+1, -1, +1);
        glTexCoord3f(+1, -1, -1); glVertex3f(+1, -1, -1);
        glTexCoord3f(-1, -1, -1); glVertex3f(-1, -1, -1);

        // +Z (front)
        glTexCoord3f(-1, -1, +1); glVertex3f(-1, -1, +1);
        glTexCoord3f(-1, +1, +1); glVertex3f(-1, +1, +1);
        glTexCoord3f(+1, +1, +1); glVertex3f(+1, +1, +1);
        glTexCoord3f(+1, -1, +1); glVertex3f(+1, -1, +1);

        // -Z (back)
        glTexCoord3f(+1, -1, -1); glVertex3f(+1, -1, -1);
        glTexCoord3f(+1, +1, -1); glVertex3f(+1, +1, -1);
        glTexCoord3f(-1, +1, -1); glVertex3f(-1, +1, -1);
        glTexCoord3f(-1, -1, -1); glVertex3f(-1, -1, -1);
        glEnd();

        glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
        glDisable(GL_TEXTURE_CUBE_MAP);
        
        // Apply blur effect if requested
        if (blurred) {
            applySimpleBlur(width, height);
        }
    }

    /** Applies a blur effect by rendering the panorama multiple times with slight offsets. */
    private void applySimpleBlur(int width, int height) {
        // Re-render the panorama multiple times with slight transparency and offsets
        // to create a blur-like effect
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Render the cubemap again multiple times with slight rotations and low alpha
        // to create blur streaks
        glEnable(GL_TEXTURE_CUBE_MAP);
        glBindTexture(GL_TEXTURE_CUBE_MAP, sky.id);
        
        float blurAlpha = 0.08f; // Low alpha for each blur pass
        int numPasses = 8; // More passes for smoother blur
        
        for (int i = 0; i < numPasses; i++) {
            glPushMatrix();
            
            // Slight rotation offset for each pass to create blur
            float offsetDeg = (i - numPasses / 2f) * 0.5f;
            glRotatef(offsetDeg, 0f, 1f, 0f);
            
            glColor4f(1f, 1f, 1f, blurAlpha);
            
            // Redraw the cubemap
            glBegin(GL_QUADS);
            // +X (right)
            glTexCoord3f(+1, -1, -1); glVertex3f(+1, -1, -1);
            glTexCoord3f(+1, -1, +1); glVertex3f(+1, -1, +1);
            glTexCoord3f(+1, +1, +1); glVertex3f(+1, +1, +1);
            glTexCoord3f(+1, +1, -1); glVertex3f(+1, +1, -1);
            
            // -X (left)
            glTexCoord3f(-1, -1, +1); glVertex3f(-1, -1, +1);
            glTexCoord3f(-1, -1, -1); glVertex3f(-1, -1, -1);
            glTexCoord3f(-1, +1, -1); glVertex3f(-1, +1, -1);
            glTexCoord3f(-1, +1, +1); glVertex3f(-1, +1, +1);
            
            // +Y (top)
            glTexCoord3f(-1, +1, -1); glVertex3f(-1, +1, -1);
            glTexCoord3f(+1, +1, -1); glVertex3f(+1, +1, -1);
            glTexCoord3f(+1, +1, +1); glVertex3f(+1, +1, +1);
            glTexCoord3f(-1, +1, +1); glVertex3f(-1, +1, +1);
            
            // -Y (bottom)
            glTexCoord3f(-1, -1, +1); glVertex3f(-1, -1, +1);
            glTexCoord3f(+1, -1, +1); glVertex3f(+1, -1, +1);
            glTexCoord3f(+1, -1, -1); glVertex3f(+1, -1, -1);
            glTexCoord3f(-1, -1, -1); glVertex3f(-1, -1, -1);
            
            // +Z (front)
            glTexCoord3f(-1, -1, +1); glVertex3f(-1, -1, +1);
            glTexCoord3f(-1, +1, +1); glVertex3f(-1, +1, +1);
            glTexCoord3f(+1, +1, +1); glVertex3f(+1, +1, +1);
            glTexCoord3f(+1, -1, +1); glVertex3f(+1, -1, +1);
            
            // -Z (back)
            glTexCoord3f(+1, -1, -1); glVertex3f(+1, -1, -1);
            glTexCoord3f(+1, +1, -1); glVertex3f(+1, +1, -1);
            glTexCoord3f(-1, +1, -1); glVertex3f(-1, +1, -1);
            glTexCoord3f(-1, -1, -1); glVertex3f(-1, -1, -1);
            glEnd();
            
            glPopMatrix();
        }
        
        glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
        glDisable(GL_TEXTURE_CUBE_MAP);
        
        // Add a subtle semi-transparent overlay to enhance the blur perception
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        glColor4f(0.0f, 0.0f, 0.0f, BLUR_OVERLAY_ALPHA);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(width, 0);
        glVertex2f(width, height);
        glVertex2f(0, height);
        glEnd();
        
        glDisable(GL_BLEND);
        glColor4f(1f, 1f, 1f, 1f); // Reset color
    }

    public void close() {
        sky.close();
    }
}
