package net.blaze3d.systems;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.pipeline.CompiledRenderPipeline;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface GpuDevice {
	record GpuDeviceInfo(
		String backendName,
		String graphicsApiName,
		String vendor,
		String renderer,
		String driverVersion,
		boolean appliesOpenGlWarnlist,
		List<String> optionalFeatureNames
	) {
		public GpuDeviceInfo {
			backendName = backendName == null ? "" : backendName;
			graphicsApiName = graphicsApiName == null ? "" : graphicsApiName;
			vendor = vendor == null ? "" : vendor;
			renderer = renderer == null ? "" : renderer;
			driverVersion = driverVersion == null ? "" : driverVersion;
			optionalFeatureNames = optionalFeatureNames == null ? List.of() : List.copyOf(optionalFeatureNames);
		}

		public String rendererDisplayString() {
			return renderer.isBlank() ? vendor : renderer;
		}

		public String driverDisplayString() {
			if (graphicsApiName.isBlank()) {
				return driverVersion;
			}
			return driverVersion.isBlank() ? graphicsApiName : graphicsApiName + " " + driverVersion;
		}

		public String backendDisplayString() {
			String driverDisplay = driverDisplayString();
			if (backendName.isBlank()) {
				return driverDisplay;
			}
			if (driverDisplay.isBlank() || backendName.equals(graphicsApiName)) {
				return driverDisplay.isBlank() ? backendName : driverDisplay;
			}
			return backendName + " (" + driverDisplay + ")";
		}
	}

	CommandEncoder createCommandEncoder();

	GpuTexture createTexture(@Nullable Supplier<String> supplier, int i, TextureFormat textureFormat, int j, int k, int l, int m);

	GpuTexture createTexture(@Nullable String string, int i, TextureFormat textureFormat, int j, int k, int l, int m);

	GpuTextureView createTextureView(GpuTexture gpuTexture);

	GpuTextureView createTextureView(GpuTexture gpuTexture, int i, int j);

	GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, int j);

	GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, ByteBuffer byteBuffer);

	String getImplementationInformation();

	List<String> getLastDebugMessages();

	boolean isDebuggingEnabled();

	String getVendor();

	String getBackendName();

	String getVersion();

	String getRenderer();

	default GpuDeviceInfo getDeviceInfo() {
		return new GpuDeviceInfo(
			getBackendName(),
			getBackendName(),
			getVendor(),
			getRenderer(),
			getVersion(),
			shouldApplyOpenGlWarnlist(),
			getOptionalFeatureNames()
		);
	}

	default boolean shouldApplyOpenGlWarnlist() {
		return "OpenGL".equals(getBackendName());
	}

	default List<String> getOptionalFeatureNames() {
		return getEnabledExtensions();
	}

	int getMaxTextureSize();

	int getUniformOffsetAlignment();

	default CompiledRenderPipeline precompilePipeline(RenderPipeline renderPipeline) {
		return this.precompilePipeline(renderPipeline, null);
	}

	CompiledRenderPipeline precompilePipeline(RenderPipeline renderPipeline, @Nullable BiFunction<ResourceLocation, ShaderType, String> biFunction);

	void clearPipelineCache();

	List<String> getEnabledExtensions();

	void close();
}
