// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.programs;

/**
 * Interface for Iris shader programs.
 * 
 * Based on IRIS's IrisProgram interface
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/programs/IrisProgram.java
 */
public interface IrisProgram {
	void iris$setupState();

	void iris$clearState();

	int iris$getBlockIndex(int program, CharSequence uniformBlockName);

	boolean iris$isSetUp();
}
