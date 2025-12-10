package net.minecraft.client.renderer.shaders.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DepthCopyStrategy interface.
 * Structure and strategy selection tests only (no OpenGL context available).
 */
class DepthCopyStrategyTest {

    @Test
    void testInterfaceExists() {
        // Verify interface exists
        assertNotNull(DepthCopyStrategy.class);
        assertTrue(DepthCopyStrategy.class.isInterface());
    }

    @Test
    void testFastestMethodExists() {
        // Verify fastest() static method exists
        assertDoesNotThrow(() -> {
            DepthCopyStrategy.class.getMethod("fastest", boolean.class);
        });
    }

    @Test
    void testInterfaceMethods() {
        // Verify required interface methods
        assertDoesNotThrow(() -> {
            DepthCopyStrategy.class.getMethod("needsDestFramebuffer");
            DepthCopyStrategy.class.getMethod("copy", 
                net.minecraft.client.renderer.shaders.framebuffer.GlFramebuffer.class,
                int.class,
                net.minecraft.client.renderer.shaders.framebuffer.GlFramebuffer.class,
                int.class,
                int.class,
                int.class);
        });
    }

    @Test
    void testGl20CopyTextureExists() {
        // Verify Gl20CopyTexture strategy class exists
        assertDoesNotThrow(() -> {
            Class.forName("net.minecraft.client.renderer.shaders.texture.DepthCopyStrategy$Gl20CopyTexture");
        });
    }

    @Test
    void testGl30BlitFbCombinedDepthStencilExists() {
        // Verify Gl30BlitFbCombinedDepthStencil strategy class exists
        assertDoesNotThrow(() -> {
            Class.forName("net.minecraft.client.renderer.shaders.texture.DepthCopyStrategy$Gl30BlitFbCombinedDepthStencil");
        });
    }

    @Test
    void testGl43CopyImageExists() {
        // Verify Gl43CopyImage strategy class exists
        assertDoesNotThrow(() -> {
            Class.forName("net.minecraft.client.renderer.shaders.texture.DepthCopyStrategy$Gl43CopyImage");
        });
    }

    @Test
    void testPackagePlacement() {
        // Verify correct package
        assertEquals("net.minecraft.client.renderer.shaders.texture", DepthCopyStrategy.class.getPackageName());
    }
}
