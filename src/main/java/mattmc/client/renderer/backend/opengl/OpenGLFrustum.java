package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.Frustum;

import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL11.*;

/**
 * OpenGL-specific frustum implementation that reads matrices from OpenGL state.
 * This is a thin wrapper around the backend-agnostic {@link Frustum} class.
 * 
 * <p>This class belongs in the backend/opengl package because it makes OpenGL calls.
 * The core frustum math is in the agnostic {@link Frustum} class.
 */
public class OpenGLFrustum extends Frustum {
    
    /**
     * Update the frustum planes from the current OpenGL matrices.
     * Call this after setting up the projection and modelview matrices.
     * 
     * <p>This method reads the current GL_PROJECTION_MATRIX and GL_MODELVIEW_MATRIX
     * from OpenGL state and passes them to the parent {@link Frustum} class.
     */
    public void updateFromGLState() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer projBuffer = stack.mallocFloat(16);
            FloatBuffer modlBuffer = stack.mallocFloat(16);
            
            // Get current matrices from OpenGL
            glGetFloatv(GL_PROJECTION_MATRIX, projBuffer);
            glGetFloatv(GL_MODELVIEW_MATRIX, modlBuffer);
            
            // Convert to arrays
            float[] projectionMatrix = new float[16];
            float[] modelviewMatrix = new float[16];
            projBuffer.get(projectionMatrix);
            modlBuffer.get(modelviewMatrix);
            
            // Use parent class to update frustum planes
            update(projectionMatrix, modelviewMatrix);
        }
    }
}
