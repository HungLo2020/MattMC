package net.minecraft.server.jsonrpc.internalapi;

import java.util.Optional;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.JsonRpcLogger;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.GameRulesService;
import net.minecraft.server.jsonrpc.methods.InvalidParameterJsonRpcException;
import net.minecraft.world.flag.FeatureFlagSet;

public class MinecraftGameRuleServiceImpl implements MinecraftGameRuleService {
	private final DedicatedServer server;
	private final JsonRpcLogger jsonrpcLogger;

	public MinecraftGameRuleServiceImpl(DedicatedServer dedicatedServer, JsonRpcLogger jsonRpcLogger) {
		this.server = dedicatedServer;
		this.jsonrpcLogger = jsonRpcLogger;
	}

	@Override
	public GameRulesService.TypedRule updateGameRule(GameRulesService.UntypedRule untypedRule, ClientInfo clientInfo) {
		net.minecraft.world.level.GameRules.Value<?> value = this.getRuleValue(untypedRule.key());
		String string = value.serialize();
		if (value instanceof net.minecraft.world.level.GameRules.BooleanValue booleanValue) {
			booleanValue.set(Boolean.parseBoolean(untypedRule.value()), this.server);
		} else {
			if (!(value instanceof net.minecraft.world.level.GameRules.IntegerValue integerValue)) {
				throw new InvalidParameterJsonRpcException("Unknown rule type for key: " + untypedRule.key());
			}

			integerValue.set(Integer.parseInt(untypedRule.value()), this.server);
		}

		GameRulesService.TypedRule typedRule = this.getTypedRule(untypedRule.key(), value);
		this.jsonrpcLogger.log(clientInfo, "Game rule '{}' updated from '{}' to '{}'", typedRule.key(), string, typedRule.value());
		this.server.onGameRuleChanged(untypedRule.key(), value);
		return typedRule;
	}

	@Override
	public <T extends net.minecraft.world.level.GameRules.Value<T>> T getRule(net.minecraft.world.level.GameRules.Key<T> key) {
		return this.server.getGameRules().getRule(key);
	}

	@Override
	public GameRulesService.TypedRule getTypedRule(String string, net.minecraft.world.level.GameRules.Value<?> value) {
		return switch (value) {
			case net.minecraft.world.level.GameRules.BooleanValue booleanValue -> new GameRulesService.TypedRule(
				string, String.valueOf(booleanValue.get()), GameRulesService.RuleType.BOOL
			);
			case net.minecraft.world.level.GameRules.IntegerValue integerValue -> new GameRulesService.TypedRule(
				string, String.valueOf(integerValue.get()), GameRulesService.RuleType.INT
			);
			default -> throw new InvalidParameterJsonRpcException("Unknown rule type");
		};
	}

	@Override
	public Stream<Entry<net.minecraft.world.level.GameRules.Key<?>, net.minecraft.world.level.GameRules.Type<?>>> getAvailableGameRules() {
		FeatureFlagSet featureFlagSet = this.server.getWorldData().getLevelSettings().getDataConfiguration().enabledFeatures();
		return net.minecraft.world.level.GameRules.availableRules(featureFlagSet);
	}

	private Optional<net.minecraft.world.level.GameRules.Key<?>> getRuleKey(String string) {
		Stream<Entry<net.minecraft.world.level.GameRules.Key<?>, net.minecraft.world.level.GameRules.Type<?>>> stream = this.getAvailableGameRules();
		return stream.filter(entry -> ((net.minecraft.world.level.GameRules.Key)entry.getKey()).getId().equals(string)).findFirst().map(Entry::getKey);
	}

	@SuppressWarnings("unchecked")
	private <T extends net.minecraft.world.level.GameRules.Value<T>> T getRuleUnchecked(net.minecraft.world.level.GameRules.Key<?> key) {
		return (T)this.server.getGameRules().getRule((net.minecraft.world.level.GameRules.Key<T>)(net.minecraft.world.level.GameRules.Key<?>)key);
	}

	private net.minecraft.world.level.GameRules.Value<?> getRuleValue(String string) {
		net.minecraft.world.level.GameRules.Key<?> key = (net.minecraft.world.level.GameRules.Key<?>)this.getRuleKey(string)
			.orElseThrow(() -> new InvalidParameterJsonRpcException("Game rule '" + string + "' does not exist"));
		return getRuleUnchecked(key);
	}
}
