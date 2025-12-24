package net.minecraft.client.renderer.block;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class MovingBlockRenderState implements BlockAndTintGetter {
	public BlockPos randomSeedPos = BlockPos.ZERO;
	public BlockPos blockPos = BlockPos.ZERO;
	public BlockState blockState = Blocks.AIR.defaultBlockState();
	@Nullable
	public Holder<Biome> biome;
	public BlockAndTintGetter level = EmptyBlockAndTintGetter.INSTANCE;

	public float getShade(Direction direction, boolean bl) {
		return this.level.getShade(direction, bl);
	}

	public LevelLightEngine getLightEngine() {
		return this.level.getLightEngine();
	}

	public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) {
		return this.biome == null ? -1 : colorResolver.getColor((Biome)this.biome.value(), blockPos.getX(), blockPos.getZ());
	}

	@Nullable
	public BlockEntity getBlockEntity(BlockPos blockPos) {
		return null;
	}

	public BlockState getBlockState(BlockPos blockPos) {
		return blockPos.equals(this.blockPos) ? this.blockState : Blocks.AIR.defaultBlockState();
	}

	public FluidState getFluidState(BlockPos blockPos) {
		return this.getBlockState(blockPos).getFluidState();
	}

	public int getHeight() {
		return 1;
	}

	public int getMinY() {
		return this.blockPos.getY();
	}
}
