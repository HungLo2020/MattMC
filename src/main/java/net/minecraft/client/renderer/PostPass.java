package net.minecraft.client.renderer;

import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.Std140Builder;
import net.blaze3d.buffers.Std140SizeCalculator;
import net.blaze3d.framegraph.FramePass;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.resource.ResourceHandle;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.FilterMode;
import net.blaze3d.textures.GpuTextureView;
import com.mojang.datafixers.util.Pair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Map.Entry;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class PostPass implements AutoCloseable {
	private static final int UBO_SIZE_PER_SAMPLER = new Std140SizeCalculator().putVec2().get();
	private final String name;
	private final RenderPipeline pipeline;
	private final ResourceLocation outputTargetId;
	private final Map<String, GpuBuffer> customUniforms = new HashMap();
	@Nullable
	private final MappableRingBuffer infoUbo;
	private final List<PostPass.Input> inputs;

	public PostPass(RenderPipeline renderPipeline, ResourceLocation resourceLocation, Map<String, List<UniformValue>> map, List<PostPass.Input> list) {
		this.pipeline = renderPipeline;
		this.name = renderPipeline.getLocation().toString();
		this.outputTargetId = resourceLocation;
		this.inputs = list;
		// Rust owns post-process source admission and execution for whole-frame
		// Vulkan; Java custom uniforms and sampler metadata are not allocated.
		this.infoUbo = null;
		return;
	}

	public void close() {
		for (GpuBuffer gpuBuffer : this.customUniforms.values()) {
			gpuBuffer.close();
		}

		if (this.infoUbo != null) {
			this.infoUbo.close();
		}
	}

	@Environment(EnvType.CLIENT)
	public interface Input {
		void addToPass(FramePass framePass, Map<ResourceLocation, ResourceHandle<RenderTarget>> map);

		default void cleanup(Map<ResourceLocation, ResourceHandle<RenderTarget>> map) {
		}

		GpuTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> map);

		String samplerName();
	}

	@Environment(EnvType.CLIENT)
	public record TargetInput(String samplerName, ResourceLocation targetId, boolean depthBuffer, boolean bilinear) implements PostPass.Input {
		private ResourceHandle<RenderTarget> getHandle(Map<ResourceLocation, ResourceHandle<RenderTarget>> map) {
			ResourceHandle<RenderTarget> resourceHandle = (ResourceHandle<RenderTarget>)map.get(this.targetId);
			if (resourceHandle == null) {
				throw new IllegalStateException("Missing handle for target " + this.targetId);
			} else {
				return resourceHandle;
			}
		}

		@Override
		public void addToPass(FramePass framePass, Map<ResourceLocation, ResourceHandle<RenderTarget>> map) {
			framePass.reads(this.getHandle(map));
		}

		@Override
		public void cleanup(Map<ResourceLocation, ResourceHandle<RenderTarget>> map) {
			throw new IllegalStateException("Java post-pass target cleanup is unavailable on the Rust Vulkan route");
		}

		@Override
		public GpuTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> map) {
			throw new IllegalStateException("Java post-pass target textures are unavailable on the Rust Vulkan route");
		}
	}

	@Environment(EnvType.CLIENT)
	public record TextureInput(String samplerName, AbstractTexture texture, int width, int height) implements PostPass.Input {
		@Override
		public void addToPass(FramePass framePass, Map<ResourceLocation, ResourceHandle<RenderTarget>> map) {
		}

		@Override
		public GpuTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> map) {
			throw new IllegalStateException("Java post-pass texture inputs are unavailable on the Rust Vulkan route");
		}
	}
}
