// Java
package net.matt.quantize.modules.tweaks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.matt.quantize.Quantize;

@Mod.EventBusSubscriber(modid = Quantize.MOD_ID)
public class DoubleDoorModule {

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        InteractionHand hand = event.getHand();

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof DoorBlock) {
            handleDoorInteraction(level, pos, state, player, hand);
        }
    }

    private static void handleDoorInteraction(Level level, BlockPos pos, BlockState state, Player player, InteractionHand hand) {
        Direction direction = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean isOpen = state.getValue(BlockStateProperties.OPEN);

        // Check both adjacent positions for doors
        BlockPos clockwisePos = pos.relative(direction.getClockWise());
        BlockPos counterClockwisePos = pos.relative(direction.getCounterClockWise());

        updateAdjacentDoor(level, clockwisePos, direction, isOpen);
        updateAdjacentDoor(level, counterClockwisePos, direction, isOpen);

        // Update the current door
        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, !isOpen), 3);
    }

    private static void updateAdjacentDoor(Level level, BlockPos adjacentPos, Direction direction, boolean isOpen) {
        BlockState adjacentState = level.getBlockState(adjacentPos);

        if (adjacentState.getBlock() instanceof DoorBlock && adjacentState.getValue(BlockStateProperties.HORIZONTAL_FACING) == direction) {
            level.setBlock(adjacentPos, adjacentState.setValue(BlockStateProperties.OPEN, !isOpen), 3);
        }
    }
}