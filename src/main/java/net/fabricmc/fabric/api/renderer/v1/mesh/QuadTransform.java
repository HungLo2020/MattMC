package net.fabricmc.fabric.api.renderer.v1.mesh;

/**
 * Functional interface for transforming quads.
 */
@FunctionalInterface
public interface QuadTransform {
    /**
     * Transforms a quad. Return true if the quad should be emitted, false to discard.
     */
    boolean transform(MutableQuadView quad);
}
