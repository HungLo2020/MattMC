package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ElevatorBlock extends Block {
	public static final MapCodec<ElevatorBlock> CODEC = simpleCodec(ElevatorBlock::new);

	@Override
	public MapCodec<ElevatorBlock> codec() {
		return CODEC;
	}

	public ElevatorBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public void stepOn(Level level, BlockPos blockPos, BlockState blockState, Entity entity) {
		if (!level.isClientSide() && entity instanceof Player player) {
			// Check if player is jumping (wants to go up)
			// We check if the player is a LivingEntity with the jumping flag set
			if (entity instanceof LivingEntity livingEntity && livingEntity.isJumping() && player.onGround()) {
				BlockPos targetPos = findElevatorAbove(level, blockPos);
				if (targetPos != null) {
					teleportPlayerToElevator(player, targetPos);
				}
			}
			// Check if player is sneaking (wants to go down)
			else if (player.isSteppingCarefully()) {
				BlockPos targetPos = findElevatorBelow(level, blockPos);
				if (targetPos != null) {
					teleportPlayerToElevator(player, targetPos);
				}
			}
		}
		super.stepOn(level, blockPos, blockState, entity);
	}

	private BlockPos findElevatorAbove(Level level, BlockPos startPos) {
		// Search up to 64 blocks above
		for (int i = 1; i <= 64; i++) {
			BlockPos checkPos = startPos.above(i);
			if (level.getBlockState(checkPos).getBlock() instanceof ElevatorBlock) {
				// Check if there are at least 2 empty blocks above the elevator
				if (isSpaceClearAbove(level, checkPos)) {
					return checkPos;
				}
			}
		}
		return null;
	}

	private BlockPos findElevatorBelow(Level level, BlockPos startPos) {
		// Search up to 64 blocks below
		for (int i = 1; i <= 64; i++) {
			BlockPos checkPos = startPos.below(i);
			if (level.getBlockState(checkPos).getBlock() instanceof ElevatorBlock) {
				// Check if there are at least 2 empty blocks above the elevator
				if (isSpaceClearAbove(level, checkPos)) {
					return checkPos;
				}
			}
		}
		return null;
	}

	private boolean isSpaceClearAbove(Level level, BlockPos elevatorPos) {
		// Check if the 2 blocks above the elevator are empty (air or replaceable)
		BlockPos above1 = elevatorPos.above();
		BlockPos above2 = elevatorPos.above(2);
		
		BlockState state1 = level.getBlockState(above1);
		BlockState state2 = level.getBlockState(above2);
		
		return (state1.isAir() || state1.canBeReplaced()) && 
		       (state2.isAir() || state2.canBeReplaced());
	}

	private void teleportPlayerToElevator(Player player, BlockPos elevatorPos) {
		// Teleport player to stand on top of the elevator block
		// Position is center of block (x + 0.5, z + 0.5) and on top of block (y + 1.0)
		double x = elevatorPos.getX() + 0.5;
		double y = elevatorPos.getY() + 1.0;
		double z = elevatorPos.getZ() + 0.5;
		
		player.teleportTo(x, y, z);
		player.resetFallDistance();
	}
}
