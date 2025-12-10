package net.minecraft.client.renderer.shaders.targets;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClearPassCreator (IRIS-based clear pass batching).
 */
class ClearPassCreatorTest {

    @Test
    void testCreateClearPasses_NoClearColors() {
        GBufferManager manager = createTestManager();
        
        ImmutableList<ClearPass> passes = ClearPassCreator.createClearPasses(
            manager, false, new HashMap<>()
        );
        
        // With no clear colors and fullClear=false, should have no passes
        assertTrue(passes.isEmpty());
    }

    @Test
    void testCreateClearPasses_FullClear() {
        GBufferManager manager = createTestManager();
        
        ImmutableList<ClearPass> passes = ClearPassCreator.createClearPasses(
            manager, true, new HashMap<>()
        );
        
        // With fullClear=true, should create passes for all allocated buffers
        // Each buffer gets 2 passes (main and alt textures)
        assertFalse(passes.isEmpty());
        assertTrue(passes.size() >= 2); // At least one buffer with main+alt
    }

    @Test
    void testCreateClearPasses_SingleBuffer() {
        GBufferManager manager = createTestManager();
        
        Map<Integer, Vector4f> clearColors = new HashMap<>();
        clearColors.put(0, new Vector4f(1.0f, 0.0f, 0.0f, 1.0f));
        
        ImmutableList<ClearPass> passes = ClearPassCreator.createClearPasses(
            manager, false, clearColors
        );
        
        // Should have 2 passes (main and alt) for buffer 0
        assertEquals(2, passes.size());
    }

    @Test
    void testCreateClearPasses_MultipleBuffers() {
        GBufferManager manager = createTestManager();
        
        Map<Integer, Vector4f> clearColors = new HashMap<>();
        clearColors.put(0, new Vector4f(1.0f, 0.0f, 0.0f, 1.0f));
        clearColors.put(1, new Vector4f(0.0f, 1.0f, 0.0f, 1.0f));
        
        ImmutableList<ClearPass> passes = ClearPassCreator.createClearPasses(
            manager, false, clearColors
        );
        
        // Should have passes for both buffers
        assertFalse(passes.isEmpty());
    }

    @Test
    void testCreateClearPasses_SameColor() {
        GBufferManager manager = createTestManager();
        
        // Multiple buffers with same clear color should be batched
        Vector4f whiteColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
        Map<Integer, Vector4f> clearColors = new HashMap<>();
        clearColors.put(0, whiteColor);
        clearColors.put(1, whiteColor);
        clearColors.put(2, whiteColor);
        
        ImmutableList<ClearPass> passes = ClearPassCreator.createClearPasses(
            manager, false, clearColors
        );
        
        // Buffers with same color and dimensions should be batched
        // Still need separate passes for main/alt textures
        assertFalse(passes.isEmpty());
    }

    @Test
    void testClearPassesNotNull() {
        GBufferManager manager = createTestManager();
        
        Map<Integer, Vector4f> clearColors = new HashMap<>();
        clearColors.put(0, null); // Null means use default
        
        ImmutableList<ClearPass> passes = ClearPassCreator.createClearPasses(
            manager, false, clearColors
        );
        
        // Should still create passes with default color
        assertEquals(2, passes.size()); // main + alt
        for (ClearPass pass : passes) {
            assertNotNull(pass);
            assertNotNull(pass.getFramebuffer());
        }
    }

    private GBufferManager createTestManager() {
        Map<Integer, GBufferManager.RenderTargetSettings> settings = new HashMap<>();
        
        // Create basic settings for a few buffers
        settings.put(0, new GBufferManager.RenderTargetSettings(
            InternalTextureFormat.RGBA8
        ));
        settings.put(1, new GBufferManager.RenderTargetSettings(
            InternalTextureFormat.RGBA8
        ));
        settings.put(2, new GBufferManager.RenderTargetSettings(
            InternalTextureFormat.RGBA8
        ));
        
        return new GBufferManager(1920, 1080, settings);
    }
}
