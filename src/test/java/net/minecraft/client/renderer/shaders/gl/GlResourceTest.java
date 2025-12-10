package net.minecraft.client.renderer.shaders.gl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GlResource base class.
 */
public class GlResourceTest {

    private static class TestResource extends GlResource {
        private boolean destroyed = false;

        public TestResource(int id) {
            super(id);
        }

        @Override
        protected void destroyInternal() {
            destroyed = true;
        }

        public boolean isDestroyed() {
            return destroyed;
        }

        public int getIdPublic() {
            return getGlId();
        }
    }

    @Test
    public void testResourceCreation() {
        TestResource resource = new TestResource(42);
        assertEquals(42, resource.getIdPublic());
    }

    @Test
    public void testResourceDestroy() {
        TestResource resource = new TestResource(100);
        assertFalse(resource.isDestroyed());
        
        resource.destroy();
        assertTrue(resource.isDestroyed());
    }

    @Test
    public void testDestroyedResourceThrows() {
        TestResource resource = new TestResource(200);
        resource.destroy();
        
        assertThrows(IllegalStateException.class, resource::getIdPublic,
            "Should throw when accessing destroyed resource");
    }

    @Test
    public void testMultipleDestroyCalls() {
        TestResource resource = new TestResource(300);
        resource.destroy();
        resource.destroy(); // Second destroy should not throw
        
        assertTrue(resource.isDestroyed());
    }

    @Test
    public void testDifferentResourceIds() {
        TestResource resource1 = new TestResource(1);
        TestResource resource2 = new TestResource(2);
        TestResource resource3 = new TestResource(3);
        
        assertEquals(1, resource1.getIdPublic());
        assertEquals(2, resource2.getIdPublic());
        assertEquals(3, resource3.getIdPublic());
    }

    @Test
    public void testZeroId() {
        TestResource resource = new TestResource(0);
        assertEquals(0, resource.getIdPublic());
    }

    @Test
    public void testNegativeId() {
        TestResource resource = new TestResource(-1);
        assertEquals(-1, resource.getIdPublic());
    }
}
