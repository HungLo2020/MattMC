package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Callback for when a server registers all commands.
 *
 * <p>To register some commands, you would register an event listener and implement the callback.
 *
 * <pre>{@code
 * CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
 *     // For example, this command is only registered on an integrated server like the vanilla publish command
 *     if (environment.integrated) dispatcher.register(CommandManager.literal("integrated_command").executes(context -> {...}));
 * })};
 * }</pre>
 */
public interface CommandRegistrationCallback {
	Event<CommandRegistrationCallback> EVENT = EventFactory.createArrayBacked(CommandRegistrationCallback.class, (callbacks) -> (dispatcher, registryAccess, environment) -> {
		for (CommandRegistrationCallback callback : callbacks) {
			callback.register(dispatcher, registryAccess, environment);
		}
	});

	/**
	 * Called when the server is registering commands.
	 *
	 * @param dispatcher the command dispatcher to register commands to
	 * @param registryAccess object exposing access to the game's registries
	 * @param environment environment the registrations should be done for, used for commands that are dedicated or integrated server only
	 */
	void register(CommandDispatcher<CommandSourceStack> dispatcher, Commands.CommandSelection registryAccess, Commands.CommandSelection environment);
}
