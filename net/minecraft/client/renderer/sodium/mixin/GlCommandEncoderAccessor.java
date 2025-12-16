package net.minecraft.client.renderer.sodium.mixin;

import net.minecraft.client.renderer.gl.advanced.device.DrawCommandList;

/**
 * Temporary accessor stub for GlCommandEncoder.
 * This will be replaced when mixins are inlined in Phase 4.
 * 
 * @see net.minecraft.client.renderer.advanced.AdvancedRenderingConfig
 */
public interface GlCommandEncoderAccessor {
    void sodium$applyPipelineState(DrawCommandList.Pipeline pipeline);
    void sodium$setLastProgram(Object program);
}
