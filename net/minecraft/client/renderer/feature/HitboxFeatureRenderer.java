package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
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
		for (SubmitNodeStorage.HitboxSubmit hitboxSubmit : submitNodeCollection.getHitboxSubmits()) {
			VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
			PoseStack poseStack = new PoseStack();
			poseStack.mulPose(hitboxSubmit.pose());
			renderHitboxesAndViewVector(poseStack, hitboxSubmit.hitboxesRenderState(), vertexConsumer, hitboxSubmit.entityRenderState().eyeHeight);
			ServerHitboxesRenderState serverHitboxesRenderState = hitboxSubmit.entityRenderState().serverHitboxesRenderState;
			if (serverHitboxesRenderState != null) {
				if (serverHitboxesRenderState.missing()) {
					HitboxRenderState hitboxRenderState = (HitboxRenderState)hitboxSubmit.hitboxesRenderState().hitboxes().getFirst();
					DebugRenderer.renderFloatingText(
						poseStack, bufferSource, "Missing", hitboxSubmit.entityRenderState().x, hitboxRenderState.y1() + 1.5, hitboxSubmit.entityRenderState().z, -65536
					);
				} else if (serverHitboxesRenderState.hitboxes() != null) {
					poseStack.translate(
						serverHitboxesRenderState.serverEntityX() - hitboxSubmit.entityRenderState().x,
						serverHitboxesRenderState.serverEntityY() - hitboxSubmit.entityRenderState().y,
						serverHitboxesRenderState.serverEntityZ() - hitboxSubmit.entityRenderState().z
					);
					renderHitboxesAndViewVector(poseStack, serverHitboxesRenderState.hitboxes(), vertexConsumer, serverHitboxesRenderState.eyeHeight());
					Vec3 vec3 = new Vec3(serverHitboxesRenderState.deltaMovementX(), serverHitboxesRenderState.deltaMovementY(), serverHitboxesRenderState.deltaMovementZ());
					ShapeRenderer.renderVector(poseStack, vertexConsumer, new Vector3f(), vec3, -256);
				}
			}
		}
	}

	private static void renderHitboxesAndViewVector(PoseStack poseStack, HitboxesRenderState hitboxesRenderState, VertexConsumer vertexConsumer, float f) {
		for (HitboxRenderState hitboxRenderState : hitboxesRenderState.hitboxes()) {
			renderHitbox(poseStack, vertexConsumer, hitboxRenderState);
		}

		Vec3 vec3 = new Vec3(hitboxesRenderState.viewX(), hitboxesRenderState.viewY(), hitboxesRenderState.viewZ());
		ShapeRenderer.renderVector(poseStack, vertexConsumer, new Vector3f(0.0F, f, 0.0F), vec3.scale(2.0), -16776961);
	}

	private static void renderHitbox(PoseStack poseStack, VertexConsumer vertexConsumer, HitboxRenderState hitboxRenderState) {
		poseStack.pushPose();
		poseStack.translate(hitboxRenderState.offsetX(), hitboxRenderState.offsetY(), hitboxRenderState.offsetZ());
		ShapeRenderer.renderLineBox(
			poseStack.last(),
			vertexConsumer,
			hitboxRenderState.x0(),
			hitboxRenderState.y0(),
			hitboxRenderState.z0(),
			hitboxRenderState.x1(),
			hitboxRenderState.y1(),
			hitboxRenderState.z1(),
			hitboxRenderState.red(),
			hitboxRenderState.green(),
			hitboxRenderState.blue(),
			1.0F
		);
		poseStack.popPose();
	}
}
