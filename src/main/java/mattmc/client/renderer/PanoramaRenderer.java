package mattmc.client.renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/** Renders a panorama (cubemap skybox) with optional blur effect. */
public final class PanoramaRenderer {
    private final CubeMap sky;
    private float yawDeg = 0f;
    private float pitchDeg = 5f;
    private final float yawSpeedDegPerSec = 2.0f;
    private double lastRenderTimeSec = System.nanoTime() * 1e-9;
    
    // Blur effect and framebuffer for rendering to texture
    private final BlurEffect blurEffect;
    private Framebuffer renderTarget;
    private int lastWidth = -1, lastHeight = -1;

    public PanoramaRenderer(CubeMap sky) {
        this.sky = sky;
        this.blurEffect = new BlurEffect();
    }

    /**
     * Render the panorama skybox. Rotation is updated each time this is called,
     * synchronized with actual rendered frames to prevent jitter.
     * @param width viewport width
     * @param height viewport height
     * @param blurred if true, applies a Gaussian blur shader effect
     */
    public void render(int width, int height, boolean blurred) {
        // Update rotation based on time since last render
        // This ensures smooth rotation synchronized with actual displayed frames
        double now = System.nanoTime() * 1e-9;
        double renderDt = now - lastRenderTimeSec;
        lastRenderTimeSec = now;
        if (renderDt < 0) renderDt = 0;
        if (renderDt > 0.25) renderDt = 0.25; // clamp huge pauses
        
        yawDeg += yawSpeedDegPerSec * (float)renderDt;
        if (yawDeg >= 360f) yawDeg -= 360f;
        if (yawDeg < 0f) yawDeg += 360f;
        if (blurred) {
            // Recreate render target if size changed
            if (lastWidth != width || lastHeight != height) {
                if (renderTarget != null) renderTarget.close();
                renderTarget = new Framebuffer(width, height);
                lastWidth = width;
                lastHeight = height;
            }
            
            // Render panorama to framebuffer
            renderTarget.bind();
            renderPanorama(width, height);
            renderTarget.unbind();
            
            // Restore viewport after framebuffer operations
            glViewport(0, 0, width, height);
            
            // Apply Gaussian blur and get result
            Framebuffer blurResult = blurEffect.applyBlur(renderTarget.getTextureId(), width, height);
            
            // Render the blurred result to screen
            renderQuadWithTexture(blurResult.getTextureId(), width, height);
        } else {
            // Render directly to screen without blur
            renderPanorama(width, height);
        }
    }
    
    private void renderPanorama(int width, int height) {
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
    }
    
    private void renderQuadWithTexture(int textureId, int width, int height) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glColor4f(1f, 1f, 1f, 1f);
        
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(0, 0);
        glTexCoord2f(1, 1); glVertex2f(width, 0);
        glTexCoord2f(1, 0); glVertex2f(width, height);
        glTexCoord2f(0, 0); glVertex2f(0, height);
        glEnd();
        
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_TEXTURE_2D);
    }

    public void close() {
        sky.close();
        if (blurEffect != null) blurEffect.close();
        if (renderTarget != null) renderTarget.close();
    }
}
