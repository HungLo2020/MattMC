package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
@DontObfuscate
public interface GpuDevice {
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
