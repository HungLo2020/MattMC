// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.program;

import org.jetbrains.annotations.Nullable;

/**
 * Fluent builder for creating shader programs.
 * 
 * COPIED STRUCTURE from IRIS's ProgramBuilder.java (simplified for Step 12).
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/gl/program/ProgramBuilder.java
 * 
 * Note: Uniforms, samplers, and images will be added in Steps 26-27 when uniform system is implemented.
 * For now, this provides the core compilation/linking structure.
 * 
 * Step 12 of NEW-SHADER-PLAN.md
 */
public class ProgramBuilder {
	private final int program;
	// Note: samplers and images builders will be added in later steps

	private ProgramBuilder(String name, int program) {
		// IRIS ProgramBuilder.java:25-31
		this.program = program;
		// TODO: Add samplers = ProgramSamplers.builder(program, reservedTextureUnits) in Step 26
		// TODO: Add images = ProgramImages.builder(program) in Step 26
	}

	public static ProgramBuilder begin(String name, @Nullable String vertexSource, @Nullable String geometrySource,
									   @Nullable String fragmentSource) {
		// IRIS ProgramBuilder.java:33-68
		// Note: reservedTextureUnits parameter will be added with samplers system

		ShaderCompiler vertex;
		ShaderCompiler geometry;
		ShaderCompiler fragment;

		vertex = buildShader(ShaderType.VERTEX, name + ".vsh", vertexSource);

		if (geometrySource != null) {
			geometry = buildShader(ShaderType.GEOMETRY, name + ".gsh", geometrySource);
		} else {
			geometry = null;
		}

		fragment = buildShader(ShaderType.FRAGMENT, name + ".fsh", fragmentSource);

		int programId;

		if (geometry != null) {
			programId = ProgramLinker.create(name, vertex, geometry, fragment);
		} else {
			programId = ProgramLinker.create(name, vertex, fragment);
		}

		vertex.delete();

		if (geometry != null) {
			geometry.delete();
		}

		fragment.delete();

		return new ProgramBuilder(name, programId);
	}

	public static ProgramBuilder beginCompute(String name, @Nullable String source) {
		// IRIS ProgramBuilder.java:70-84
		// Note: Compute shader support check will be added with IrisRenderSystem

		ShaderCompiler compute = buildShader(ShaderType.COMPUTE, name + ".csh", source);

		int programId = ProgramLinker.create(name, compute);

		compute.delete();

		return new ProgramBuilder(name, programId);
	}

	private static ShaderCompiler buildShader(ShaderType shaderType, String name, @Nullable String source) {
		// IRIS ProgramBuilder.java:86-94
		try {
			return new ShaderCompiler(shaderType, name, source);
		} catch (ShaderCompileException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new RuntimeException("Failed to compile " + shaderType + " shader for program " + name, e);
		}
	}

	public void bindAttributeLocation(int index, String name) {
		// IRIS ProgramBuilder.java:96-98
		// Note: Will use IrisRenderSystem.bindAttributeLocation when available
		// For now, attribute locations are bound during linking in ProgramLinker
	}

	public Program build() {
		// IRIS ProgramBuilder.java:100-102
		// Note: Will pass uniforms, samplers, images when those systems are implemented
		return new Program(program);
	}

	// Note: Sampler and image methods from IRIS ProgramBuilder.java:109-155 will be added in Steps 26-27
	// when the uniform/sampler/image systems are implemented
}
