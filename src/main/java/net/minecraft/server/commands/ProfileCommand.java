package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.profiling.custom.ProfilerManager;

import java.nio.file.Path;

/**
 * Command for starting and stopping the custom profiler.
 */
public class ProfileCommand {
    private static final SimpleCommandExceptionType ERROR_ALREADY_RUNNING = 
        new SimpleCommandExceptionType(Component.literal("Profiler is already running"));
    private static final SimpleCommandExceptionType ERROR_NOT_RUNNING = 
        new SimpleCommandExceptionType(Component.literal("Profiler is not running"));
    private static final SimpleCommandExceptionType START_FAILED = 
        new SimpleCommandExceptionType(Component.literal("Failed to start profiler"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("profile")
                .requires(source -> source.hasPermission(2))  // Operator level 2
                .then(Commands.literal("start")
                    .executes(context -> startProfiling(context.getSource())))
                .then(Commands.literal("stop")
                    .executes(context -> stopProfiling(context.getSource())))
        );
    }

    private static int startProfiling(CommandSourceStack source) throws CommandSyntaxException {
        if (ProfilerManager.isRunning()) {
            throw ERROR_ALREADY_RUNNING.create();
        }

        if (!ProfilerManager.start(source)) {
            throw START_FAILED.create();
        }

        source.sendSuccess(
            () -> Component.literal("Profiling started. Use '/profile stop' to end and generate report.")
                .withStyle(ChatFormatting.GREEN),
            true
        );
        return 1;
    }

    private static int stopProfiling(CommandSourceStack source) throws CommandSyntaxException {
        if (!ProfilerManager.isRunning()) {
            throw ERROR_NOT_RUNNING.create();
        }

        try {
            Path reportPath = ProfilerManager.stop();
            
            Component pathComponent = Component.literal(reportPath.toString())
                .withStyle(ChatFormatting.UNDERLINE)
                .withStyle(ChatFormatting.AQUA)
                .withStyle(style -> 
                    style.withClickEvent(new ClickEvent.CopyToClipboard(reportPath.toAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy path")))
                );

            source.sendSuccess(
                () -> Component.literal("Profiling stopped. Report saved to: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(pathComponent),
                true
            );
            return 1;
        } catch (Exception e) {
            source.sendFailure(
                Component.literal("Failed to generate profiling report: " + e.getMessage())
                    .withStyle(ChatFormatting.RED)
            );
            return 0;
        }
    }
}
