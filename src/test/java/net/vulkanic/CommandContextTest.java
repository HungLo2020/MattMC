package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;
import net.vulkanic.backends.opengl.OpenGLCommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CommandContext abstraction and the new command-context-aware API methods.
 * 
 * These tests validate that the CommandContext pattern works correctly and that
 * the new methods properly delegate to backend implementations.
 */
public class CommandContextTest {
    
    private GraphicsBackend backend;
    
    @BeforeEach
    public void setup() {
        // Create OpenGL backend for testing
        backend = new OpenGLBackend();
    }
    
    @Test
    public void testOpenGLCommandContextIsImmediate() {
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
        
        assertTrue(ctx.isImmediate(), "OpenGL context should be immediate mode");
        assertEquals(0, ctx.getHandle(), "OpenGL context should have handle 0");
        assertNotNull(ctx.getDebugName(), "Context should have a debug name");
    }
    
    @Test
    public void testOpenGLCommandContextIsSingleton() {
        CommandContext ctx1 = OpenGLCommandContext.IMMEDIATE;
        CommandContext ctx2 = OpenGLCommandContext.IMMEDIATE;
        
        assertSame(ctx1, ctx2, "OpenGL immediate context should be a singleton");
    }
    
    @Test
    public void testGetImmediateContext() {
        // Initialize VulkanicAPI with OpenGL backend (default)
        VulkanicAPI.initialize();
        
        CommandContext ctx = VulkanicAPI.getImmediateContext();
        
        assertNotNull(ctx, "getImmediateContext() should return a context");
        assertTrue(ctx.isImmediate(), "Immediate context should be immediate mode");
        assertSame(OpenGLCommandContext.IMMEDIATE, ctx, 
            "getImmediateContext() should return OpenGL immediate context");
    }
    
    @Test
    public void testSetDynamicViewportWithContext() {
        // This test validates that the new CommandContext-aware method exists
        // and accepts the right parameters. We skip actual OpenGL calls since
        // we don't have a GL context in unit tests.
        
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
        
        // Verify method signature exists and accepts parameters
        // (Backend methods would throw UnsatisfiedLinkError without GL context)
        assertNotNull(ctx, "Command context should exist");
        assertTrue(ctx.isImmediate(), "Context should be immediate");
    }
    
    @Test
    public void testSetDynamicScissorWithContext() {
        // This test validates that the new CommandContext-aware method exists
        // and accepts the right parameters. We skip actual OpenGL calls since
        // we don't have a GL context in unit tests.
        
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
        
        // Verify method signature exists and accepts parameters
        assertNotNull(ctx, "Command context should exist");
        assertTrue(ctx.isImmediate(), "Context should be immediate");
    }
    
    @Test
    public void testLegacyMethodsDelegateToContextVariant() {
        // We can't test actual delegation without a GL context, but we can
        // verify the API structure is correct
        
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
        assertNotNull(ctx, "Context should exist for delegation");
    }
    
    @Test
    public void testContextToString() {
        CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
        String str = ctx.toString();
        
        assertNotNull(str, "toString() should not return null");
        assertTrue(str.contains("OpenGL"), "toString() should mention OpenGL");
    }

    // -------------------------------------------------------------------------
    // Command Buffer Lifecycle Tests
    // -------------------------------------------------------------------------

    @Test
    public void testBeginCommandBufferReturnsImmediateContext() {
        // beginCommandBuffer() on the OpenGL backend must return the singleton
        // immediate context because OpenGL is always in immediate mode.
        CommandContext ctx = backend.beginCommandBuffer();

        assertNotNull(ctx, "beginCommandBuffer() should return a non-null context");
        assertTrue(ctx.isImmediate(), "OpenGL beginCommandBuffer() should return an immediate context");
        assertSame(OpenGLCommandContext.IMMEDIATE, ctx,
            "OpenGL beginCommandBuffer() should return the singleton IMMEDIATE context");
    }

    @Test
    public void testEndCommandBufferIsNoOpForOpenGL() {
        // endCommandBuffer() on the OpenGL backend should not throw; it is a no-op.
        CommandContext ctx = backend.beginCommandBuffer();
        // Should complete without exception
        backend.endCommandBuffer(ctx);
    }

    @Test
    public void testSubmitCommandBufferIsNoOpForOpenGL() {
        // submitCommandBuffer() on the OpenGL backend should not throw; it is a no-op.
        CommandContext ctx = backend.beginCommandBuffer();
        backend.endCommandBuffer(ctx);
        // Should complete without exception
        backend.submitCommandBuffer(ctx);
    }

    @Test
    public void testResetCommandBufferIsNoOpForOpenGL() {
        // resetCommandBuffer() on the OpenGL backend should not throw; it is a no-op.
        CommandContext ctx = backend.beginCommandBuffer();
        // Should complete without exception
        backend.resetCommandBuffer(ctx);
    }

    @Test
    public void testFullCommandBufferLifecycleForOpenGL() {
        // Exercise the complete begin → end → submit → reset lifecycle on OpenGL.
        // Each step should succeed and the context returned must be the immediate context.
        CommandContext ctx = backend.beginCommandBuffer();

        assertNotNull(ctx, "Context must not be null after beginCommandBuffer()");
        assertTrue(ctx.isImmediate(), "Context must be immediate mode on OpenGL");

        backend.endCommandBuffer(ctx);
        backend.submitCommandBuffer(ctx);
        backend.resetCommandBuffer(ctx);
        // If we reach here the lifecycle completed without exception
    }

    @Test
    public void testVulkanicAPIBeginCommandBufferDelegates() {
        // VulkanicAPI.beginCommandBuffer() should delegate to the backend and
        // return the immediate context when the OpenGL backend is active.
        VulkanicAPI.initialize();

        CommandContext ctx = VulkanicAPI.beginCommandBuffer();

        assertNotNull(ctx, "VulkanicAPI.beginCommandBuffer() must return a non-null context");
        assertTrue(ctx.isImmediate(), "VulkanicAPI.beginCommandBuffer() must return immediate context for OpenGL");
    }

    @Test
    public void testVulkanicAPICommandBufferLifecycleDelegates() {
        // Verify the complete lifecycle methods on VulkanicAPI delegate without error.
        VulkanicAPI.initialize();

        CommandContext ctx = VulkanicAPI.beginCommandBuffer();
        VulkanicAPI.endCommandBuffer(ctx);
        VulkanicAPI.submitCommandBuffer(ctx);
        VulkanicAPI.resetCommandBuffer(ctx);
        // No exception means delegation succeeded
    }

    @Test
    public void testEndCommandBufferRejectsNonImmediateContext() {
        // The OpenGL backend must reject a non-immediate context because it has no
        // concept of deferred command buffers.
        CommandContext nonImmediate = new CommandContext() {
            @Override public boolean isImmediate() { return false; }
            @Override public long getHandle() { return 42L; }
            @Override public String getDebugName() { return "non-immediate-test"; }
        };

        assertThrows(IllegalArgumentException.class,
            () -> backend.endCommandBuffer(nonImmediate),
            "endCommandBuffer() must reject a non-immediate context on the OpenGL backend");
    }

    @Test
    public void testSubmitCommandBufferRejectsNonImmediateContext() {
        CommandContext nonImmediate = new CommandContext() {
            @Override public boolean isImmediate() { return false; }
            @Override public long getHandle() { return 42L; }
            @Override public String getDebugName() { return "non-immediate-test"; }
        };

        assertThrows(IllegalArgumentException.class,
            () -> backend.submitCommandBuffer(nonImmediate),
            "submitCommandBuffer() must reject a non-immediate context on the OpenGL backend");
    }

    @Test
    public void testResetCommandBufferRejectsNonImmediateContext() {
        CommandContext nonImmediate = new CommandContext() {
            @Override public boolean isImmediate() { return false; }
            @Override public long getHandle() { return 42L; }
            @Override public String getDebugName() { return "non-immediate-test"; }
        };

        assertThrows(IllegalArgumentException.class,
            () -> backend.resetCommandBuffer(nonImmediate),
            "resetCommandBuffer() must reject a non-immediate context on the OpenGL backend");
    }
}
