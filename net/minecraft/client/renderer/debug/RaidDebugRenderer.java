package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;

@Environment(EnvType.CLIENT)
public class RaidDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final int MAX_RENDER_DIST = 160;
	private static final float TEXT_SCALE = 0.04F;
	private final Minecraft minecraft;

	public RaidDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		BlockPos blockPos = this.getCamera().getBlockPosition();
		debugValueAccess.forEachChunk(DebugSubscriptions.RAIDS, (chunkPos, list) -> {
			for (BlockPos blockPos2 : list) {
				if (blockPos.closerThan(blockPos2, 160.0)) {
					highlightRaidCenter(poseStack, multiBufferSource, blockPos2);
				}
			}
		});
	}

	private static void highlightRaidCenter(PoseStack poseStack, MultiBufferSource multiBufferSource, BlockPos blockPos) {
		DebugRenderer.renderFilledUnitCube(poseStack, multiBufferSource, blockPos, 1.0F, 0.0F, 0.0F, 0.15F);
		renderTextOverBlock(poseStack, multiBufferSource, "Raid center", blockPos, -65536);
	}

	private static void renderTextOverBlock(PoseStack poseStack, MultiBufferSource multiBufferSource, String string, BlockPos blockPos, int i) {
		double d = blockPos.getX() + 0.5;
		double e = blockPos.getY() + 1.3;
		double f = blockPos.getZ() + 0.5;
		DebugRenderer.renderFloatingText(poseStack, multiBufferSource, string, d, e, f, i, 0.04F, true, 0.0F, true);
	}

	private Camera getCamera() {
		return this.minecraft.gameRenderer.getMainCamera();
	}
}
