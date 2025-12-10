package net.minecraft.client.renderer.shaders.program;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL20C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Links compiled shaders into an OpenGL program.
 * 
 * Based on IRIS's ProgramCreator.java - following pattern exactly.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/gl/shader/ProgramCreator.java
 * 
 * This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.
 * 
 * Step 11 of NEW-SHADER-PLAN.md
 */
public class ProgramLinker {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProgramLinker.class);

	/**
	 * Creates and links a shader program from compiled shaders.
	 * 
	 * Following IRIS's ProgramCreator.create() method exactly (ProgramCreator.java:16-56).
	 * 
	 * @param name The program name for debugging
	 * @param shaders The compiled shaders to link
	 * @return The OpenGL program handle
	 * @throws ShaderCompileException if linking fails
	 */
	public static int create(String name, ShaderCompiler... shaders) {
		// Create program (IRIS ProgramCreator.java:17)
		int program = GlStateManager.glCreateProgram();

		// Bind attribute locations for Iris/OptiFine compatibility (IRIS ProgramCreator.java:19-27)
		// These are the standard attribute locations used by Iris shaders
		GlStateManager._glBindAttribLocation(program, 11, "iris_Entity");
		GlStateManager._glBindAttribLocation(program, 11, "mc_Entity");
		GlStateManager._glBindAttribLocation(program, 12, "mc_midTexCoord");
		GlStateManager._glBindAttribLocation(program, 13, "at_tangent");
		GlStateManager._glBindAttribLocation(program, 14, "at_midBlock");

		// Bind standard Minecraft attributes (IRIS ProgramCreator.java:25-26)
		GlStateManager._glBindAttribLocation(program, 0, "Position");
		GlStateManager._glBindAttribLocation(program, 1, "UV0");

		// Attach all shaders (IRIS ProgramCreator.java:28-32)
		for (ShaderCompiler shader : shaders) {
			GlStateManager.glAttachShader(program, shader.getHandle());
		}

		// Link program (IRIS ProgramCreator.java:34)
		GlStateManager.glLinkProgram(program);

		// Always detach shaders according to OpenGL best practices (IRIS ProgramCreator.java:38-41)
		// https://www.khronos.org/opengl/wiki/Shader_Compilation#Cleanup
		for (ShaderCompiler shader : shaders) {
			detachShader(program, shader.getHandle());
		}

		// Get program info log (IRIS ProgramCreator.java:43)
		String log = getProgramInfoLog(program);

		// Log warnings if present (IRIS ProgramCreator.java:45-47)
		if (!log.isEmpty()) {
			LOGGER.warn("Program link log for {}: {}", name, log);
		}

		// Check link status (IRIS ProgramCreator.java:49-53)
		int result = GlStateManager.glGetProgrami(program, GL20C.GL_LINK_STATUS);

		if (result != GL20C.GL_TRUE) {
			throw new ShaderCompileException(name, log);
		}

		LOGGER.debug("Successfully linked program: {}", name);

		return program;
	}

	/**
	 * Detaches a shader from a program.
	 * 
	 * Following IRIS's IrisRenderSystem.detachShader() pattern.
	 * 
	 * @param program The program handle
	 * @param shader The shader handle
	 */
	private static void detachShader(int program, int shader) {
		GL20C.glDetachShader(program, shader);
	}

	/**
	 * Retrieves the info log from a program object.
	 * 
	 * Following IRIS's IrisRenderSystem.getProgramInfoLog() pattern.
	 * 
	 * @param program The program handle
	 * @return The info log string
	 */
	private static String getProgramInfoLog(int program) {
		// Get log length
		int logLength = GlStateManager.glGetProgrami(program, GL20C.GL_INFO_LOG_LENGTH);
		
		if (logLength == 0) {
			return "";
		}

		// Retrieve log
		return GlStateManager.glGetProgramInfoLog(program, logLength);
	}

	/**
	 * Deletes a program and frees OpenGL resources.
	 * 
	 * @param program The program handle to delete
	 */
	public static void deleteProgram(int program) {
		if (program != 0) {
			GlStateManager.glDeleteProgram(program);
		}
	}
}
