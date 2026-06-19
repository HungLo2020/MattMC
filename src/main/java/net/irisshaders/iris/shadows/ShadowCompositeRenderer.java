package net.irisshaders.iris.shadows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BlendModeStorage;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.framebuffer.ViewportData;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.samplers.IrisImages;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import net.irisshaders.iris.shaderpack.programs.ComputeSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBlendFactor;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureParameterValue;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;

public class ShadowCompositeRenderer {
	private final ShadowRenderTargets renderTargets;

	private final ImmutableList<Pass> passes;
	private final TextureAccess noiseTexture;
	private final Object2ObjectMap<String, TextureAccess> customTextureIds;
	private final ImmutableSet<Integer> flippedAtLeastOnceFinal;
	private final CustomUniforms customUniforms;
	private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
	private final WorldRenderingPipeline pipeline;
	private final Set<GlImage> irisCustomImages;

	public ShadowCompositeRenderer(WorldRenderingPipeline pipeline, PackDirectives packDirectives, ProgramSource[] sources, ComputeSource[][] computes, ShadowRenderTargets renderTargets, ShaderStorageBufferHolder holder,
								   TextureAccess noiseTexture, FrameUpdateNotifier updateNotifier,
								   Object2ObjectMap<String, TextureAccess> customTextureIds, Set<GlImage> customImages, ImmutableMap<Integer, Boolean> explicitPreFlips, Object2ObjectMap<String, TextureAccess> irisCustomTextures, CustomUniforms customUniforms) {
		this.pipeline = pipeline;
		this.noiseTexture = noiseTexture;
		this.renderTargets = renderTargets;
		this.customTextureIds = customTextureIds;
		this.irisCustomTextures = irisCustomTextures;
		this.irisCustomImages = customImages;
		this.customUniforms = customUniforms;

		final PackRenderTargetDirectives renderTargetDirectives = packDirectives.getRenderTargetDirectives();
		final Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings =
			renderTargetDirectives.getRenderTargetSettings();

		final ImmutableList.Builder<Pass> passes = ImmutableList.builder();
		final ImmutableSet.Builder<Integer> flippedAtLeastOnce = new ImmutableSet.Builder<>();

		explicitPreFlips.forEach((buffer, shouldFlip) -> {
			if (shouldFlip) {
				renderTargets.flip(buffer);
				// NB: Flipping deferred_pre or composite_pre does NOT cause the "flippedAtLeastOnce" flag to trigger
			}
		});

		for (int i = 0, sourcesLength = sources.length; i < sourcesLength; i++) {
			ProgramSource source = sources[i];

			ImmutableSet<Integer> flipped = renderTargets.snapshot();
			ImmutableSet<Integer> flippedAtLeastOnceSnapshot = flippedAtLeastOnce.build();

			if (source == null || !source.isValid()) {
				if (computes.length > 0 && computes[i] != null) {
					ComputeOnlyPass pass = new ComputeOnlyPass();
					pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, renderTargets, holder);
					passes.add(pass);
				}
				continue;
			}

			Pass pass = new Pass();
			ProgramDirectives directives = source.getDirectives();

			pass.name = source.getName();
			pass.program = createProgram(source, flipped, flippedAtLeastOnceSnapshot, renderTargets);
			pass.blendModeOverride = source.getDirectives().getBlendModeOverride().orElse(null);
			if (computes.length > 0) {
				pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, renderTargets, holder);
			} else {
				pass.computes = new ComputeProgram[0];
			}
			int[] drawBuffers = source.getDirectives().hasUnknownDrawBuffers() ? new int[]{0, 1} : source.getDirectives().getDrawBuffers();

			GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(flipped, drawBuffers);

			pass.stageReadsFromAlt = flipped;
			pass.framebuffer = framebuffer;
			pass.viewWidth = renderTargets.getResolution();
			pass.viewHeight = renderTargets.getResolution();
			pass.viewportScale = directives.getViewportScale();
			pass.mipmappedBuffers = directives.getMipmappedBuffers();
			pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;

			passes.add(pass);

			ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();

			// Flip the buffers that this shader wrote to
			for (int buffer : drawBuffers) {
				// compare with boxed Boolean objects to avoid NPEs
				if (explicitFlips.get(buffer) == Boolean.FALSE) {
					continue;
				}

				renderTargets.flip(buffer);
				flippedAtLeastOnce.add(buffer);
			}

			explicitFlips.forEach((buffer, shouldFlip) -> {
				if (shouldFlip) {
					renderTargets.flip(buffer);
					flippedAtLeastOnce.add(buffer);
				}
			});
		}

		this.passes = passes.build();
		this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();

		var ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.bindReadFramebuffer(ctx, 0);
	}

	private static void setupMipmapping(net.irisshaders.iris.targets.RenderTarget target, boolean readFromAlt) {
		int texture = readFromAlt ? target.getAltTexture() : target.getMainTexture();

		// TODO: Only generate the mipmap if a valid mipmap hasn't been generated or if we've written to the buffer
		// (since the last mipmap was generated)
		//
		// NB: We leave mipmapping enabled even if the buffer is written to again, this appears to match the
		// behavior of ShadersMod/OptiFine, however I'm not sure if it's desired behavior. It's possible that a
		// program could use mipmapped sampling with a stale mipmap, which probably isn't great. However, the
		// sampling mode is always reset between frames, so this only persists after the first program to use
		// mipmapping on this buffer.
		//
		// Also note that this only applies to one of the two buffers in a render target buffer pair - making it
		// unlikely that this issue occurs in practice with most shader packs.
		IrisRenderSystem.generateMipmaps(texture);
		VulkanicTextureParameterValue minFilter = target.getInternalFormat().getPixelFormat().isInteger()
			? VulkanicTextureParameterValue.NEAREST_MIPMAP_NEAREST
			: VulkanicTextureParameterValue.LINEAR_MIPMAP_LINEAR;
		IrisRenderSystem.texParameteri(texture, VulkanicTextureParameterName.MIN_FILTER, minFilter);
	}

	private static void resetRenderTarget(RenderTarget target) {
		// Resets the sampling mode of the given render target and then unbinds it to prevent accidental sampling of it
		// elsewhere.

		VulkanicTextureParameterValue filter = VulkanicTextureParameterValue.LINEAR;
		if (target.getInternalFormat().getPixelFormat().isInteger()) {
			filter = VulkanicTextureParameterValue.NEAREST;
		}

		IrisRenderSystem.texParameteri(target.getMainTexture(), VulkanicTextureParameterName.MIN_FILTER, filter);
		IrisRenderSystem.texParameteri(target.getAltTexture(), VulkanicTextureParameterName.MIN_FILTER, filter);
	}

	public ImmutableSet<Integer> getFlippedAtLeastOnceFinal() {
		return this.flippedAtLeastOnceFinal;
	}

	public void renderAll() {
		GpuBuffer indices = VulkanicAPI.getSequentialBuffer(VertexFormat.Mode.QUADS).getBuffer(6);
		VertexFormat.IndexType type = VulkanicAPI.getSequentialBuffer(VertexFormat.Mode.QUADS).type();

		for (Pass renderPass : passes) {
			boolean ranCompute = false;
			for (ComputeProgram computeProgram : renderPass.computes) {
				if (computeProgram != null) {
					ranCompute = true;
					computeProgram.use();
					this.customUniforms.push(computeProgram);
					net.blaze3d.pipeline.RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
					computeProgram.dispatch(main.width, main.height);
				}
			}

			if (ranCompute) {
				IrisRenderSystem.memoryBarrierComputeWritesVisibleToTextureSampling();
			}

			Program.unbind();

			if (renderPass instanceof ComputeOnlyPass) {
				continue;
			}

			if (!renderPass.mipmappedBuffers.isEmpty()) {
				net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(0);

				for (int index : renderPass.mipmappedBuffers) {
					setupMipmapping(renderTargets.get(index), renderPass.stageReadsFromAlt.contains(index));
				}
			}

			renderPass.ensurePipelineState();

			try (RenderPass pass = renderPass.createRenderPass(() -> "Shadow composites")) {
				pass.setPipeline(CompositeRenderer.COMPOSITE_PIPELINE);
				VulkanicAPI.bindDefaultUniforms(pass);
				pass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad());
				pass.setIndexBuffer(indices, type);
				pass.iris$setCustomPass(renderPass);

				float scaledWidth = renderTargets.getResolution() * renderPass.viewportScale.scale();
				float scaledHeight = renderTargets.getResolution() * renderPass.viewportScale.scale();
				int beginWidth = (int) (renderTargets.getResolution() * renderPass.viewportScale.viewportX());
				int beginHeight = (int) (renderTargets.getResolution() * renderPass.viewportScale.viewportY());
				var ctx = VulkanicAPI.getCommandContext();
				VulkanicAPI.setDynamicViewport(ctx, beginWidth, beginHeight, (int) scaledWidth, (int) scaledHeight);

				renderPass.program.use();

				this.customUniforms.push(renderPass.program);

				pass.drawIndexed(0, 0, 6, 1);
			}
		}

		// Make sure to reset the viewport to how it was before... Otherwise weird issues could occur.
		ProgramUniforms.clearActiveUniforms();
		net.irisshaders.iris.gl.IrisRenderSystem.useProgram(0);

		// TODO IMS: Apparantly we are not supposed to do this for shadowcomp...
		/*
		for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
			// Reset mipmapping states at the end of the frame.
			if (renderTargets.get(i) != null) {
				resetRenderTarget(renderTargets.get(i));
			}
		}
		 */

		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(0);
	}

	// TODO: Don't just copy this from DeferredWorldRenderingPipeline
	private Program createProgram(ProgramSource source, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
								  ShadowRenderTargets targets) {
		// TODO: Properly handle empty shaders
		Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(
			source.getName(),
			source.getVertexSource().orElseThrow(NullPointerException::new),
			source.getGeometrySource().orElse(null),
			source.getFragmentSource().orElseThrow(NullPointerException::new), TextureStage.SHADOWCOMP, pipeline.getTextureMap());
		String vertex = transformed.get(PatchShaderType.VERTEX);
		String geometry = transformed.get(PatchShaderType.GEOMETRY);
		String fragment = transformed.get(PatchShaderType.FRAGMENT);
		ShaderPrinter.printProgram(source.getName()).addSources(transformed).print();

		Objects.requireNonNull(flipped);
		ProgramBuilder builder;

		try {
			builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment,
				IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
		} catch (RuntimeException e) {
			// TODO: Better error handling
			throw new RuntimeException("Shader compilation failed for shadow composite " + source.getName() + "!", e);
		}

		ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

		CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
		this.customUniforms.assignTo(builder);

		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
		IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, irisCustomTextures);

		IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, targets, flipped, pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
		IrisImages.addShadowColorImages(builder, targets, flipped);
		IrisImages.addCustomImages(builder, irisCustomImages);
		IrisSamplers.addCustomImages(builder, irisCustomImages);
		Program build = builder.build();
		this.customUniforms.mapholderToPass(builder, build);

		return build;
	}

	private ComputeProgram[] createComputes(ComputeSource[] sources, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
											ShadowRenderTargets targets, ShaderStorageBufferHolder holder) {
		ComputeProgram[] programs = new ComputeProgram[sources.length];
		for (int i = 0; i < programs.length; i++) {
			ComputeSource source = sources[i];
			if (source == null || source.getSource().isEmpty()) {
			} else {
				Objects.requireNonNull(flipped);
				ProgramBuilder builder;

				try {
					String transformed = TransformPatcher.patchCompute(source.getName(), source.getSource().orElse(null), TextureStage.SHADOWCOMP, pipeline.getTextureMap());

					ShaderPrinter.printProgram(source.getName()).addSource(PatchShaderType.COMPUTE, transformed).print();

					builder = ProgramBuilder.beginComputeIfSupported(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
				} catch (RuntimeException e) {
					// TODO: Better error handling
					throw new RuntimeException("Shader compilation failed for shadowcomp compute " + source.getName() + "!", e);
				}

				if (builder == null) {
					continue;
				}

				ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

				CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
				this.customUniforms.assignTo(builder);
				IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
				IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, irisCustomTextures);

				IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, targets, flipped, pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
				IrisImages.addShadowColorImages(builder, targets, flipped);

				IrisImages.addCustomImages(builder, irisCustomImages);
				IrisSamplers.addCustomImages(builder, irisCustomImages);
				programs[i] = builder.buildCompute();

				this.customUniforms.mapholderToPass(builder, programs[i]);


				programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups(), FilledIndirectPointer.basedOff(holder, source.getIndirectPointer()));
			}
		}

		return programs;
	}

	public void destroy() {
		for (Pass renderPass : passes) {
			renderPass.destroy();
		}
	}

	private static class Pass implements CustomPass {
		String name;
		Program program;
		BlendModeOverride blendModeOverride;
		GlFramebuffer framebuffer;
		int viewWidth;
		int viewHeight;
		PipelineDescriptor pipelineDescriptor;
		PipelineHandle pipelineHandle;
		final java.util.Map<PipelineDescriptor.ResourceLayout, PipelineHandle> pipelineLayoutVariants = new java.util.HashMap<>();
		ImmutableSet<Integer> flippedAtLeastOnce;
		ImmutableSet<Integer> stageReadsFromAlt;
		ImmutableSet<Integer> mipmappedBuffers;
		ViewportData viewportScale;
		ComputeProgram[] computes;

		private void closePipelineVariants() {
			for (PipelineHandle pipelineVariant : this.pipelineLayoutVariants.values()) {
				pipelineVariant.close();
			}
			this.pipelineLayoutVariants.clear();
		}

		private net.vulkanic.VulkanicRenderTargetDescriptor renderTargetDescriptor() {
			return this.framebuffer.createRenderTargetDescriptor(() -> this.name, this.viewWidth, this.viewHeight);
		}

		private PipelineHandle createCompatiblePipeline(PipelineDescriptor descriptor) {
			return VulkanicAPI.createPipeline(descriptor, this.framebuffer.getId());
		}

		private RenderPass createRenderPass(Supplier<String> label) {
			return VulkanicAPI.createRenderPass(label, this.framebuffer.getId(), this.framebuffer.hasDepthAttachment());
		}

		private void ensurePipelineState() {
			var ctx = VulkanicAPI.getCommandContext();
			if (ctx.isImmediate()) {
				return;
			}

			if (this.pipelineHandle != null && this.pipelineHandle.isValid() && this.pipelineDescriptor != null) {
				return;
			}

			PipelineDescriptor descriptor = VulkanicAPI.createLiveProgramPipelineDescriptor(
				ctx,
				PipelineDescriptor.fromRenderPipeline(CompositeRenderer.COMPOSITE_PIPELINE),
				this.program.getProgramId()
			);
			descriptor = applyBlendOverride(descriptor, this.blendModeOverride);

			this.closePipelineVariants();
			if (this.pipelineHandle != null) {
				this.pipelineHandle.close();
			}

			this.pipelineDescriptor = descriptor;
			this.pipelineHandle = this.createCompatiblePipeline(descriptor);
		}

		protected void destroy() {
			this.closePipelineVariants();
			if (this.pipelineHandle != null) {
				this.pipelineHandle.close();
				this.pipelineHandle = null;
			}
			this.program.destroy();
			for (ComputeProgram compute : this.computes) {
				if (compute != null) {
					compute.destroy();
				}
			}
		}

		@Override
		public void setupState() {
			if (blendModeOverride != null) {
				blendModeOverride.apply();
			} else {
				BlendModeStorage.restoreBlend();
				BlendModeStorage.setBlendEnabled(false);
			}
		}

		@Override
		public void bindRenderPassResources(RenderPass renderPass) {
			this.program.bindRenderPassResources(renderPass);
		}

		@Override
		public Program program() {
			return this.program;
		}

		@Override
		public PipelineDescriptor pipelineDescriptor() {
			return this.pipelineDescriptor;
		}

		@Override
		public PipelineHandle pipelineHandle() {
			return this.pipelineHandle;
		}

		@Override
		public PipelineHandle pipelineHandle(PipelineDescriptor descriptor) {
			if (descriptor == null || this.pipelineDescriptor == null || this.pipelineHandle == null) {
				return this.pipelineHandle;
			}

			if (descriptor.getResourceLayout().equals(this.pipelineDescriptor.getResourceLayout())) {
				return this.pipelineHandle;
			}

			PipelineHandle pipelineVariant = this.pipelineLayoutVariants.get(descriptor.getResourceLayout());
			if (pipelineVariant != null && pipelineVariant.isValid()) {
				return pipelineVariant;
			}

			if (pipelineVariant != null) {
				pipelineVariant.close();
			}

			PipelineHandle createdVariant = this.createCompatiblePipeline(descriptor);
			this.pipelineLayoutVariants.put(descriptor.getResourceLayout(), createdVariant);
			return createdVariant;
		}

		private static PipelineDescriptor applyBlendOverride(PipelineDescriptor descriptor, BlendModeOverride blendModeOverride) {
			if (blendModeOverride == null || blendModeOverride.blendMode() == null) {
				return descriptor;
			}

			net.irisshaders.iris.gl.blending.BlendMode blendMode = blendModeOverride.blendMode();
			java.util.Optional<net.blaze3d.platform.SourceFactor> sourceColor = VulkanicBlendFactor.fromLegacyGlConstant(blendMode.srcRgb())
				.map(factor -> net.blaze3d.platform.SourceFactor.valueOf(factor.name()));
			java.util.Optional<net.blaze3d.platform.DestFactor> destColor = VulkanicBlendFactor.fromLegacyGlConstant(blendMode.dstRgb())
				.map(factor -> net.blaze3d.platform.DestFactor.valueOf(factor.name()));
			java.util.Optional<net.blaze3d.platform.SourceFactor> sourceAlpha = VulkanicBlendFactor.fromLegacyGlConstant(blendMode.srcAlpha())
				.map(factor -> net.blaze3d.platform.SourceFactor.valueOf(factor.name()));
			java.util.Optional<net.blaze3d.platform.DestFactor> destAlpha = VulkanicBlendFactor.fromLegacyGlConstant(blendMode.dstAlpha())
				.map(factor -> net.blaze3d.platform.DestFactor.valueOf(factor.name()));
			if (sourceColor.isEmpty() || destColor.isEmpty() || sourceAlpha.isEmpty() || destAlpha.isEmpty()) {
				return descriptor;
			}

			PipelineDescriptor.PortableState portableState = descriptor.getPortableState();
			PipelineDescriptor.PortableState blendPortableState = new PipelineDescriptor.PortableState(
				portableState.location(),
				portableState.vertexShader(),
				portableState.fragmentShader(),
				portableState.shaderDefineValues(),
				portableState.shaderDefineFlags(),
				portableState.samplers(),
				portableState.uniforms(),
				java.util.Optional.of(new PipelineDescriptor.BlendState(
					sourceColor.get(),
					destColor.get(),
					sourceAlpha.get(),
					destAlpha.get()
				)),
				portableState.depthTestFunction(),
				portableState.polygonMode(),
				portableState.cull(),
				portableState.writeColor(),
				portableState.writeAlpha(),
				portableState.writeDepth(),
				portableState.colorLogic(),
				portableState.vertexFormat(),
				portableState.vertexFormatMode(),
				portableState.depthBiasScaleFactor(),
				portableState.depthBiasConstant()
			);

			return PipelineDescriptor.fromPortableStateAndSpirvModules(blendPortableState, descriptor.getSpirvModules())
				.withPushConstantRanges(descriptor.getPushConstantRanges())
				.withResourceLayout(descriptor.getResourceLayout());
		}
	}

	private static class ComputeOnlyPass extends Pass {
		@Override
		protected void destroy() {
			for (ComputeProgram compute : this.computes) {
				if (compute != null) {
					compute.destroy();
				}
			}
		}
	}
}
