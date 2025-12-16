package net.minecraft.client.renderer.sodium.services;

import net.minecraft.client.renderer.sodium.model.color.ColorProviderRegistry;
import net.minecraft.client.renderer.sodium.model.light.LightPipelineProvider;
import net.minecraft.client.renderer.sodium.model.quad.blender.BlendedColorProvider;
import net.minecraft.client.renderer.chunk.advanced.compile.pipeline.FluidRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public interface FluidRendererFactory {
    FluidRendererFactory INSTANCE = Services.load(FluidRendererFactory.class);

    static FluidRendererFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new platform dependent fluid renderer.
     * @param colorRegistry The current color registry.
     * @param lightPipelineProvider The current {@code LightPipelineProvider}.
     * @return A new fluid renderer.
     */
    FluidRenderer createPlatformFluidRenderer(ColorProviderRegistry colorRegistry, LightPipelineProvider lightPipelineProvider);

    BlendedColorProvider<FluidState> getWaterColorProvider();

    BlendedColorProvider<BlockState> getWaterBlockColorProvider();
}
