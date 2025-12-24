package com.seibel.distanthorizons.common.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.distant_horizons.common.wrappers.misc.ServerPlayerWrapper;
import net.distant_horizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import net.minecraft.commands.CommandSourceStack;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Abstract class providing common functionality for DH's commands.
 */
public abstract class AbstractCommand
{
	public abstract LiteralArgumentBuilder<CommandSourceStack> buildCommand();
	
	
	/**
	 * Sends a success response to the player with the given text.
	 *
	 * @param commandContext The command context to send the response to.
	 * @param text The text to display in the success message.
	 * @return 1, indicating that the command was successful.
	 */
	protected int sendSuccessResponse(CommandContext<CommandSourceStack> commandContext, String text, boolean notifyAdmins)
	{
		commandContext.getSource().sendSuccess(() -> Component.literal(text), notifyAdmins);
		return 1;
	}
	
	/**
	 * Sends a failure response to the player with the given text.
	 *
	 * @param commandContext The command context to send the response to.
	 * @param text The text to display in the failure message.
	 * @return 1, indicating that the command was successful.
	 */
	protected int sendFailureResponse(CommandContext<CommandSourceStack> commandContext, String text)
	{
		commandContext.getSource().sendFailure(Component.literal(text));
		return 1;
	}
	
	/**
	 * Gets the server player from a command context.
	 *
	 * @param commandContext The command context to get the server player from.
	 * @return The server player wrapper for the player who sent the command.
	 */
	protected IServerPlayerWrapper getSourcePlayer(CommandContext<CommandSourceStack> commandContext)
	{
		return ServerPlayerWrapper.getWrapper(Objects.requireNonNull(commandContext.getSource().getPlayer()));
	}
	
	/**
	 * Checks if the source of a command is a player.
	 *
	 * @param source The source of the command to check.
	 * @return True if the source is a player, false otherwise.
	 */
	protected boolean isPlayerSource(CommandSourceStack source)
	{
		return source.isPlayer();
	}
	
}
