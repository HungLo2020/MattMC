package net.minecraft.client.renderer.feature;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.client.renderer.entity.state.ServerHitboxesRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class HitboxFeatureRenderer {
	public void render(SubmitNodeCollection submitNodeCollection, MultiBufferSource.BufferSource bufferSource) {
		if (submitNodeCollection != null && !submitNodeCollection.getHitboxSubmits().isEmpty()) {
			throw new IllegalStateException(
				"Java hitbox rendering is unavailable while Rust Vulkan owns presentation"
			);
		}
		return;
	}

}
