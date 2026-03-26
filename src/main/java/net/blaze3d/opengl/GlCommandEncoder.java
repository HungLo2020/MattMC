package net.blaze3d.opengl;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.GpuFence;
import net.blaze3d.pipeline.BlendFunction;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.DepthTestFunction;
import net.blaze3d.platform.NativeImage;
import net.blaze3d.shaders.UniformType;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.blaze3d.vertex.VertexFormat;
import net.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.util.ARGB;
import net.vulkanic.CommandContext;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineResourceBindings;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicCoreAPI;
import net.vulkanic.VulkanicDepthCompareOp;
import net.vulkanic.VulkanicIndexType;
import net.vulkanic.VulkanicLogicOp;
import net.vulkanic.VulkanicPolygonFace;
import net.vulkanic.VulkanicPrimitiveMode;
import net.vulkanic.VulkanicBufferTarget;
import net.vulkanic.VulkanicTextureParameterName;
import net.vulkanic.VulkanicTextureTarget;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlCommandEncoder implements CommandEncoder {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final GlDevice device;
	private final int readFbo;
	private final int drawFbo;
	@Nullable
	private RenderPipeline lastPipeline;
	private boolean inRenderPass;
	@Nullable
	private GlProgram lastProgram;
	
	// Iris: From MixinGlCommandEncoder - Shadow rendering state and program tracking
	private int iris$tempFBO;
	private java.util.List<net.irisshaders.iris.pipeline.programs.IrisProgram> iris$programsToClear = new java.util.ArrayList<>();
	private static GlRenderPass iris$lastPass;
	/**
	 * The active Vulkanic render pass created by the normal (non-Iris-shadow) code path.
	 * Non-null when a render pass was begun by delegating to {@link VulkanicAPI#beginRenderPass};
	 * null in the Iris shadow/safeMultiply path where the old GlTexture FBO cache is used.
	 * Closed by {@link #finishRenderPass()}.
	 */
	@Nullable
	private net.vulkanic.VulkanicRenderPass activeVulkanicRenderPass;
	@Nullable
	private net.vulkanic.CommandContext activeRenderPassContext;
	private static int DEBUG_PIPELINE_BIND_LOGS = 0;
	private static int DEBUG_SODIUM_SAMPLER_BIND_LOGS = 0;
	private static int DEBUG_PARTICLE_VULKAN_BIND_LOGS = 0;

	private static CommandContext commandContext() {
		return VulkanicAPI.getCommandContext();
	}

	protected GlCommandEncoder(GlDevice glDevice) {
		this.device = glDevice;
		this.readFbo = glDevice.directStateAccess().createFrameBufferObject();
		this.drawFbo = glDevice.directStateAccess().createFrameBufferObject();
	}

	@Override
	public RenderPass createRenderPass(Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt) {
		return this.createRenderPass(supplier, gpuTextureView, optionalInt, null, OptionalDouble.empty());
	}

	@Override
	public RenderPass createRenderPass(
		Supplier<String> supplier, GpuTextureView gpuTextureView, OptionalInt optionalInt, @Nullable GpuTextureView gpuTextureView2, OptionalDouble optionalDouble
	) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else {
			if (optionalDouble.isPresent() && gpuTextureView2 == null) {
				LOGGER.warn("Depth clear value was provided but no depth texture is being used");
			}

			if (gpuTextureView.isClosed()) {
				throw new IllegalStateException("Color texture is closed");
			} else if ((gpuTextureView.texture().usage() & 8) == 0) {
				throw new IllegalStateException("Color texture must have USAGE_RENDER_ATTACHMENT");
			} else if (gpuTextureView.texture().getDepthOrLayers() > 1) {
				throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported as an attachment");
			} else {
				if (gpuTextureView2 != null) {
					if (gpuTextureView2.isClosed()) {
						throw new IllegalStateException("Depth texture is closed");
					}

					if ((gpuTextureView2.texture().usage() & 8) == 0) {
						throw new IllegalStateException("Depth texture must have USAGE_RENDER_ATTACHMENT");
					}

					if (gpuTextureView2.texture().getDepthOrLayers() > 1) {
						throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported as an attachment");
					}
				}

				this.inRenderPass = true;
				this.device.debugLabels().pushDebugGroup(supplier);

				// Iris: the shadow temp-FBO shortcut is only safe on the immediate/OpenGL seam.
				// Recorded Vulkan shadow passes still need a real backend beginRenderPass.
				if (net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered() && commandContext().isImmediate()) {
					// Iris shadow path: use GlTexture's cached FBO but do not bind it.
					int i = VulkanicAPI.resolveFramebufferForTextures(gpuTextureView.texture(), gpuTextureView2 == null ? null : gpuTextureView2.texture());
					this.iris$tempFBO = i;
					this.activeVulkanicRenderPass = null;
					this.activeRenderPassContext = null;
					CommandContext ctx = commandContext();

					// Perform requested clears on whatever FBO is currently bound
					boolean shouldClearColor = false;
					boolean shouldClearDepth = false;
					if (optionalInt.isPresent()) {
						int k = optionalInt.getAsInt();
						VulkanicAPI.setClearColor(ctx, ARGB.redFloat(k), ARGB.greenFloat(k), ARGB.blueFloat(k), ARGB.alphaFloat(k));
						shouldClearColor = true;
					}
					if (gpuTextureView2 != null && optionalDouble.isPresent()) {
						VulkanicAPI.setClearDepth(ctx, optionalDouble.getAsDouble());
						shouldClearDepth = true;
					}
					if (shouldClearColor || shouldClearDepth) {
						VulkanicAPI.setScissorTestEnabled(ctx, false);
						net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(true);
						net.irisshaders.iris.gl.blending.DepthColorStorage.setColorMask(true, true, true, true);
						if (shouldClearColor && shouldClearDepth) {
							VulkanicAPI.clearColorAndDepthBuffersWithMacosWorkaround(ctx);
						} else if (shouldClearColor) {
							VulkanicAPI.clearColorBufferWithMacosWorkaround(ctx);
						} else {
							VulkanicAPI.clearDepthBufferWithMacosWorkaround(ctx);
						}
					}
					if (!net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
						VulkanicAPI.setDynamicViewport(ctx, 0, 0, gpuTextureView.getWidth(0), gpuTextureView.getHeight(0));
					}
				} else {
					// Normal path: delegate FBO creation, attachment, clear and viewport to VulkanicAPI.
					// VulkanicAPI.beginRenderPass() routes through GraphicsBackend → OpenGLBackend,
					// which creates a fresh FBO, attaches the textures, clears, and sets the viewport.
					CommandContext renderPassCtx = VulkanicAPI.beginCommandBuffer();
					this.activeVulkanicRenderPass = VulkanicAPI.beginRenderPass(
						renderPassCtx, supplier,
						toVulkanicTextureView((GlTextureView) gpuTextureView), optionalInt,
						gpuTextureView2 != null ? toVulkanicTextureView((GlTextureView) gpuTextureView2) : null,
						optionalDouble
					);
					this.activeRenderPassContext = renderPassCtx;
				}

				this.lastPipeline = null;
				return new GlRenderPass(this, gpuTextureView2 != null);
			}
		}
	}

	/**
	 * Wraps a Blaze3D {@link GlTextureView} as a {@link net.vulkanic.VulkanicTextureView}.
	 *
	 * <p>Now that {@code GlTexture} implements {@code VulkanicTexture} (via {@code GpuTexture}),
	 * no GL-handle bridge object is needed. An {@code OpenGLTextureView} is constructed
	 * directly from the {@code GlTexture} and the view's mip range. This is a lightweight
	 * descriptor with no new GPU allocations, and {@link net.vulkanic.backends.opengl.OpenGLTextureView#close()}
	 * does not delete the underlying texture (the caller remains the owner).
	 */
	net.vulkanic.VulkanicTextureView createSamplerResourceView(GlTextureView view) {
		return toVulkanicTextureView(view);
	}

	private net.vulkanic.VulkanicTextureView toVulkanicTextureView(GlTextureView view) {
		return VulkanicAPI.createManagedTextureView(
			view.texture(), view.baseMipLevel(), view.mipLevels()
		);
	}

	private record PipelineResourceBindingSubmission(
		PipelineDescriptor descriptor,
		PipelineResourceBindings bindings,
		boolean completeCoverage
	) {
	}

	private void prepareTexelBufferBindingsForVulkanDescriptors(GlRenderPass glRenderPass, CommandContext ctx) {
		for (Entry<String, Uniform> entry : glRenderPass.pipeline.program().getUniforms().entrySet()) {
			String name = entry.getKey();
			Uniform uniform = entry.getValue();
			if (uniform instanceof Uniform.Utb(int location, int samplerIndex, TextureFormat format, int textureId)) {
				net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(samplerIndex);
				VulkanicAPI.bindTextureBuffer(ctx, textureId);

				GpuBufferSlice slice = glRenderPass.uniforms.get(name);
				if (slice != null) {
					VulkanicAPI.bindTextureBufferData(
						ctx,
						GlConst.toGlInternalId(format),
						((GlBuffer)slice.buffer()).handle
					);
				}
			}
		}
	}

	@Nullable
	private PipelineResourceBindingSubmission buildPipelineResourceBindings(GlRenderPass glRenderPass) {
		GlRenderPipeline glRenderPipeline = glRenderPass.pipeline;
		RenderPipeline pipelineInfo = glRenderPipeline.info();
		PipelineDescriptor.ResourceLayout layout = glRenderPipeline.descriptor().getResourceLayout();
		PipelineResourceBindings.Builder builder = PipelineResourceBindings.builder();
		java.util.List<PipelineDescriptor.ResourceBinding> boundResources = new java.util.ArrayList<>();
		boolean logSodiumChunkSamplers = DEBUG_SODIUM_SAMPLER_BIND_LOGS < 24
			&& pipelineInfo.getLocation().toString().contains("sodium:pipeline/vulkan_chunk_");
		boolean logParticleSamplers = DEBUG_PARTICLE_VULKAN_BIND_LOGS < 80
			&& pipelineInfo.getLocation().toString().contains("pipeline/")
			&& pipelineInfo.getLocation().toString().contains("particle");

		if (logSodiumChunkSamplers) {
			DEBUG_SODIUM_SAMPLER_BIND_LOGS++;
			LOGGER.info(
				"Sodium Vulkan sampler prep#{} pipeline={} declaredSamplers={} renderPassSamplers={} layoutBindings={}",
				DEBUG_SODIUM_SAMPLER_BIND_LOGS,
				pipelineInfo.getLocation(),
				pipelineInfo.getSamplers(),
				glRenderPass.samplers.keySet(),
				layout.bindings().stream().map(PipelineDescriptor.ResourceBinding::name).toList()
			);
		}

		if (logParticleSamplers) {
			DEBUG_PARTICLE_VULKAN_BIND_LOGS++;
			LOGGER.info(
				"Particle Vulkan sampler prep#{} pipeline={} declaredSamplers={} renderPassSamplers={} layoutBindings={}",
				DEBUG_PARTICLE_VULKAN_BIND_LOGS,
				pipelineInfo.getLocation(),
				pipelineInfo.getSamplers(),
				glRenderPass.samplers.keySet(),
				layout.bindings().stream().map(PipelineDescriptor.ResourceBinding::name).toList()
			);
		}

		for (PipelineDescriptor.ResourceBinding resourceBinding : layout.bindings()) {
			switch (resourceBinding.type()) {
				case SAMPLER -> {
					Uniform uniform = glRenderPipeline.program().getUniform(resourceBinding.name());
					net.vulkanic.VulkanicTextureView textureView = glRenderPass.getSamplerResourceView(resourceBinding.name());
					if (logSodiumChunkSamplers) {
						LOGGER.info(
							"Sodium Vulkan sampler resource pipeline={} binding={} uniformPresent={} textureViewPresent={} boundSamplerKeys={} resourceSamplerKeys={}",
							pipelineInfo.getLocation(),
							resourceBinding.name(),
							uniform instanceof Uniform.Sampler,
							textureView != null,
							glRenderPass.samplers.keySet(),
							layout.bindings().stream()
								.filter(binding -> binding.type() == PipelineDescriptor.ResourceType.SAMPLER)
								.map(PipelineDescriptor.ResourceBinding::name)
								.toList()
						);
					}
					if (logParticleSamplers) {
						GpuTextureView boundView = glRenderPass.samplers.get(resourceBinding.name());
						GpuTexture boundTexture = boundView != null ? boundView.texture() : null;
						GpuTexture resourceTexture = textureView != null && textureView.texture() instanceof GpuTexture gpuTexture ? gpuTexture : null;
						LOGGER.info(
							"Particle Vulkan sampler binding pipeline={} binding={} uniformPresent={} reflectedUnit={} boundViewPresent={} boundTexId={} boundLabel={} resourceViewPresent={} resourceViewTexId={} resourceViewLabel={}",
							pipelineInfo.getLocation(),
							resourceBinding.name(),
							uniform instanceof Uniform.Sampler,
							uniform instanceof Uniform.Sampler(int location, int reflectedSamplerIndex) ? reflectedSamplerIndex : resourceBinding.binding(),
							boundView != null,
							boundTexture != null ? VulkanicCoreAPI.textureId(boundTexture) : 0,
							boundTexture != null ? boundTexture.getLabel() : "null",
							textureView != null,
							resourceTexture != null ? VulkanicCoreAPI.textureId(resourceTexture) : 0,
							resourceTexture != null ? resourceTexture.getLabel() : "null"
						);
					}
					if (textureView != null) {
						int samplerIndex = uniform instanceof Uniform.Sampler(int location, int reflectedSamplerIndex)
							? reflectedSamplerIndex
							: resourceBinding.binding();
						builder.bindSampler(resourceBinding.name(), textureView, samplerIndex);
						boundResources.add(resourceBinding);
					}
				}
				case UNIFORM_BUFFER -> {
					GpuBufferSlice slice = glRenderPass.uniforms.get(resourceBinding.name());
					if (slice != null) {
						builder.bindUniformBuffer(
							resourceBinding.name(),
							new net.vulkanic.VulkanicBufferSlice(
								VulkanicAPI.resolveVulkanicBuffer(slice.buffer()),
								slice.offset(),
								slice.length()
							)
						);
						boundResources.add(resourceBinding);
					}
				}
				case TEXEL_BUFFER -> {
					Uniform uniform = glRenderPipeline.program().getUniform(resourceBinding.name());
					if (uniform instanceof Uniform.Utb(int location, int samplerIndex, TextureFormat format, int texture)) {
						builder.bindTexelBuffer(resourceBinding.name(), samplerIndex);
						boundResources.add(resourceBinding);
					}
				}
			}
		}

		if (boundResources.isEmpty()) {
			return null;
		}

		boolean completeCoverage = boundResources.size() == layout.bindings().size();
		if (logParticleSamplers) {
			LOGGER.info(
				"Particle Vulkan sampler submission pipeline={} boundResources={} totalBindings={} completeCoverage={}",
				pipelineInfo.getLocation(),
				boundResources.stream().map(PipelineDescriptor.ResourceBinding::name).toList(),
				layout.bindings().size(),
				completeCoverage
			);
		}

		PipelineDescriptor filteredDescriptor = glRenderPipeline.descriptor()
			.withResourceLayout(new PipelineDescriptor.ResourceLayout(boundResources));

		return new PipelineResourceBindingSubmission(
			filteredDescriptor,
			builder.build(),
			completeCoverage
		);
	}

	private void prepareSamplerBindingsForVulkanDescriptors(GlRenderPass glRenderPass, CommandContext ctx) {
		for (Entry<String, Uniform> entry : glRenderPass.pipeline.program().getUniforms().entrySet()) {
			if (!(entry.getValue() instanceof Uniform.Sampler(int location, int samplerIndex))) {
				continue;
			}

			GlTextureView glTextureView = (GlTextureView)glRenderPass.samplers.get(entry.getKey());
			if (glTextureView == null) {
				continue;
			}

			net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(samplerIndex);
			GpuTexture texture = glTextureView.texture();
			int textureHandle = VulkanicCoreAPI.textureId(texture);
			net.irisshaders.iris.gl.IrisRenderSystem.setTextureBinding(samplerIndex, textureHandle);
			VulkanicTextureTarget textureTarget;
			if ((texture.usage() & 16) != 0) {
				textureTarget = VulkanicTextureTarget.TEXTURE_CUBE_MAP;
				VulkanicAPI.bindCubemapTexture(ctx, textureHandle);
			} else {
				textureTarget = VulkanicTextureTarget.TEXTURE_2D;
				VulkanicAPI.bindTexture2D(ctx, textureHandle);
			}

			VulkanicAPI.setTextureParameter(ctx, textureTarget, VulkanicTextureParameterName.BASE_LEVEL, glTextureView.baseMipLevel());
			VulkanicAPI.setTextureParameter(ctx, textureTarget, VulkanicTextureParameterName.MAX_LEVEL, glTextureView.baseMipLevel() + glTextureView.mipLevels() - 1);
			texture.flushModeChanges(textureTarget);
		}
	}

	@Override
	public void clearColorTexture(GpuTexture gpuTexture, int i) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else {
			this.verifyColorTexture(gpuTexture);
			CommandContext ctx = commandContext();
			this.device.directStateAccess().bindFrameBufferTextures(this.drawFbo, VulkanicCoreAPI.textureId(gpuTexture), 0, 0, VulkanicAPI.GL_FRAMEBUFFER);
			VulkanicAPI.setClearColor(ctx, ARGB.redFloat(i), ARGB.greenFloat(i), ARGB.blueFloat(i), ARGB.alphaFloat(i));
			VulkanicAPI.setScissorTestEnabled(ctx, false);
			net.irisshaders.iris.gl.blending.DepthColorStorage.setColorMask(true, true, true, true);
			VulkanicAPI.clearColorBufferWithMacosWorkaround(ctx);
			VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, 0, 0);
			VulkanicAPI.bindDefaultFramebuffer(ctx);
		}
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture2, double d) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else {
			this.verifyColorTexture(gpuTexture);
			this.verifyDepthTexture(gpuTexture2);
			CommandContext ctx = commandContext();
			if (!ctx.isImmediate()) {
				try (net.vulkanic.VulkanicRenderPass ignored = VulkanicAPI.beginRenderPass(
					ctx,
					() -> "Clear color/depth textures",
					VulkanicAPI.createManagedTextureView(gpuTexture, 0, 1),
					OptionalInt.of(i),
					VulkanicAPI.createManagedTextureView(gpuTexture2, 0, 1),
					OptionalDouble.of(d)
				)) {
				}
				return;
			}

			int j = VulkanicAPI.resolveFramebufferForTextures(gpuTexture, gpuTexture2);
			VulkanicAPI.bindFramebuffer(ctx, j);
			VulkanicAPI.setScissorTestEnabled(ctx, false);
			VulkanicAPI.setClearDepth(ctx, d);
			VulkanicAPI.setClearColor(ctx, ARGB.redFloat(i), ARGB.greenFloat(i), ARGB.blueFloat(i), ARGB.alphaFloat(i));
			net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(true);
			net.irisshaders.iris.gl.blending.DepthColorStorage.setColorMask(true, true, true, true);
			VulkanicAPI.clearColorAndDepthBuffersWithMacosWorkaround(ctx);
			VulkanicAPI.bindDefaultFramebuffer(ctx);
		}
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture2, double d, int j, int k, int l, int m) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else {
			this.verifyColorTexture(gpuTexture);
			this.verifyDepthTexture(gpuTexture2);
			this.verifyRegion(gpuTexture, j, k, l, m);
			int n = VulkanicAPI.resolveFramebufferForTextures(gpuTexture, gpuTexture2);
			CommandContext ctx = commandContext();
			VulkanicAPI.bindFramebuffer(ctx, n);
			VulkanicAPI.setDynamicScissor(ctx, j, k, l, m);
			VulkanicAPI.setScissorTestEnabled(ctx, true);
			VulkanicAPI.setClearDepth(ctx, d);
			VulkanicAPI.setClearColor(ctx, ARGB.redFloat(i), ARGB.greenFloat(i), ARGB.blueFloat(i), ARGB.alphaFloat(i));
			net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(true);
			net.irisshaders.iris.gl.blending.DepthColorStorage.setColorMask(true, true, true, true);
			VulkanicAPI.clearColorAndDepthBuffersWithMacosWorkaround(ctx);
			VulkanicAPI.bindDefaultFramebuffer(ctx);
		}
	}

	private void verifyRegion(GpuTexture gpuTexture, int i, int j, int k, int l) {
		if (i < 0 || i >= gpuTexture.getWidth(0)) {
			throw new IllegalArgumentException("regionX should not be outside of the texture");
		} else if (j < 0 || j >= gpuTexture.getHeight(0)) {
			throw new IllegalArgumentException("regionY should not be outside of the texture");
		} else if (k <= 0) {
			throw new IllegalArgumentException("regionWidth should be greater than 0");
		} else if (i + k > gpuTexture.getWidth(0)) {
			throw new IllegalArgumentException("regionWidth + regionX should be less than the texture width");
		} else if (l <= 0) {
			throw new IllegalArgumentException("regionHeight should be greater than 0");
		} else if (j + l > gpuTexture.getHeight(0)) {
			throw new IllegalArgumentException("regionWidth + regionX should be less than the texture height");
		}
	}

	@Override
	public void clearDepthTexture(GpuTexture gpuTexture, double d) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else {
			this.verifyDepthTexture(gpuTexture);
			CommandContext ctx = commandContext();
			this.device.directStateAccess().bindFrameBufferTextures(this.drawFbo, 0, VulkanicCoreAPI.textureId(gpuTexture), 0, VulkanicAPI.GL_FRAMEBUFFER);
			VulkanicAPI.setDrawBufferNone(ctx);
			VulkanicAPI.setClearDepth(ctx, d);
			net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(true);
			VulkanicAPI.setScissorTestEnabled(ctx, false);
			VulkanicAPI.clearDepthBufferWithMacosWorkaround(ctx);
			VulkanicAPI.setDrawBufferColorAttachment0(ctx);
			VulkanicAPI.framebufferDepthAttachmentTexture2D(ctx, VulkanicAPI.GL_FRAMEBUFFER, 0, 0);
			VulkanicAPI.bindDefaultFramebuffer(ctx);
		}
	}

	private void verifyColorTexture(GpuTexture gpuTexture) {
		if (!gpuTexture.getFormat().hasColorAspect()) {
			throw new IllegalStateException("Trying to clear a non-color texture as color");
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Color texture is closed");
		} else if ((gpuTexture.usage() & 8) == 0) {
			throw new IllegalStateException("Color texture must have USAGE_RENDER_ATTACHMENT");
		} else if (gpuTexture.getDepthOrLayers() > 1) {
			throw new UnsupportedOperationException("Clearing a texture with multiple layers or depths is not yet supported");
		}
	}

	private void verifyDepthTexture(GpuTexture gpuTexture) {
		if (!gpuTexture.getFormat().hasDepthAspect()) {
			throw new IllegalStateException("Trying to clear a non-depth texture as depth");
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Depth texture is closed");
		} else if ((gpuTexture.usage() & 8) == 0) {
			throw new IllegalStateException("Depth texture must have USAGE_RENDER_ATTACHMENT");
		} else if (gpuTexture.getDepthOrLayers() > 1) {
			throw new UnsupportedOperationException("Clearing a texture with multiple layers or depths is not yet supported");
		}
	}

	@Override
	public void writeToBuffer(GpuBufferSlice gpuBufferSlice, ByteBuffer byteBuffer) {
		// Iris: From MixinGlCommandEncoder - Ignore render pass check if temporarilyIgnorePass is true
		if (!net.irisshaders.iris.vertices.ImmediateState.temporarilyIgnorePass && this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			GlBuffer glBuffer = (GlBuffer)gpuBufferSlice.buffer();
			if (glBuffer.closed) {
				throw new IllegalStateException("Buffer already closed");
			} else if ((glBuffer.usage() & 8) == 0) {
				throw new IllegalStateException("Buffer needs USAGE_COPY_DST to be a destination for a copy");
			} else {
				int i = byteBuffer.remaining();
				if (i > gpuBufferSlice.length()) {
					throw new IllegalArgumentException(
						"Cannot write more data than the slice allows (attempting to write " + i + " bytes into a slice of length " + gpuBufferSlice.length() + ")"
					);
				} else if (gpuBufferSlice.length() + gpuBufferSlice.offset() > glBuffer.size()) {
					throw new IllegalArgumentException(
						"Cannot write more data than this buffer can hold (attempting to write "
							+ i
							+ " bytes at offset "
							+ gpuBufferSlice.offset()
							+ " to "
							+ glBuffer.size()
							+ " size buffer)"
					);
				} else {
					this.device.directStateAccess().bufferSubData(glBuffer.handle, gpuBufferSlice.offset(), byteBuffer, glBuffer.usage());
				}
			}
		}
	}

	@Override
	public GpuBuffer.MappedView mapBuffer(GpuBuffer gpuBuffer, boolean bl, boolean bl2) {
		return this.mapBuffer(gpuBuffer.slice(), bl, bl2);
	}

	@Override
	public GpuBuffer.MappedView mapBuffer(GpuBufferSlice gpuBufferSlice, boolean bl, boolean bl2) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			GlBuffer glBuffer = (GlBuffer)gpuBufferSlice.buffer();
			if (glBuffer.closed) {
				throw new IllegalStateException("Buffer already closed");
			} else if (!bl && !bl2) {
				throw new IllegalArgumentException("At least read or write must be true");
			} else if (bl && (glBuffer.usage() & 1) == 0) {
				throw new IllegalStateException("Buffer is not readable");
			} else if (bl2 && (glBuffer.usage() & 2) == 0) {
				throw new IllegalStateException("Buffer is not writable");
			} else if (gpuBufferSlice.offset() + gpuBufferSlice.length() > glBuffer.size()) {
				throw new IllegalArgumentException(
					"Cannot map more data than this buffer can hold (attempting to map "
						+ gpuBufferSlice.length()
						+ " bytes at offset "
						+ gpuBufferSlice.offset()
						+ " from "
						+ glBuffer.size()
						+ " size buffer)"
				);
			} else {
				int i = 0;
				if (bl) {
					i |= 1;
				}

				if (bl2) {
					i |= 34;
				}
				return this.device.getBufferStorage().mapBuffer(this.device.directStateAccess(), glBuffer, gpuBufferSlice.offset(), gpuBufferSlice.length(), i);
			}
		}
	}

	@Override
	public void copyToBuffer(GpuBufferSlice gpuBufferSlice, GpuBufferSlice gpuBufferSlice2) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			GlBuffer glBuffer = (GlBuffer)gpuBufferSlice.buffer();
			if (glBuffer.closed) {
				throw new IllegalStateException("Source buffer already closed");
			} else if ((glBuffer.usage() & 16) == 0) {
				throw new IllegalStateException("Source buffer needs USAGE_COPY_SRC to be a source for a copy");
			} else {
				GlBuffer glBuffer2 = (GlBuffer)gpuBufferSlice2.buffer();
				if (glBuffer2.closed) {
					throw new IllegalStateException("Target buffer already closed");
				} else if ((glBuffer2.usage() & 8) == 0) {
					throw new IllegalStateException("Target buffer needs USAGE_COPY_DST to be a destination for a copy");
				} else if (gpuBufferSlice.length() != gpuBufferSlice2.length()) {
					throw new IllegalArgumentException(
						"Cannot copy from slice of size " + gpuBufferSlice.length() + " to slice of size " + gpuBufferSlice2.length() + ", they must be equal"
					);
				} else if (gpuBufferSlice.offset() + gpuBufferSlice.length() > glBuffer.size()) {
					throw new IllegalArgumentException(
						"Cannot copy more data than the source buffer holds (attempting to copy "
							+ gpuBufferSlice.length()
							+ " bytes at offset "
							+ gpuBufferSlice.offset()
							+ " from "
							+ glBuffer.size()
							+ " size buffer)"
					);
				} else if (gpuBufferSlice2.offset() + gpuBufferSlice2.length() > glBuffer2.size()) {
					throw new IllegalArgumentException(
						"Cannot copy more data than the target buffer can hold (attempting to copy "
							+ gpuBufferSlice2.length()
							+ " bytes at offset "
							+ gpuBufferSlice2.offset()
							+ " to "
							+ glBuffer2.size()
							+ " size buffer)"
					);
				} else {
					this.device
						.directStateAccess()
						.copyBufferSubData(glBuffer.handle, glBuffer2.handle, gpuBufferSlice.offset(), gpuBufferSlice2.offset(), gpuBufferSlice.length());
				}
			}
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage) {
		int i = gpuTexture.getWidth(0);
		int j = gpuTexture.getHeight(0);
		if (nativeImage.getWidth() != i || nativeImage.getHeight() != j) {
			throw new IllegalArgumentException(
				"Cannot replace texture of size " + i + "x" + j + " with image of size " + nativeImage.getWidth() + "x" + nativeImage.getHeight()
			);
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Destination texture is closed");
		} else if ((gpuTexture.usage() & 1) == 0) {
			throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
		} else {
			this.writeToTexture(gpuTexture, nativeImage, 0, 0, 0, 0, i, j, 0, 0);
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage, int i, int j, int k, int l, int m, int n, int o, int p) {
		// Iris: From MixinGlCommandEncoder - Ignore render pass check if temporarilyIgnorePass is true
		if (!net.irisshaders.iris.vertices.ImmediateState.temporarilyIgnorePass && this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels()) {
			if (o + m > nativeImage.getWidth() || p + n > nativeImage.getHeight()) {
				throw new IllegalArgumentException(
					"Copy source ("
						+ nativeImage.getWidth()
						+ "x"
						+ nativeImage.getHeight()
						+ ") is not large enough to read a rectangle of "
						+ m
						+ "x"
						+ n
						+ " from "
						+ o
						+ "x"
						+ p
				);
			} else if (k + m > gpuTexture.getWidth(i) || l + n > gpuTexture.getHeight(i)) {
				throw new IllegalArgumentException(
					"Dest texture (" + m + "x" + n + ") is not large enough to write a rectangle of " + m + "x" + n + " at " + k + "x" + l + " (at mip level " + i + ")"
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Destination texture is closed");
			} else if ((gpuTexture.usage() & 1) == 0) {
				throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
			} else if (j >= gpuTexture.getDepthOrLayers()) {
				throw new UnsupportedOperationException("Depth or layer is out of range, must be >= 0 and < " + gpuTexture.getDepthOrLayers());
			} else {
				CommandContext ctx = commandContext();
				int textureHandle = VulkanicCoreAPI.textureId(gpuTexture);
				boolean cubemap = (gpuTexture.usage() & 16) != 0;
				int cubemapFaceTarget = 0;
				if ((gpuTexture.usage() & 16) != 0) {
					cubemapFaceTarget = GlConst.CUBEMAP_TARGETS[j % 6];
					VulkanicAPI.bindCubemapTexture(ctx, textureHandle);
				} else {
					VulkanicAPI.bindTexture2D(ctx, textureHandle);
				}

				VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ROW_LENGTH, nativeImage.getWidth());
				VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_PIXELS, o);
				VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_ROWS, p);
				VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ALIGNMENT, nativeImage.format().components());
				if (cubemap) {
					VulkanicAPI.uploadTexture2DSubImage(ctx,
						cubemapFaceTarget,
						i,
						k,
						l,
						m,
						n,
						GlConst.toGl(nativeImage.format()),
						VulkanicAPI.GL_UNSIGNED_BYTE,
						nativeImage.getPointer()
					);
				} else {
					VulkanicAPI.uploadTexture2DSubImage(ctx,
						i,
						k,
						l,
						m,
						n,
						GlConst.toGl(nativeImage.format()),
						VulkanicAPI.GL_UNSIGNED_BYTE,
						nativeImage.getPointer()
					);
				}
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel " + i + ", must be >= 0 and < " + gpuTexture.getMipLevels());
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, ByteBuffer byteBuffer, NativeImage.Format format, int i, int j, int k, int l, int m, int n) {
		// Iris: From MixinGlCommandEncoder - Ignore render pass check if temporarilyIgnorePass is true
		if (!net.irisshaders.iris.vertices.ImmediateState.temporarilyIgnorePass && this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels()) {
			if (m * n * format.components() > byteBuffer.remaining()) {
				throw new IllegalArgumentException(
					"Copy would overrun the source buffer (remaining length of " + byteBuffer.remaining() + ", but copy is " + m + "x" + n + " of format " + format + ")"
				);
			} else if (k + m > gpuTexture.getWidth(i) || l + n > gpuTexture.getHeight(i)) {
				throw new IllegalArgumentException(
					"Dest texture ("
						+ gpuTexture.getWidth(i)
						+ "x"
						+ gpuTexture.getHeight(i)
						+ ") is not large enough to write a rectangle of "
						+ m
						+ "x"
						+ n
						+ " at "
						+ k
						+ "x"
						+ l
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Destination texture is closed");
			} else if ((gpuTexture.usage() & 1) == 0) {
				throw new IllegalStateException("Color texture must have USAGE_COPY_DST to be a destination for a write");
			} else if (j >= gpuTexture.getDepthOrLayers()) {
				throw new UnsupportedOperationException("Depth or layer is out of range, must be >= 0 and < " + gpuTexture.getDepthOrLayers());
			} else {
				CommandContext ctx = commandContext();
				int textureHandle = VulkanicCoreAPI.textureId(gpuTexture);
				boolean cubemap = (gpuTexture.usage() & 16) != 0;
				int cubemapFaceTarget = 0;
				if ((gpuTexture.usage() & 16) != 0) {
					cubemapFaceTarget = GlConst.CUBEMAP_TARGETS[j % 6];
					VulkanicAPI.bindCubemapTexture(ctx, textureHandle);
				} else {
					VulkanicAPI.bindTexture2D(ctx, textureHandle);
				}

				VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ROW_LENGTH, m);
				VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_PIXELS, 0);
				VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_SKIP_ROWS, 0);
				VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_UNPACK_ALIGNMENT, format.components());
				if (cubemap) {
					VulkanicAPI.uploadTexture2DSubImage(ctx,
						cubemapFaceTarget,
						i,
						k,
						l,
						m,
						n,
						GlConst.toGl(format),
						VulkanicAPI.GL_UNSIGNED_BYTE,
						byteBuffer
					);
				} else {
					VulkanicAPI.uploadTexture2DSubImage(ctx,
						i,
						k,
						l,
						m,
						n,
						GlConst.toGl(format),
						VulkanicAPI.GL_UNSIGNED_BYTE,
						byteBuffer
					);
				}
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel, must be >= 0 and < " + gpuTexture.getMipLevels());
		}
	}

	@Override
	public void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, int i, Runnable runnable, int j) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			this.copyTextureToBuffer(gpuTexture, gpuBuffer, i, runnable, j, 0, 0, gpuTexture.getWidth(j), gpuTexture.getHeight(j));
		}
	}

	@Override
	public void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, int i, Runnable runnable, int j, int k, int l, int m, int n) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (j >= 0 && j < gpuTexture.getMipLevels()) {
			if (gpuTexture.getWidth(j) * gpuTexture.getHeight(j) * gpuTexture.getFormat().pixelSize() + i > gpuBuffer.size()) {
				throw new IllegalArgumentException(
					"Buffer of size "
						+ gpuBuffer.size()
						+ " is not large enough to hold "
						+ m
						+ "x"
						+ n
						+ " pixels ("
						+ gpuTexture.getFormat().pixelSize()
						+ " bytes each) starting from offset "
						+ i
				);
			} else if ((gpuTexture.usage() & 2) == 0) {
				throw new IllegalArgumentException("Texture needs USAGE_COPY_SRC to be a source for a copy");
			} else if ((gpuBuffer.usage() & 8) == 0) {
				throw new IllegalArgumentException("Buffer needs USAGE_COPY_DST to be a destination for a copy");
			} else if (k + m > gpuTexture.getWidth(j) || l + n > gpuTexture.getHeight(j)) {
				throw new IllegalArgumentException(
					"Copy source texture ("
						+ gpuTexture.getWidth(j)
						+ "x"
						+ gpuTexture.getHeight(j)
						+ ") is not large enough to read a rectangle of "
						+ m
						+ "x"
						+ n
						+ " from "
						+ k
						+ ","
						+ l
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Source texture is closed");
			} else if (gpuBuffer.isClosed()) {
				throw new IllegalStateException("Destination buffer is closed");
			} else if (gpuTexture.getDepthOrLayers() > 1) {
				throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for copying");
			} else {
				CommandContext ctx = commandContext();
				while (VulkanicAPI.getError(ctx) != 0) {
				}
				this.device.directStateAccess().bindFrameBufferTextures(this.readFbo, VulkanicCoreAPI.textureId(gpuTexture), 0, j, VulkanicAPI.GL_READ_FRAMEBUFFER);
				VulkanicAPI.bindPixelPackBuffer(ctx, ((GlBuffer)gpuBuffer).handle);
				VulkanicAPI.setPixelStore(ctx, VulkanicAPI.GL_PACK_ROW_LENGTH, m);
				VulkanicAPI.readPixels(ctx, k, l, m, n, GlConst.toGlExternalId(gpuTexture.getFormat()), GlConst.toGlType(gpuTexture.getFormat()), i);
				VulkanicAPI.queueFencedTask(runnable);
				VulkanicAPI.framebufferColorAttachment0Texture2D(ctx, VulkanicAPI.GL_READ_FRAMEBUFFER, 0, j);
				VulkanicAPI.bindReadFramebuffer(ctx, 0);
				VulkanicAPI.bindPixelPackBuffer(ctx, 0);
				int o = VulkanicAPI.getError(ctx);
				if (o != 0) {
					throw new IllegalStateException("Couldn't perform copyTobuffer for texture " + gpuTexture.getLabel() + ": GL error " + o);
				}
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel " + j + ", must be >= 0 and < " + gpuTexture.getMipLevels());
		}
	}

	@Override
	public void copyTextureToTexture(GpuTexture gpuTexture, GpuTexture gpuTexture2, int i, int j, int k, int l, int m, int n, int o) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels() && i < gpuTexture2.getMipLevels()) {
			if (j + n > gpuTexture2.getWidth(i) || k + o > gpuTexture2.getHeight(i)) {
				throw new IllegalArgumentException(
					"Dest texture ("
						+ gpuTexture2.getWidth(i)
						+ "x"
						+ gpuTexture2.getHeight(i)
						+ ") is not large enough to write a rectangle of "
						+ n
						+ "x"
						+ o
						+ " at "
						+ j
						+ "x"
						+ k
				);
			} else if (l + n > gpuTexture.getWidth(i) || m + o > gpuTexture.getHeight(i)) {
				throw new IllegalArgumentException(
					"Source texture ("
						+ gpuTexture.getWidth(i)
						+ "x"
						+ gpuTexture.getHeight(i)
						+ ") is not large enough to read a rectangle of "
						+ n
						+ "x"
						+ o
						+ " at "
						+ l
						+ "x"
						+ m
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Source texture is closed");
			} else if (gpuTexture2.isClosed()) {
				throw new IllegalStateException("Destination texture is closed");
			} else if ((gpuTexture.usage() & 2) == 0) {
				throw new IllegalArgumentException("Texture needs USAGE_COPY_SRC to be a source for a copy");
			} else if ((gpuTexture2.usage() & 1) == 0) {
				throw new IllegalArgumentException("Texture needs USAGE_COPY_DST to be a destination for a copy");
			} else if (gpuTexture.getDepthOrLayers() > 1) {
				throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for copying");
			} else if (gpuTexture2.getDepthOrLayers() > 1) {
				throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for copying");
			} else {
				CommandContext ctx = commandContext();
				while (VulkanicAPI.getError(ctx) != 0) {
				}
				VulkanicAPI.setScissorTestEnabled(ctx, false);
				boolean bl = gpuTexture.getFormat().hasDepthAspect();
				int p = VulkanicCoreAPI.textureId(gpuTexture);
				int q = VulkanicCoreAPI.textureId(gpuTexture2);
				this.device.directStateAccess().bindFrameBufferTextures(this.readFbo, bl ? 0 : p, bl ? p : 0, 0, 0);
				this.device.directStateAccess().bindFrameBufferTextures(this.drawFbo, bl ? 0 : q, bl ? q : 0, 0, 0);
				this.device.directStateAccess().blitFrameBuffers(
					this.readFbo,
					this.drawFbo,
					l,
					m,
					n,
					o,
					j,
					k,
					n,
					o,
					bl ? VulkanicAPI.GL_DEPTH_BUFFER_BIT : VulkanicAPI.GL_COLOR_BUFFER_BIT,
					VulkanicAPI.GL_NEAREST
				);
				int r = VulkanicAPI.getError(ctx);
				if (r != 0) {
					throw new IllegalStateException(
						"Couldn't perform copyToTexture for texture " + gpuTexture.getLabel() + " to " + gpuTexture2.getLabel() + ": GL error " + r
					);
				}
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel " + i + ", must be >= 0 and < " + gpuTexture.getMipLevels() + " and < " + gpuTexture2.getMipLevels());
		}
	}

	@Override
	public void presentTexture(GpuTextureView gpuTextureView) {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (!gpuTextureView.texture().getFormat().hasColorAspect()) {
			throw new IllegalStateException("Cannot present a non-color texture!");
		} else if ((gpuTextureView.texture().usage() & 8) == 0) {
			throw new IllegalStateException("Color texture must have USAGE_RENDER_ATTACHMENT to presented to the screen");
		} else if (gpuTextureView.texture().getDepthOrLayers() > 1) {
			throw new UnsupportedOperationException("Textures with multiple depths or layers are not yet supported for presentation");
		} else {
			CommandContext ctx = commandContext();
			VulkanicCoreAPI.presentTextureToScreen(ctx, gpuTextureView);
		}
	}

	@Override
	public GpuFence createFence() {
		if (this.inRenderPass) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			return new GlFence();
		}
	}

	protected <T> void executeDrawMultiple(
		GlRenderPass glRenderPass,
		Collection<RenderPass.Draw<T>> collection,
		@Nullable GpuBuffer gpuBuffer,
		@Nullable VertexFormat.IndexType indexType,
		Collection<String> collection2,
		T object
	) {
		if (this.trySetup(glRenderPass, collection2)) {
			if (indexType == null) {
				indexType = VertexFormat.IndexType.SHORT;
			}

			for (RenderPass.Draw<T> draw : collection) {
				VertexFormat.IndexType indexType2 = draw.indexType() == null ? indexType : draw.indexType();
				glRenderPass.setIndexBuffer(draw.indexBuffer() == null ? gpuBuffer : draw.indexBuffer(), indexType2);
				glRenderPass.setVertexBuffer(draw.slot(), draw.vertexBuffer());
				if (GlRenderPass.VALIDATION) {
					if (glRenderPass.indexBuffer == null) {
						throw new IllegalStateException("Missing index buffer");
					}

					if (glRenderPass.indexBuffer.isClosed()) {
						throw new IllegalStateException("Index buffer has been closed!");
					}

					if (glRenderPass.vertexBuffers[0] == null) {
						throw new IllegalStateException("Missing vertex buffer at slot 0");
					}

					if (glRenderPass.vertexBuffers[0].isClosed()) {
						throw new IllegalStateException("Vertex buffer at slot 0 has been closed!");
					}
				}

				BiConsumer<T, RenderPass.UniformUploader> biConsumer = draw.uniformUploaderConsumer();
				if (biConsumer != null) {
					biConsumer.accept(object, (RenderPass.UniformUploader)(string, gpuBufferSlice) -> {
						if (glRenderPass.pipeline.program().getUniform(string) instanceof Uniform.Ubo(int i)) {
							VulkanicAPI.bindUniformBufferRange(VulkanicAPI.getCommandContext(), i, ((GlBuffer)gpuBufferSlice.buffer()).handle, gpuBufferSlice.offset(), gpuBufferSlice.length());
						}
					});
				}

				this.drawFromBuffers(glRenderPass, 0, draw.firstIndex(), draw.indexCount(), indexType2, glRenderPass.pipeline, 1);
			}
		}
	}

	protected void executeDraw(GlRenderPass glRenderPass, int i, int j, int k, @Nullable VertexFormat.IndexType indexType, int l) {
		if (this.trySetup(glRenderPass, Collections.emptyList())) {
			if (GlRenderPass.VALIDATION) {
				if (indexType != null) {
					if (glRenderPass.indexBuffer == null) {
						throw new IllegalStateException("Missing index buffer");
					}

					if (glRenderPass.indexBuffer.isClosed()) {
						throw new IllegalStateException("Index buffer has been closed!");
					}

					if ((glRenderPass.indexBuffer.usage() & 64) == 0) {
						throw new IllegalStateException("Index buffer must have GpuBuffer.USAGE_INDEX!");
					}
				}

				GlRenderPipeline glRenderPipeline = glRenderPass.pipeline;
				if (glRenderPass.vertexBuffers[0] == null && glRenderPipeline != null && !glRenderPipeline.info().getVertexFormat().getElements().isEmpty()) {
					throw new IllegalStateException("Vertex format contains elements but vertex buffer at slot 0 is null");
				}

				if (glRenderPass.vertexBuffers[0] != null && glRenderPass.vertexBuffers[0].isClosed()) {
					throw new IllegalStateException("Vertex buffer at slot 0 has been closed!");
				}

				if (glRenderPass.vertexBuffers[0] != null && (glRenderPass.vertexBuffers[0].usage() & 32) == 0) {
					throw new IllegalStateException("Vertex buffer must have GpuBuffer.USAGE_VERTEX!");
				}
			}

			this.drawFromBuffers(glRenderPass, i, j, k, indexType, glRenderPass.pipeline, l);
		}
	}

	private void drawFromBuffers(
		GlRenderPass glRenderPass, int i, int j, int k, @Nullable VertexFormat.IndexType indexType, GlRenderPipeline glRenderPipeline, int l
	) {
		GlBuffer vertexBuffer = (GlBuffer)glRenderPass.vertexBuffers[0];
		this.device.vertexArrayCache().bindVertexArray(glRenderPipeline.info().getVertexFormat(), vertexBuffer);
		// Obtain a single context for all VulkanicAPI calls in this draw — avoids repeated singleton lookups
		// and makes explicit that every draw operation flows through VulkanicAPI → OpenGLBackend.
		net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();
		if (vertexBuffer != null) {
			VulkanicAPI.bindBuffer(ctx, VulkanicBufferTarget.VERTEX, vertexBuffer.handle);
		}
		int glPrimitiveMode = GlConst.toGl(glRenderPipeline.info().getVertexFormatMode());
		java.util.Optional<VulkanicPrimitiveMode> typedPrimitiveMode = VulkanicPrimitiveMode.fromLegacyGlConstant(glPrimitiveMode);
		if (indexType != null) {
			// Route index buffer bind through VulkanicAPI rather than the GlStateManager wrapper.
			VulkanicAPI.bindIndexBuffer(ctx, ((GlBuffer)glRenderPass.indexBuffer).handle);
			VulkanicIndexType vkIndexType = toVulkanicIndexType(indexType);
			long indexOffset = (long)j * indexType.bytes;
			if (l > 1) {
				if (i > 0) {
					typedPrimitiveMode.ifPresentOrElse(
						typedMode -> VulkanicAPI.drawIndexedInstancedBaseVertex(ctx, typedMode, k, vkIndexType, indexOffset, l, i),
						() -> VulkanicAPI.drawIndexedInstancedBaseVertex(ctx, glPrimitiveMode, k, vkIndexType, indexOffset, l, i)
					);
				} else {
					typedPrimitiveMode.ifPresentOrElse(
						typedMode -> VulkanicAPI.drawIndexedInstanced(ctx, typedMode, k, vkIndexType, indexOffset, l),
						() -> VulkanicAPI.drawIndexedInstanced(ctx, glPrimitiveMode, k, vkIndexType, indexOffset, l)
					);
				}
			} else if (i > 0) {
				typedPrimitiveMode.ifPresentOrElse(
					typedMode -> VulkanicAPI.drawIndexedBaseVertex(ctx, typedMode, k, vkIndexType, indexOffset, i),
					() -> VulkanicAPI.drawIndexedBaseVertex(ctx, glPrimitiveMode, k, vkIndexType, indexOffset, i)
				);
			} else {
				// Route non-instanced indexed draw through VulkanicAPI.
				// Iris: apply tessellation mode override for the non-instanced path, matching the
				// GlStateManager._drawElements Iris override that this replaces.
				boolean useTessellationMode = typedPrimitiveMode
					.map(mode -> mode == VulkanicPrimitiveMode.TRIANGLES)
					.orElse(false)
					&& net.irisshaders.iris.vertices.ImmediateState.usingTessellation;
				if (useTessellationMode) {
					VulkanicAPI.drawElements(ctx, VulkanicPrimitiveMode.PATCHES, k, vkIndexType, indexOffset);
				} else {
					typedPrimitiveMode.ifPresentOrElse(
						typedMode -> VulkanicAPI.drawElements(ctx, typedMode, k, vkIndexType, indexOffset),
						() -> VulkanicAPI.drawElements(ctx, glPrimitiveMode, k, vkIndexType, indexOffset)
					);
				}
			}
		} else if (l > 1) {
			typedPrimitiveMode.ifPresentOrElse(
				typedMode -> VulkanicAPI.drawArraysInstanced(ctx, typedMode, i, k, l),
				() -> VulkanicAPI.drawArraysInstanced(ctx, glPrimitiveMode, i, k, l)
			);
		} else {
			// Route non-instanced non-indexed draw through VulkanicAPI rather than the GlStateManager wrapper.
			typedPrimitiveMode.ifPresentOrElse(
				typedMode -> VulkanicAPI.drawArrays(ctx, typedMode, i, k),
				() -> VulkanicAPI.drawArrays(ctx, glPrimitiveMode, i, k)
			);
		}
	}

	private static VulkanicIndexType toVulkanicIndexType(VertexFormat.IndexType indexType) {
		return switch (indexType) {
			case SHORT -> VulkanicIndexType.SHORT;
			case INT -> VulkanicIndexType.INT;
		};
	}

	private boolean trySetup(GlRenderPass glRenderPass, Collection<String> collection) {
		// Iris: From MixinGlCommandEncoder - Unlock depth color and handle custom passes
		net.irisshaders.iris.gl.blending.DepthColorStorage.unlockDepthColor();
		
		if (commandContext().isImmediate()
			&& net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()
			&& !(glRenderPass.pipeline.program() instanceof net.irisshaders.iris.pipeline.programs.ExtendedShader)) {
			VulkanicAPI.bindFramebuffer(VulkanicAPI.getCommandContext(), iris$tempFBO);
		}
		
		iris$lastPass = glRenderPass;
		
		// Handle Iris custom pass
		if (glRenderPass.iris$getCustomPass() != null) {
			this.lastProgram = null;
			net.vulkanic.CommandContext ctx = VulkanicAPI.getCommandContext();
			
			((net.irisshaders.iris.mixinterface.CustomPass)glRenderPass.iris$getCustomPass()).setupState();
			
			RenderPipeline renderPipeline = glRenderPass.pipeline.info();
			
			if (glRenderPass.isScissorEnabled()) {
				VulkanicAPI.setScissorTestEnabled(ctx, true);
				VulkanicAPI.setDynamicScissor(ctx, glRenderPass.getScissorX(), glRenderPass.getScissorY(), glRenderPass.getScissorWidth(), glRenderPass.getScissorHeight());
			} else {
				VulkanicAPI.setScissorTestEnabled(ctx, false);
			}
			
			if (this.lastPipeline != renderPipeline) {
				this.lastPipeline = renderPipeline;
				
				if (renderPipeline.getDepthTestFunction() != DepthTestFunction.NO_DEPTH_TEST) {
					VulkanicAPI.setDepthTestEnabled(ctx, true);
					VulkanicAPI.setDepthFunc(ctx, toVulkanicDepthCompareOp(renderPipeline.getDepthTestFunction()));
				} else {
					VulkanicAPI.setDepthTestEnabled(ctx, false);
				}
				
				if (renderPipeline.isCull()) {
					VulkanicAPI.setCullFaceEnabled(ctx, true);
				} else {
					VulkanicAPI.setCullFaceEnabled(ctx, false);
				}
				
				VulkanicAPI.setPolygonMode(ctx, VulkanicPolygonFace.FRONT_AND_BACK, GlConst.toGl(renderPipeline.getPolygonMode()));
				net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(renderPipeline.isWriteDepth());
				net.irisshaders.iris.gl.blending.DepthColorStorage.setColorMask(renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteAlpha());
				
				if (renderPipeline.getDepthBiasConstant() == 0.0F && renderPipeline.getDepthBiasScaleFactor() == 0.0F) {
					VulkanicAPI.setPolygonOffsetFillEnabled(ctx, false);
				} else {
					VulkanicAPI.setPolygonOffset(ctx, renderPipeline.getDepthBiasScaleFactor(), renderPipeline.getDepthBiasConstant());
					VulkanicAPI.setPolygonOffsetFillEnabled(ctx, true);
				}
				
				switch (renderPipeline.getColorLogic()) {
					case NONE:
						VulkanicAPI.setColorLogicOpEnabled(ctx, false);
						break;
					case OR_REVERSE:
						VulkanicAPI.setColorLogicOpEnabled(ctx, true);
						VulkanicAPI.setLogicOp(ctx, VulkanicLogicOp.OR_REVERSE);
				}
			}
			
			return true;
		}
		
		if (GlRenderPass.VALIDATION) {
			if (glRenderPass.pipeline == null) {
				throw new IllegalStateException("Can't draw without a render pipeline");
			}

			if (glRenderPass.pipeline.program() == GlProgram.INVALID_PROGRAM) {
				throw new IllegalStateException("Pipeline contains invalid shader program");
			}

			for (RenderPipeline.UniformDescription uniformDescription : glRenderPass.pipeline.info().getUniforms()) {
				GpuBufferSlice gpuBufferSlice = (GpuBufferSlice)glRenderPass.uniforms.get(uniformDescription.name());
				if (!collection.contains(uniformDescription.name())) {
					if (gpuBufferSlice == null) {
						throw new IllegalStateException("Missing uniform " + uniformDescription.name() + " (should be " + uniformDescription.type() + ")");
					}

					if (uniformDescription.type() == UniformType.UNIFORM_BUFFER) {
						if (gpuBufferSlice.buffer().isClosed()) {
							throw new IllegalStateException("Uniform buffer " + uniformDescription.name() + " is already closed");
						}

						if ((gpuBufferSlice.buffer().usage() & 128) == 0) {
							throw new IllegalStateException("Uniform buffer " + uniformDescription.name() + " must have GpuBuffer.USAGE_UNIFORM");
						}
					}

					if (uniformDescription.type() == UniformType.TEXEL_BUFFER) {
						if (gpuBufferSlice.offset() != 0 || gpuBufferSlice.length() != gpuBufferSlice.buffer().size()) {
							throw new IllegalStateException("Uniform texel buffers do not support a slice of a buffer, must be entire buffer");
						}

						if (uniformDescription.textureFormat() == null) {
							throw new IllegalStateException("Invalid uniform texel buffer " + uniformDescription.name() + " (missing a texture format)");
						}
					}
				}
			}

			for (Entry<String, Uniform> entry : glRenderPass.pipeline.program().getUniforms().entrySet()) {
				if (entry.getValue() instanceof Uniform.Sampler) {
					String string = (String)entry.getKey();
					GlTextureView glTextureView = (GlTextureView)glRenderPass.samplers.get(string);
					if (glTextureView == null) {
						throw new IllegalStateException("Missing sampler " + string);
					}

					if (glTextureView.isClosed()) {
						throw new IllegalStateException("Sampler " + string + " (" + glTextureView.texture().getLabel() + ") has been closed!");
					}

					if ((glTextureView.texture().usage() & 4) == 0) {
						throw new IllegalStateException("Sampler " + string + " (" + glTextureView.texture().getLabel() + ") must have USAGE_TEXTURE_BINDING!");
					}
				}
			}

			if (glRenderPass.pipeline.info().wantsDepthTexture() && !glRenderPass.hasDepthTexture()) {
				LOGGER.warn("Render pipeline {} wants a depth texture but none was provided - this is probably a bug", glRenderPass.pipeline.info().getLocation());
			}
		} else if (glRenderPass.pipeline == null || glRenderPass.pipeline.program() == GlProgram.INVALID_PROGRAM) {
			return false;
		}

		RenderPipeline renderPipeline = glRenderPass.pipeline.info();
		GlProgram glProgram = glRenderPass.pipeline.program();
		boolean logParticlePipeline = DEBUG_PARTICLE_VULKAN_BIND_LOGS < 80
			&& renderPipeline.getLocation().toString().contains("pipeline/")
			&& renderPipeline.getLocation().toString().contains("particle");
		this.applyPipelineState(renderPipeline);
		CommandContext ctx = commandContext();
		boolean bl = this.lastProgram != glProgram;
		if (bl) {
			net.irisshaders.iris.gl.IrisRenderSystem.useProgram(glProgram.getProgramId());
			this.lastProgram = glProgram;
		}

		boolean immediateSeamHasCompleteCoverage = false;
		if (!ctx.isImmediate()) {
			this.prepareTexelBufferBindingsForVulkanDescriptors(glRenderPass, ctx);
			this.prepareSamplerBindingsForVulkanDescriptors(glRenderPass, ctx);
		}
		PipelineResourceBindingSubmission submission = this.buildPipelineResourceBindings(glRenderPass);
		if (!ctx.isImmediate() && DEBUG_PIPELINE_BIND_LOGS < 80) {
			DEBUG_PIPELINE_BIND_LOGS++;
			LOGGER.info(
				"Vulkan trySetup bind#{} pipeline={} submissionPresent={} layoutBindings={} writeColor={} writeDepth={} depthTest={} cull={} blend={}",
				DEBUG_PIPELINE_BIND_LOGS,
				glRenderPass.pipeline.info().getLocation(),
				submission != null,
				glRenderPass.pipeline.descriptor().getResourceLayout().bindings().size(),
				renderPipeline.isWriteColor(),
				renderPipeline.isWriteDepth(),
				renderPipeline.getDepthTestFunction(),
				renderPipeline.isCull(),
				renderPipeline.getBlendFunction().isPresent()
			);
		}
		if (submission != null) {
			net.vulkanic.PipelineHandle pipelineHandle;
			if (ctx.isImmediate()) {
				pipelineHandle = new net.vulkanic.backends.opengl.OpenGLPipelineHandle(glRenderPass.pipeline);
			} else {
				pipelineHandle = VulkanicAPI.resolvePipelineHandle(
						glRenderPass.pipeline.info(), glRenderPass.pipeline.descriptor());
				if (DEBUG_PIPELINE_BIND_LOGS < 80) {
					DEBUG_PIPELINE_BIND_LOGS++;
					LOGGER.info(
						"Vulkan trySetup bind#{} pipeline={} resolvedPipelineHandle={} completeCoverage={}",
						DEBUG_PIPELINE_BIND_LOGS,
						glRenderPass.pipeline.info().getLocation(),
						pipelineHandle != null,
						submission.completeCoverage()
					);
				}
			}
			if (pipelineHandle != null) {
				if (logParticlePipeline) {
					GpuTextureView sampler0 = glRenderPass.samplers.get("Sampler0");
					GpuTextureView sampler2 = glRenderPass.samplers.get("Sampler2");
					LOGGER.info(
						"Particle Vulkan trySetup pipeline={} immediate={} sampler0TexId={} sampler0Label={} sampler2TexId={} sampler2Label={} completeCoverage={}",
						renderPipeline.getLocation(),
						ctx.isImmediate(),
						sampler0 != null ? VulkanicCoreAPI.textureId(sampler0.texture()) : 0,
						sampler0 != null ? sampler0.texture().getLabel() : "null",
						sampler2 != null ? VulkanicCoreAPI.textureId(sampler2.texture()) : 0,
						sampler2 != null ? sampler2.texture().getLabel() : "null",
						submission.completeCoverage()
					);
				}
				VulkanicAPI.bindPipelineResources(
					ctx,
					pipelineHandle,
					submission.descriptor(),
					submission.bindings()
				);
				if (ctx.isImmediate() && submission.completeCoverage()) {
					immediateSeamHasCompleteCoverage = true;
				}
			}
		}

		for (Entry<String, Uniform> entry2 : glProgram.getUniforms().entrySet()) {
			String string2 = (String)entry2.getKey();
			boolean bl2 = glRenderPass.dirtyUniforms.contains(string2);
			switch ((Uniform)entry2.getValue()) {
				case Uniform.Ubo(int var61):
					int var39 = var61;
					if (!immediateSeamHasCompleteCoverage && bl2) {
						GpuBufferSlice gpuBufferSlice2 = (GpuBufferSlice)glRenderPass.uniforms.get(string2);
						if (gpuBufferSlice2 != null) {
							VulkanicAPI.bindUniformBufferRange(ctx, var39, ((GlBuffer)gpuBufferSlice2.buffer()).handle, gpuBufferSlice2.offset(), gpuBufferSlice2.length());
						}
					}
					break;
				case Uniform.Utb(int var41, int var42, TextureFormat var43, int var59):
					int var44 = var59;
					if (!immediateSeamHasCompleteCoverage && (bl || bl2)) {
						VulkanicAPI.setUniform1i(ctx, var41, var42);
					}
						net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(var42);
						VulkanicAPI.bindTextureBuffer(ctx, var44);
					if (bl2) {
						GpuBufferSlice gpuBufferSlice3 = (GpuBufferSlice)glRenderPass.uniforms.get(string2);
							VulkanicAPI.bindTextureBufferData(ctx, GlConst.toGlInternalId(var43), ((GlBuffer)gpuBufferSlice3.buffer()).handle);
					}
					break;
				case Uniform.Sampler(int glTextureView2, int var51):
					int var46 = var51;
					GlTextureView glTextureView2x = (GlTextureView)glRenderPass.samplers.get(string2);
					if (glTextureView2x == null) {
						break;
					}

					if (!immediateSeamHasCompleteCoverage && (bl || bl2)) {
						VulkanicAPI.setUniform1i(ctx, glTextureView2, var46);
					}

					net.irisshaders.iris.gl.IrisRenderSystem.setActiveTextureUnitIndex(var46);
					GpuTexture texture = glTextureView2x.texture();
					int textureHandle = VulkanicCoreAPI.textureId(texture);
					net.irisshaders.iris.gl.IrisRenderSystem.setTextureBinding(var46, textureHandle);
					VulkanicTextureTarget textureTarget;
					if ((texture.usage() & 16) != 0) {
						textureTarget = VulkanicTextureTarget.TEXTURE_CUBE_MAP;
						VulkanicAPI.bindCubemapTexture(ctx, textureHandle);
					} else {
						textureTarget = VulkanicTextureTarget.TEXTURE_2D;
						VulkanicAPI.bindTexture2D(ctx, textureHandle);
					}

					VulkanicAPI.setTextureParameter(ctx, textureTarget, VulkanicTextureParameterName.BASE_LEVEL, glTextureView2x.baseMipLevel());
					VulkanicAPI.setTextureParameter(ctx, textureTarget, VulkanicTextureParameterName.MAX_LEVEL, glTextureView2x.baseMipLevel() + glTextureView2x.mipLevels() - 1);
					texture.flushModeChanges(textureTarget);
					break;
				default:
					throw new MatchException(null, null);
			}
		}

		glRenderPass.dirtyUniforms.clear();
		if (glRenderPass.isScissorEnabled()) {
			VulkanicAPI.setScissorTestEnabled(ctx, true);
			VulkanicAPI.setDynamicScissor(ctx, glRenderPass.getScissorX(), glRenderPass.getScissorY(), glRenderPass.getScissorWidth(), glRenderPass.getScissorHeight());
		} else {
			VulkanicAPI.setScissorTestEnabled(ctx, false);
		}

		// Iris: From MixinGlCommandEncoder - Setup IrisProgram state if needed
		if (glRenderPass.pipeline.program() instanceof net.irisshaders.iris.pipeline.programs.IrisProgram is && !is.iris$isSetUp()) {
			GpuTextureView sam = glRenderPass.samplers.get("Sampler0");
			if (sam != null) {
				net.irisshaders.iris.pbr.TextureTracker.INSTANCE.onSetShaderTexture(0, sam);
			}
			is.iris$setupState();
			iris$programsToClear.add(is);
		}

		return true;
	}

	@Override
	public void applyPipelineState(RenderPipeline renderPipeline) {
		if (this.lastPipeline != renderPipeline) {
			CommandContext ctx = commandContext();
			this.lastPipeline = renderPipeline;
			if (renderPipeline.getDepthTestFunction() != DepthTestFunction.NO_DEPTH_TEST) {
				VulkanicAPI.setDepthTestEnabled(ctx, true);
				VulkanicAPI.setDepthFunc(ctx, toVulkanicDepthCompareOp(renderPipeline.getDepthTestFunction()));
			} else {
				VulkanicAPI.setDepthTestEnabled(ctx, false);
			}

			if (renderPipeline.isCull()) {
				VulkanicAPI.setCullFaceEnabled(ctx, true);
			} else {
				VulkanicAPI.setCullFaceEnabled(ctx, false);
			}

			if (renderPipeline.getBlendFunction().isPresent()) {
				net.irisshaders.iris.gl.blending.BlendModeStorage.setBlendEnabled(true);
				BlendFunction blendFunction = (BlendFunction)renderPipeline.getBlendFunction().get();
				net.irisshaders.iris.gl.blending.BlendModeStorage.setBlendFuncSeparate(
					GlConst.toGl(blendFunction.sourceColor()),
					GlConst.toGl(blendFunction.destColor()),
					GlConst.toGl(blendFunction.sourceAlpha()),
					GlConst.toGl(blendFunction.destAlpha())
				);
			} else {
				net.irisshaders.iris.gl.blending.BlendModeStorage.setBlendEnabled(false);
			}

			VulkanicAPI.setPolygonMode(ctx, VulkanicPolygonFace.FRONT_AND_BACK, GlConst.toGl(renderPipeline.getPolygonMode()));
			net.irisshaders.iris.gl.blending.DepthColorStorage.setDepthMask(renderPipeline.isWriteDepth());
			net.irisshaders.iris.gl.blending.DepthColorStorage.setColorMask(renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteAlpha());
			if (renderPipeline.getDepthBiasConstant() == 0.0F && renderPipeline.getDepthBiasScaleFactor() == 0.0F) {
				VulkanicAPI.setPolygonOffsetFillEnabled(ctx, false);
			} else {
				VulkanicAPI.setPolygonOffset(ctx, renderPipeline.getDepthBiasScaleFactor(), renderPipeline.getDepthBiasConstant());
				VulkanicAPI.setPolygonOffsetFillEnabled(ctx, true);
			}

			switch (renderPipeline.getColorLogic()) {
				case NONE:
					VulkanicAPI.setColorLogicOpEnabled(ctx, false);
					break;
				case OR_REVERSE:
					VulkanicAPI.setColorLogicOpEnabled(ctx, true);
					VulkanicAPI.setLogicOp(ctx, VulkanicLogicOp.OR_REVERSE);
			}
		}
	}

	@Override
	public void invalidateCachedProgramBinding() {
		this.lastProgram = null;
	}

	public void finishRenderPass() {
		// Iris: From MixinGlCommandEncoder - Clear IrisProgram state
		iris$programsToClear.forEach(net.irisshaders.iris.pipeline.programs.IrisProgram::iris$clearState);
		iris$programsToClear.clear();

		this.inRenderPass = false;

		if (this.activeVulkanicRenderPass != null) {
			// Normal path: VulkanicRenderPass.close() unbinds and deletes the FBO it created.
			net.vulkanic.CommandContext renderPassCtx = this.activeRenderPassContext;
			this.activeVulkanicRenderPass.close();
			this.activeVulkanicRenderPass = null;
			if (renderPassCtx != null) {
				VulkanicAPI.submitCommandBuffer(renderPassCtx);
			}
		} else if (!net.irisshaders.iris.vertices.ImmediateState.safeToMultiply) {
			// Iris shadow/safeMultiply path: manually unbind the GlTexture cached FBO.
			// Don't unbind when in safe-multiply state (Iris manages that separately).
			VulkanicAPI.bindDefaultFramebuffer(commandContext());
		}
		this.activeRenderPassContext = null;

		this.device.debugLabels().popDebugGroup();
	}

	private static VulkanicDepthCompareOp toVulkanicDepthCompareOp(DepthTestFunction depthTestFunction) {
		return switch (depthTestFunction) {
			case LESS_DEPTH_TEST -> VulkanicDepthCompareOp.LESS;
			case LEQUAL_DEPTH_TEST -> VulkanicDepthCompareOp.LEQUAL;
			case GREATER_DEPTH_TEST -> VulkanicDepthCompareOp.GREATER;
			case EQUAL_DEPTH_TEST -> VulkanicDepthCompareOp.EQUAL;
			case NO_DEPTH_TEST -> throw new IllegalArgumentException("NO_DEPTH_TEST has no depth compare op");
		};
	}

	/**
	 * Returns the active {@link net.vulkanic.VulkanicRenderPass} for the current render pass,
	 * or {@code null} when no render pass is active or when Iris is managing the FBO directly
	 * (shadow rendering / safeMultiply path).
	 *
	 * <p>This accessor exists so that future code paths can interact with the Vulkanic
	 * render-pass abstraction during an active render pass without casting or coupling to
	 * {@link GlCommandEncoder} internals.
	 */
	@Nullable
	public net.vulkanic.VulkanicRenderPass getActiveVulkanicRenderPass() {
		return activeVulkanicRenderPass;
	}

	protected GlDevice getDevice() {
		return this.device;
	}
}
