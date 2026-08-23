package net.irisshaders.iris.pathways;

import com.google.common.collect.ImmutableSet;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BlendModeStorage;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.minecraft.client.Minecraft;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicTextureUploadFormat;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntSupplier;

public class CenterDepthSampler {
	private static final CustomPass EMPTY_STATE = new CustomPass() {
		@Override
		public void setupState() {

		}
	};
	private final Program program;
	private final GlFramebuffer framebuffer;
	private final int texture;
	private final int altTexture;
	private final boolean smoothingProgramAvailable;
	private boolean hasFirstSample;
	private boolean everRetrieved;
	private boolean destroyed;

	public CenterDepthSampler(IntSupplier depthSupplier, float halfLife) {
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris center-depth resources are unavailable while Rust owns whole-frame presentation");
		}
		this.texture = IrisRenderSystem.createTextureId();
		this.altTexture = IrisRenderSystem.createTextureId();
		this.framebuffer = new GlFramebuffer();

		setupColorTexture(texture);
		setupColorTexture(altTexture);
		var ctx = VulkanicAPI.getCommandContext();
		VulkanicAPI.bindTexture2D(ctx, 0);

		this.framebuffer.addColorAttachment(0, texture);

		if (VulkanicAPI.isVulkanBackendSelected()) {
			// This pass relies on legacy standalone-uniform program plumbing that is still
			// being migrated for Vulkan GLSL rules. Keep the center-depth textures alive
			// but skip program creation/use on Vulkan to avoid startup crashes.
			this.program = null;
			this.smoothingProgramAvailable = false;
			return;
		}

		this.smoothingProgramAvailable = true;
		ProgramBuilder builder;

		try {
			String fsh = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/centerDepth.fsh"))), StandardCharsets.UTF_8);
			String vsh = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/centerDepth.vsh"))), StandardCharsets.UTF_8);

			builder = ProgramBuilder.begin("centerDepthSmooth", vsh, null, fsh, ImmutableSet.of(0, 1, 2));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		builder.addDynamicSampler(depthSupplier, "depth");
		builder.addDynamicSampler(() -> altTexture, "altDepth");
		this.program = builder.build();
	}

	public void sampleCenterDepth() {
		if (!smoothingProgramAvailable) {
			return;
		}

		if ((hasFirstSample && (!everRetrieved)) || destroyed) {
			// If the shaderpack isn't reading center depth values, don't bother sampling it
			// This improves performance with most shaderpacks
			return;
		}

		hasFirstSample = true;

		GpuBuffer indices = VulkanicAPI.getSequentialBuffer(VertexFormat.Mode.QUADS).getBuffer(6);
		VertexFormat.IndexType type = VulkanicAPI.getSequentialBuffer(VertexFormat.Mode.QUADS).type();
		BlendModeOverride.restore();

		BlendModeStorage.setBlendEnabled(false);
		try (RenderPass renderPass = VulkanicAPI.createRenderPass(() -> "centerDepthSmooth sampler", Minecraft.getInstance().getMainRenderTarget().getColorTextureView(), OptionalInt.empty())) {
			renderPass.setPipeline(CompositeRenderer.COMPOSITE_PIPELINE);
			renderPass.setIndexBuffer(indices, type);
			renderPass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad());

			renderPass.iris$setCustomPass(EMPTY_STATE);

			this.framebuffer.bind();
			this.program.use();

			var ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setDynamicViewport(ctx, 0, 0, 1, 1);

			renderPass.drawIndexed(0, 0, 6, 1);

			ProgramUniforms.clearActiveUniforms();
			ProgramSamplers.clearActiveSamplers();
			BlendModeOverride.restore();

		}

		// The API contract of DepthCopyStrategy claims it can only copy depth, however the 2 non-stencil methods used are entirely capable of copying color as of now.
		DepthCopyStrategy.fastest(false).copy(this.framebuffer, texture, null, altTexture, 1, 1);

	}

	public void setupColorTexture(int texture) {
		IrisRenderSystem.texImage2D(texture, 0, VulkanicTextureUploadFormat.RED32_SFLOAT, 1, 1, 0, null);

		IrisRenderSystem.setTextureLinearFiltering(texture);
		IrisRenderSystem.setTextureWrapMode2D(texture, true);
	}

	public int getCenterDepthTexture() {
		return altTexture;
	}

	public void setUsage(boolean usage) {
		everRetrieved |= usage;
	}

	public void destroy() {
		IrisRenderSystem.deleteTextureId(texture);
		IrisRenderSystem.deleteTextureId(altTexture);
		framebuffer.destroy();
		if (program != null) {
			program.destroy();
		}
		destroyed = true;
	}
}
