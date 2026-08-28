package net.irisshaders.iris.pathways.colorspace;

import com.google.common.collect.ImmutableSet;
import net.blaze3d.textures.GpuTexture;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.vulkanic.VulkanicAPI;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ColorSpaceComputeConverter implements ColorSpaceConverter {
	private int width;
	private int height;
	private ColorSpace colorSpace;
	private ComputeProgram program;

	private GpuTexture target;

	public ColorSpaceComputeConverter(int width, int height, ColorSpace colorSpace) {
		ensureJavaColorSpaceComputeAvailable();
		rebuildProgram(width, height, colorSpace);
	}

	public void rebuildProgram(int width, int height, ColorSpace colorSpace) {
		ensureJavaColorSpaceComputeAvailable();
		if (program != null) {
			program.destroy();
			program = null;
		}

		this.width = width;
		this.height = height;
		this.colorSpace = colorSpace;

		String source;
		try {
			source = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/colorSpace.csh"))), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		List<StringPair> defineList = new ArrayList<>();
		defineList.add(new StringPair("COMPUTE", ""));
		defineList.add(new StringPair("CURRENT_COLOR_SPACE", String.valueOf(colorSpace.ordinal())));

		for (ColorSpace space : ColorSpace.values()) {
			defineList.add(new StringPair(space.name(), String.valueOf(space.ordinal())));
		}
		source = JcppProcessor.glslPreprocessSource(source, defineList);

		ProgramBuilder builder = ProgramBuilder.beginCompute("colorSpaceCompute", source, ImmutableSet.of());
		builder.addTextureImage(() -> net.vulkanic.VulkanicCoreAPI.textureId(target), InternalTextureFormat.RGBA8, "readImage");
		this.program = builder.buildCompute();
	}

	public void process(GpuTexture targetImage) {
		ensureJavaColorSpaceComputeAvailable();
		if (colorSpace == ColorSpace.SRGB) return;

		this.target = targetImage;
		program.use();
		IrisRenderSystem.dispatchCompute(width / 8, height / 8, 1);
		IrisRenderSystem.memoryBarrierImageWritesVisibleToTextureSampling();
		ComputeProgram.unbind();
	}

	private static void ensureJavaColorSpaceComputeAvailable() {
		if (VulkanicAPI.isVulkanBackendSelected()
				|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			throw new IllegalStateException("Java Iris color-space compute is unavailable on the Rust Vulkan route");
		}
	}
}
