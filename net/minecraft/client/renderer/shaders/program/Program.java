// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.program;

import com.mojang.blaze3d.opengl.GlStateManager;

/**
 * Represents a compiled OpenGL shader program.
 * 
 * COPIED VERBATIM from IRIS's Program.java (with minimal adaptations for MattMC).
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/gl/program/Program.java
 * 
 * Step 12 of NEW-SHADER-PLAN.md
 */
public final class Program {
	private final int programId;
	// Note: ProgramUniforms, ProgramSamplers, ProgramImages will be added in Steps 26-27
	// For now, keeping minimal structure from IRIS

	Program(int program) {
		this.programId = program;
	}

	public static void unbind() {
		// IRIS Program.java:22-24
		GlStateManager._glUseProgram(0);
	}

	public void use() {
		// IRIS Program.java:27-34
		// Note: Memory barriers and uniform/sampler updates will be added with uniforms system
		GlStateManager._glUseProgram(programId);
	}

	public void destroyInternal() {
		// IRIS Program.java:36-38
		GlStateManager.glDeleteProgram(programId);
	}

	/**
	 * @return the OpenGL ID of this program.
	 * @deprecated this should be encapsulated eventually
	 */
	@Deprecated
	public int getProgramId() {
		// IRIS Program.java:44-47
		return programId;
	}
}
