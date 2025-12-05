package net.minecraft.network.chat;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ChatDecorator {
	ChatDecorator PLAIN = (serverPlayer, component) -> component;

	Component decorate(@Nullable ServerPlayer serverPlayer, Component component);
}
