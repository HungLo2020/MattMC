package net.irisshaders.iris.pipeline;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.blaze3d.buffers.GpuBuffer;
import net.logging.LogUtils;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.vertex.DefaultVertexFormat;
import net.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.GLDebug;
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
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.gl.shader.ShaderCompileException;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.pathways.CenterDepthSampler;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
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
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.BufferFlipper;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBlendFactor;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureParameterValue;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;

public class CompositeRenderer {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final RenderPipeline COMPOSITE_PIPELINE = RenderPipeline.builder()
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.withDepthWrite(false)
		.withColorWrite(true)
		.withoutBlend()
		.withLocation(ResourceLocation.fromNamespaceAndPath("iris", "composite")).withVertexShader("core/screenquad").withFragmentShader("core/blit_screen")
		.withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
		.build();

	private final RenderTargets renderTargets;

	private final ImmutableList<Pass> passes;
	private final TextureAccess noiseTexture;
	private final CenterDepthSampler centerDepthSampler;
	private final Object2ObjectMap<String, TextureAccess> customTextureIds;
	private final ImmutableSet<Integer> flippedAtLeastOnceFinal;
	private final CustomUniforms customUniforms;
	private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
	private final Set<GlImage> customImages;
	private final TextureStage textureStage;
	private final WorldRenderingPipeline pipeline;
	private final CompositePass compositePass;

	public CompositeRenderer(WorldRenderingPipeline pipeline, CompositePass compositePass, PackDirectives packDirectives, ProgramSource[] sources, ComputeSource[][] computes, RenderTargets renderTargets, ShaderStorageBufferHolder holder,
							 TextureAccess noiseTexture, FrameUpdateNotifier updateNotifier,
							 CenterDepthSampler centerDepthSampler, BufferFlipper bufferFlipper,
							 Supplier<ShadowRenderTargets> shadowTargetsSupplier, TextureStage textureStage,
							 Object2ObjectMap<String, TextureAccess> customTextureIds, Object2ObjectMap<String, TextureAccess> irisCustomTextures, Set<GlImage> customImages, ImmutableMap<Integer, Boolean> explicitPreFlips,
							 CustomUniforms customUniforms) {
		this.pipeline = pipeline;
		this.compositePass = compositePass;
		this.noiseTexture = noiseTexture;
		this.centerDepthSampler = centerDepthSampler;
		this.renderTargets = renderTargets;
		this.customTextureIds = customTextureIds;
		this.customUniforms = customUniforms;
		this.irisCustomTextures = irisCustomTextures;
		this.customImages = customImages;
		this.textureStage = textureStage;

		final PackRenderTargetDirectives renderTargetDirectives = packDirectives.getRenderTargetDirectives();
		final Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings =
			renderTargetDirectives.getRenderTargetSettings();

		final ImmutableList.Builder<Pass> passes = ImmutableList.builder();
		final ImmutableSet.Builder<Integer> flippedAtLeastOnce = new ImmutableSet.Builder<>();

		explicitPreFlips.forEach((buffer, shouldFlip) -> {
			if (shouldFlip) {
				bufferFlipper.flip(buffer);
				// NB: Flipping deferred_pre or composite_pre does NOT cause the "flippedAtLeastOnce" flag to trigger
			}
		});

		for (int i = 0; i < sources.length; i++) {
			ProgramSource source = sources[i];

			ImmutableSet<Integer> flipped = bufferFlipper.snapshot();
			ImmutableSet<Integer> flippedAtLeastOnceSnapshot = flippedAtLeastOnce.build();

			if (source == null || !source.isValid()) {
				if (computes.length != 0 && computes[i] != null && computes[i].length > 0) {
					ComputeOnlyPass pass = new ComputeOnlyPass();
					pass.name = computes[i].length > 0 ? Arrays.stream(computes[i]).filter(Objects::nonNull).findFirst().map(ComputeSource::getName).orElse("unknown") : "unknown";
					pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier, holder);
					passes.add(pass);
				}
				continue;
			}

			Pass pass = new Pass();
			ProgramDirectives directives = source.getDirectives();

			pass.name = source.getName();
			pass.program = createProgram(source, flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier);
			pass.blendModeOverride = source.getDirectives().getBlendModeOverride().orElse(null);
			if (computes.length != 0) {
				pass.computes = createComputes(computes[i], flipped, flippedAtLeastOnceSnapshot, shadowTargetsSupplier, holder);
			} else {
				pass.computes = new ComputeProgram[0];
			}
			int[] drawBuffers = directives.getDrawBuffers();


			int passWidth = 0, passHeight = 0;
			// Flip the buffers that this shader wrote to, and set pass width and height
			ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();

			GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(flipped, drawBuffers);

			for (int buffer : drawBuffers) {
				RenderTarget target = renderTargets.get(buffer);
				if ((passWidth > 0 && passWidth != target.getWidth()) || (passHeight > 0 && passHeight != target.getHeight())) {
					throw new IllegalStateException("Pass sizes must match for drawbuffers " + Arrays.toString(drawBuffers) + "\nOriginal width: " + passWidth + " New width: " + target.getWidth() + " Original height: " + passHeight + " New height: " + target.getHeight());
				}
				passWidth = target.getWidth();
				passHeight = target.getHeight();

				// compare with boxed Boolean objects to avoid NPEs
				if (explicitFlips.get(buffer) == Boolean.FALSE) {
					continue;
				}

				bufferFlipper.flip(buffer);
				flippedAtLeastOnce.add(buffer);
			}

			explicitFlips.forEach((buffer, shouldFlip) -> {
				if (shouldFlip) {
					bufferFlipper.flip(buffer);
					flippedAtLeastOnce.add(buffer);
				}
			});

			pass.drawBuffers = directives.getDrawBuffers();
			pass.viewWidth = passWidth;
			pass.viewHeight = passHeight;
			pass.stageReadsFromAlt = flipped;
			pass.framebuffer = framebuffer;
			pass.viewportScale = directives.getViewportScale();
			pass.mipmappedBuffers = directives.getMipmappedBuffers();
			pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;

			passes.add(pass);
		}

		this.passes = passes.build();
		this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();

		VulkanicAPI.bindReadFramebuffer(VulkanicAPI.getCommandContext(), 0);
	}

	private boolean hasComputes(ComputeSource[][] computes) {
		boolean hasCompute = false;

		for (int i = 0; i < computes.length; i++) {
			if (computes[i].length > 0) {
				for (int j = 0; j < computes[i].length; j++) {
					if (computes[i][j] != null) {
						hasCompute = true;
						break;
					}
				}
			}
		}

		return hasCompute;
	}

	private static void setupMipmapping(net.irisshaders.iris.targets.RenderTarget target, boolean readFromAlt) {
		if (target == null) return;

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

		VulkanicTextureParameterValue filter = VulkanicTextureParameterValue.LINEAR_MIPMAP_LINEAR;
		if (target.getInternalFormat().getPixelFormat().isInteger()) {
			filter = VulkanicTextureParameterValue.NEAREST_MIPMAP_NEAREST;
		}

		IrisRenderSystem.texParameteri(texture, VulkanicTextureParameterName.MIN_FILTER, filter);
	}

	public ImmutableSet<Integer> getFlippedAtLeastOnceFinal() {
		return this.flippedAtLeastOnceFinal;
	}

	public void recalculateSizes() {
		for (Pass pass : passes) {
			if (pass instanceof ComputeOnlyPass) {
				continue;
			}
			int passWidth = 0, passHeight = 0;
			for (int buffer : pass.drawBuffers) {
				RenderTarget target = renderTargets.get(buffer);
				if ((passWidth > 0 && passWidth != target.getWidth()) || (passHeight > 0 && passHeight != target.getHeight())) {
					throw new IllegalStateException("Pass widths must match");
				}
				passWidth = target.getWidth();
				passHeight = target.getHeight();
			}
			renderTargets.destroyFramebuffer(pass.framebuffer);
			pass.framebuffer = renderTargets.createColorFramebuffer(pass.stageReadsFromAlt, pass.drawBuffers);
			pass.viewWidth = passWidth;
			pass.viewHeight = passHeight;
		}
	}

	public void renderAll() {
		ImmediateState.temporarilyIgnorePass = true;

		GLDebug.pushGroup(20 + compositePass.ordinal(), compositePass.name().toLowerCase(Locale.ROOT));

		net.blaze3d.pipeline.RenderTarget main = Minecraft.getInstance().getMainRenderTarget();

		GpuBuffer indices = VulkanicAPI.getSequentialBuffer(VertexFormat.Mode.QUADS).getBuffer(6);
		VertexFormat.IndexType type = VulkanicAPI.getSequentialBuffer(VertexFormat.Mode.QUADS).type();

		for (int i = 0, passesSize = passes.size(); i < passesSize; i++) {
			Pass compositePass = passes.get(i);
			GLDebug.pushGroup(20 * this.compositePass.ordinal() + i, compositePass.name);
			boolean ranCompute = false;
			for (ComputeProgram computeProgram : compositePass.computes) {
				if (computeProgram != null) {
					ranCompute = true;
					computeProgram.use();
					this.customUniforms.push(computeProgram);
					computeProgram.dispatch(main.width, main.height);
				}
			}

			if (ranCompute) {
				IrisRenderSystem.memoryBarrierComputeWritesVisibleToTextureSampling();
			}

			Program.unbind();

			if (compositePass instanceof ComputeOnlyPass) {
				GLDebug.popGroup();
				continue;
			}

			if (!compositePass.mipmappedBuffers.isEmpty()) {
				net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(0);

				for (int index : compositePass.mipmappedBuffers) {
					setupMipmapping(CompositeRenderer.this.renderTargets.get(index), compositePass.stageReadsFromAlt.contains(index));
				}
			}

			compositePass.ensurePipelineState();

			try (RenderPass renderPass = VulkanicAPI.createRenderPass(
				() -> "Composites",
				compositePass.framebuffer.getId(),
				compositePass.framebuffer.hasDepthAttachment()
			)) {
				renderPass.setPipeline(COMPOSITE_PIPELINE);
				VulkanicAPI.bindDefaultUniforms(renderPass);
				renderPass.setIndexBuffer(indices, type);
				renderPass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad());
				renderPass.iris$setCustomPass(compositePass);
				var ctx = VulkanicAPI.getCommandContext();

				float scaledWidth = compositePass.viewWidth * compositePass.viewportScale.scale();
				float scaledHeight = compositePass.viewHeight * compositePass.viewportScale.scale();
				int beginWidth = (int) (compositePass.viewWidth * compositePass.viewportScale.viewportX());
				int beginHeight = (int) (compositePass.viewHeight * compositePass.viewportScale.viewportY());
				VulkanicAPI.setDynamicViewport(ctx, beginWidth, beginHeight, (int) scaledWidth, (int) scaledHeight);

				compositePass.program.use();

				// program is the identifier for composite :shrug:
				this.customUniforms.push(compositePass.program);

				renderPass.drawIndexed(0, 0, 6, 1);
			}

			BlendModeOverride.restore();
			GLDebug.popGroup();
		}


		// Make sure to reset the viewport to how it was before... Otherwise weird issues could occur.
		// Also bind the "main" framebuffer if it isn't already bound.
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();
		net.irisshaders.iris.gl.IrisRenderSystem.useProgram(0);

		// NB: Unbinding all of these textures is necessary for proper shaderpack reloading.
		for (int i = 0; i < SamplerLimits.get().getMaxTextureUnits(); i++) {
			// Unbind all textures that we may have used.
			// NB: This is necessary for shader pack reloading to work propely
			if (net.irisshaders.iris.gl.IrisRenderSystem.getTextureBinding(i) != 0) {
				net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(i);
				var ctx = VulkanicAPI.getCommandContext();
				VulkanicAPI.bindTexture2D(ctx, 0);
			}
		}

		net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(0);

		GLDebug.popGroup();

		ImmediateState.temporarilyIgnorePass = false;

	}

	// TODO: Don't just copy this from DeferredWorldRenderingPipeline
	private Program createProgram(ProgramSource source, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
								  Supplier<ShadowRenderTargets> shadowTargetsSupplier) {
		// TODO: Properly handle empty shaders
		Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(
			source.getName(),
			source.getVertexSource().orElseThrow(NullPointerException::new),
			source.getGeometrySource().orElse(null),
			source.getFragmentSource().orElseThrow(NullPointerException::new), textureStage, pipeline.getTextureMap());
		String vertex = transformed.get(PatchShaderType.VERTEX);
		String geometry = transformed.get(PatchShaderType.GEOMETRY);
		String fragment = transformed.get(PatchShaderType.FRAGMENT);

		ShaderPrinter.printProgram(source.getName()).addSources(transformed).print();

		Objects.requireNonNull(flipped);
		ProgramBuilder builder;

		try {
			builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment,
				IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
		} catch (ShaderCompileException e) {
			throw e;
		} catch (RuntimeException e) {
			// TODO: Better error handling
			throw new RuntimeException("Shader compilation failed for " + source.getName() + "!", e);
		}


		CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
		this.customUniforms.assignTo(builder);

		ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

		IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, renderTargets, true, pipeline);
		IrisSamplers.addCustomTextures(builder, irisCustomTextures);
		IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);

		IrisImages.addRenderTargetImages(builder, () -> flipped, renderTargets);
		IrisImages.addCustomImages(builder, customImages);

		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
		IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);
		IrisSamplers.addCompositePbrSamplers(customTextureSamplerInterceptor, pipeline);

		if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
			IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
			IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get(), null);
		}

		// TODO: Don't duplicate this with FinalPassRenderer
		centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

		Program build = builder.build();
		if (VulkanicAPI.shouldTraceStandaloneUniforms()) {
			LOGGER.info(
				"CompositePassProgramTrace stage=createProgram passName={} programId={} programObjectId={}",
				source.getName(),
				build.getProgramId(),
				System.identityHashCode(build)
			);
		}

		// tell the customUniforms that those locations belong to this pass
		// this is just an object to index the internal map
		this.customUniforms.mapholderToPass(builder, build);

		return build;
	}

	private ComputeProgram[] createComputes(ComputeSource[] compute, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot, Supplier<ShadowRenderTargets> shadowTargetsSupplier, ShaderStorageBufferHolder holder) {
		ComputeProgram[] programs = new ComputeProgram[compute.length];
		for (int i = 0; i < programs.length; i++) {
			ComputeSource source = compute[i];
			if (source == null || source.getSource().isEmpty()) {
			} else {
				// TODO: Properly handle empty shaders
				Objects.requireNonNull(flipped);
				ProgramBuilder builder;

				try {
					String transformed = TransformPatcher.patchCompute(source.getName(), source.getSource().orElse(null), textureStage, pipeline.getTextureMap());

					ShaderPrinter.printProgram(source.getName()).addSource(PatchShaderType.COMPUTE, transformed).print();

					builder = ProgramBuilder.beginComputeIfSupported(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
				} catch (ShaderCompileException e) {
					throw e;
				} catch (RuntimeException e) {
					// TODO: Better error handling
					throw new RuntimeException("Shader compilation failed for compute " + source.getName() + "!", e);
				}

				if (builder == null) {
					continue;
				}

				ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

				CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);

				customUniforms.assignTo(builder);

				IrisSamplers.addRenderTargetSamplers(customTextureSamplerInterceptor, () -> flipped, renderTargets, true, pipeline);
				IrisSamplers.addCustomTextures(builder, irisCustomTextures);
				IrisSamplers.addCustomImages(customTextureSamplerInterceptor, customImages);

				IrisImages.addRenderTargetImages(builder, () -> flipped, renderTargets);
				IrisImages.addCustomImages(builder, customImages);

				IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
				IrisSamplers.addCompositeSamplers(customTextureSamplerInterceptor, renderTargets);

				if (IrisSamplers.hasShadowSamplers(customTextureSamplerInterceptor)) {
					IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, shadowTargetsSupplier.get(), null, pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
					IrisImages.addShadowColorImages(builder, shadowTargetsSupplier.get(), null);
				}

				// TODO: Don't duplicate this with FinalPassRenderer
				centerDepthSampler.setUsage(builder.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, "iris_centerDepthSmooth"));

				programs[i] = builder.buildCompute();

				customUniforms.mapholderToPass(builder, programs[i]);

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
		int[] drawBuffers;
		int viewWidth;
		int viewHeight;
		String name;
		Program program;
		BlendModeOverride blendModeOverride;
		ComputeProgram[] computes;
		GlFramebuffer framebuffer;
		PipelineDescriptor pipelineDescriptor;
		PipelineHandle pipelineHandle;
		final java.util.Map<PipelineDescriptor.ResourceLayout, PipelineHandle> pipelineLayoutVariants = new java.util.HashMap<>();
		ImmutableSet<Integer> flippedAtLeastOnce;
		ImmutableSet<Integer> stageReadsFromAlt;
		ImmutableSet<Integer> mipmappedBuffers;
		ViewportData viewportScale;

		private void closePipelineVariants() {
			for (PipelineHandle pipelineVariant : this.pipelineLayoutVariants.values()) {
				pipelineVariant.close();
			}
			this.pipelineLayoutVariants.clear();
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
				PipelineDescriptor.fromRenderPipeline(COMPOSITE_PIPELINE),
				this.program.getProgramId()
			);
			descriptor = applyBlendOverride(descriptor, this.blendModeOverride);

			this.closePipelineVariants();
			if (this.pipelineHandle != null) {
				this.pipelineHandle.close();
			}

			this.pipelineDescriptor = descriptor;
			this.pipelineHandle = VulkanicAPI.createPipeline(descriptor, this.framebuffer.getId());
			if (VulkanicAPI.shouldTraceStandaloneUniforms()) {
				boolean descriptorHasStandaloneBlock = descriptor.getResourceLayout().bindings().stream()
					.anyMatch(binding -> VulkanicAPI.generatedStandaloneUniformBlockName().equals(binding.name()));
				LOGGER.info(
					"CompositePassProgramTrace stage=ensurePipelineState passName={} programId={} programObjectId={} descriptorStandaloneBlockPresent={} framebuffer={}",
					this.name,
					this.program.getProgramId(),
					System.identityHashCode(this.program),
					descriptorHasStandaloneBlock ? "yes" : "no",
					this.framebuffer.getId()
				);
				VulkanicAPI.logStandaloneSliceTrace(
					ctx,
					"composite-pass-program",
					this.program.getProgramId(),
					this.name,
					"programObjectId=" + System.identityHashCode(this.program)
						+ " descriptorStandaloneBlockPresent=" + (descriptorHasStandaloneBlock ? "yes" : "no")
				);
			}
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

			PipelineHandle createdVariant = VulkanicAPI.createPipeline(descriptor, this.framebuffer.getId());
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
