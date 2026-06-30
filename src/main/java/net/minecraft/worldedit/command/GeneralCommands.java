package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.worldedit.command.argument.WorldEditMaskArgument;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.mask.Mask;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.session.LocalSession;

/**
 * General WorldEdit commands.
 */
public class GeneralCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerGlobalMaskCommand(dispatcher, "/gmask");
        registerGlobalMaskCommand(dispatcher, "gmask");
    }

    private static void registerGlobalMaskCommand(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        dispatcher.register(Commands.literal(name)
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.global-mask"))
            .executes(GeneralCommands::clearGlobalMask)
            .then(Commands.argument("mask", WorldEditMaskArgument.mask())
                .executes(ctx -> setGlobalMask(ctx, WorldEditMaskArgument.getMask(ctx, "mask")))));
    }

    private static int clearGlobalMask(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        session.setMask(null);
        player.sendSystemMessage(Component.literal("§eGlobal mask disabled"));
        return Command.SINGLE_SUCCESS;
    }

    private static int setGlobalMask(CommandContext<CommandSourceStack> context, Mask mask) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        session.setMask(mask);
        player.sendSystemMessage(Component.literal("§aGlobal mask set"));
        return Command.SINGLE_SUCCESS;
    }

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
