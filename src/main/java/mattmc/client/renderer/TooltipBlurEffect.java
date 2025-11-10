package mattmc.client.renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Handles blur effect specifically for tooltips.
 * Captures a rectangular region of the screen, blurs it, and renders it back.
 */
public class TooltipBlurEffect {
    private final Shader blurShader;
    private Framebuffer captureBuffer, blurBuffer1, blurBuffer2;
    private int lastWidth = -1, lastHeight = -1;
    
    public TooltipBlurEffect() {
        this.blurShader = createBlurShader();
    }
    
    /**
     * Create the Gaussian blur shader for tooltips.
     */
    private Shader createBlurShader() {
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
                
                // 7-tap Gaussian blur for stronger effect
                float weights[4];
                weights[0] = 0.324;
                weights[1] = 0.232;
                weights[2] = 0.0855;
                weights[3] = 0.0205;
                
                color += texture2D(uTexture, vTexCoord) * weights[0];
                
                for(int i = 1; i < 4; i++) {
                    vec2 offset = uDirection * texelSize * float(i);
                    color += texture2D(uTexture, vTexCoord + offset) * weights[i];
                    color += texture2D(uTexture, vTexCoord - offset) * weights[i];
                }
                
                gl_FragColor = color;
            }
            """;
        
        return new Shader(vertexShader, fragmentShader);
    }
    
    /**
     * Ensure framebuffers match the given dimensions.
     */
    private void ensureFramebuffers(int width, int height) {
        if (lastWidth != width || lastHeight != height) {
            if (captureBuffer != null) captureBuffer.close();
            if (blurBuffer1 != null) blurBuffer1.close();
            if (blurBuffer2 != null) blurBuffer2.close();
            captureBuffer = new Framebuffer(width, height);
            blurBuffer1 = new Framebuffer(width, height);
            blurBuffer2 = new Framebuffer(width, height);
            lastWidth = width;
            lastHeight = height;
        }
    }
    
    /**
     * Apply blur to a rectangular region of the screen.
     * This captures the current screen content in the specified region, blurs it,
     * and renders it back to the same region.
     * 
     * @param x X position of the tooltip rectangle
     * @param y Y position of the tooltip rectangle
     * @param width Width of the tooltip rectangle
     * @param height Height of the tooltip rectangle
     * @param screenWidth Full screen width for viewport restoration
     * @param screenHeight Full screen height for viewport restoration
     */
    public void applyRegionalBlur(float x, float y, float width, float height, int screenWidth, int screenHeight) {
        // Ensure dimensions are positive
        int w = (int) Math.max(1, width);
        int h = (int) Math.max(1, height);
        
        ensureFramebuffers(w, h);
        
        // Step 1: Capture the screen region into a texture
        // We need to read from the default framebuffer (0) which has the rendered scene
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        
        int capturedTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, capturedTexture);
        // OpenGL's coordinate system has (0,0) at bottom-left, so we need to flip Y
        glCopyTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, (int)x, screenHeight - (int)y - h, w, h, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        
        // Step 2: Apply horizontal blur
        blurBuffer1.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        
        blurShader.use();
        blurShader.setUniform2f("uResolution", w, h);
        blurShader.setUniform1i("uTexture", 0);
        blurShader.setUniform2f("uDirection", 1.0f, 0.0f);
        
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, capturedTexture);
        renderQuad(w, h);
        
        blurBuffer1.unbind();
        glViewport(0, 0, screenWidth, screenHeight);
        
        // Step 3: Apply vertical blur
        blurBuffer2.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        
        blurShader.setUniform2f("uDirection", 0.0f, 1.0f);
        glActiveTexture(GL_TEXTURE0);
        blurBuffer1.bindTexture();
        renderQuad(w, h);
        
        blurBuffer2.unbind();
        glViewport(0, 0, screenWidth, screenHeight);
        
        Shader.unbind();
        
        // Step 4: Render the blurred result back to the screen region
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_TEXTURE_2D);
        
        blurBuffer2.bindTexture();
        
        // Set up orthographic projection for screen coordinates
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        glColor4f(1f, 1f, 1f, 1f);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(x, y);
        glTexCoord2f(1, 1); glVertex2f(x + width, y);
        glTexCoord2f(1, 0); glVertex2f(x + width, y + height);
        glTexCoord2f(0, 0); glVertex2f(x, y + height);
        glEnd();
        
        // Restore matrices
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        
        glDisable(GL_TEXTURE_2D);
        // Keep blending enabled for subsequent rendering
        // glDisable(GL_BLEND);  // Removed - let caller manage blend state
        
        // Clean up temporary texture
        glDeleteTextures(capturedTexture);
    }
    
    /**
     * Render a quad for post-processing.
     */
    private void renderQuad(int width, int height) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        glEnable(GL_TEXTURE_2D);
        glColor4f(1f, 1f, 1f, 1f);
        
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(0, 0);
        glTexCoord2f(1, 1); glVertex2f(width, 0);
        glTexCoord2f(1, 0); glVertex2f(width, height);
        glTexCoord2f(0, 0); glVertex2f(0, height);
        glEnd();
        
        glDisable(GL_TEXTURE_2D);
        
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }
    
    /**
     * Clean up resources.
     */
    public void close() {
        if (blurShader != null) blurShader.close();
        if (captureBuffer != null) captureBuffer.close();
        if (blurBuffer1 != null) blurBuffer1.close();
        if (blurBuffer2 != null) blurBuffer2.close();
    }
}
