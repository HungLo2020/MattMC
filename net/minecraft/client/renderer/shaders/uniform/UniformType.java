// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

/**
 * Enumeration of uniform types.
 * 
 * COPIED VERBATIM from IRIS's UniformType.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/UniformType.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public enum UniformType {
	INT,
	FLOAT,
	MAT3,
	MAT4,
	VEC2,
	VEC2I,
	VEC3,
	VEC3I,
	VEC4,
	VEC4I
}
