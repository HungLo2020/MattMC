package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ElevatorBlock extends Block {
	public static final MapCodec<ElevatorBlock> CODEC = simpleCodec(ElevatorBlock::new);

	@Override
	public MapCodec<ElevatorBlock> codec() {
		return CODEC;
	}

	public ElevatorBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/**
	 * Called when a player tries to jump while standing on this elevator.
	 * @return true if teleportation occurred, false otherwise
	 */
	public boolean tryTeleportUp(Level level, BlockPos blockPos, Player player) {
		BlockPos targetPos = findElevatorAbove(level, blockPos);
		if (targetPos != null) {
			// Only do the actual teleportation on the server
			if (!level.isClientSide()) {
				teleportPlayerToElevator(player, targetPos);
			}
			// Return true on both client and server to prevent jump execution and maintain sync
			return true;
		}
		return false;
	}

	@Override
	public void stepOn(Level level, BlockPos blockPos, BlockState blockState, Entity entity) {
		// Sneak handling is done client-side in LocalPlayer.handleElevatorInput()
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

	private boolean isSpaceClearAbove(Level level, BlockPos elevatorPos) {
		// Check if space above elevator is clear - matches reference mod's isValidPos check
		// Reference: TeleportPacket.java line 150
		return !level.getBlockState(elevatorPos.above()).isSuffocating(level, elevatorPos.above());
	}

	public void teleportPlayerToElevator(Player player, BlockPos elevatorPos) {
		// EXACT copy of reference mod's TeleportPacket.handle() teleportation logic
		Level level = player.level();
		BlockState toState = level.getBlockState(elevatorPos);
		
		// X and Z positioning - reference mod uses precisionTarget config, defaulting to center
		double toX = elevatorPos.getX() + 0.5;
		double toZ = elevatorPos.getZ() + 0.5;
		
		// Y positioning - EXACT match to reference mod (TeleportPacket.java line 80-81)
		double blockYOffset = toState.getBlockSupportShape(level, elevatorPos).max(net.minecraft.core.Direction.Axis.Y);
		double toY = Math.max(elevatorPos.getY(), elevatorPos.getY() + blockYOffset);
		
		player.teleportTo(toX, toY, toZ);
		player.resetFallDistance();
		
		// EXACT copy from reference mod (TeleportPacket.java line 107)
		player.setDeltaMovement(player.getDeltaMovement().multiply(new Vec3(1.0, 0.0, 1.0)));
	}
}
