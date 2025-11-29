package net.matt.quantize.utils;

import net.matt.quantize.Quantize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.matt.quantize.block.block.ElevatorBlock;
import net.matt.quantize.network.NetworkHandler;
import net.matt.quantize.utils.TeleportHandler;
import net.matt.quantize.network.packet.TeleportRequest;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = Quantize.MOD_ID)
public class ElevatorHandler {
    private static boolean lastSneaking;
    private static boolean lastJumping;

    @SubscribeEvent
    public static void onInput(InputEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator() || !player.isAlive() || player.input == null) {
            return;
        }

        boolean sneaking = player.input.shiftKeyDown;
        if (lastSneaking != sneaking) {
            lastSneaking = sneaking;
            if (sneaking)
                tryTeleport(player, Direction.DOWN);
        }

        boolean jumping = player.input.jumping;
        if (lastJumping != jumping) {
            lastJumping = jumping;
            if (jumping)
                tryTeleport(player, Direction.UP);
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(TickEvent.PlayerTickEvent event) {
//        if (event.phase != TickEvent.Phase.END || !event.player.level().isClientSide)
//            return;
//
//        LocalPlayer player = (LocalPlayer) event.player;
//
//        boolean jumping = (player.getDeltaMovement().y > 0 || player.input.jumping) // for 2 block gaps where the player hits the ceiling
//                && !player.onGround();
//        if (lastJumping != jumping) {
//            lastJumping = jumping;
//            if (jumping) {
//                tryTeleport(player, Direction.UP);
//            }
//        }
//
//        boolean crouching = player.isCrouching();
//        if (lastSneaking != crouching) {
//            lastSneaking = crouching;
//            if (crouching) {
//                tryTeleport(player, Direction.DOWN);
//            }
//        }
    }

    private static void tryTeleport(LocalPlayer player, Direction facing) {
        BlockGetter world = player.getCommandSenderWorld();

        BlockPos fromPos = getOriginElevator(player);
        if (fromPos == null) return;

        BlockPos.MutableBlockPos toPos = fromPos.mutable();

        ElevatorBlock fromElevator;
        fromElevator = (ElevatorBlock) world.getBlockState(fromPos).getBlock();

        while (true) {
            toPos.setY(toPos.getY() + facing.getStepY());
            if (world.isOutsideBuildHeight(toPos) || Math.abs(toPos.getY() - fromPos.getY()) > 384)
                break;

            ElevatorBlock toElevator = TeleportHandler.getElevator(world.getBlockState(toPos));
            if (toElevator != null && TeleportHandler.isValidPos(world, toPos)) {
                NetworkHandler.sendMSGToServer(new TeleportRequest(fromPos, toPos));
                break;
            }
        }
    }

    /**
     * Checks if a player(lower part) is in or has an elevator up to 2 blocks below
     *
     * @param player the player trying to teleport
     * @return the position of the first valid elevator or null if it doesn't exist
     */
    private static BlockPos getOriginElevator(LocalPlayer player) {
        Level world = player.level();
        BlockPos pos = player.blockPosition();

        // Check the player's feet and the 2 blocks under it
        for (int i = 0; i < 3; i++) {
            if (TeleportHandler.getElevator(world.getBlockState(pos)) != null)
                return pos;
            pos = pos.below();
        }

        // Elevator doesn't exist or it's invalid
        return null;
    }
}
