package net.irisshaders.iris.pathways.colorspace;

import com.google.common.collect.ImmutableSet;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.minecraft.client.Minecraft;
import net.vulkanic.VulkanicAPI;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import static net.irisshaders.iris.pipeline.CompositeRenderer.COMPOSITE_PIPELINE;

public class ColorSpaceFragmentConverter implements ColorSpaceConverter {
	private static final CustomPass EMPTY = new CustomPass() {
		@Override
		public void setupState() {

		}
	};
	private int width;
	private int height;
	private ColorSpace colorSpace;
	private Program program;
	private GlFramebuffer framebuffer;
	private int swapTexture;

	private GpuTexture target;

	public ColorSpaceFragmentConverter(int width, int height, ColorSpace colorSpace) {
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris color-space resources are unavailable on selected Vulkan");
		}
		rebuildProgram(width, height, colorSpace);
	}

	public void rebuildProgram(int width, int height, ColorSpace colorSpace) {
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris color-space resources are unavailable on selected Vulkan");
		}
		if (program != null) {
			program.destroy();
			program = null;
			framebuffer.destroy();
			framebuffer = null;
			IrisRenderSystem.deleteTextureId(swapTexture);
			swapTexture = 0;
		}

		this.width = width;
		this.height = height;
		this.colorSpace = colorSpace;

		String vertexSource;
		String source;
		try {
			vertexSource = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/colorSpace.vsh"))), StandardCharsets.UTF_8);
			source = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/colorSpace.csh"))), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		List<StringPair> defineList = new ArrayList<>();
		defineList.add(new StringPair("CURRENT_COLOR_SPACE", String.valueOf(colorSpace.ordinal())));

		for (ColorSpace space : ColorSpace.values()) {
			defineList.add(new StringPair(space.name(), String.valueOf(space.ordinal())));
		}
		source = JcppProcessor.glslPreprocessSource(source, defineList);

		ProgramBuilder builder = ProgramBuilder.begin("colorSpaceFragment", vertexSource, null, source, ImmutableSet.of());

		builder.uniformMatrix(UniformUpdateFrequency.ONCE, "projection", () -> new Matrix4f(2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, -1, -1, 0, 1));
		builder.addDynamicSampler(() -> net.vulkanic.VulkanicCoreAPI.textureId(target), "readImage");

		swapTexture = IrisRenderSystem.createTextureId();
		IrisRenderSystem.texImage2D(swapTexture, 0, VulkanicAPI.GL_RGBA8, width, height, 0, VulkanicAPI.GL_RGBA, VulkanicAPI.GL_UNSIGNED_BYTE, null);

		this.framebuffer = new GlFramebuffer();
		framebuffer.addColorAttachment(0, swapTexture);
		this.program = builder.build();
	}

	public void process(GpuTexture targetImage) {
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris color-space post-processing is unavailable while Rust owns whole-frame presentation");
		}
		if (colorSpace == ColorSpace.SRGB) return;

		this.target = targetImage;
		GpuBuffer indices = VulkanicAPI.getSequentialBuffer(VertexFormat.Mode.QUADS).getBuffer(6);
		VertexFormat.IndexType type = VulkanicAPI.getSequentialBuffer(VertexFormat.Mode.QUADS).type();

		try (RenderPass pass = VulkanicAPI.createRenderPass(() -> "Color space", Minecraft.getInstance().getMainRenderTarget().getColorTextureView(), OptionalInt.empty())) {
			pass.setPipeline(COMPOSITE_PIPELINE);
			pass.iris$setCustomPass(EMPTY);

			program.use();
			framebuffer.bind();

			pass.setIndexBuffer(indices, type);
			pass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad());

			pass.drawIndexed(0, 0, 6, 1);
		}
		Program.unbind();
		framebuffer.bindAsReadBuffer();
		IrisRenderSystem.copyTexSubImage2D(net.vulkanic.VulkanicCoreAPI.textureId(targetImage), 0, 0, 0, 0, 0, width, height);
	}
}
