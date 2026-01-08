package net.fabricmc.fabric.api.renderer.v1.model;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Fabric extension interface for BlockModelPart to support enhanced rendering.
 * This interface is applied to BlockModelPart via mixin, NOT via inheritance
 * to avoid ClassCircularityError during class loading.
 */
public interface FabricBlockModelPart {
    /**
     * Emits quads from this model part to the given emitter.
     */
    default void emitQuads(QuadEmitter emitter, Predicate<@Nullable Direction> cullTest) {
        // Default implementation does nothing - mixins will override
    }
}
