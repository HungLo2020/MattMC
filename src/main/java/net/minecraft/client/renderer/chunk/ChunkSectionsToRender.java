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
import net.minecraft.hooks.ChunkRenderLayerHooks;
import net.minecraft.hooks.HookRegistry;

@Environment(EnvType.CLIENT)
public record ChunkSectionsToRender(
	EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> drawsPerLayer, int maxIndicesRequired, GpuBufferSlice[] dynamicTransforms
) implements net.caffeinemc.mods.sodium.client.util.SodiumChunkSection {
	// Sodium: SodiumChunkSection interface implementation (from ChunkSectionsToRenderMixin)
	private static net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer renderer;
	private static net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices matrices;
	private static double x;
	private static double y;
	private static double z;
	
	public void renderGroup(ChunkSectionLayerGroup chunkSectionLayerGroup) {
		// Sodium: Call DH hooks before Sodium rendering (from ChunkSectionsToRenderMixin)
		for (ChunkRenderLayerHooks hook : HookRegistry.getChunkRenderLayerHooks()) {
			hook.onBeforeRenderLayer(chunkSectionLayerGroup);
		}
		
		// Sodium: Let Sodium renderer handle if active (from ChunkSectionsToRenderMixin)
		if (renderer != null) {
			net.caffeinemc.mods.sodium.client.gl.device.RenderDevice.enterManagedCode();
			try {
				renderer.drawChunkLayer(chunkSectionLayerGroup, matrices, x, y, z);
			} finally {
				net.caffeinemc.mods.sodium.client.gl.device.RenderDevice.exitManagedCode();
			}
			return;
		}
		
		// NOTE: Hook calls are in Sodium's ChunkSectionsToRenderMixin
		// Sodium cancels this method when active, so hooks must run before cancellation
		
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
	
	// Sodium: SodiumChunkSection interface method (from ChunkSectionsToRenderMixin)
	@Override
	public void sodium$setRendering(net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer renderer, net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices matrices, double x, double y, double z) {
		ChunkSectionsToRender.renderer = renderer;
		ChunkSectionsToRender.matrices = matrices;
		ChunkSectionsToRender.x = x;
		ChunkSectionsToRender.y = y;
		ChunkSectionsToRender.z = z;
	}
}
