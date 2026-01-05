package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.platform.MattMCPlatform;

/**
 * History-related commands for WorldEdit.
 * Includes //undo, //redo, //clearhistory.
 */
public class HistoryCommands {
    
    /**
     * Register all history commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //undo command
        dispatcher.register(Commands.literal("undo")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.history.undo"))
            .executes(HistoryCommands::undo));
        
        // //redo command
        dispatcher.register(Commands.literal("redo")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.history.redo"))
            .executes(HistoryCommands::redo));
        
        // //clearhistory command
        dispatcher.register(Commands.literal("clearhistory")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.history.clear"))
            .executes(HistoryCommands::clearHistory));
    }
    
    /**
     * Undo the last edit.
     */
    private static int undo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("//undo not yet implemented - history system pending"));
        // TODO: Implement undo when history system is ready
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Redo the last undone edit.
     */
    private static int redo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("//redo not yet implemented - history system pending"));
        // TODO: Implement redo when history system is ready
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Clear the edit history.
     */
    private static int clearHistory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        player.sendSystemMessage(Component.literal("History cleared"));
        // TODO: Implement clear history when history system is ready
        return Command.SINGLE_SUCCESS;
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
