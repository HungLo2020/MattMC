package net.fabricmc.fabric.api.renderer.v1.model;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Fabric extension interface for BlockStateModel to support enhanced rendering.
 * This interface is applied to BlockStateModel via mixin, NOT via inheritance
 * to avoid ClassCircularityError during class loading.
 */
public interface FabricBlockStateModel {
    /**
     * Emits quads from this model to the given emitter.
     */
    default void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, 
                           RandomSource random, Predicate<@Nullable Direction> cullTest) {
        // Default implementation does nothing - mixins will override
    }
}
