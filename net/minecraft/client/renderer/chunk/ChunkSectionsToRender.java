package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.EnumMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.sodium.util.SodiumChunkSection;
import net.minecraft.client.renderer.sodium.render.SodiumWorldRenderer;
import net.minecraft.client.renderer.chunk.advanced.ChunkRenderMatrices;
import net.minecraft.client.renderer.gl.advanced.device.RenderDevice;

@Environment(EnvType.CLIENT)
public record ChunkSectionsToRender(
	EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> drawsPerLayer, int maxIndicesRequired, GpuBufferSlice[] dynamicTransforms
) implements SodiumChunkSection {
	// Sodium integration fields
	private static final ThreadLocal<SodiumWorldRenderer> sodium$renderer = new ThreadLocal<>();
	private static final ThreadLocal<ChunkRenderMatrices> sodium$matrices = new ThreadLocal<>();
	private static final ThreadLocal<Double> sodium$x = new ThreadLocal<>();
	private static final ThreadLocal<Double> sodium$y = new ThreadLocal<>();
	private static final ThreadLocal<Double> sodium$z = new ThreadLocal<>();
	public void renderGroup(ChunkSectionLayerGroup chunkSectionLayerGroup) {
		// Sodium integration: check if Sodium rendering is active
		SodiumWorldRenderer renderer = sodium$renderer.get();
		if (renderer != null) {
			RenderDevice.enterManagedCode();
			try {
				renderer.drawChunkLayer(chunkSectionLayerGroup, sodium$matrices.get(), 
					sodium$x.get(), sodium$y.get(), sodium$z.get());
			} finally {
				RenderDevice.exitManagedCode();
			}
			return;
		}
		
		// Vanilla rendering path
		RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		GpuBuffer gpuBuffer = this.maxIndicesRequired == 0 ? null : autoStorageIndexBuffer.getBuffer(this.maxIndicesRequired);
		VertexFormat.IndexType indexType = this.maxIndicesRequired == 0 ? null : autoStorageIndexBuffer.type();
		ChunkSectionLayer[] chunkSectionLayers = chunkSectionLayerGroup.layers();
		Minecraft minecraft = Minecraft.getInstance();
		boolean bl = SharedConstants.DEBUG_HOTKEYS && minecraft.wireframe;
		RenderTarget renderTarget = chunkSectionLayerGroup.outputTarget();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
					() -> "Section layers for " + chunkSectionLayerGroup.label(),
					renderTarget.getColorTextureView(),
					OptionalInt.empty(),
					renderTarget.getDepthTextureView(),
					OptionalDouble.empty()
				)) {
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.bindSampler("Sampler2", minecraft.gameRenderer.lightTexture().getTextureView());

			for (ChunkSectionLayer chunkSectionLayer : chunkSectionLayers) {
				List<RenderPass.Draw<GpuBufferSlice[]>> list = (List<RenderPass.Draw<GpuBufferSlice[]>>)this.drawsPerLayer.get(chunkSectionLayer);
				if (!list.isEmpty()) {
					if (chunkSectionLayer == ChunkSectionLayer.TRANSLUCENT) {
						list = list.reversed();
					}

					renderPass.setPipeline(bl ? RenderPipelines.WIREFRAME : chunkSectionLayer.pipeline());
					renderPass.bindSampler("Sampler0", chunkSectionLayer.textureView());
					renderPass.drawMultipleIndexed(list, gpuBuffer, indexType, List.of("DynamicTransforms"), this.dynamicTransforms);
				}
			}
		}
	}
	
	// Sodium integration: implement SodiumChunkSection interface
	@Override
	public void sodium$setRendering(SodiumWorldRenderer renderer, ChunkRenderMatrices matrices, double x, double y, double z) {
		sodium$renderer.set(renderer);
		sodium$matrices.set(matrices);
		sodium$x.set(x);
		sodium$y.set(y);
		sodium$z.set(z);
	}
}
