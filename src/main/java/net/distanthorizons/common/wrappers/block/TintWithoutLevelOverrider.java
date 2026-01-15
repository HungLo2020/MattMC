package net.distanthorizons.common.wrappers.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

public class TintWithoutLevelOverrider extends AbstractDhTintGetter
{
	
	//=============//
	// constructor //
	//=============//
	
	public TintWithoutLevelOverrider()
	{ }
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public float getShade(Direction direction, boolean shade)
	{ throw new UnsupportedOperationException("ERROR: getShade() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	@Override
	public LevelLightEngine getLightEngine()
	{ throw new UnsupportedOperationException("ERROR: getLightEngine() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	@Nullable
	@Override
	public BlockEntity getBlockEntity(BlockPos pos)
	{ throw new UnsupportedOperationException("ERROR: getBlockEntity() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	
	@Override
	public BlockState getBlockState(BlockPos pos)
	{ throw new UnsupportedOperationException("ERROR: getBlockState() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	@Override
	public FluidState getFluidState(BlockPos pos)
	{ throw new UnsupportedOperationException("ERROR: getFluidState() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	
	
	//==============//
	// post MC 1.17 //
	//==============//
	
	
	@Override
	public int getHeight()
	{ throw new UnsupportedOperationException("ERROR: getHeight() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	
	@Override
	public int getMinY()
	{ throw new UnsupportedOperationException("ERROR: getMinY() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	
	
}
