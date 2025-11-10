package mattmc.client.renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

/**
 * Handles blur effect specifically for tooltips.
 * Similar to BlurEffect but optimized for small rectangular areas.
 */
public class TooltipBlurEffect {
    private final Shader blurShader;
    private Framebuffer fbo1, fbo2;
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
                
                // 5-tap Gaussian blur (lighter than full screen blur)
                float weights[3];
                weights[0] = 0.4;
                weights[1] = 0.3;
                weights[2] = 0.15;
                
                color += texture2D(uTexture, vTexCoord) * weights[0];
                
                for(int i = 1; i < 3; i++) {
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
    public void ensureFramebuffers(int width, int height) {
        if (lastWidth != width || lastHeight != height) {
            if (fbo1 != null) fbo1.close();
            if (fbo2 != null) fbo2.close();
            fbo1 = new Framebuffer(width, height);
            fbo2 = new Framebuffer(width, height);
            lastWidth = width;
            lastHeight = height;
        }
    }
    
    /**
     * Apply Gaussian blur to a source texture and return the result framebuffer.
     * @param sourceTextureId Source texture to blur
     * @param width Width of the texture
     * @param height Height of the texture
     * @return Framebuffer containing the blurred result
     */
    public Framebuffer applyBlur(int sourceTextureId, int width, int height) {
        ensureFramebuffers(width, height);
        
        blurShader.use();
        blurShader.setUniform2f("uResolution", width, height);
        blurShader.setUniform1i("uTexture", 0);
        
        // First pass: horizontal blur
        fbo1.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        blurShader.setUniform2f("uDirection", 1.0f, 0.0f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sourceTextureId);
        renderQuad(width, height);
        fbo1.unbind();
        
        // Second pass: vertical blur
        fbo2.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        blurShader.setUniform2f("uDirection", 0.0f, 1.0f);
        fbo1.bindTexture();
        renderQuad(width, height);
        fbo2.unbind();
        
        Shader.unbind();
        
        return fbo2; // Return final result
    }
    
    /**
     * Render a full-screen quad for post-processing.
     */
    private void renderQuad(int width, int height) {
        // Save current matrices
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
        
        // Restore matrices
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
        if (fbo1 != null) fbo1.close();
        if (fbo2 != null) fbo2.close();
    }
}
