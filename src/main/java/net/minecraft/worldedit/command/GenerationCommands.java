package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.pattern.BlockPatternParser;
import net.minecraft.worldedit.pattern.Pattern;
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
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .executes(ctx -> cylinder(ctx, StringArgumentType.getString(ctx, "args"), false))));
        
        // //hcyl command (hollow cylinder)
        dispatcher.register(Commands.literal("/hcyl")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.generation.cylinder"))
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .executes(ctx -> cylinder(ctx, StringArgumentType.getString(ctx, "args"), true))));
        
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
    private static int cylinder(CommandContext<CommandSourceStack> context, String args, boolean forceHollow) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);

        CylinderArgs parsed = parseCylinderArgs(player, args, forceHollow);
        if (parsed == null) {
            return 0;
        }

        // Create edit session
        EditSession editSession = session.createEditSession(world);
        
        // Generate cylinder
        BlockVector3 center = BlockVector3.from(player.blockPosition());
        int count = editSession.makeCylinder(center, parsed.pattern(), parsed.radiusX(), parsed.radiusZ(), parsed.height(), !parsed.hollow());
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(
            String.format("Generated %s cylinder: %d blocks", parsed.hollow() ? "hollow" : "solid", count)
        ));
        
        return Command.SINGLE_SUCCESS;
    }

    private static CylinderArgs parseCylinderArgs(ServerPlayer player, String args, boolean forceHollow) {
        List<String> tokens = splitArgs(args);
        boolean hollow = forceHollow;

        for (int i = tokens.size() - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if ("-h".equals(token)) {
                hollow = true;
                tokens.remove(i);
            } else if (token.startsWith("-") && !looksLikeNumber(token)) {
                player.sendSystemMessage(Component.literal("§cUnknown cylinder switch: " + token));
                return null;
            }
        }

        if (tokens.size() < 2 || tokens.size() > 3) {
            player.sendSystemMessage(Component.literal("§cUsage: //cyl <pattern> <radius>[,<radius>] [height] [-h]"));
            return null;
        }

        Pattern pattern;
        try {
            pattern = BlockPatternParser.parse(tokens.get(0));
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("§cInvalid cylinder pattern: " + e.getMessage()));
            return null;
        }

        double[] radii = parseRadii(tokens.get(1));
        if (radii == null) {
            player.sendSystemMessage(Component.literal("§cInvalid cylinder radius: " + tokens.get(1)));
            return null;
        }

        int height = 1;
        if (tokens.size() == 3) {
            try {
                height = Integer.parseInt(tokens.get(2));
            } catch (NumberFormatException e) {
                player.sendSystemMessage(Component.literal("§cInvalid cylinder height: " + tokens.get(2)));
                return null;
            }
        }

        return new CylinderArgs(pattern, Math.max(1, radii[0]), Math.max(1, radii[1]), height, hollow);
    }

    private static List<String> splitArgs(String args) {
        String trimmed = args == null ? "" : args.trim();
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(trimmed.split("\\s+")));
    }

    private static double[] parseRadii(String text) {
        String[] parts = text.split(",", -1);
        if (parts.length < 1 || parts.length > 2) {
            return null;
        }
        try {
            double radiusX = Double.parseDouble(parts[0]);
            double radiusZ = parts.length == 1 ? radiusX : Double.parseDouble(parts[1]);
            if (!Double.isFinite(radiusX) || !Double.isFinite(radiusZ)) {
                return null;
            }
            return new double[] { radiusX, radiusZ };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean looksLikeNumber(String token) {
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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

    private record CylinderArgs(Pattern pattern, double radiusX, double radiusZ, int height, boolean hollow) {
    }
}
