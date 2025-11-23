package mattmc.client.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CoordinateUtils utility class.
 * Note: These tests verify the structure and basic functionality.
 * Full integration tests would require a GLFW context which is not available in unit tests.
 */
public class CoordinateUtilsTest {
    
    @Test
    public void testPoint2DCreation() {
        CoordinateUtils.Point2D point = new CoordinateUtils.Point2D(100.5f, 200.75f);
        assertEquals(100.5f, point.x, 0.001f);
        assertEquals(200.75f, point.y, 0.001f);
    }
    
    @Test
    public void testScaleFactorsCreation() {
        CoordinateUtils.ScaleFactors scale = new CoordinateUtils.ScaleFactors(2.0f, 2.0f);
        assertEquals(2.0f, scale.scaleX, 0.001f);
        assertEquals(2.0f, scale.scaleY, 0.001f);
    }
    
    @Test
    public void testPoint2DImmutability() {
        CoordinateUtils.Point2D point = new CoordinateUtils.Point2D(50.0f, 100.0f);
        // Fields are final, so this is just a sanity check
        assertNotNull(point);
        assertEquals(50.0f, point.x);
        assertEquals(100.0f, point.y);
    }
    
    @Test
    public void testScaleFactorsImmutability() {
        CoordinateUtils.ScaleFactors scale = new CoordinateUtils.ScaleFactors(1.5f, 2.0f);
        // Fields are final, so this is just a sanity check
        assertNotNull(scale);
        assertEquals(1.5f, scale.scaleX);
        assertEquals(2.0f, scale.scaleY);
    }
    
    // Note: Testing getFramebufferScale, windowToFramebuffer, and framebufferToWindow
    // would require a valid GLFW window handle, which is not available in unit tests.
    // These methods are tested implicitly through integration tests when the application runs.
}
