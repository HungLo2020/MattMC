package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;

@Environment(EnvType.CLIENT)
public class EntityBlockIntersectionDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final float PADDING = 0.02F;

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		debugValueAccess.forEachBlock(DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS, (blockPos, debugEntityBlockIntersection) -> {
			float fx = ARGB.redFloat(debugEntityBlockIntersection.color());
			float g = ARGB.greenFloat(debugEntityBlockIntersection.color());
			float h = ARGB.blueFloat(debugEntityBlockIntersection.color());
			float i = ARGB.alphaFloat(debugEntityBlockIntersection.color());
			DebugRenderer.renderFilledBox(poseStack, multiBufferSource, blockPos, 0.02F, fx, g, h, i);
		});
	}
}
