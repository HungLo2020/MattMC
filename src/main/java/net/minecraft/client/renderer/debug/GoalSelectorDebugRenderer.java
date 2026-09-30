package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.util.debug.DebugGoalInfo.DebugGoal;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class GoalSelectorDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final int MAX_RENDER_DIST = 160;
	private final Minecraft minecraft;

	public GoalSelectorDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	/** Copies nearby goal-selector labels into Rust-owned semantic text. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage text) {
		DebugValueAccess access = this.minecraft.getConnection().createDebugValueAccess();
		BlockPos center = BlockPos.containing(camera.getPosition().x, 0.0, camera.getPosition().z);
		access.forEachEntity(DebugSubscriptions.GOAL_SELECTORS, (entity, info) -> {
			if (!center.closerThan(entity.blockPosition(), MAX_RENDER_DIST)) return;
			for (int index = 0; index < info.goals().size(); index++) {
				DebugGoal goal = info.goals().get(index);
				PoseStack pose = new PoseStack();
				pose.translate(entity.getBlockX() + 0.5 - camera.getPosition().x, entity.getY() + 2.0 + index * 0.25 - camera.getPosition().y, entity.getBlockZ() + 0.5 - camera.getPosition().z);
				pose.mulPose(camera.rotation());
				pose.scale(0.02F, -0.02F, 0.02F);
				text.submitTextSemantic(0, pose, 0.0F, 0.0F, Component.literal(goal.name()).getVisualOrderText(), true,
					Font.DisplayMode.SEE_THROUGH, goal.isRunning() ? -16711936 : -3355444, -1, 0, 0);
			}
		});
	}
}
