package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.worldedit.clipboard.Clipboard;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.region.Region;
import net.minecraft.worldedit.schematic.SchematicHandler;
import net.minecraft.worldedit.session.LocalSession;

/**
 * Schematic-related commands for WorldEdit.
 * Includes //schematic save, //schematic load, //schematic list, etc.
 */
public class SchematicCommands {
    
    /**
     * Register all schematic commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //schematic save command
        dispatcher.register(Commands.literal("schematic")
            .then(Commands.literal("save")
                .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.schematic.save"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> save(ctx, StringArgumentType.getString(ctx, "name"))))));
        
        // //schematic load command
        dispatcher.register(Commands.literal("schematic")
            .then(Commands.literal("load")
                .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.schematic.load"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> load(ctx, StringArgumentType.getString(ctx, "name"))))));
        
        // //schematic list command
        dispatcher.register(Commands.literal("schematic")
            .then(Commands.literal("list")
                .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.schematic.list"))
                .executes(SchematicCommands::list)));
        
        // //schematic delete command
        dispatcher.register(Commands.literal("schematic")
            .then(Commands.literal("delete")
                .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.schematic.delete"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> delete(ctx, StringArgumentType.getString(ctx, "name"))))));
        
        // Alias: //schem
        dispatcher.register(Commands.literal("schem")
            .requires(source -> source.isPlayer())
            .then(Commands.literal("save")
                .requires(source -> hasPermission(source, "worldedit.schematic.save"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> save(ctx, StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("load")
                .requires(source -> hasPermission(source, "worldedit.schematic.load"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> load(ctx, StringArgumentType.getString(ctx, "name")))))
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
    private static int save(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        // Get selection
        Region region = session.getSelection(world);
        if (region == null) {
            player.sendSystemMessage(Component.literal("§cNo selection defined. Use the wand or //pos commands."));
            return 0;
        }
        
        // Copy selection to clipboard
        Clipboard clipboard = new Clipboard(region, region.getMinimumPoint());
        
        // Copy all blocks from region to clipboard
        for (int x = region.getMinimumPoint().getX(); x <= region.getMaximumPoint().getX(); x++) {
            for (int y = region.getMinimumPoint().getY(); y <= region.getMaximumPoint().getY(); y++) {
                for (int z = region.getMinimumPoint().getZ(); z <= region.getMaximumPoint().getZ(); z++) {
                    net.minecraft.worldedit.math.BlockVector3 pos = net.minecraft.worldedit.math.BlockVector3.at(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = 
                        world.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
                    clipboard.setBlock(pos, state);
                }
            }
        }
        
        // Save schematic
        try {
            SchematicHandler handler = new SchematicHandler(world.getServer().getServerDirectory().toFile());
            handler.save(clipboard, name);
            player.sendSystemMessage(Component.literal(String.format("§aSchematic saved as '%s'", name)));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§cFailed to save schematic: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * Load a schematic into the clipboard.
     */
    private static int load(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        try {
            SchematicHandler handler = new SchematicHandler(world.getServer().getServerDirectory().toFile());
            Clipboard clipboard = handler.load(name);
            
            session.setClipboard(clipboard);
            player.sendSystemMessage(Component.literal(String.format("§aSchematic '%s' loaded into clipboard", name)));
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§cFailed to load schematic: " + e.getMessage()));
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
}
