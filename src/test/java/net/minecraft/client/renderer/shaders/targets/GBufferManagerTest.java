package net.minecraft.client.renderer.shaders.targets;

import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Structure tests for GBufferManager class (no OpenGL calls).
 */
public class GBufferManagerTest {

    @Test
    public void testConstructor() {
        Map<Integer, GBufferManager.RenderTargetSettings> settings = new HashMap<>();
        settings.put(0, new GBufferManager.RenderTargetSettings(InternalTextureFormat.RGBA8));
        
        GBufferManager manager = new GBufferManager(1920, 1080, settings);
        
        assertNotNull(manager);
        assertEquals(1920, manager.getCurrentWidth());
        assertEquals(1080, manager.getCurrentHeight());
        assertEquals(16, manager.getRenderTargetCount());
        assertFalse(manager.isDestroyed());
    }

    @Test
    public void testGetReturnsNullForNonExistentTarget() {
        Map<Integer, GBufferManager.RenderTargetSettings> settings = new HashMap<>();
        GBufferManager manager = new GBufferManager(1920, 1080, settings);
        
        assertNull(manager.get(0), "Should return null for non-existent target");
    }

    @Test
    public void testResizeIfNeededNoChange() {
        Map<Integer, GBufferManager.RenderTargetSettings> settings = new HashMap<>();
        GBufferManager manager = new GBufferManager(1920, 1080, settings);
        
        boolean resized = manager.resizeIfNeeded(1920, 1080);
        
        assertFalse(resized, "Should not resize if dimensions are the same");
    }

    @Test
    public void testInvalidIndexGet() {
        Map<Integer, GBufferManager.RenderTargetSettings> settings = new HashMap<>();
        GBufferManager manager = new GBufferManager(1920, 1080, settings);
        
        assertNull(manager.get(-1));
        assertNull(manager.get(16));
    }

    @Test
    public void testRenderTargetSettings() {
        GBufferManager.RenderTargetSettings settings = 
            new GBufferManager.RenderTargetSettings(InternalTextureFormat.RGBA16F);
        
        assertEquals(InternalTextureFormat.RGBA16F, settings.getInternalFormat());
    }

    @Test
    public void testGetCurrentDimensions() {
        Map<Integer, GBufferManager.RenderTargetSettings> settings = new HashMap<>();
        GBufferManager manager = new GBufferManager(1024, 768, settings);
        
        assertEquals(1024, manager.getCurrentWidth());
        assertEquals(768, manager.getCurrentHeight());
    }

    @Test
    public void testDestroy() {
        Map<Integer, GBufferManager.RenderTargetSettings> settings = new HashMap<>();
        GBufferManager manager = new GBufferManager(1920, 1080, settings);
        
        assertFalse(manager.isDestroyed());
        manager.destroy();
        assertTrue(manager.isDestroyed());
    }

    @Test
    public void testAccessAfterDestroy() {
        Map<Integer, GBufferManager.RenderTargetSettings> settings = new HashMap<>();
        GBufferManager manager = new GBufferManager(1920, 1080, settings);
        
        manager.destroy();
        
        assertThrows(IllegalStateException.class, () -> manager.get(0));
        assertThrows(IllegalStateException.class, () -> manager.resizeIfNeeded(100, 100));
    }
}
