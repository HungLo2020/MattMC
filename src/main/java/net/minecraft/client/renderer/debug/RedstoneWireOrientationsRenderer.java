package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class RedstoneWireOrientationsRenderer implements DebugRenderer.SimpleDebugRenderer {
	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());
		debugValueAccess.forEachBlock(DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS, (blockPos, orientation) -> {
			Vector3f vector3f = blockPos.getBottomCenter().subtract(d, e - 0.1, f).toVector3f();
			ShapeRenderer.renderVector(poseStack, vertexConsumer, vector3f, orientation.getFront().getUnitVec3().scale(0.5), -16776961);
			ShapeRenderer.renderVector(poseStack, vertexConsumer, vector3f, orientation.getUp().getUnitVec3().scale(0.4), -65536);
			ShapeRenderer.renderVector(poseStack, vertexConsumer, vector3f, orientation.getSide().getUnitVec3().scale(0.3), -256);
		});
	}

	/** Copies redstone orientation vectors into Rust's explicit debug-line stream. */
	public void collectRustSemantics(Camera camera) {
		if (!net.vulkanic.world.WorldRenderRoutePolicy.currentDebugLineRoute().usesRustWholeFrameVulkan()) {
			if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
				throw new IllegalStateException("Rust whole-frame redstone-orientation route is unavailable; Java debug geometry is not a fallback");
			}
			return;
		}
		if (camera == null || !camera.isInitialized()) return;
		DebugValueAccess access = net.minecraft.client.Minecraft.getInstance().getConnection().createDebugValueAccess();
		org.joml.Matrix4f transform = new org.joml.Matrix4f().translate(
			(float)-camera.getPosition().x, (float)-camera.getPosition().y, (float)-camera.getPosition().z
		);
		access.forEachBlock(DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS, (pos, orientation) -> {
			org.joml.Vector3f origin = pos.getBottomCenter().toVector3f().add(0.0F, 0.1F, 0.0F);
			if (!queueVector(transform, origin, orientation.getFront().getUnitVec3().scale(0.5), 0xFF0000FF)
				|| !queueVector(transform, origin, orientation.getUp().getUnitVec3().scale(0.4), 0xFFFF0000)
				|| !queueVector(transform, origin, orientation.getSide().getUnitVec3().scale(0.3), 0xFFFFFF00)) {
				throw new IllegalStateException("Rust whole-frame redstone-orientation route rejected semantic vector");
			}
		});
	}

	private static boolean queueVector(org.joml.Matrix4f transform, org.joml.Vector3f origin, net.minecraft.world.phys.Vec3 direction, int color) {
		return net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueDebugLineSegments(transform,
			new float[] {origin.x(), origin.y(), origin.z(), origin.x() + (float)direction.x, origin.y() + (float)direction.y, origin.z() + (float)direction.z}, color, 1.0F);
	}
}
