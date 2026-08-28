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

	/** Copies nearby goal-selector labels into Rust-owned semantic text. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage text) {
		if ((!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentWorldTextRoute().usesRustWholeFrameVulkan()) return;
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
