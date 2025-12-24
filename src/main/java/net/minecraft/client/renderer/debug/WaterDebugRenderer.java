package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

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
}
