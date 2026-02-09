package net.minecraft.test;

// This import should FAIL at compile time because the main module
// does not have org.lwjgl.opengl in its dependencies
import org.lwjgl.opengl.GL11;

/**
 * This class is intentionally designed to FAIL compilation.
 * It tests that the module boundary enforcement is working.
 * 
 * If this class compiles, the boundary enforcement is NOT working!
 */
public class BoundaryViolationTest {
    public void testDirectOpenGLAccess() {
        // This should NOT compile - main module cannot access OpenGL directly
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }
}
