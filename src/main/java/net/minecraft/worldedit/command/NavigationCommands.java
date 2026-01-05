package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.platform.MattMCPlatform;

/**
 * Navigation commands for WorldEdit.
 * Includes //unstuck, //ascend, //descend, //thru, //jumpto, //up.
 */
public class NavigationCommands {
    
    /**
     * Register all navigation commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //unstuck command
        dispatcher.register(Commands.literal("unstuck")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.navigation.unstuck"))
            .executes(NavigationCommands::unstuck));
        
        // //ascend command
        dispatcher.register(Commands.literal("ascend")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.navigation.ascend"))
            .executes(ctx -> ascend(ctx, 1))
            .then(Commands.argument("levels", IntegerArgumentType.integer(1, 10))
                .executes(ctx -> ascend(ctx, IntegerArgumentType.getInteger(ctx, "levels")))));
        
        // //descend command
        dispatcher.register(Commands.literal("descend")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.navigation.descend"))
            .executes(ctx -> descend(ctx, 1))
            .then(Commands.argument("levels", IntegerArgumentType.integer(1, 10))
                .executes(ctx -> descend(ctx, IntegerArgumentType.getInteger(ctx, "levels")))));
        
        // //up command
        dispatcher.register(Commands.literal("up")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.navigation.up"))
            .then(Commands.argument("distance", IntegerArgumentType.integer(1, 256))
                .executes(ctx -> up(ctx, IntegerArgumentType.getInteger(ctx, "distance")))));
        
        // //jumpto command
        dispatcher.register(Commands.literal("jumpto")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.navigation.jumpto"))
            .executes(NavigationCommands::jumpTo));
        
        // //thru command
        dispatcher.register(Commands.literal("thru")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.navigation.thru"))
            .executes(NavigationCommands::thru));
    }
    
    /**
     * Get player unstuck by moving them to a safe location.
     */
    private static int unstuck(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        BlockPos pos = player.blockPosition();
        
        // Try to find safe location above
        for (int y = 0; y < 10; y++) {
            BlockPos tryPos = pos.above(y);
            if (isSafeLocation(world, tryPos)) {
                player.teleportTo(tryPos.getX() + 0.5, tryPos.getY(), tryPos.getZ() + 0.5);
                player.sendSystemMessage(Component.literal("Moved to safe location"));
                return Command.SINGLE_SUCCESS;
            }
        }
        
        player.sendSystemMessage(Component.literal("Could not find safe location"));
        return 0;
    }
    
    /**
     * Ascend to the next level.
     */
    private static int ascend(CommandContext<CommandSourceStack> context, int levels) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        BlockPos startPos = player.blockPosition();
        
        int levelsFound = 0;
        for (int y = startPos.getY() + 2; y < world.getMaxY(); y++) {
            BlockPos checkPos = new BlockPos(startPos.getX(), y, startPos.getZ());
            
            if (isSafeLocation(world, checkPos)) {
                levelsFound++;
                if (levelsFound >= levels) {
                    player.teleportTo(checkPos.getX() + 0.5, checkPos.getY(), 
                        checkPos.getZ() + 0.5);
                    player.sendSystemMessage(Component.literal(
                        String.format("Ascended %d level(s)", levelsFound)
                    ));
                    return Command.SINGLE_SUCCESS;
                }
            }
        }
        
        if (levelsFound > 0) {
            player.sendSystemMessage(Component.literal(
                String.format("Only found %d level(s)", levelsFound)
            ));
        } else {
            player.sendSystemMessage(Component.literal("No free spot above"));
        }
        return 0;
    }
    
    /**
     * Descend to the next level.
     */
    private static int descend(CommandContext<CommandSourceStack> context, int levels) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        BlockPos startPos = player.blockPosition();
        
        int levelsFound = 0;
        for (int y = startPos.getY() - 2; y > world.getMinY(); y--) {
            BlockPos checkPos = new BlockPos(startPos.getX(), y, startPos.getZ());
            
            if (isSafeLocation(world, checkPos)) {
                levelsFound++;
                if (levelsFound >= levels) {
                    player.teleportTo(checkPos.getX() + 0.5, checkPos.getY(), 
                        checkPos.getZ() + 0.5);
                    player.sendSystemMessage(Component.literal(
                        String.format("Descended %d level(s)", levelsFound)
                    ));
                    return Command.SINGLE_SUCCESS;
                }
            }
        }
        
        if (levelsFound > 0) {
            player.sendSystemMessage(Component.literal(
                String.format("Only found %d level(s)", levelsFound)
            ));
        } else {
            player.sendSystemMessage(Component.literal("No free spot below"));
        }
        return 0;
    }
    
    /**
     * Go up a certain distance.
     */
    private static int up(CommandContext<CommandSourceStack> context, int distance) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        BlockPos startPos = player.blockPosition();
        
        BlockPos targetPos = startPos.above(distance);
        
        if (targetPos.getY() >= world.getMaxY()) {
            player.sendSystemMessage(Component.literal("Would be above world"));
            return 0;
        }
        
        // Place glass block below player
        BlockPos glassPos = targetPos.below();
        world.setBlock(glassPos, net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);
        
        // Teleport player
        player.teleportTo(targetPos.getX() + 0.5, targetPos.getY(), 
            targetPos.getZ() + 0.5);
        
        player.sendSystemMessage(Component.literal(
            String.format("Went up %d blocks", distance)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Jump to the block you're looking at.
     */
    private static int jumpTo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        // Ray trace to find target block
        net.minecraft.world.phys.HitResult hit = player.pick(100, 0, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            BlockPos targetPos = blockHit.getBlockPos().above();
            
            if (isSafeLocation(world, targetPos)) {
                player.teleportTo(targetPos.getX() + 0.5, targetPos.getY(), 
                    targetPos.getZ() + 0.5);
                player.sendSystemMessage(Component.literal("Jumped to block"));
                return Command.SINGLE_SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("Not a safe location"));
                return 0;
            }
        }
        
        player.sendSystemMessage(Component.literal("No block in sight"));
        return 0;
    }
    
    /**
     * Pass through a wall.
     */
    private static int thru(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("//thru not yet fully implemented"));
        // TODO: Implement wall passing logic
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Check if a location is safe to teleport to.
     */
    private static boolean isSafeLocation(ServerLevel world, BlockPos pos) {
        BlockState feet = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.above());
        BlockState ground = world.getBlockState(pos.below());
        
        return !feet.blocksMotion() && !head.blocksMotion() && ground.blocksMotion();
    }
    
    /**
     * Check if the source has a permission.
     */
    private static boolean hasPermission(CommandSourceStack source, String permission) {
        if (!WorldEdit.isInitialized()) {
            return false;
        }
        
        try {
            ServerPlayer player = source.getPlayerOrException();
            MattMCPlatform platform = WorldEdit.getInstance().getPlatform();
            return platform.hasPermission(player, permission);
        } catch (CommandSyntaxException e) {
            return false;
        }
    }
}
