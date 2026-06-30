package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.session.LocalSession;

/**
 * Generation commands for WorldEdit.
 * Includes //cyl, //sphere, //pyramid, //hcyl, //hsphere, etc.
 */
public class GenerationCommands {
    
    /**
     * Register all generation commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //sphere command
        dispatcher.register(Commands.literal("/sphere")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.generation.sphere"))
            .then(Commands.argument("block", StringArgumentType.word())
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> sphere(ctx, 
                        StringArgumentType.getString(ctx, "block"),
                        IntegerArgumentType.getInteger(ctx, "radius"),
                        false)))));
        
        // //hsphere command (hollow sphere)
        dispatcher.register(Commands.literal("/hsphere")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.generation.sphere"))
            .then(Commands.argument("block", StringArgumentType.word())
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> sphere(ctx, 
                        StringArgumentType.getString(ctx, "block"),
                        IntegerArgumentType.getInteger(ctx, "radius"),
                        true)))));
        
        // //cyl command (cylinder)
        dispatcher.register(Commands.literal("/cyl")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.generation.cylinder"))
            .then(Commands.argument("block", StringArgumentType.word())
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                    .then(Commands.argument("height", IntegerArgumentType.integer(1, 256))
                        .executes(ctx -> cylinder(ctx,
                            StringArgumentType.getString(ctx, "block"),
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            IntegerArgumentType.getInteger(ctx, "height"),
                            false))))));
        
        // //hcyl command (hollow cylinder)
        dispatcher.register(Commands.literal("/hcyl")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.generation.cylinder"))
            .then(Commands.argument("block", StringArgumentType.word())
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                    .then(Commands.argument("height", IntegerArgumentType.integer(1, 256))
                        .executes(ctx -> cylinder(ctx,
                            StringArgumentType.getString(ctx, "block"),
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            IntegerArgumentType.getInteger(ctx, "height"),
                            true))))));
        
        // //pyramid command
        dispatcher.register(Commands.literal("/pyramid")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.generation.pyramid"))
            .then(Commands.argument("block", StringArgumentType.word())
                .then(Commands.argument("size", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> pyramid(ctx,
                        StringArgumentType.getString(ctx, "block"),
                        IntegerArgumentType.getInteger(ctx, "size"))))));
    }
    
    /**
     * Generate a sphere.
     */
    private static int sphere(CommandContext<CommandSourceStack> context, String blockName, int radius, boolean hollow) throws CommandSyntaxException {
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
        
        // Generate sphere
        BlockVector3 center = BlockVector3.from(player.blockPosition());
        int count = 0;
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    
                    if (hollow) {
                        // Hollow: only place blocks on the surface
                        if (distance <= radius && distance >= radius - 1) {
                            BlockVector3 pos = center.add(x, y, z);
                            if (editSession.setBlock(pos, state)) {
                                count++;
                            }
                        }
                    } else {
                        // Solid: place all blocks inside
                        if (distance <= radius) {
                            BlockVector3 pos = center.add(x, y, z);
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
            String.format("Generated %s sphere: %d blocks", hollow ? "hollow" : "solid", count)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Generate a cylinder.
     */
    private static int cylinder(CommandContext<CommandSourceStack> context, String blockName, int radius, int height, boolean hollow) throws CommandSyntaxException {
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
        
        // Generate cylinder
        BlockVector3 center = BlockVector3.from(player.blockPosition());
        int count = 0;
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                
                boolean shouldPlace = hollow ? (distance <= radius && distance >= radius - 1) : (distance <= radius);
                
                if (shouldPlace) {
                    for (int y = 0; y < height; y++) {
                        BlockVector3 pos = center.add(x, y, z);
                        if (editSession.setBlock(pos, state)) {
                            count++;
                        }
                    }
                }
            }
        }
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(
            String.format("Generated %s cylinder: %d blocks", hollow ? "hollow" : "solid", count)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Generate a pyramid.
     */
    private static int pyramid(CommandContext<CommandSourceStack> context, String blockName, int size) throws CommandSyntaxException {
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
        
        // Generate pyramid
        BlockVector3 center = BlockVector3.from(player.blockPosition());
        int count = 0;
        
        for (int y = 0; y < size; y++) {
            int layerSize = size - y;
            for (int x = -layerSize; x <= layerSize; x++) {
                for (int z = -layerSize; z <= layerSize; z++) {
                    BlockVector3 pos = center.add(x, y, z);
                    if (editSession.setBlock(pos, state)) {
                        count++;
                    }
                }
            }
        }
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(
            String.format("Generated pyramid: %d blocks", count)
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
