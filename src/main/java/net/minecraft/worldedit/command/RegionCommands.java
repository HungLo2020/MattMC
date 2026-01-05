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
        
        // //move command (stubbed - to be fully implemented)
        dispatcher.register(Commands.literal("move")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.move"))
            .executes(ctx -> notImplemented(ctx, "move")));
        
        // //stack command (stubbed - to be fully implemented)
        dispatcher.register(Commands.literal("stack")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.stack"))
            .executes(ctx -> notImplemented(ctx, "stack")));
        
        // //line command (stubbed - placeholder block argument for future implementation)
        dispatcher.register(Commands.literal("line")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.line"))
            .then(Commands.argument("block", StringArgumentType.word())
                .executes(ctx -> notImplemented(ctx, "line"))));
        
        // //hollow command (stubbed - to be fully implemented)
        dispatcher.register(Commands.literal("hollow")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.hollow"))
            .executes(ctx -> notImplemented(ctx, "hollow")));
        
        // //naturalize command (stubbed - to be fully implemented)
        dispatcher.register(Commands.literal("naturalize")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.naturalize"))
            .executes(ctx -> notImplemented(ctx, "naturalize")));
        
        // //center command (stubbed - placeholder block argument for future implementation)
        dispatcher.register(Commands.literal("center")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.region.center"))
            .then(Commands.argument("block", StringArgumentType.word())
                .executes(ctx -> notImplemented(ctx, "center"))));
        
        // //distr command (stubbed - to be fully implemented)
        dispatcher.register(Commands.literal("distr")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.analysis.distr"))
            .executes(ctx -> notImplemented(ctx, "distr")));
    }
    
    /**
     * Helper method for stubbed commands - consistent "not implemented" message.
     */
    private static int notImplemented(CommandContext<CommandSourceStack> context, String commandName) throws CommandSyntaxException {
        context.getSource().getPlayerOrException().sendSystemMessage(
            Component.literal(String.format("§e//%s command not yet implemented", commandName))
        );
        return Command.SINGLE_SUCCESS;
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
        
        // Create edit session
        net.minecraft.worldedit.core.EditSession editSession = 
            new net.minecraft.worldedit.core.EditSession(world, session.getDefaultChangeLimit());
        editSession.setFastMode(session.isFastMode());
        
        // Set blocks
        int count = editSession.setBlocks(region, state);
        
        // Remember for undo
        session.remember(editSession);
        
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
        
        BlockState fromState = from.defaultBlockState();
        BlockState toState = to.defaultBlockState();
        
        // Create edit session
        net.minecraft.worldedit.core.EditSession editSession = 
            new net.minecraft.worldedit.core.EditSession(world, session.getDefaultChangeLimit());
        editSession.setFastMode(session.isFastMode());
        
        // Replace blocks
        int count = editSession.replaceBlocks(region, fromState, toState);
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(String.format("Replaced %d blocks", count)));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Create walls around selection.
     */
    private static int walls(CommandContext<CommandSourceStack> context, String blockName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Region region = session.getSelection(world);
        
        if (region == null) {
            player.sendSystemMessage(Component.literal("No selection defined"));
            return 0;
        }
        
        // Parse block
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
        
        // Create edit session
        net.minecraft.worldedit.core.EditSession editSession = 
            new net.minecraft.worldedit.core.EditSession(world, session.getDefaultChangeLimit());
        editSession.setFastMode(session.isFastMode());
        
        // Build walls (only the vertical sides, not floor/ceiling)
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int count = 0;
        
        // Walls along X axis (north and south sides)
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                // South wall (min Z)
                if (editSession.setBlock(BlockVector3.at(x, y, min.getZ()), state)) count++;
                // North wall (max Z)
                if (editSession.setBlock(BlockVector3.at(x, y, max.getZ()), state)) count++;
            }
        }
        
        // Walls along Z axis (east and west sides)
        for (int z = min.getZ() + 1; z < max.getZ(); z++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                // West wall (min X)
                if (editSession.setBlock(BlockVector3.at(min.getX(), y, z), state)) count++;
                // East wall (max X)
                if (editSession.setBlock(BlockVector3.at(max.getX(), y, z), state)) count++;
            }
        }
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(String.format("Created walls: %d blocks", count)));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Create faces of selection.
     */
    private static int faces(CommandContext<CommandSourceStack> context, String blockName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Region region = session.getSelection(world);
        
        if (region == null) {
            player.sendSystemMessage(Component.literal("No selection defined"));
            return 0;
        }
        
        // Parse block
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
        
        // Create edit session
        net.minecraft.worldedit.core.EditSession editSession = 
            new net.minecraft.worldedit.core.EditSession(world, session.getDefaultChangeLimit());
        editSession.setFastMode(session.isFastMode());
        
        // Build all 6 faces
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int count = 0;
        
        // Bottom and top faces
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                // Bottom
                if (editSession.setBlock(BlockVector3.at(x, min.getY(), z), state)) count++;
                // Top
                if (editSession.setBlock(BlockVector3.at(x, max.getY(), z), state)) count++;
            }
        }
        
        // Side faces (excluding edges already covered by top/bottom)
        for (int y = min.getY() + 1; y < max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                // South and north
                if (editSession.setBlock(BlockVector3.at(x, y, min.getZ()), state)) count++;
                if (editSession.setBlock(BlockVector3.at(x, y, max.getZ()), state)) count++;
            }
            for (int z = min.getZ() + 1; z < max.getZ(); z++) {
                // West and east
                if (editSession.setBlock(BlockVector3.at(min.getX(), y, z), state)) count++;
                if (editSession.setBlock(BlockVector3.at(max.getX(), y, z), state)) count++;
            }
        }
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(String.format("Created faces: %d blocks", count)));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Overlay blocks on top surface.
     */
    private static int overlay(CommandContext<CommandSourceStack> context, String blockName) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Region region = session.getSelection(world);
        
        if (region == null) {
            player.sendSystemMessage(Component.literal("No selection defined"));
            return 0;
        }
        
        // Parse block
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
        
        // Create edit session
        net.minecraft.worldedit.core.EditSession editSession = 
            new net.minecraft.worldedit.core.EditSession(world, session.getDefaultChangeLimit());
        editSession.setFastMode(session.isFastMode());
        
        // Place overlay on top of solid blocks
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int count = 0;
        
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                // Find the highest solid block in this column
                for (int y = max.getY(); y >= min.getY(); y--) {
                    BlockVector3 pos = BlockVector3.at(x, y, z);
                    BlockState current = editSession.getBlock(pos);
                    
                    if (!current.isAir() && current.blocksMotion()) {
                        // Place overlay block on top
                        BlockVector3 above = pos.add(0, 1, 0);
                        if (above.getY() <= world.getMaxY()) {
                            if (editSession.setBlock(above, state)) {
                                count++;
                            }
                        }
                        break; // Move to next column
                    }
                }
            }
        }
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(String.format("Created overlay: %d blocks", count)));
        
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
