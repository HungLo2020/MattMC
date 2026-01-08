package net.fabricmc.fabric.api.client.render.fluid.v1;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A class containing some utilities for rendering fluids.
 */
public final class FluidRendering {
    private FluidRendering() { }

    /**
     * Renders a fluid using the given handler, default renderer, and context.
     */
    public static void render(FluidRenderHandler handler, BlockAndTintGetter world, BlockPos pos, 
                              VertexConsumer vertexConsumer, BlockState blockState, FluidState fluidState, 
                              DefaultRenderer defaultRenderer) {
        if (handler == null) {
            // Handler should never be null at this point - this indicates a registry initialization issue
            throw new IllegalStateException("FluidRenderHandler is null for fluid: " + fluidState.getType() + 
                ". This may indicate that FluidRenderHandlerRegistry was not properly initialized.");
        }
        // Invoke the default renderer which will use the handler
        defaultRenderer.render(handler, world, pos, vertexConsumer, blockState, fluidState);
    }

    /**
     * Interface for default fluid rendering.
     */
    public interface DefaultRenderer {
        /**
         * Render the default geometry when requested by the fluid render handler.
         */
        default void render(FluidRenderHandler handler, BlockAndTintGetter world, BlockPos pos, 
                           VertexConsumer vertexConsumer, BlockState blockState, FluidState fluidState) {
            // Default implementation - subclasses should override
        }
    }
}
