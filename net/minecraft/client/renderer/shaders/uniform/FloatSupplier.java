// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

/**
 * Functional interface for supplying float values.
 * 
 * COPIED VERBATIM from IRIS's FloatSupplier.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/FloatSupplier.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
@FunctionalInterface
public interface FloatSupplier {
	float getAsFloat();
}
