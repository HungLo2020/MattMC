package MattMC.gfx;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/** Renders a panorama (cubemap skybox) with optional blur effect. */
public final class PanoramaRenderer {
    private final CubeMap sky;
    private float yawDeg = 0f;
    private float pitchDeg = 5f;
    private final float yawSpeedDegPerSec = 2.0f;
    private double lastFrameTimeSec = System.nanoTime() * 1e-9;
    
    // Blur shader and framebuffers
    private Shader blurShader;
    private Framebuffer fbo1, fbo2;
    private int lastWidth = -1, lastHeight = -1;

    public PanoramaRenderer(CubeMap sky) {
        this.sky = sky;
        initBlurShader();
    }
    
    private void initBlurShader() {
        // Simple passthrough vertex shader
        String vertexShader = """
            #version 120
            varying vec2 vTexCoord;
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                vTexCoord = gl_MultiTexCoord0.xy;
            }
            """;
        
        // Gaussian blur fragment shader
        String fragmentShader = """
            #version 120
            uniform sampler2D uTexture;
            uniform vec2 uDirection;
            uniform vec2 uResolution;
            varying vec2 vTexCoord;
            
            void main() {
                vec2 texelSize = 1.0 / uResolution;
                vec4 color = vec4(0.0);
                
                // 9-tap Gaussian blur
                float weights[5];
                weights[0] = 0.227027;
                weights[1] = 0.1945946;
                weights[2] = 0.1216216;
                weights[3] = 0.054054;
                weights[4] = 0.016216;
                
                color += texture2D(uTexture, vTexCoord) * weights[0];
                
                for(int i = 1; i < 5; i++) {
                    vec2 offset = uDirection * texelSize * float(i);
                    color += texture2D(uTexture, vTexCoord + offset) * weights[i];
                    color += texture2D(uTexture, vTexCoord - offset) * weights[i];
                }
                
                gl_FragColor = color;
            }
            """;
        
        blurShader = new Shader(vertexShader, fragmentShader);
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
     * @param blurred if true, applies a Gaussian blur shader effect
     */
    public void render(int width, int height, boolean blurred) {
        // Recreate framebuffers if size changed
        if (blurred && (lastWidth != width || lastHeight != height)) {
            if (fbo1 != null) fbo1.close();
            if (fbo2 != null) fbo2.close();
            fbo1 = new Framebuffer(width, height);
            fbo2 = new Framebuffer(width, height);
            lastWidth = width;
            lastHeight = height;
        }
        
        if (blurred) {
            // Render panorama to framebuffer
            fbo1.bind();
            renderPanorama(width, height);
            fbo1.unbind();
            
            // Apply two-pass Gaussian blur
            applyGaussianBlur(width, height);
            
            // Render the blurred result to screen
            glViewport(0, 0, width, height);
            renderQuadWithTexture(fbo2.getTextureId(), width, height);
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
    
    private void applyGaussianBlur(int width, int height) {
        blurShader.use();
        blurShader.setUniform2f("uResolution", width, height);
        blurShader.setUniform1i("uTexture", 0);
        
        // First pass: horizontal blur
        fbo2.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        blurShader.setUniform2f("uDirection", 1.0f, 0.0f);
        fbo1.bindTexture();
        renderQuadWithTexture(fbo1.getTextureId(), width, height);
        fbo2.unbind();
        
        // Second pass: vertical blur
        fbo1.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        blurShader.setUniform2f("uDirection", 0.0f, 1.0f);
        fbo2.bindTexture();
        renderQuadWithTexture(fbo2.getTextureId(), width, height);
        fbo1.unbind();
        
        // Third pass: horizontal blur again for stronger effect
        fbo2.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        blurShader.setUniform2f("uDirection", 1.0f, 0.0f);
        fbo1.bindTexture();
        renderQuadWithTexture(fbo1.getTextureId(), width, height);
        fbo2.unbind();
        
        Shader.unbind();
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
        if (blurShader != null) blurShader.close();
        if (fbo1 != null) fbo1.close();
        if (fbo2 != null) fbo2.close();
    }
}
