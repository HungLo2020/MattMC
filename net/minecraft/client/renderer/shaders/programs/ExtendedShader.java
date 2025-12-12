// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.programs;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.shaders.framebuffer.GlFramebuffer;
import net.minecraft.client.renderer.shaders.gl.IrisRenderSystem;
import net.minecraft.client.renderer.shaders.gl.blending.AlphaTest;
import net.minecraft.client.renderer.shaders.gl.blending.BlendModeOverride;
import net.minecraft.client.renderer.shaders.gl.blending.BufferBlendOverride;
import net.minecraft.client.renderer.shaders.gl.blending.DepthColorStorage;
import net.minecraft.client.renderer.shaders.gl.image.ImageHolder;
import net.minecraft.client.renderer.shaders.gl.sampler.SamplerHolder;
import net.minecraft.client.renderer.shaders.pipeline.ShaderPackPipeline;
import net.minecraft.client.renderer.shaders.program.ProgramImages;
import net.minecraft.client.renderer.shaders.program.ProgramSamplers;
import net.minecraft.client.renderer.shaders.program.ProgramUniforms;
import net.minecraft.client.renderer.shaders.samplers.IrisSamplers;
import net.minecraft.client.renderer.shaders.uniform.DynamicLocationalUniformHolder;
import net.minecraft.client.renderer.shaders.uniform.custom.CustomUniforms;
import net.minecraft.client.renderer.shaders.uniform.providers.CapturedRenderingState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL46C;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Extended shader program that binds Iris-compatible uniforms, samplers, and images.
 * 
 * Based on IRIS's ExtendedShader class
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/programs/ExtendedShader.java
 */
public class ExtendedShader extends GlProgram implements IrisProgram {
	private static final Matrix4f identity = new Matrix4f().identity();
	private static final Logger LOGGER = LogUtils.getLogger();
	private static ExtendedShader lastApplied;

	private final boolean intensitySwizzle;
	private final List<BufferBlendOverride> bufferBlendOverrides;
	private final boolean hasOverrides;
	private final int modelViewInverse;
	private final int projectionInverse;
	private final Matrix3f normalMatrix = new Matrix3f();
	private final CustomUniforms customUniforms;
	private final ShaderPackPipeline parent;
	private final ProgramUniforms uniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;
	private final GlFramebuffer writingToBeforeTranslucent;
	private final GlFramebuffer writingToAfterTranslucent;
	private final BlendModeOverride blendModeOverride;
	private final float alphaTest;
	private final boolean usesTessellation;
	private final Matrix4f tempMatrix4f = new Matrix4f();
	private final Matrix3f tempMatrix3f = new Matrix3f();
	private final float[] tempFloats = new float[16];
	private final float[] tempFloats2 = new float[9];
	private final int normalMat;
	private boolean hasUV;
	private boolean isSetup;

	public ExtendedShader(int programId, String name, VertexFormat vertexFormat, boolean usesTessellation,
						  GlFramebuffer writingToBeforeTranslucent, GlFramebuffer writingToAfterTranslucent,
						  BlendModeOverride blendModeOverride, AlphaTest alphaTest,
						  Consumer<DynamicLocationalUniformHolder> uniformCreator, 
						  BiConsumer<SamplerHolder, ImageHolder> samplerCreator, boolean isIntensity,
						  ShaderPackPipeline parent, @Nullable List<BufferBlendOverride> bufferBlendOverrides, 
						  CustomUniforms customUniforms) throws IOException {
		super(programId, name);

		// Name the program for debugging
		try {
			GL43C.glObjectLabel(GL43C.GL_PROGRAM, programId, name);
		} catch (Exception e) {
			// OpenGL debug naming not supported, ignore
		}

		List<RenderPipeline.UniformDescription> uniformList = new ArrayList<>();
		List<String> samplerList = new ArrayList<>();
		uniformList.add(new RenderPipeline.UniformDescription("DynamicTransforms", UniformType.UNIFORM_BUFFER));
		uniformList.add(new RenderPipeline.UniformDescription("CloudInfo", UniformType.UNIFORM_BUFFER));
		uniformList.add(new RenderPipeline.UniformDescription("CloudFaces", UniformType.TEXEL_BUFFER, TextureFormat.RED8I));
		uniformList.add(new RenderPipeline.UniformDescription("Projection", UniformType.UNIFORM_BUFFER));
		uniformList.add(new RenderPipeline.UniformDescription("Fog", UniformType.UNIFORM_BUFFER));
		uniformList.add(new RenderPipeline.UniformDescription("Globals", UniformType.UNIFORM_BUFFER));

		if (vertexFormat.contains(VertexFormatElement.UV)) {
			this.hasUV = true;
			samplerList.add("Sampler0");
		}

		if (vertexFormat.contains(VertexFormatElement.UV1)) {
			samplerList.add("Sampler1");
		}

		if (vertexFormat.contains(VertexFormatElement.UV2)) {
			samplerList.add("Sampler2");
		}

		super.setupUniforms(uniformList, samplerList);

		ProgramUniforms.Builder uniformBuilder = ProgramUniforms.builder(name, programId);
		ProgramSamplers.Builder samplerBuilder = ProgramSamplers.builder(programId, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS);
		uniformCreator.accept(uniformBuilder);
		this.normalMat = GlStateManager._glGetUniformLocation(programId, "iris_NormalMat");
		ProgramImages.Builder imageBuilder = ProgramImages.builder(programId);
		samplerCreator.accept(samplerBuilder, imageBuilder);
		customUniforms.mapholderToPass(uniformBuilder, this);
		this.usesTessellation = usesTessellation;

		uniforms = uniformBuilder.buildUniforms();
		this.customUniforms = customUniforms;
		samplers = samplerBuilder.build();
		images = imageBuilder.build();
		this.writingToBeforeTranslucent = writingToBeforeTranslucent;
		this.writingToAfterTranslucent = writingToAfterTranslucent;
		this.blendModeOverride = blendModeOverride;
		this.bufferBlendOverrides = bufferBlendOverrides;
		this.hasOverrides = bufferBlendOverrides != null && !bufferBlendOverrides.isEmpty();
		this.alphaTest = alphaTest.reference();
		this.parent = parent;

		this.modelViewInverse = GlStateManager._glGetUniformLocation(programId, "iris_ModelViewMatInverse");
		this.projectionInverse = GlStateManager._glGetUniformLocation(programId, "iris_ProjMatInverse");

		this.intensitySwizzle = isIntensity;
	}

	public boolean isIntensitySwizzle() {
		return intensitySwizzle;
	}

	@Override
	public void iris$clearState() {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();

		if (this.blendModeOverride != null || hasOverrides) {
			BlendModeOverride.restore();
		}

		isSetup = false;
	}

	private float[] tempF = new float[9];

	@Override
	public void iris$setupState() {
		isSetup = true;
		DepthColorStorage.unlockDepthColor();

		CapturedRenderingState.INSTANCE.setCurrentAlphaTest(alphaTest);
		GlStateManager._glUseProgram(getProgramId());

		if (modelViewInverse > -1) {
			IrisRenderSystem.uniformMatrix4fv(modelViewInverse, false, 
				RenderSystem.getModelViewMatrix().invert(tempMatrix4f).get(tempFloats));
		}

		if (normalMat > -1) {
			tempF = RenderSystem.getModelViewMatrix().invert(tempMatrix4f).transpose3x3(normalMatrix).get(tempF);
			IrisRenderSystem.uniformMatrix3fv(normalMat, false, tempF);
		}

		if (projectionInverse > -1) {
			IrisRenderSystem.uniformMatrix4fv(projectionInverse, false, 
				CapturedRenderingState.INSTANCE.getGbufferProjection().invert(tempMatrix4f).get(tempFloats));
		}

		samplers.update();
		uniforms.update();

		customUniforms.push(this);

		images.update();

		BlendModeOverride.restore();

		if (this.blendModeOverride != null) {
			this.blendModeOverride.apply();
		}

		if (hasOverrides) {
			bufferBlendOverrides.forEach(BufferBlendOverride::apply);
		}

		// NOTE: G-buffer framebuffer binding is disabled because composite/final passes
		// are not yet implemented. Without those passes, rendering to G-buffers would
		// result in nothing visible on screen.
		// 
		// For now, let shaders render directly to Minecraft's main render target.
		// This provides immediate visual effects from shader packs (gbuffers coloring,
		// lighting modifications) without full deferred rendering pipeline.
		//
		// TODO: Enable G-buffer binding once CompositeRenderer and FinalPassRenderer
		// are fully implemented with proper passes.
		// 
		// When enabled, this code would bind the appropriate framebuffer:
		// if (parent != null) {
		//     GlFramebuffer beforeTranslucent = writingToBeforeTranslucent != null ? 
		//         writingToBeforeTranslucent : parent.getWritingToBeforeTranslucent();
		//     GlFramebuffer afterTranslucent = writingToAfterTranslucent != null ? 
		//         writingToAfterTranslucent : parent.getWritingToAfterTranslucent();
		//     
		//     if (parent.isBeforeTranslucent() && beforeTranslucent != null) {
		//         beforeTranslucent.bind();
		//     } else if (afterTranslucent != null) {
		//         afterTranslucent.bind();
		//     }
		// }
	}

	public boolean hasActiveImages() {
		return images.getActiveImages() > 0;
	}

	@Override
	public int iris$getBlockIndex(int program, CharSequence uniformBlockName) {
		return GL46C.glGetUniformBlockIndex(program, "iris_" + uniformBlockName);
	}

	@Override
	public boolean iris$isSetUp() {
		return isSetup;
	}

	public ProgramUniforms getIrisUniforms() {
		return uniforms;
	}

	public ProgramSamplers getSamplers() {
		return samplers;
	}

	public ProgramImages getImages() {
		return images;
	}

	public String getShaderName() {
		return getDebugLabel();
	}
}
