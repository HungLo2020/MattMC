package net.vulkanic.backends.vulkan;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.pipeline.CompiledRenderPipeline;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.GpuDevice;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
final class VulkanCompatibilityGpuDevice implements GpuDevice {
	private final VulkanBackend backend;
	private final net.blaze3d.opengl.GlDevice compatibilityDevice;

	VulkanCompatibilityGpuDevice(VulkanBackend backend, net.blaze3d.opengl.GlDevice compatibilityDevice) {
		this.backend = backend;
		this.compatibilityDevice = compatibilityDevice;
	}

	@Override
	public CommandEncoder createCommandEncoder() {
		return this.backend.createCommandEncoder();
	}

	@Override
	public GpuTexture createTexture(@Nullable Supplier<String> supplier, int i, TextureFormat textureFormat, int j, int k, int l, int m) {
		return this.backend.createTexture(supplier, i, textureFormat, j, k, l, m);
	}

	@Override
	public GpuTexture createTexture(@Nullable String string, int i, TextureFormat textureFormat, int j, int k, int l, int m) {
		return this.backend.createTexture(string, i, textureFormat, j, k, l, m);
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture) {
		return this.backend.createTextureView(gpuTexture);
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture, int i, int j) {
		return this.backend.createTextureView(gpuTexture, i, j);
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, int j) {
		return this.backend.createBuffer(supplier, i, j);
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, ByteBuffer byteBuffer) {
		return this.backend.createBuffer(supplier, i, byteBuffer);
	}

	@Override
	public String getImplementationInformation() {
		return this.backend.isNativeVulkanReady()
			? "Vulkan backend selected (backend-owned compatibility device, native runtime ready)"
			: "Vulkan backend selected (backend-owned compatibility device, native runtime not yet ready)";
	}

	@Override
	public List<String> getLastDebugMessages() {
		return this.compatibilityDevice.getLastDebugMessages();
	}

	@Override
	public boolean isDebuggingEnabled() {
		return this.compatibilityDevice.isDebuggingEnabled();
	}

	@Override
	public String getVendor() {
		return this.backend.getBackendVendorName();
	}

	@Override
	public String getBackendName() {
		return "Vulkan";
	}

	@Override
	public String getVersion() {
		return this.backend.getBackendVersionName();
	}

	@Override
	public String getRenderer() {
		return this.backend.getBackendRendererName();
	}

	@Override
	public GpuDeviceInfo getDeviceInfo() {
		return this.backend.getBackendDeviceInfo();
	}

	@Override
	public int getMaxTextureSize() {
		return this.backend.getBackendMaxTextureSize();
	}

	@Override
	public int getUniformOffsetAlignment() {
		return this.backend.getBackendUniformOffsetAlignment();
	}

	@Override
	public CompiledRenderPipeline precompilePipeline(RenderPipeline renderPipeline, @Nullable BiFunction<ResourceLocation, ShaderType, String> biFunction) {
		return this.backend.precompileRenderPipeline(renderPipeline, biFunction);
	}

	@Override
	public void clearPipelineCache() {
		this.backend.clearPrecompiledPipelineCache();
		this.compatibilityDevice.clearPipelineCache();
	}

	@Override
	public List<String> getEnabledExtensions() {
		return this.backend.getBackendEnabledExtensions();
	}

	@Override
	public List<String> getOptionalFeatureNames() {
		return this.backend.getBackendOptionalFeatureNames();
	}

	@Override
	public void close() {
		this.compatibilityDevice.close();
	}
}