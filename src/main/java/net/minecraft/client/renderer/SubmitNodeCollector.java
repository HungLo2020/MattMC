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

	/**
	 * A count-only collector invokes normal producer callbacks to inventory
	 * semantic families. Producers must not enqueue Rust work through it.
	 */
	default boolean isSemanticCoverageOnly() {
		return false;
	}

	/**
	 * Records copied text semantics without requiring every specialized collector
	 * to expose a second callback. Rust-owned collectors override this entrypoint;
	 * compatibility collectors inherit the ordinary storage callback.
	 */
	default void submitTextSemantic(
		PoseStack poseStack, float x, float y, net.minecraft.util.FormattedCharSequence text,
		boolean shadow, net.minecraft.client.gui.Font.DisplayMode mode, int color,
		int backgroundColor, int packedLight, int packedOverlay
	) {
		submitText(poseStack, x, y, text, shadow, mode, color, backgroundColor, packedLight, packedOverlay);
	}

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
