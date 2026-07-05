package net.minecraft.client.renderer;

import net.blaze3d.systems.RenderPass;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface SubmitNodeCollector extends OrderedSubmitNodeCollector {
	OrderedSubmitNodeCollector order(int i);

	@Environment(EnvType.CLIENT)
	public interface CustomGeometryRenderer {
		void render(PoseStack.Pose pose, VertexConsumer vertexConsumer);
	}

	@Environment(EnvType.CLIENT)
	public interface ImmediateCustomGeometryRenderer extends CustomGeometryRenderer {
		void render(PoseStack.Pose pose, RenderType renderType, MultiBufferSource.BufferSource bufferSource);

		@Override
		default void render(PoseStack.Pose pose, VertexConsumer vertexConsumer) {
		}
	}

	@Environment(EnvType.CLIENT)
	public interface ParticleGroupRenderer {
		@Nullable
		QuadParticleRenderState.PreparedBuffers prepare(ParticleFeatureRenderer.ParticleBufferCache particleBufferCache);

		void render(
			QuadParticleRenderState.PreparedBuffers preparedBuffers,
			ParticleFeatureRenderer.ParticleBufferCache particleBufferCache,
			RenderPass renderPass,
			TextureManager textureManager,
			boolean bl
		);
	}
}
