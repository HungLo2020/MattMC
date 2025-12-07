package net.minecraft.client.renderer;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
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
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class PostPass implements AutoCloseable {
	private static final int UBO_SIZE_PER_SAMPLER = new Std140SizeCalculator().putVec2().get();
	private final String name;
	private final RenderPipeline pipeline;
	private final ResourceLocation outputTargetId;
	private final Map<String, GpuBuffer> customUniforms = new HashMap();
	private final MappableRingBuffer infoUbo;
	private final List<PostPass.Input> inputs;

	public PostPass(RenderPipeline renderPipeline, ResourceLocation resourceLocation, Map<String, List<UniformValue>> map, List<PostPass.Input> list) {
		this.pipeline = renderPipeline;
		this.name = renderPipeline.getLocation().toString();
		this.outputTargetId = resourceLocation;
		this.inputs = list;

		for (Entry<String, List<UniformValue>> entry : map.entrySet()) {
			List<UniformValue> list2 = (List<UniformValue>)entry.getValue();
			if (!list2.isEmpty()) {
				Std140SizeCalculator std140SizeCalculator = new Std140SizeCalculator();

				for (UniformValue uniformValue : list2) {
					uniformValue.addSize(std140SizeCalculator);
				}

				int i = std140SizeCalculator.get();

				try (MemoryStack memoryStack = MemoryStack.stackPush()) {
					Std140Builder std140Builder = Std140Builder.onStack(memoryStack, i);

					for (UniformValue uniformValue2 : list2) {
						uniformValue2.writeTo(std140Builder);
					}

					this.customUniforms
						.put((String)entry.getKey(), RenderSystem.getDevice().createBuffer(() -> this.name + " / " + (String)entry.getKey(), 128, std140Builder.get()));
				}
			}
		}

		this.infoUbo = new MappableRingBuffer(() -> this.name + " SamplerInfo", 130, (list.size() + 1) * UBO_SIZE_PER_SAMPLER);
	}

	public void addToFrame(FrameGraphBuilder frameGraphBuilder, Map<ResourceLocation, ResourceHandle<RenderTarget>> map, GpuBufferSlice gpuBufferSlice) {
		FramePass framePass = frameGraphBuilder.addPass(this.name);

		for (PostPass.Input input : this.inputs) {
			input.addToPass(framePass, map);
		}

		ResourceHandle<RenderTarget> resourceHandle = (ResourceHandle<RenderTarget>)map.computeIfPresent(
			this.outputTargetId, (resourceLocation, resourceHandlex) -> framePass.readsAndWrites(resourceHandlex)
		);
		if (resourceHandle == null) {
			throw new IllegalStateException("Missing handle for target " + this.outputTargetId);
		} else {
			framePass.executes(
				() -> {
					RenderTarget renderTarget = resourceHandle.get();
					RenderSystem.backupProjectionMatrix();
					RenderSystem.setProjectionMatrix(gpuBufferSlice, ProjectionType.ORTHOGRAPHIC);
					CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
					List<Pair<String, GpuTextureView>> list = this.inputs.stream().map(inputxx -> Pair.of(inputxx.samplerName(), inputxx.texture(map))).toList();

					try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(this.infoUbo.currentBuffer(), false, true)) {
						Std140Builder std140Builder = Std140Builder.intoBuffer(mappedView.data());
						std140Builder.putVec2(renderTarget.width, renderTarget.height);

						for (Pair<String, GpuTextureView> pair : list) {
							std140Builder.putVec2(pair.getSecond().getWidth(0), pair.getSecond().getHeight(0));
						}
					}

					try (RenderPass renderPass = commandEncoder.createRenderPass(
							() -> "Post pass " + this.name,
							renderTarget.getColorTextureView(),
							OptionalInt.empty(),
							renderTarget.useDepth ? renderTarget.getDepthTextureView() : null,
							OptionalDouble.empty()
						)) {
						renderPass.setPipeline(this.pipeline);
						RenderSystem.bindDefaultUniforms(renderPass);
						renderPass.setUniform("SamplerInfo", this.infoUbo.currentBuffer());

						for (Entry<String, GpuBuffer> entry : this.customUniforms.entrySet()) {
							renderPass.setUniform((String)entry.getKey(), (GpuBuffer)entry.getValue());
						}

						for (Pair<String, GpuTextureView> pair2 : list) {
							renderPass.bindSampler(pair2.getFirst() + "Sampler", pair2.getSecond());
						}

						renderPass.draw(0, 3);
					}

					this.infoUbo.rotate();
					RenderSystem.restoreProjectionMatrix();

					for (PostPass.Input inputx : this.inputs) {
						inputx.cleanup(map);
					}
				}
			);
		}
	}

	public void close() {
		for (GpuBuffer gpuBuffer : this.customUniforms.values()) {
			gpuBuffer.close();
		}

		this.infoUbo.close();
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
			if (this.bilinear) {
				this.getHandle(map).get().setFilterMode(FilterMode.NEAREST);
			}
		}

		@Override
		public GpuTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> map) {
			ResourceHandle<RenderTarget> resourceHandle = this.getHandle(map);
			RenderTarget renderTarget = resourceHandle.get();
			renderTarget.setFilterMode(this.bilinear ? FilterMode.LINEAR : FilterMode.NEAREST);
			GpuTextureView gpuTextureView = this.depthBuffer ? renderTarget.getDepthTextureView() : renderTarget.getColorTextureView();
			if (gpuTextureView == null) {
				throw new IllegalStateException("Missing " + (this.depthBuffer ? "depth" : "color") + "texture for target " + this.targetId);
			} else {
				return gpuTextureView;
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public record TextureInput(String samplerName, AbstractTexture texture, int width, int height) implements PostPass.Input {
		@Override
		public void addToPass(FramePass framePass, Map<ResourceLocation, ResourceHandle<RenderTarget>> map) {
		}

		@Override
		public GpuTextureView texture(Map<ResourceLocation, ResourceHandle<RenderTarget>> map) {
			return this.texture.getTextureView();
		}
	}
}
