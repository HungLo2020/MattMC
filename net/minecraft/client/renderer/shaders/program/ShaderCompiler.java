package net.minecraft.client.renderer.shaders.program;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL20C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Compiles GLSL shader source code into OpenGL shader objects.
 * 
 * Based on IRIS's GlShader.java, adapted for MattMC.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/gl/shader/GlShader.java
 * 
 * This file is based on code from Sodium by JellySquid, licensed under the LGPLv3 license.
 * 
 * Step 11 of NEW-SHADER-PLAN.md
 */
public class ShaderCompiler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShaderCompiler.class);

	private final String name;
	private final int handle;

	/**
	 * Compiles a shader from GLSL source code.
	 * 
	 * Following IRIS's GlShader constructor pattern exactly (GlShader.java:24-28).
	 * 
	 * @param type The shader type (VERTEX, FRAGMENT, etc.)
	 * @param name The shader name for debugging
	 * @param source The GLSL source code
	 * @throws ShaderCompileException if compilation fails
	 */
	public ShaderCompiler(ShaderType type, String name, String source) {
		this.name = name;
		this.handle = compileShader(type, name, source);
	}

	/**
	 * Compiles a shader and returns its OpenGL handle.
	 * 
	 * Following IRIS's createShader method pattern exactly (GlShader.java:30-50).
	 * 
	 * @param type The shader type
	 * @param name The shader name for debugging
	 * @param source The GLSL source code
	 * @return The OpenGL shader handle
	 * @throws ShaderCompileException if compilation fails
	 */
	private static int compileShader(ShaderType type, String name, String source) {
		// Create shader object (IRIS GlShader.java:31)
		int handle = GlStateManager.glCreateShader(type.id);
		
		// Set shader source with AMD workaround (IRIS GlShader.java:32)
		ShaderWorkarounds.safeShaderSource(handle, source);
		
		// Compile shader (IRIS GlShader.java:33)
		GlStateManager.glCompileShader(handle);

		// Get compilation log (IRIS GlShader.java:37)
		String log = getShaderInfoLog(handle);

		// Log warnings if present (IRIS GlShader.java:39-41)
		if (!log.isEmpty()) {
			LOGGER.warn("Shader compilation log for {}: {}", name, log);
		}

		// Check compilation status (IRIS GlShader.java:43-47)
		int result = GlStateManager.glGetShaderi(handle, GL20C.GL_COMPILE_STATUS);

		if (result != GL20C.GL_TRUE) {
			throw new ShaderCompileException(name, log);
		}

		LOGGER.debug("Successfully compiled shader: {} ({})", name, type.name().toLowerCase(Locale.ROOT));

		return handle;
	}

	/**
	 * Retrieves the info log from a shader object.
	 * 
	 * Following IRIS's IrisRenderSystem.getShaderInfoLog() pattern.
	 * 
	 * @param handle The shader handle
	 * @return The info log string
	 */
	private static String getShaderInfoLog(int handle) {
		// Get log length
		int logLength = GlStateManager.glGetShaderi(handle, GL20C.GL_INFO_LOG_LENGTH);
		
		if (logLength == 0) {
			return "";
		}

		// Retrieve log
		return GlStateManager.glGetShaderInfoLog(handle, logLength);
	}

	/**
	 * Gets the shader name for debugging.
	 * 
	 * @return The shader name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Gets the OpenGL shader handle.
	 * 
	 * @return The shader handle
	 */
	public int getHandle() {
		return this.handle;
	}

	/**
	 * Deletes the shader object and frees OpenGL resources.
	 * 
	 * Following IRIS's GlResource.destroyInternal() pattern (GlShader.java:61-63).
	 */
	public void delete() {
		if (this.handle != 0) {
			GlStateManager.glDeleteShader(this.handle);
			LOGGER.debug("Deleted shader: {}", this.name);
		}
	}
}
