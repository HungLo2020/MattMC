package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.region.Region;
import net.minecraft.worldedit.session.LocalSession;

/**
 * Region manipulation commands for WorldEdit.
 * Includes //set, //replace, //overlay, //walls, //faces, etc.
 */
public class RegionCommands {
    
    /**
     * Register all region commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //set command
        dispatcher.register(Commands.literal("set")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.set"))
            .then(Commands.argument("block", StringArgumentType.word())
                .executes(ctx -> set(ctx, StringArgumentType.getString(ctx, "block")))));
        
        // //replace command
        dispatcher.register(Commands.literal("replace")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.replace"))
            .then(Commands.argument("from", StringArgumentType.word())
                .then(Commands.argument("to", StringArgumentType.word())
                    .executes(ctx -> replace(ctx, 
                        StringArgumentType.getString(ctx, "from"),
                        StringArgumentType.getString(ctx, "to"))))));
        
        // //walls command
        dispatcher.register(Commands.literal("walls")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.walls"))
            .then(Commands.argument("block", StringArgumentType.word())
                .executes(ctx -> walls(ctx, StringArgumentType.getString(ctx, "block")))));
        
        // //faces command
        dispatcher.register(Commands.literal("faces")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.faces"))
            .then(Commands.argument("block", StringArgumentType.word())
                .executes(ctx -> faces(ctx, StringArgumentType.getString(ctx, "block")))));
        
        // //overlay command
        dispatcher.register(Commands.literal("overlay")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.overlay"))
            .then(Commands.argument("block", StringArgumentType.word())
                .executes(ctx -> overlay(ctx, StringArgumentType.getString(ctx, "block")))));
    }
    
    /**
     * Set all blocks in selection to a block type.
     */
    private static int set(CommandContext<CommandSourceStack> context, String blockName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Region region = session.getSelection(world);
        
        if (region == null) {
            player.sendSystemMessage(Component.literal("No selection defined"));
            return 0;
        }
        
        // Parse block type
        ResourceLocation blockId = parseBlockId(blockName);
        if (blockId == null) {
            player.sendSystemMessage(Component.literal("Invalid block: " + blockName));
            return 0;
        }
        
        Block block = BuiltInRegistries.BLOCK.getValue(blockId);
        if (block == null) {
            player.sendSystemMessage(Component.literal("Unknown block: " + blockName));
            return 0;
        }
        
        BlockState state = block.defaultBlockState();
        
        // Set blocks
        int count = 0;
        for (BlockVector3 pos : region) {
            world.setBlock(pos.toBlockPos(), state, 3);
            count++;
            
            // Limit to prevent server lag (temporary)
            if (count > 100000) {
                player.sendSystemMessage(Component.literal("§cWarning: Operation limited to 100,000 blocks"));
                break;
            }
        }
        
        player.sendSystemMessage(Component.literal(String.format("Set %d blocks", count)));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Replace blocks in selection.
     */
    private static int replace(CommandContext<CommandSourceStack> context, String fromBlock, String toBlock) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Region region = session.getSelection(world);
        
        if (region == null) {
            player.sendSystemMessage(Component.literal("No selection defined"));
            return 0;
        }
        
        // Parse block types
        ResourceLocation fromId = parseBlockId(fromBlock);
        ResourceLocation toId = parseBlockId(toBlock);
        
        if (fromId == null || toId == null) {
            player.sendSystemMessage(Component.literal("Invalid block name"));
            return 0;
        }
        
        Block from = BuiltInRegistries.BLOCK.getValue(fromId);
        Block to = BuiltInRegistries.BLOCK.getValue(toId);
        
        if (from == null || to == null) {
            player.sendSystemMessage(Component.literal("Unknown block"));
            return 0;
        }
        
        BlockState toState = to.defaultBlockState();
        
        // Replace blocks
        int count = 0;
        for (BlockVector3 pos : region) {
            BlockState current = world.getBlockState(pos.toBlockPos());
            if (current.getBlock() == from) {
                world.setBlock(pos.toBlockPos(), toState, 3);
                count++;
            }
            
            // Limit to prevent server lag (temporary)
            if (count > 100000) {
                player.sendSystemMessage(Component.literal("§cWarning: Operation limited to 100,000 blocks"));
                break;
            }
        }
        
        player.sendSystemMessage(Component.literal(String.format("Replaced %d blocks", count)));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Create walls around selection.
     */
    private static int walls(CommandContext<CommandSourceStack> context, String blockName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("//walls not yet fully implemented"));
        // TODO: Implement walls logic
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Create faces of selection.
     */
    private static int faces(CommandContext<CommandSourceStack> context, String blockName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("//faces not yet fully implemented"));
        // TODO: Implement faces logic
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Overlay blocks on top surface.
     */
    private static int overlay(CommandContext<CommandSourceStack> context, String blockName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("//overlay not yet fully implemented"));
        // TODO: Implement overlay logic
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Parse a block ID from a string.
     */
    private static ResourceLocation parseBlockId(String name) {
        if (name.contains(":")) {
            return ResourceLocation.tryParse(name);
        } else {
            return ResourceLocation.withDefaultNamespace(name);
        }
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
