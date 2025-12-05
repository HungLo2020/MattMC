package net.minecraft.server.jsonrpc.internalapi;

import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.GameRulesService;

public interface MinecraftGameRuleService {
	GameRulesService.TypedRule updateGameRule(GameRulesService.UntypedRule untypedRule, ClientInfo clientInfo);

	<T extends net.minecraft.world.level.GameRules.Value<T>> T getRule(net.minecraft.world.level.GameRules.Key<T> key);

	GameRulesService.TypedRule getTypedRule(String string, net.minecraft.world.level.GameRules.Value<?> value);

	Stream<Entry<net.minecraft.world.level.GameRules.Key<?>, net.minecraft.world.level.GameRules.Type<?>>> getAvailableGameRules();
}
