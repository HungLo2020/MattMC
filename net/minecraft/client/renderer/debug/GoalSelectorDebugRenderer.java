package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.util.debug.DebugGoalInfo.DebugGoal;

@Environment(EnvType.CLIENT)
public class GoalSelectorDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final int MAX_RENDER_DIST = 160;
	private final Minecraft minecraft;

	public GoalSelectorDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		Camera camera = this.minecraft.gameRenderer.getMainCamera();
		BlockPos blockPos = BlockPos.containing(camera.getPosition().x, 0.0, camera.getPosition().z);
		debugValueAccess.forEachEntity(DebugSubscriptions.GOAL_SELECTORS, (entity, debugGoalInfo) -> {
			if (blockPos.closerThan(entity.blockPosition(), 160.0)) {
				for (int i = 0; i < debugGoalInfo.goals().size(); i++) {
					DebugGoal debugGoal = (DebugGoal)debugGoalInfo.goals().get(i);
					double dx = entity.getBlockX() + 0.5;
					double ex = entity.getY() + 2.0 + i * 0.25;
					double fx = entity.getBlockZ() + 0.5;
					int j = debugGoal.isRunning() ? -16711936 : -3355444;
					DebugRenderer.renderFloatingText(poseStack, multiBufferSource, debugGoal.name(), dx, ex, fx, j);
				}
			}
		});
	}
}
