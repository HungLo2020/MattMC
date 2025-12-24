package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.debug.DebugStructureInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.util.debug.DebugStructureInfo.Piece;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

@Environment(EnvType.CLIENT)
public class StructureRenderer implements DebugRenderer.SimpleDebugRenderer {
	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());
		debugValueAccess.forEachChunk(DebugSubscriptions.STRUCTURES, (chunkPos, list) -> {
			for (DebugStructureInfo debugStructureInfo : list) {
				renderBox(poseStack, d, e, f, vertexConsumer, debugStructureInfo.boundingBox(), 1.0F, 1.0F, 1.0F, 1.0F);

				for (Piece piece : debugStructureInfo.pieces()) {
					if (piece.isStart()) {
						renderBox(poseStack, d, e, f, vertexConsumer, piece.boundingBox(), 0.0F, 1.0F, 0.0F, 1.0F);
					} else {
						renderBox(poseStack, d, e, f, vertexConsumer, piece.boundingBox(), 0.0F, 0.0F, 1.0F, 1.0F);
					}
				}
			}
		});
	}

	private static void renderBox(
		PoseStack poseStack, double d, double e, double f, VertexConsumer vertexConsumer, BoundingBox boundingBox, float g, float h, float i, float j
	) {
		ShapeRenderer.renderLineBox(
			poseStack.last(),
			vertexConsumer,
			boundingBox.minX() - d,
			boundingBox.minY() - e,
			boundingBox.minZ() - f,
			boundingBox.maxX() + 1 - d,
			boundingBox.maxY() + 1 - e,
			boundingBox.maxZ() + 1 - f,
			g,
			h,
			i,
			j,
			g,
			h,
			i
		);
	}
}
