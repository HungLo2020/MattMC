package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Path.DebugData;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public class PathfindingRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final float MAX_RENDER_DIST = 80.0F;
	private static final int MAX_TARGETING_DIST = 8;
	private static final boolean SHOW_ONLY_SELECTED = false;
	private static final boolean SHOW_OPEN_CLOSED = true;
	private static final boolean SHOW_OPEN_CLOSED_COST_MALUS = false;
	private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_TEXT = false;
	private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_BOX = true;
	private static final boolean SHOW_GROUND_LABELS = true;
	private static final float TEXT_SCALE = 0.02F;

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		debugValueAccess.forEachEntity(
			DebugSubscriptions.ENTITY_PATHS,
			(entity, debugPathInfo) -> renderPath(poseStack, multiBufferSource, d, e, f, debugPathInfo.path(), debugPathInfo.maxNodeDistance())
		);
	}

	private static void renderPath(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, Path path, float g) {
		renderPath(poseStack, multiBufferSource, path, g, true, true, d, e, f);
	}

	public static void renderPath(
		PoseStack poseStack, MultiBufferSource multiBufferSource, Path path, float f, boolean bl, boolean bl2, double d, double e, double g
	) {
		renderPathLine(poseStack, multiBufferSource.getBuffer(RenderType.debugLineStrip(6.0)), path, d, e, g);
		BlockPos blockPos = path.getTarget();
		if (distanceToCamera(blockPos, d, e, g) <= 80.0F) {
			DebugRenderer.renderFilledBox(
				poseStack,
				multiBufferSource,
				new AABB(
						blockPos.getX() + 0.25F, blockPos.getY() + 0.25F, blockPos.getZ() + 0.25, blockPos.getX() + 0.75F, blockPos.getY() + 0.75F, blockPos.getZ() + 0.75F
					)
					.move(-d, -e, -g),
				0.0F,
				1.0F,
				0.0F,
				0.5F
			);

			for (int i = 0; i < path.getNodeCount(); i++) {
				Node node = path.getNode(i);
				if (distanceToCamera(node.asBlockPos(), d, e, g) <= 80.0F) {
					float h = i == path.getNextNodeIndex() ? 1.0F : 0.0F;
					float j = i == path.getNextNodeIndex() ? 0.0F : 1.0F;
					DebugRenderer.renderFilledBox(
						poseStack,
						multiBufferSource,
						new AABB(node.x + 0.5F - f, node.y + 0.01F * i, node.z + 0.5F - f, node.x + 0.5F + f, node.y + 0.25F + 0.01F * i, node.z + 0.5F + f).move(-d, -e, -g),
						h,
						0.0F,
						j,
						0.5F
					);
				}
			}
		}

		DebugData debugData = path.debugData();
		if (bl && debugData != null) {
			for (Node node2 : debugData.closedSet()) {
				if (distanceToCamera(node2.asBlockPos(), d, e, g) <= 80.0F) {
					DebugRenderer.renderFilledBox(
						poseStack,
						multiBufferSource,
						new AABB(node2.x + 0.5F - f / 2.0F, node2.y + 0.01F, node2.z + 0.5F - f / 2.0F, node2.x + 0.5F + f / 2.0F, node2.y + 0.1, node2.z + 0.5F + f / 2.0F)
							.move(-d, -e, -g),
						1.0F,
						0.8F,
						0.8F,
						0.5F
					);
				}
			}

			for (Node node2x : debugData.openSet()) {
				if (distanceToCamera(node2x.asBlockPos(), d, e, g) <= 80.0F) {
					DebugRenderer.renderFilledBox(
						poseStack,
						multiBufferSource,
						new AABB(node2x.x + 0.5F - f / 2.0F, node2x.y + 0.01F, node2x.z + 0.5F - f / 2.0F, node2x.x + 0.5F + f / 2.0F, node2x.y + 0.1, node2x.z + 0.5F + f / 2.0F)
							.move(-d, -e, -g),
						0.8F,
						1.0F,
						1.0F,
						0.5F
					);
				}
			}
		}

		if (bl2) {
			for (int k = 0; k < path.getNodeCount(); k++) {
				Node node3 = path.getNode(k);
				if (distanceToCamera(node3.asBlockPos(), d, e, g) <= 80.0F) {
					DebugRenderer.renderFloatingText(
						poseStack, multiBufferSource, String.valueOf(node3.type), node3.x + 0.5, node3.y + 0.75, node3.z + 0.5, -1, 0.02F, true, 0.0F, true
					);
					DebugRenderer.renderFloatingText(
						poseStack,
						multiBufferSource,
						String.format(Locale.ROOT, "%.2f", node3.costMalus),
						node3.x + 0.5,
						node3.y + 0.25,
						node3.z + 0.5,
						-1,
						0.02F,
						true,
						0.0F,
						true
					);
				}
			}
		}
	}

	public static void renderPathLine(PoseStack poseStack, VertexConsumer vertexConsumer, Path path, double d, double e, double f) {
		for (int i = 0; i < path.getNodeCount(); i++) {
			Node node = path.getNode(i);
			if (!(distanceToCamera(node.asBlockPos(), d, e, f) > 80.0F)) {
				float g = (float)i / path.getNodeCount() * 0.33F;
				int j = i == 0 ? -16777216 : ARGB.opaque(Mth.hsvToRgb(g, 0.9F, 0.9F));
				vertexConsumer.addVertex(poseStack.last(), (float)(node.x - d + 0.5), (float)(node.y - e + 0.5), (float)(node.z - f + 0.5)).setColor(j);
			}
		}
	}

	private static float distanceToCamera(BlockPos blockPos, double d, double e, double f) {
		return (float)(Math.abs(blockPos.getX() - d) + Math.abs(blockPos.getY() - e) + Math.abs(blockPos.getZ() - f));
	}
}
