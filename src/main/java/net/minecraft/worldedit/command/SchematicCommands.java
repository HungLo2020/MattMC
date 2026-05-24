package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.worldedit.clipboard.Clipboard;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.schematic.SchematicHandler;
import net.minecraft.worldedit.session.LocalSession;

/**
 * Schematic-related commands for WorldEdit.
 * Includes //schematic save, //schematic load, //schematic list, etc.
 */
public class SchematicCommands {
    private static final Set<Character> SAVE_SWITCHES = Set.of('f');
    
    /**
     * Register all schematic commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //schematic save command
        dispatcher.register(Commands.literal("/schematic")
            .then(Commands.literal("save")
                .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.schematic.save"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> save(ctx, StringArgumentType.getString(ctx, "name"), ""))
                    .then(Commands.argument("tail", StringArgumentType.greedyString())
                        .executes(ctx -> save(
                            ctx,
                            StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "tail")
                        ))))));
        
        // //schematic load command
        dispatcher.register(Commands.literal("/schematic")
            .then(Commands.literal("load")
                .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.schematic.load"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> load(ctx, StringArgumentType.getString(ctx, "name"), ""))
                    .then(Commands.argument("tail", StringArgumentType.greedyString())
                        .executes(ctx -> load(
                            ctx,
                            StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "tail")
                        ))))));
        
        // //schematic list command
        dispatcher.register(Commands.literal("/schematic")
            .then(Commands.literal("list")
                .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.schematic.list"))
                .executes(SchematicCommands::list)));
        
        // //schematic delete command
        dispatcher.register(Commands.literal("/schematic")
            .then(Commands.literal("delete")
                .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.schematic.delete"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> delete(ctx, StringArgumentType.getString(ctx, "name"))))));
        
        // Alias: //schem
        dispatcher.register(Commands.literal("/schem")
            .requires(source -> source.isPlayer())
            .then(Commands.literal("save")
                .requires(source -> hasPermission(source, "worldedit.schematic.save"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> save(ctx, StringArgumentType.getString(ctx, "name"), ""))
                    .then(Commands.argument("tail", StringArgumentType.greedyString())
                        .executes(ctx -> save(
                            ctx,
                            StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "tail")
                        )))))
            .then(Commands.literal("load")
                .requires(source -> hasPermission(source, "worldedit.schematic.load"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> load(ctx, StringArgumentType.getString(ctx, "name"), ""))
                    .then(Commands.argument("tail", StringArgumentType.greedyString())
                        .executes(ctx -> load(
                            ctx,
                            StringArgumentType.getString(ctx, "name"),
                            StringArgumentType.getString(ctx, "tail")
                        )))))
            .then(Commands.literal("list")
                .requires(source -> hasPermission(source, "worldedit.schematic.list"))
                .executes(SchematicCommands::list))
            .then(Commands.literal("delete")
                .requires(source -> hasPermission(source, "worldedit.schematic.delete"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> delete(ctx, StringArgumentType.getString(ctx, "name"))))));
    }
    
    /**
     * Save the current selection as a schematic.
     */
    private static int save(CommandContext<CommandSourceStack> context, String name, String tail) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        net.minecraft.server.level.ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        ParsedOptions options = parseOptions(tail, SAVE_SWITCHES, player);
        if (options == null) {
            return 0;
        }

        String format = options.positionals().isEmpty() ? "sponge" : options.positionals().getFirst();
        if (!isSupportedFormat(format)) {
            player.sendSystemMessage(Component.literal("§cUnsupported schematic format '" + format + "'. Only sponge/.schem is supported."));
            return 0;
        }

        File targetFile = new File(new File(world.getServer().getServerDirectory().toFile(), "schematics"), name + ".schem");
        boolean overwrite = options.switches().contains('f');
        if (targetFile.exists() && !overwrite) {
            player.sendSystemMessage(Component.literal("§cSchematic already exists. Use -f to overwrite."));
            return 0;
        }

        if (!session.hasClipboard()) {
            player.sendSystemMessage(Component.literal("§cNo clipboard available. Use //copy first."));
            return 0;
        }

        Clipboard clipboard = (Clipboard) session.getClipboard();
        if (clipboard.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cClipboard is empty. Use //copy first."));
            return 0;
        }
        
        // Save schematic
        try {
            SchematicHandler handler = new SchematicHandler(world.getServer().getServerDirectory().toFile());
            System.out.println("Saving schematic '" + name + "' with " + clipboard.getVolume() + " blocks from clipboard");
            handler.save(clipboard, name);
            player.sendSystemMessage(Component.literal(String.format("§aSchematic saved as '%s'", name)));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§cFailed to save schematic: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * Load a schematic into the clipboard.
     */
    private static int load(CommandContext<CommandSourceStack> context, String name, String tail) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        ParsedOptions options = parseOptions(tail, Set.of(), player);
        if (options == null) {
            return 0;
        }

        if (!options.positionals().isEmpty()) {
            String format = options.positionals().getFirst();
            if (!isSupportedFormat(format)) {
                player.sendSystemMessage(Component.literal("§cUnsupported schematic format '" + format + "'. Only sponge/.schem is supported."));
                return 0;
            }
        }
        
        try {
            SchematicHandler handler = new SchematicHandler(world.getServer().getServerDirectory().toFile());
            Clipboard clipboard = handler.load(name);
            
            System.out.println("Loaded schematic '" + name + "' into clipboard with " + clipboard.getVolume() + " blocks");
            session.setClipboard(clipboard);
            player.sendSystemMessage(Component.literal(String.format("§aSchematic '%s' loaded into clipboard", name)));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§cFailed to load schematic: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * List available schematics.
     */
    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        SchematicHandler handler = new SchematicHandler(world.getServer().getServerDirectory().toFile());
        String[] schematics = handler.listSchematics();
        
        if (schematics.length == 0) {
            player.sendSystemMessage(Component.literal("§eNo schematics found"));
        } else {
            player.sendSystemMessage(Component.literal("§aAvailable schematics:"));
            for (String schematic : schematics) {
                player.sendSystemMessage(Component.literal("  - " + schematic));
            }
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Delete a schematic file.
     */
    private static int delete(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        SchematicHandler handler = new SchematicHandler(world.getServer().getServerDirectory().toFile());
        
        if (handler.delete(name)) {
            player.sendSystemMessage(Component.literal(String.format("§aSchematic '%s' deleted", name)));
            return Command.SINGLE_SUCCESS;
        } else {
            player.sendSystemMessage(Component.literal(String.format("§cFailed to delete schematic '%s'", name)));
            return 0;
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

    private static boolean isSupportedFormat(String format) {
        String normalized = format.toLowerCase();
        return normalized.equals("sponge") || normalized.equals("schem");
    }

    private static ParsedOptions parseOptions(String tail, Set<Character> allowedSwitches, ServerPlayer player) {
        Set<Character> switches = new HashSet<>();
        List<String> positionals = new ArrayList<>();

        if (!tail.isBlank()) {
            for (String token : tail.trim().split("\\s+")) {
                if (token.startsWith("-") && token.length() > 1) {
                    for (int i = 1; i < token.length(); i++) {
                        char option = token.charAt(i);
                        if (!allowedSwitches.contains(option)) {
                            player.sendSystemMessage(Component.literal("§cUnknown switch '-" + option + "'."));
                            return null;
                        }
                        switches.add(option);
                    }
                } else {
                    positionals.add(token);
                }
            }
        }

        if (positionals.size() > 1) {
            player.sendSystemMessage(Component.literal("§cToo many arguments."));
            return null;
        }

        return new ParsedOptions(switches, positionals);
    }

    private record ParsedOptions(Set<Character> switches, List<String> positionals) {
    }
}
