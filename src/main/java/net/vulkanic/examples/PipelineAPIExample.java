package net.vulkanic.examples;

import net.vulkanic.*;

/**
 * Example demonstrating how to use the new Vulkan-compatible Pipeline API.
 * 
 * This replaces the old stateful approach with immutable Pipeline State Objects.
 * 
 * OLD WAY (deprecated):
 * <pre>
 * VulkanicAPI.enable(GL_BLEND);
 * VulkanicAPI.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
 * VulkanicAPI.enable(GL_DEPTH_TEST);
 * VulkanicAPI.setDepthTestFunction(GL_LESS);
 * VulkanicAPI.setDepthWriteEnabled(true);
 * VulkanicAPI.enable(GL_CULL_FACE);
 * VulkanicAPI.glCullFace(GL_BACK);
 * // ... draw
 * </pre>
 * 
 * NEW WAY (Vulkan-compatible):
 * <pre>
 * // Create pipeline once (typically at initialization)
 * PipelineStateDesc desc = new PipelineStateDesc()
 *     .setBlendMode(BlendMode.ALPHA_BLEND)
 *     .setDepthTest(true, CompareOp.LESS)
 *     .setDepthWrite(true)
 *     .setCullMode(CullMode.BACK)
 *     .setDebugName("MyRenderingPipeline");
 * 
 * Pipeline pipeline = VulkanicAPI.createPipeline(desc);
 * 
 * // Use it when rendering
 * CommandBuffer cmd = VulkanicAPI.allocateCommandBuffer();
 * VulkanicAPI.beginCommandBuffer(cmd);
 * VulkanicAPI.bindPipeline(cmd, pipeline);
 * // ... draw calls automatically use this pipeline state
 * VulkanicAPI.endCommandBuffer(cmd);
 * VulkanicAPI.submitCommandBuffer(cmd);
 * </pre>
 */
public class PipelineAPIExample {
    
    /**
     * Example: Creating a pipeline for opaque geometry rendering
     */
    public static Pipeline createOpaqueGeometryPipeline() {
        PipelineStateDesc desc = new PipelineStateDesc()
            .setBlendMode(BlendMode.NONE)              // No blending for opaque
            .setDepthTest(true, CompareOp.LESS)        // Standard depth test
            .setDepthWrite(true)                        // Write to depth buffer
            .setCullMode(CullMode.BACK)                 // Cull back faces
            .setFrontFace(true)                         // Counter-clockwise is front
            .setDebugName("OpaqueGeometry");
        
        return VulkanicAPI.createPipeline(desc);
    }
    
    /**
     * Example: Creating a pipeline for transparent geometry rendering
     */
    public static Pipeline createTransparentGeometryPipeline() {
        PipelineStateDesc desc = new PipelineStateDesc()
            .setBlendMode(BlendMode.ALPHA_BLEND)        // Alpha blending
            .setDepthTest(true, CompareOp.LESS)        // Test depth
            .setDepthWrite(false)                       // Don't write depth (for transparency)
            .setCullMode(CullMode.NONE)                 // Don't cull (see both sides)
            .setDebugName("TransparentGeometry");
        
        return VulkanicAPI.createPipeline(desc);
    }
    
    /**
     * Example: Creating a pipeline for UI/overlay rendering
     */
    public static Pipeline createUIOverlayPipeline() {
        PipelineStateDesc desc = new PipelineStateDesc()
            .setBlendMode(BlendMode.ALPHA_BLEND)        // Alpha blending for UI
            .setDepthTest(false, CompareOp.ALWAYS)     // No depth test for UI
            .setDepthWrite(false)                       // No depth write
            .setCullMode(CullMode.NONE)                 // No culling for quads
            .setDebugName("UIOverlay");
        
        return VulkanicAPI.createPipeline(desc);
    }
    
    /**
     * Example: Using pipelines in a rendering loop
     */
    public static void exampleRenderingLoop(
            Pipeline opaquePipeline,
            Pipeline transparentPipeline,
            Pipeline uiPipeline) {
        
        // Allocate command buffer for recording commands
        CommandBuffer cmd = VulkanicAPI.allocateCommandBuffer();
        VulkanicAPI.beginCommandBuffer(cmd);
        
        // Render opaque geometry
        VulkanicAPI.bindPipeline(cmd, opaquePipeline);
        // ... opaque draw calls here
        // Pipeline state is automatically applied before each draw
        
        // Render transparent geometry
        VulkanicAPI.bindPipeline(cmd, transparentPipeline);
        // ... transparent draw calls here
        
        // Render UI overlay
        VulkanicAPI.bindPipeline(cmd, uiPipeline);
        // ... UI draw calls here
        
        // End and submit command buffer
        VulkanicAPI.endCommandBuffer(cmd);
        VulkanicAPI.submitCommandBuffer(cmd);
    }
    
    /**
     * Example: Migration pattern for existing code
     * 
     * If you have code like this:
     * <pre>
     * void renderSomething() {
     *     VulkanicAPI.enable(GL_BLEND);
     *     VulkanicAPI.setDepthTestFunction(GL_LESS);
     *     // ... rendering
     * }
     * </pre>
     * 
     * Migrate to:
     * <pre>
     * Pipeline somethingPipeline;  // Created once at init
     * 
     * void init() {
     *     somethingPipeline = VulkanicAPI.createPipeline(
     *         new PipelineStateDesc()
     *             .setBlendMode(BlendMode.ALPHA_BLEND)
     *             .setDepthTest(true, CompareOp.LESS)
     *     );
     * }
     * 
     * void renderSomething() {
     *     CommandBuffer cmd = VulkanicAPI.allocateCommandBuffer();
     *     VulkanicAPI.beginCommandBuffer(cmd);
     *     VulkanicAPI.bindPipeline(cmd, somethingPipeline);
     *     // ... rendering
     *     VulkanicAPI.endCommandBuffer(cmd);
     *     VulkanicAPI.submitCommandBuffer(cmd);
     * }
     * </pre>
     */
    public static class MigrationPattern {
        // This class just documents the pattern
    }
    
    /**
     * Important notes for migration:
     * 
     * 1. Create pipelines ONCE (at initialization), not every frame
     * 2. Pipelines are immutable - create new ones if you need different state
     * 3. Bind the appropriate pipeline before drawing
     * 4. Pipeline state is automatically applied before draw calls
     * 5. You can have multiple pipelines for different rendering passes
     * 
     * Benefits:
     * - Vulkan backend will be trivial to implement (direct 1:1 mapping)
     * - Better performance (state changes are batched and optimized)
     * - Clearer code (rendering intent is explicit)
     * - Future-proof (matches modern GPU architecture)
     */
}
