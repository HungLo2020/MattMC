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
import net.vulkanic.GraphicsBackend;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
final class VulkanCompatibilityGpuDevice implements GpuDevice {
	private final VulkanBackend backend;
	private final net.blaze3d.opengl.GlDevice compatibilityDevice;
	private final GraphicsBackend compatibilityBackend;
	private final boolean compatibilityOnly;

	VulkanCompatibilityGpuDevice(
		VulkanBackend backend,
		net.blaze3d.opengl.GlDevice compatibilityDevice
	) {
		this(backend, compatibilityDevice, null, false);
	}

	VulkanCompatibilityGpuDevice(
		VulkanBackend backend,
		net.blaze3d.opengl.GlDevice compatibilityDevice,
		@Nullable GraphicsBackend compatibilityBackend,
		boolean compatibilityOnly
	) {
		this.backend = backend;
		this.compatibilityDevice = compatibilityDevice;
		this.compatibilityBackend = compatibilityBackend;
		this.compatibilityOnly = compatibilityOnly;
	}

	@Override
	public CommandEncoder createCommandEncoder() {
		if (this.compatibilityOnly) {
			return this.withCompatibilityBackend(this.compatibilityDevice::createCommandEncoder);
		}
		return this.backend.createCommandEncoder();
	}

	@Override
	public GpuTexture createTexture(@Nullable Supplier<String> supplier, int i, TextureFormat textureFormat, int j, int k, int l, int m) {
		if (this.compatibilityOnly) {
			return this.withCompatibilityBackend(() -> this.compatibilityDevice.createTexture(supplier, i, textureFormat, j, k, l, m));
		}
		return this.backend.createTexture(supplier, i, textureFormat, j, k, l, m);
	}

	@Override
	public GpuTexture createTexture(@Nullable String string, int i, TextureFormat textureFormat, int j, int k, int l, int m) {
		if (this.compatibilityOnly) {
			return this.withCompatibilityBackend(() -> this.compatibilityDevice.createTexture(string, i, textureFormat, j, k, l, m));
		}
		return this.backend.createTexture(string, i, textureFormat, j, k, l, m);
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture) {
		if (this.compatibilityOnly) {
			return this.withCompatibilityBackend(() -> this.compatibilityDevice.createTextureView(gpuTexture));
		}
		return this.backend.createTextureView(gpuTexture);
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture, int i, int j) {
		if (this.compatibilityOnly) {
			return this.withCompatibilityBackend(() -> this.compatibilityDevice.createTextureView(gpuTexture, i, j));
		}
		return this.backend.createTextureView(gpuTexture, i, j);
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, int j) {
		if (this.compatibilityOnly) {
			return this.withCompatibilityBackend(() -> this.compatibilityDevice.createBuffer(supplier, i, j));
		}
		return this.backend.createBuffer(supplier, i, j);
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, int i, ByteBuffer byteBuffer) {
		if (this.compatibilityOnly) {
			return this.withCompatibilityBackend(() -> this.compatibilityDevice.createBuffer(supplier, i, byteBuffer));
		}
		return this.backend.createBuffer(supplier, i, byteBuffer);
	}

	@Override
	public String getImplementationInformation() {
		if (this.compatibilityOnly) {
			return "Rust Vulkan whole-frame shell selected (Java compatibility device isolated on hidden OpenGL context)";
		}
		return this.backend.isNativeVulkanReady()
			? "Vulkan backend selected (backend-owned compatibility device, native runtime ready)"
			: "Vulkan backend selected (backend-owned compatibility device, native runtime not yet ready)";
	}

	@Override
	public List<String> getLastDebugMessages() {
		return this.backend.getBackendLastDebugMessages();
	}

	@Override
	public boolean isDebuggingEnabled() {
		return this.backend.isBackendDebuggingEnabled();
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
		try {
			// Teardown is the one compatibility operation that remains legal after
			// presentation ownership has moved to Rust.  It only releases the
			// bootstrap objects; it must never be reachable from frame rendering.
			this.withCompatibilityBackendForTeardown(() -> {
				this.compatibilityDevice.close();
				return null;
			});
		} finally {
			try {
				this.backend.releaseCompatibilityDevice(this.compatibilityDevice);
			} finally {
				if (this.compatibilityOnly) {
					this.backend.cleanupRendererBootstrapResources();
				}
			}
		}
	}

	private <T> T withCompatibilityBackend(Supplier<T> action) {
		if (this.compatibilityOnly
			&& net.vulkanic.bridge.RustGalVulkanWholeFrameMode.isRustPresentationActive()) {
			throw new IllegalStateException(
				"Rust Vulkan whole-frame presentation is active; Java OpenGL compatibility device "
					+ "cannot execute rendering work. Port this callsite to explicit VulkanicGAL semantics."
			);
		}
		return this.withCompatibilityBackendForTeardown(action);
	}

	private <T> T withCompatibilityBackendForTeardown(Supplier<T> action) {
		return this.compatibilityOnly && this.compatibilityBackend != null
			? net.vulkanic.VulkanicAPI.withScopedBackendOverride(this.compatibilityBackend, action)
			: action.get();
	}
}
