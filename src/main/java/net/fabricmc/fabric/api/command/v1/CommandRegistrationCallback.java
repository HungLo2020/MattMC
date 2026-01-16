package net.fabricmc.fabric.api.command.v1;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Backwards compatibility stub for Command API v1.
 * Redirects to v2 implementation for 1.21.10 compatibility.
 * 
 * @deprecated Use {@link net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback} instead
 */
@Deprecated
public interface CommandRegistrationCallback {
	Event<CommandRegistrationCallback> EVENT = EventFactory.createArrayBacked(CommandRegistrationCallback.class, callbacks -> (dispatcher, registryAccess, environment) -> {
		for (CommandRegistrationCallback callback : callbacks) {
			callback.register(dispatcher, registryAccess, environment);
		}
	});

	/**
	 * Called when commands are being registered.
	 *
	 * @param dispatcher the command dispatcher
	 * @param registryAccess the registry access
	 * @param environment the command selection environment
	 */
	void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment);
}
