package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.pattern.BlockPatternParser;
import net.minecraft.worldedit.pattern.Pattern;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.session.LocalSession;
import net.minecraft.worldedit.tool.SuperPickaxeTool;
import net.minecraft.worldedit.tool.BrushTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool-related commands for WorldEdit.
 * Includes /tool, //tool, /none, //none, //, brush commands, etc.
 */
public class ToolCommands {
    
    /**
     * Register all tool commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //tool command (unbind tool)
        dispatcher.register(Commands.literal("/tool")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.tool.none"))
            .then(Commands.literal("none")
                .executes(ToolCommands::toolNone)));
        
        // //none command (unbind tool)
        dispatcher.register(Commands.literal("/none")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.tool.none"))
            .executes(ToolCommands::toolNone));
        
        // // command (super pickaxe toggle)
        dispatcher.register(Commands.literal("/")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.superpickaxe"))
            .executes(ToolCommands::superPickaxeToggle));
        
        // //superpickaxe command
        dispatcher.register(Commands.literal("/superpickaxe")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.superpickaxe"))
            .then(Commands.literal("single")
                .executes(ctx -> superPickaxeMode(ctx, "single")))
            .then(Commands.literal("area")
                .then(Commands.argument("range", IntegerArgumentType.integer(1, 5))
                    .executes(ctx -> superPickaxeMode(ctx, "area"))))
            .then(Commands.literal("recursive")
                .then(Commands.argument("range", IntegerArgumentType.integer(1, 5))
                    .executes(ctx -> superPickaxeMode(ctx, "recursive")))));
        
        registerBrushCommand(dispatcher, "/brush");
        registerBrushCommand(dispatcher, "/br");
    }

    private static void registerBrushCommand(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name)
            .requires(CommandSourceStack::isPlayer)
            .then(Commands.literal("unbind")
                .requires(source -> hasPermission(source, "worldedit.tool.none"))
                .executes(ToolCommands::toolNone))
            .then(Commands.literal("none")
                .requires(source -> hasPermission(source, "worldedit.tool.none"))
                .executes(ToolCommands::toolNone))
            .then(Commands.literal("sphere")
                .requires(source -> hasPermission(source, "worldedit.brush.sphere"))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(ctx -> brushSphere(ctx, StringArgumentType.getString(ctx, "args")))))
            .then(Commands.literal("cylinder")
                .requires(source -> hasPermission(source, "worldedit.brush.cylinder"))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(ctx -> brushCylinder(ctx, StringArgumentType.getString(ctx, "args")))))
            .then(Commands.literal("smooth")
                .requires(source -> hasPermission(source, "worldedit.brush.smooth"))
                .executes(ctx -> brushSmooth(ctx, ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(ctx -> brushSmooth(ctx, StringArgumentType.getString(ctx, "args")))));

        dispatcher.register(root);
    }
    
    /**
     * Unbind the current tool.
     */
    private static int toolNone(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to unbind"));
            return 0;
        }
        
        session.setTool(item.getItem(), null);
        player.sendSystemMessage(Component.literal("Tool unbound from " + item.getHoverName().getString()));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Toggle super pickaxe.
     */
    private static int superPickaxeToggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to bind super pickaxe"));
            return 0;
        }
        
        if (session.hasTool(item.getItem())) {
            // Toggle off
            session.setTool(item.getItem(), null);
            player.sendSystemMessage(Component.literal("§eSuper pickaxe disabled"));
        } else {
            // Toggle on
            SuperPickaxeTool tool = new SuperPickaxeTool("single", 1);
            session.setTool(item.getItem(), tool);
            player.sendSystemMessage(Component.literal("§aSuper pickaxe enabled (single mode)"));
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Set super pickaxe mode.
     */
    private static int superPickaxeMode(CommandContext<CommandSourceStack> context, String mode) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to bind super pickaxe"));
            return 0;
        }
        
        int range = 1;
        if (!mode.equals("single")) {
            try {
                range = IntegerArgumentType.getInteger(context, "range");
            } catch (IllegalArgumentException e) {
                range = 3;
            }
        }
        
        SuperPickaxeTool tool = new SuperPickaxeTool(mode, range);
        session.setTool(item.getItem(), tool);
        
        player.sendSystemMessage(Component.literal(
            String.format("§aSuper pickaxe enabled (%s mode, range: %d)", mode, range)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Bind sphere brush to item.
     */
    private static int brushSphere(CommandContext<CommandSourceStack> context, String args) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to bind brush"));
            return 0;
        }

        BrushPatternArgs parsed = parsePatternWithOptionalRadius(player, args, 2);
        if (parsed == null) {
            return 0;
        }

        // Create and bind brush
        BrushTool brush = new BrushTool("sphere", parsed.pattern(), parsed.radius());
        session.setTool(item.getItem(), brush);
        
        player.sendSystemMessage(Component.literal(
            String.format("§aSphere brush bound to %s (radius: %d, block: %s)", 
                item.getHoverName().getString(), parsed.radius(), parsed.patternText())
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Bind cylinder brush to item.
     */
    private static int brushCylinder(CommandContext<CommandSourceStack> context, String args) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to bind brush"));
            return 0;
        }

        CylinderBrushArgs parsed = parseCylinderArgs(player, args);
        if (parsed == null) {
            return 0;
        }

        // Create and bind brush
        BrushTool brush = new BrushTool("cylinder", parsed.pattern(), parsed.radius(), parsed.height());
        session.setTool(item.getItem(), brush);
        
        player.sendSystemMessage(Component.literal(
            String.format("§aCylinder brush bound to %s (radius: %d, height: %d, block: %s)",
                item.getHoverName().getString(), parsed.radius(), parsed.height(), parsed.patternText())
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Bind smooth brush to item.
     */
    private static int brushSmooth(CommandContext<CommandSourceStack> context, String args) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to bind brush"));
            return 0;
        }

        SmoothBrushArgs parsed = parseSmoothArgs(player, args);
        if (parsed == null) {
            return 0;
        }

        // Create and bind brush (smooth doesn't need a block)
        BrushTool brush = new BrushTool(
            "smooth",
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
            parsed.radius(),
            parsed.iterations()
        );
        session.setTool(item.getItem(), brush);
        
        player.sendSystemMessage(Component.literal(
            String.format("§aSmooth brush bound to %s (radius: %d, iterations: %d)",
                item.getHoverName().getString(), parsed.radius(), parsed.iterations())
        ));
        
        return Command.SINGLE_SUCCESS;
    }

    private static BrushPatternArgs parsePatternWithOptionalRadius(ServerPlayer player, String args, int defaultRadius) {
        List<String> tokens = splitArgs(args);
        if (tokens.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cUsage: /brush sphere <pattern> [radius]"));
            return null;
        }

        int radius = defaultRadius;
        Integer last = parseInteger(tokens.get(tokens.size() - 1));
        if (last != null) {
            radius = last;
            tokens.remove(tokens.size() - 1);
        }

        if (!validateRange(player, "radius", radius, 1, 10) || tokens.isEmpty()) {
            if (tokens.isEmpty()) {
                player.sendSystemMessage(Component.literal("§cMissing brush pattern"));
            }
            return null;
        }

        String patternText = String.join(" ", tokens);
        Pattern pattern = parsePattern(player, patternText);
        return pattern == null ? null : new BrushPatternArgs(pattern, patternText, radius);
    }

    private static CylinderBrushArgs parseCylinderArgs(ServerPlayer player, String args) {
        List<String> tokens = splitArgs(args);
        if (tokens.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cUsage: /brush cylinder <pattern> [radius] [height]"));
            return null;
        }

        int radius = 2;
        int height = 1;
        int size = tokens.size();
        Integer trailing = parseInteger(tokens.get(size - 1));
        Integer previous = size >= 2 ? parseInteger(tokens.get(size - 2)) : null;
        if (trailing != null && previous != null) {
            radius = previous;
            height = trailing;
            tokens.remove(tokens.size() - 1);
            tokens.remove(tokens.size() - 1);
        } else if (trailing != null) {
            radius = trailing;
            tokens.remove(tokens.size() - 1);
        }

        if (!validateRange(player, "radius", radius, 1, 10)
            || !validateRange(player, "height", height, 1, 10)
            || tokens.isEmpty()) {
            if (tokens.isEmpty()) {
                player.sendSystemMessage(Component.literal("§cMissing brush pattern"));
            }
            return null;
        }

        String patternText = String.join(" ", tokens);
        Pattern pattern = parsePattern(player, patternText);
        return pattern == null ? null : new CylinderBrushArgs(pattern, patternText, radius, height);
    }

    private static SmoothBrushArgs parseSmoothArgs(ServerPlayer player, String args) {
        List<String> tokens = splitArgs(args);
        if (tokens.size() > 2) {
            player.sendSystemMessage(Component.literal("§cUsage: /brush smooth [radius] [iterations]"));
            return null;
        }

        int radius = 2;
        int iterations = 3;
        if (!tokens.isEmpty()) {
            Integer parsedRadius = parseInteger(tokens.get(0));
            if (parsedRadius == null) {
                player.sendSystemMessage(Component.literal("§cInvalid radius: " + tokens.get(0)));
                return null;
            }
            radius = parsedRadius;
        }
        if (tokens.size() == 2) {
            Integer parsedIterations = parseInteger(tokens.get(1));
            if (parsedIterations == null) {
                player.sendSystemMessage(Component.literal("§cInvalid iterations: " + tokens.get(1)));
                return null;
            }
            iterations = parsedIterations;
        }

        if (!validateRange(player, "radius", radius, 1, 10)
            || !validateRange(player, "iterations", iterations, 1, 10)) {
            return null;
        }

        return new SmoothBrushArgs(radius, iterations);
    }

    private static List<String> splitArgs(String args) {
        String trimmed = args == null ? "" : args.trim();
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(trimmed.split("\\s+")));
    }

    private static Pattern parsePattern(ServerPlayer player, String patternText) {
        try {
            return BlockPatternParser.parse(patternText);
        } catch (IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal("§cInvalid brush pattern: " + e.getMessage()));
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean validateRange(ServerPlayer player, String name, int value, int min, int max) {
        if (value < min || value > max) {
            player.sendSystemMessage(Component.literal(
                String.format("§cBrush %s must be between %d and %d", name, min, max)
            ));
            return false;
        }
        return true;
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

    private record BrushPatternArgs(Pattern pattern, String patternText, int radius) {
    }

    private record CylinderBrushArgs(Pattern pattern, String patternText, int radius, int height) {
    }

    private record SmoothBrushArgs(int radius, int iterations) {
    }
}
