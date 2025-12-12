// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.programs;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.shaders.framebuffer.GlFramebuffer;
import net.minecraft.client.renderer.shaders.gl.FogMode;
import net.minecraft.client.renderer.shaders.gl.blending.AlphaTest;
import net.minecraft.client.renderer.shaders.gl.blending.BlendModeOverride;
import net.minecraft.client.renderer.shaders.gl.blending.BufferBlendOverride;
import net.minecraft.client.renderer.shaders.pipeline.ShaderPackPipeline;
import net.minecraft.client.renderer.shaders.program.ShaderType;
import net.minecraft.client.renderer.shaders.samplers.IrisSamplers;
import net.minecraft.client.renderer.shaders.uniform.DynamicLocationalUniformHolder;
import net.minecraft.client.renderer.shaders.uniform.custom.CustomUniforms;
import net.minecraft.client.renderer.shaders.uniform.providers.CommonUniforms;
import net.minecraft.client.renderer.shaders.uniform.providers.MatrixUniforms;
import net.minecraft.client.renderer.shaders.uniform.providers.VanillaUniforms;
import net.minecraft.client.renderer.shaders.uniform.providers.builtin.BuiltinReplacementUniforms;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL32C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating ExtendedShader instances.
 * 
 * Based on IRIS's ShaderCreator class
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/programs/ShaderCreator.java
 */
public class ShaderCreator {
	private static final Logger LOGGER = LoggerFactory.getLogger(ShaderCreator.class);

	/**
	 * Creates an ExtendedShader from compiled vertex and fragment shaders.
	 * 
	 * @param name The shader name
	 * @param vertexSource The vertex shader source
	 * @param fragmentSource The fragment shader source
	 * @param vertexFormat The vertex format to use
	 * @param parent The parent pipeline
	 * @param blendModeOverride Optional blend mode override
	 * @param alphaTest Alpha test configuration
	 * @param customUniforms Custom uniform configuration
	 * @return The created ExtendedShader
	 */
	public static ExtendedShader create(
			String name,
			String vertexSource,
			String fragmentSource,
			VertexFormat vertexFormat,
			ShaderPackPipeline parent,
			BlendModeOverride blendModeOverride,
			AlphaTest alphaTest,
			CustomUniforms customUniforms) throws IOException {
		
		// Compile and link the program
		int programId = linkProgram(name, vertexSource, fragmentSource, vertexFormat);
		
		if (programId <= 0) {
			throw new IOException("Failed to link shader program: " + name);
		}
		
		// Create list of buffer blend overrides (empty for now)
		List<BufferBlendOverride> overrides = new ArrayList<>();
		
		// Create the ExtendedShader with uniform and sampler setup
		return new ExtendedShader(
			programId,
			name,
			vertexFormat,
			false, // usesTessellation
			null,  // writingToBeforeTranslucent (to be set by pipeline)
			null,  // writingToAfterTranslucent (to be set by pipeline)
			blendModeOverride,
			alphaTest,
			// Uniform creator
			uniforms -> {
				// Add matrix uniforms (gbufferModelView, gbufferProjection, etc.)
				MatrixUniforms.addMatrixUniforms(uniforms);
				// Add common uniforms (player state, world state, etc.)
				CommonUniforms.addCommonUniforms(uniforms);
				// Add builtin replacement uniforms
				BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);
				// Add vanilla uniforms
				VanillaUniforms.addVanillaUniforms(uniforms);
				// Add custom uniforms
				if (customUniforms != null) {
					customUniforms.assignTo(uniforms);
				}
			},
			// Sampler creator
			(samplerHolder, imageHolder) -> {
				// Set up external samplers for reserved texture units
				samplerHolder.addExternalSampler(IrisSamplers.ALBEDO_TEXTURE_UNIT, "gtexture", "texture", "gcolor");
				samplerHolder.addExternalSampler(IrisSamplers.OVERLAY_TEXTURE_UNIT, "overlay");
				samplerHolder.addExternalSampler(IrisSamplers.LIGHTMAP_TEXTURE_UNIT, "lightmap");
			},
			false, // isIntensity
			parent,
			overrides,
			customUniforms != null ? customUniforms : CustomUniforms.empty()
		);
	}

	/**
	 * Creates an ExtendedShader with default settings.
	 */
	public static ExtendedShader createBasic(
			String name,
			String vertexSource,
			String fragmentSource,
			ShaderPackPipeline parent) throws IOException {
		return create(
			name,
			vertexSource,
			fragmentSource,
			DefaultVertexFormat.BLOCK,
			parent,
			null, // no blend override
			AlphaTest.ALWAYS,
			null  // no custom uniforms
		);
	}

	/**
	 * Compiles and links a shader program.
	 */
	private static int linkProgram(String name, String vertexSource, String fragmentSource, VertexFormat vertexFormat) {
		int programId = GlStateManager.glCreateProgram();
		if (programId <= 0) {
			LOGGER.error("Failed to create shader program for: {}", name);
			return -1;
		}
		
		// Compile vertex shader
		int vertexShader = compileShader(name, ShaderType.VERTEX, vertexSource);
		if (vertexShader <= 0) {
			GlStateManager.glDeleteProgram(programId);
			return -1;
		}
		
		// Compile fragment shader
		int fragmentShader = compileShader(name, ShaderType.FRAGMENT, fragmentSource);
		if (fragmentShader <= 0) {
			GL20C.glDeleteShader(vertexShader);
			GlStateManager.glDeleteProgram(programId);
			return -1;
		}
		
		// Attach shaders
		GlStateManager.glAttachShader(programId, vertexShader);
		GlStateManager.glAttachShader(programId, fragmentShader);
		
		// Bind attribute locations based on vertex format
		int attribIndex = 0;
		for (String attributeName : vertexFormat.getElementAttributeNames()) {
			GlStateManager._glBindAttribLocation(programId, attribIndex, attributeName);
			attribIndex++;
		}
		
		// Link program
		GlStateManager.glLinkProgram(programId);
		
		int linkStatus = GlStateManager.glGetProgrami(programId, GL20C.GL_LINK_STATUS);
		String linkLog = GlStateManager.glGetProgramInfoLog(programId, 32768);
		
		// Detach and delete shaders (they're now part of the program)
		GL20C.glDetachShader(programId, vertexShader);
		GL20C.glDetachShader(programId, fragmentShader);
		GL20C.glDeleteShader(vertexShader);
		GL20C.glDeleteShader(fragmentShader);
		
		if (linkStatus == 0) {
			LOGGER.error("Failed to link shader program {}: {}", name, linkLog);
			GlStateManager.glDeleteProgram(programId);
			return -1;
		}
		
		if (!linkLog.isEmpty()) {
			LOGGER.info("Shader {} link info: {}", name, linkLog);
		}
		
		return programId;
	}

	/**
	 * Compiles a shader from source.
	 */
	private static int compileShader(String programName, ShaderType type, String source) {
		int shader = GL20C.glCreateShader(type.id);
		if (shader <= 0) {
			LOGGER.error("Failed to create {} shader for: {}", type, programName);
			return -1;
		}
		
		GL20C.glShaderSource(shader, source);
		GL20C.glCompileShader(shader);
		
		int compileStatus = GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS);
		String compileLog = GL20C.glGetShaderInfoLog(shader, 32768);
		
		if (compileStatus == 0) {
			LOGGER.error("Failed to compile {} shader for {}: {}", type, programName, compileLog);
			GL20C.glDeleteShader(shader);
			return -1;
		}
		
		if (!compileLog.isEmpty() && !compileLog.isBlank()) {
			LOGGER.debug("Shader {} {} compile info: {}", programName, type, compileLog);
		}
		
		return shader;
	}
}
