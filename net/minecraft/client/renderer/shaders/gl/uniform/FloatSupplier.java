package net.minecraft.client.renderer.shaders.gl.uniform;

/**
 * Functional interface for supplying float values
 * IRIS 1.21.9 compatible
 */
@FunctionalInterface
public interface FloatSupplier {
	float getAsFloat();
}
