package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Fabric API stub for CommandRegistrationCallback
 */
@FunctionalInterface
public interface CommandRegistrationCallback {
    Event<CommandRegistrationCallback> EVENT = Event.create(CommandRegistrationCallback.class, 
        callbacks -> (dispatcher, registryAccess, environment) -> {
            for (CommandRegistrationCallback callback : callbacks) {
                callback.register(dispatcher, registryAccess, environment);
            }
        });
    
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment);
}
