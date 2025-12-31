package com.seibel.distanthorizons.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.Nullable;

import static com.seibel.distanthorizons.core.network.messages.MessageRegistry.DEBUG_CODEC_CRASH_MESSAGE;
import static net.minecraft.commands.Commands.literal;


public class CommandInitializer
{
	private boolean serverReady = false;
	
	private static final int REQUIRED_PERMISSION_LEVEL = 4;
	
	
	/**
	 * A received command dispatcher, which is held until the server is ready to initialize the commands.
	 */
	@Nullable
	private CommandDispatcher<CommandSourceStack> commandDispatcher;
	
	/**
	 * Notify the command initializer that the game is ready to accept commands.
	 * If {@link CommandInitializer#initCommands(CommandDispatcher)} has been fired before it was ready, it will also initialize the commands.
	 */
	public void onServerReady()
	{
		this.serverReady = true;
		if (this.commandDispatcher != null)
		{
			this.initCommands(this.commandDispatcher);
			this.commandDispatcher = null;
		}
	}
	
	/**
	 * Initializes all available commands.
	 * If the game is not ready yet, it stores the dispatcher to initialize the commands later.
	 *
	 * @param commandDispatcher The command dispatcher to register commands to.
	 */
	public void initCommands(CommandDispatcher<CommandSourceStack> commandDispatcher)
	{
		if (!this.serverReady)
		{
			this.commandDispatcher = commandDispatcher;
			return;
		}
		
		LiteralArgumentBuilder<CommandSourceStack> builder = literal("dh")
				.requires((source) ->
				{
					return source.hasPermission(REQUIRED_PERMISSION_LEVEL);
				});
		
		builder.then(new ConfigCommand().buildCommand());
		builder.then(new DebugCommand().buildCommand());
		builder.then(new PregenCommand().buildCommand());
		
		if (DEBUG_CODEC_CRASH_MESSAGE)
		{
			builder.then(new CrashCommand().buildCommand());
		}
		
		commandDispatcher.register(builder);
	}
	
}
