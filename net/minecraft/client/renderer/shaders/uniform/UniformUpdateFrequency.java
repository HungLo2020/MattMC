// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform;

/**
 * Enumeration of uniform update frequencies.
 * 
 * COPIED VERBATIM from IRIS's UniformUpdateFrequency.java
 * Reference: frnsrc/Iris-1.21.9/.../gl/uniform/UniformUpdateFrequency.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public enum UniformUpdateFrequency {
	ONCE,
	PER_TICK,
	PER_FRAME,
	CUSTOM
}
