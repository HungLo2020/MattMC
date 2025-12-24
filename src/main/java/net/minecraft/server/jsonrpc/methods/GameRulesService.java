package net.minecraft.server.jsonrpc.methods;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.GameRules;

public class GameRulesService {
	@SuppressWarnings("unchecked")
	private static <T extends GameRules.Value<T>> T getRuleUnchecked(MinecraftApi minecraftApi, GameRules.Key<?> key) {
		return (T)minecraftApi.gameRuleService().getRule((GameRules.Key<T>)(GameRules.Key<?>)key);
	}

	public static List<GameRulesService.TypedRule> get(MinecraftApi minecraftApi) {
		List<? extends GameRules.Key<?>> list = minecraftApi.gameRuleService().getAvailableGameRules().map(Entry::getKey).toList();
		List<GameRulesService.TypedRule> list2 = new ArrayList();

		for (GameRules.Key<?> key : list) {
			GameRules.Value<?> value = getRuleUnchecked(minecraftApi, key);
			list2.add(getTypedRule(minecraftApi, key.getId(), value));
		}

		return list2;
	}

	public static GameRulesService.TypedRule getTypedRule(MinecraftApi minecraftApi, String string, GameRules.Value<?> value) {
		return minecraftApi.gameRuleService().getTypedRule(string, value);
	}

	public static GameRulesService.TypedRule update(MinecraftApi minecraftApi, GameRulesService.UntypedRule untypedRule, ClientInfo clientInfo) {
		return minecraftApi.gameRuleService().updateGameRule(untypedRule, clientInfo);
	}

	public static enum RuleType implements StringRepresentable {
		INT("integer"),
		BOOL("boolean");

		private final String name;

		private RuleType(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public record TypedRule(String key, String value, GameRulesService.RuleType type) {
		public static final MapCodec<GameRulesService.TypedRule> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.fieldOf("key").forGetter(GameRulesService.TypedRule::key),
					Codec.STRING.fieldOf("value").forGetter(GameRulesService.TypedRule::value),
					StringRepresentable.fromEnum(GameRulesService.RuleType::values).fieldOf("type").forGetter(GameRulesService.TypedRule::type)
				)
				.apply(instance, GameRulesService.TypedRule::new)
		);
	}

	public record UntypedRule(String key, String value) {
		public static final MapCodec<GameRulesService.UntypedRule> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.STRING.fieldOf("key").forGetter(GameRulesService.UntypedRule::key), Codec.STRING.fieldOf("value").forGetter(GameRulesService.UntypedRule::value)
				)
				.apply(instance, GameRulesService.UntypedRule::new)
		);
	}
}
