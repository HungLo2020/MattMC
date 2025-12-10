// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

import java.util.function.BooleanSupplier;

/**
 * A uniform that holds a boolean value (0 or 1).
 * 
 * COPIED VERBATIM from IRIS's BooleanUniform.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/BooleanUniform.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class BooleanUniform extends IntUniform {
	BooleanUniform(int location, BooleanSupplier value) {
		super(location, () -> value.getAsBoolean() ? 1 : 0);
	}
}
