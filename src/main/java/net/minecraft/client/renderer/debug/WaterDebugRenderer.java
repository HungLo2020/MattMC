package net.minecraft.client.renderer.debug;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class WaterDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;

	public WaterDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum) {
		BlockPos blockPos = this.minecraft.player.blockPosition();
		LevelReader levelReader = this.minecraft.player.level();

		for (BlockPos blockPos2 : BlockPos.betweenClosed(blockPos.offset(-10, -10, -10), blockPos.offset(10, 10, 10))) {
			FluidState fluidState = levelReader.getFluidState(blockPos2);
			if (fluidState.is(FluidTags.WATER)) {
				double g = blockPos2.getY() + fluidState.getHeight(levelReader, blockPos2);
				DebugRenderer.renderFilledBox(
					poseStack,
					multiBufferSource,
					new AABB(blockPos2.getX() + 0.01F, blockPos2.getY() + 0.01F, blockPos2.getZ() + 0.01F, blockPos2.getX() + 0.99F, g, blockPos2.getZ() + 0.99F)
						.move(-d, -e, -f),
					0.0F,
					1.0F,
					0.0F,
					0.15F
				);
			}
		}

		for (BlockPos blockPos2x : BlockPos.betweenClosed(blockPos.offset(-10, -10, -10), blockPos.offset(10, 10, 10))) {
			FluidState fluidState = levelReader.getFluidState(blockPos2x);
			if (fluidState.is(FluidTags.WATER)) {
				DebugRenderer.renderFloatingText(
					poseStack,
					multiBufferSource,
					String.valueOf(fluidState.getAmount()),
					blockPos2x.getX() + 0.5,
					(double)(blockPos2x.getY() + fluidState.getHeight(levelReader, blockPos2x)),
					blockPos2x.getZ() + 0.5,
					-16777216
				);
			}
		}
	}

	/** Copies nearby water levels and amounts into Rust-owned semantic geometry/text. */
	public void collectRustSemantics(Camera camera, SubmitNodeStorage geometry, SubmitNodeStorage text) {
		if ((!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			&& !net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
			|| !net.vulkanic.world.WorldRenderRoutePolicy.currentProceduralQuadRoute().usesRustWholeFrameVulkan()) return;
		if (this.minecraft.player == null) return;
		BlockPos center = this.minecraft.player.blockPosition();
		LevelReader levelReader = this.minecraft.player.level();
		PoseStack transform = new PoseStack();
		transform.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
		float[] uvs = {0,0,1,0,1,1,0,1};
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-10, -10, -10), center.offset(10, 10, 10))) {
			FluidState fluid = levelReader.getFluidState(pos);
			if (!fluid.is(FluidTags.WATER)) continue;
			float x0 = pos.getX() + 0.01F, y0 = pos.getY() + 0.01F, z0 = pos.getZ() + 0.01F;
			float x1 = pos.getX() + 0.99F, y1 = (float)(pos.getY() + fluid.getHeight(levelReader, pos)), z1 = pos.getZ() + 0.99F;
			if (y1 <= y0) continue;
			float[][] faces = {
				{x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0}, {x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1},
				{x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1}, {x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0},
				{x0,y0,z0,x0,y0,z1,x1,y0,z1,x1,y0,z0}, {x0,y1,z1,x0,y1,z0,x1,y1,z0,x1,y1,z1}
			};
			for (float[] face : faces) if (!geometry.submitColoredQuadsSemantic(transform, RenderType.debugFilledBox(), face, uvs, new int[]{0x2600FF00}, 15728880)) {
				throw new IllegalStateException("Rust whole-frame water-debug route rejected semantic box");
			}
			PoseStack label = new PoseStack();
			label.translate(pos.getX() + 0.5F - camera.getPosition().x, pos.getY() + fluid.getHeight(levelReader, pos) - camera.getPosition().y, pos.getZ() + 0.5F - camera.getPosition().z);
			label.mulPose(camera.rotation());
			label.scale(0.02F, -0.02F, 0.02F);
			text.submitTextSemantic(0, label, 0.0F, 0.0F,
				Component.literal(String.valueOf(fluid.getAmount())).getVisualOrderText(), true,
				Font.DisplayMode.SEE_THROUGH, -16777216, -1, 0, 0);
		}
	}
}
