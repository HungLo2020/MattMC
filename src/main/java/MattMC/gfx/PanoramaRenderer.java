package MattMC.gfx;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/** Renders a panorama (cubemap skybox) with optional blur effect. */
public final class PanoramaRenderer {
    private static final float BLUR_DIM_FACTOR = 0.5f;     // Dimming intensity for blur effect
    private static final float BLUR_OVERLAY_ALPHA = 0.3f;  // Overlay opacity for blur effect
    
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
        
        // Apply blur effect by dimming the panorama
        if (blurred) {
            glColor4f(BLUR_DIM_FACTOR, BLUR_DIM_FACTOR, BLUR_DIM_FACTOR, 1f);
        } else {
            glColor4f(1f, 1f, 1f, 1f);
        }

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
        
        // Apply additional blur overlay for blurred panoramas
        if (blurred) {
            applyBlurOverlay(width, height);
        }
    }

    /** Applies a semi-transparent dark overlay to simulate blur. */
    private void applyBlurOverlay(int width, int height) {
        // Switch to orthographic for overlay
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Enable blending for transparency
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw a semi-transparent dark overlay
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
