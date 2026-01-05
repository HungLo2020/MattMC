package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.region.IncompleteRegionException;
import net.minecraft.worldedit.region.Region;
import net.minecraft.worldedit.region.RegionSelector;
import net.minecraft.worldedit.session.LocalSession;

/**
 * Selection-related commands for WorldEdit.
 * Includes //pos1, //pos2, //chunk, //expand, //contract, etc.
 */
public class SelectionCommands {
    
    /**
     * Register all selection commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //pos1 command
        dispatcher.register(Commands.literal("/pos1")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.selection.pos"))
            .executes(SelectionCommands::pos1));
        
        // //pos2 command
        dispatcher.register(Commands.literal("/pos2")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.selection.pos"))
            .executes(SelectionCommands::pos2));
        
        // //chunk command
        dispatcher.register(Commands.literal("/chunk")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.selection.chunk"))
            .executes(SelectionCommands::chunk));
        
        // //sel command (show selection info)
        dispatcher.register(Commands.literal("/sel")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.selection.pos"))
            .executes(SelectionCommands::selectionInfo));
        
        // //desel command (clear selection)
        dispatcher.register(Commands.literal("/desel")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.selection.pos"))
            .executes(SelectionCommands::deselect));
        
        // //expand command
        dispatcher.register(Commands.literal("/expand")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.selection.expand"))
            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                .executes(ctx -> expand(ctx, IntegerArgumentType.getInteger(ctx, "amount")))));
        
        // //contract command
        dispatcher.register(Commands.literal("/contract")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.selection.contract"))
            .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                .executes(ctx -> contract(ctx, IntegerArgumentType.getInteger(ctx, "amount")))));
        
        // //count command
        dispatcher.register(Commands.literal("/count")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.analysis.count"))
            .executes(SelectionCommands::count));
        
        // //size command
        dispatcher.register(Commands.literal("/size")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.selection.size"))
            .executes(SelectionCommands::size));
    }
    
    /**
     * Set position 1 at the player's location.
     */
    private static int pos1(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        BlockPos pos = player.blockPosition();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        RegionSelector selector = session.getRegionSelector(world);
        BlockVector3 blockPos = BlockVector3.from(pos);
        
        if (selector.selectPrimary(blockPos)) {
            selector.explainPrimarySelection(player, blockPos);
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Set position 2 at the player's location.
     */
    private static int pos2(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        BlockPos pos = player.blockPosition();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        RegionSelector selector = session.getRegionSelector(world);
        BlockVector3 blockPos = BlockVector3.from(pos);
        
        if (selector.selectSecondary(blockPos)) {
            selector.explainSecondarySelection(player, blockPos);
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Select the current chunk.
     */
    private static int chunk(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        BlockPos pos = player.blockPosition();
        
        // Calculate chunk boundaries
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        
        BlockVector3 min = BlockVector3.at(minX, world.getMinY(), minZ);
        BlockVector3 max = BlockVector3.at(maxX, world.getMaxY() - 1, maxZ);
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        RegionSelector selector = session.getRegionSelector(world);
        
        selector.selectPrimary(min);
        selector.selectSecondary(max);
        
        player.sendSystemMessage(Component.literal(
            String.format("Chunk selected: %d blocks", (16 * 16 * (world.getMaxY() - world.getMinY())))
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Show selection information.
     */
    private static int selectionInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        Region region = session.getSelection(world);
        if (region == null) {
            player.sendSystemMessage(Component.literal("No selection defined"));
            return 0;
        }
        
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        
        player.sendSystemMessage(Component.literal(
            String.format("Selection: %s to %s (%d blocks, %dx%dx%d)",
                min, max, region.getVolume(),
                region.getWidth(), region.getHeight(), region.getLength())
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Clear the selection.
     */
    private static int deselect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        RegionSelector selector = session.getRegionSelector(world);
        selector.clear();
        
        player.sendSystemMessage(Component.literal("Selection cleared"));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Expand the selection in the direction the player is facing.
     */
    private static int expand(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("//expand not yet fully implemented"));
        // TODO: Implement full expand logic
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Contract the selection in the direction the player is facing.
     */
    private static int contract(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("//contract not yet fully implemented"));
        // TODO: Implement full contract logic
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Count blocks in the selection.
     */
    private static int count(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        Region region = session.getSelection(world);
        if (region == null) {
            player.sendSystemMessage(Component.literal("No selection defined"));
            return 0;
        }
        
        player.sendSystemMessage(Component.literal(
            String.format("Selection contains %d blocks", region.getVolume())
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Show selection size.
     */
    private static int size(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return selectionInfo(context);
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
