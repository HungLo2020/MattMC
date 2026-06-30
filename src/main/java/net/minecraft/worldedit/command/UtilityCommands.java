package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.command.argument.WorldEditMaskArgument;
import net.minecraft.worldedit.command.argument.WorldEditPatternArgument;
import net.minecraft.worldedit.mask.Mask;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.pattern.Pattern;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.session.LocalSession;

/**
 * Utility commands for WorldEdit.
 * Includes //drain, //fill, //fixwater, //fixlava, //snow, //thaw, etc.
 */
public class UtilityCommands {
    
    /**
     * Register all utility commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //drain command
        dispatcher.register(Commands.literal("/drain")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.drain"))
            .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                .executes(ctx -> drain(ctx, IntegerArgumentType.getInteger(ctx, "radius")))));
        
        // //fill command  
        dispatcher.register(Commands.literal("/fill")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.fill"))
            .then(Commands.argument("block", StringArgumentType.word())
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> fill(ctx,
                        StringArgumentType.getString(ctx, "block"),
                        IntegerArgumentType.getInteger(ctx, "radius"))))));
        
        // //fixwater command
        dispatcher.register(Commands.literal("/fixwater")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.fixwater"))
            .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                .executes(ctx -> fixFluid(ctx, IntegerArgumentType.getInteger(ctx, "radius"), true))));
        
        // //fixlava command
        dispatcher.register(Commands.literal("/fixlava")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.fixlava"))
            .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                .executes(ctx -> fixFluid(ctx, IntegerArgumentType.getInteger(ctx, "radius"), false))));
        
        // //removeabove command
        dispatcher.register(Commands.literal("/removeabove")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.removeabove"))
            .executes(ctx -> removeAbove(ctx, 256))
            .then(Commands.argument("height", IntegerArgumentType.integer(1, 320))
                .executes(ctx -> removeAbove(ctx, IntegerArgumentType.getInteger(ctx, "height")))));
        
        // //removebelow command
        dispatcher.register(Commands.literal("/removebelow")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.removebelow"))
            .executes(ctx -> removeBelow(ctx, 256))
            .then(Commands.argument("depth", IntegerArgumentType.integer(1, 320))
                .executes(ctx -> removeBelow(ctx, IntegerArgumentType.getInteger(ctx, "depth")))));
        
        // //replacenear command
        dispatcher.register(Commands.literal("/replacenear")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.replacenear"))
            .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                .then(Commands.argument("from", WorldEditMaskArgument.mask())
                    .executes(ctx -> missingReplaceNearOutput(ctx))
                    .then(Commands.argument("to", WorldEditPatternArgument.pattern())
                        .executes(ctx -> replaceNear(ctx,
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            WorldEditMaskArgument.getMask(ctx, "from"),
                            WorldEditPatternArgument.getPattern(ctx, "to")))))));
    }
    
    /**
     * Drain water and lava in a radius.
     */
    private static int drain(CommandContext<CommandSourceStack> context, int radius) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        // Create edit session
        EditSession editSession = session.createEditSession(world);
        
        // Drain fluids
        BlockVector3 center = BlockVector3.from(player.blockPosition());
        BlockState air = Blocks.AIR.defaultBlockState();
        int count = 0;
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        BlockVector3 pos = center.add(x, y, z);
                        BlockState current = editSession.getBlock(pos);
                        
                        // Check if it's a fluid
                        if (current.getFluidState().getType() != Fluids.EMPTY) {
                            if (editSession.setBlock(pos, air)) {
                                count++;
                            }
                        }
                    }
                }
            }
        }
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(
            String.format("Drained %d fluid blocks", count)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Fill air with a block in a radius.
     */
    private static int fill(CommandContext<CommandSourceStack> context, String blockName, int radius) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
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
        EditSession editSession = session.createEditSession(world);
        
        // Fill air
        BlockVector3 center = BlockVector3.from(player.blockPosition());
        int count = 0;
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        BlockVector3 pos = center.add(x, y, z);
                        BlockState current = editSession.getBlock(pos);
                        
                        // Only fill air blocks
                        if (current.isAir()) {
                            if (editSession.setBlock(pos, state)) {
                                count++;
                            }
                        }
                    }
                }
            }
        }
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(
            String.format("Filled %d blocks", count)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Fix flowing water or lava.
     */
    private static int fixFluid(CommandContext<CommandSourceStack> context, int radius, boolean water) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal(
            String.format("//fix%s not yet fully implemented", water ? "water" : "lava")
        ));
        // TODO: Implement fluid fixing logic
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Remove blocks above the player.
     */
    private static int removeAbove(CommandContext<CommandSourceStack> context, int height) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        // Create edit session
        EditSession editSession = session.createEditSession(world);
        
        // Remove blocks above
        BlockVector3 playerPos = BlockVector3.from(player.blockPosition());
        BlockState air = Blocks.AIR.defaultBlockState();
        int count = 0;
        
        for (int y = 1; y <= height; y++) {
            BlockVector3 pos = playerPos.add(0, y, 0);
            if (pos.getY() >= world.getMaxY()) break;
            
            if (editSession.setBlock(pos, air)) {
                count++;
            }
        }
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(
            String.format("Removed %d blocks above", count)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Remove blocks below the player.
     */
    private static int removeBelow(CommandContext<CommandSourceStack> context, int depth) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        // Create edit session
        EditSession editSession = session.createEditSession(world);
        
        // Remove blocks below
        BlockVector3 playerPos = BlockVector3.from(player.blockPosition());
        BlockState air = Blocks.AIR.defaultBlockState();
        int count = 0;
        
        for (int y = 1; y <= depth; y++) {
            BlockVector3 pos = playerPos.subtract(0, y, 0);
            if (pos.getY() < world.getMinY()) break;
            
            if (editSession.setBlock(pos, air)) {
                count++;
            }
        }
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(
            String.format("Removed %d blocks below", count)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Replace blocks near the player.
     */
    private static int missingReplaceNearOutput(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("Usage: //replacenear <radius> <from-blocks> <to-blocks>"));
        return 0;
    }

    private static int replaceNear(CommandContext<CommandSourceStack> context, int radius, Mask fromMask, Pattern toPattern) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        // Create edit session
        EditSession editSession = session.createEditSession(world);
        
        // Replace blocks
        BlockVector3 center = BlockVector3.from(player.blockPosition());
        int count = 0;
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        BlockVector3 pos = center.add(x, y, z);
                        BlockState current = editSession.getBlock(pos);
                        
                        if (fromMask.test(current)) {
                            BlockState replacement = toPattern.apply(pos);
                            if (replacement != null && editSession.setBlock(pos, replacement)) {
                                count++;
                            }
                        }
                    }
                }
            }
        }
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(
            String.format("Replaced %d blocks nearby", count)
        ));
        
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
