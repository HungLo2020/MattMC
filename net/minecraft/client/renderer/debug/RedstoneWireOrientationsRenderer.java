package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
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
}
