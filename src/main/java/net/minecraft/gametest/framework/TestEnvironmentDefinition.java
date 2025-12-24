package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;

public interface TestEnvironmentDefinition {
	Codec<TestEnvironmentDefinition> DIRECT_CODEC = BuiltInRegistries.TEST_ENVIRONMENT_DEFINITION_TYPE
		.byNameCodec()
		.dispatch(TestEnvironmentDefinition::codec, mapCodec -> mapCodec);
	Codec<Holder<TestEnvironmentDefinition>> CODEC = RegistryFileCodec.create(Registries.TEST_ENVIRONMENT, DIRECT_CODEC);

	static MapCodec<? extends TestEnvironmentDefinition> bootstrap(Registry<MapCodec<? extends TestEnvironmentDefinition>> registry) {
		Registry.register(registry, "all_of", TestEnvironmentDefinition.AllOf.CODEC);
		Registry.register(registry, "game_rules", TestEnvironmentDefinition.SetGameRules.CODEC);
		Registry.register(registry, "time_of_day", TestEnvironmentDefinition.TimeOfDay.CODEC);
		Registry.register(registry, "weather", TestEnvironmentDefinition.Weather.CODEC);
		return Registry.register(registry, "function", TestEnvironmentDefinition.Functions.CODEC);
	}

	void setup(ServerLevel serverLevel);

	default void teardown(ServerLevel serverLevel) {
	}

	MapCodec<? extends TestEnvironmentDefinition> codec();

	public record AllOf(List<Holder<TestEnvironmentDefinition>> definitions) implements TestEnvironmentDefinition {
		public static final MapCodec<TestEnvironmentDefinition.AllOf> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(TestEnvironmentDefinition.CODEC.listOf().fieldOf("definitions").forGetter(TestEnvironmentDefinition.AllOf::definitions))
				.apply(instance, TestEnvironmentDefinition.AllOf::new)
		);

		public AllOf(TestEnvironmentDefinition... testEnvironmentDefinitions) {
			this(Arrays.stream(testEnvironmentDefinitions).map(Holder::direct).toList());
		}

		@Override
		public void setup(ServerLevel serverLevel) {
			this.definitions.forEach(holder -> ((TestEnvironmentDefinition)holder.value()).setup(serverLevel));
		}

		@Override
		public void teardown(ServerLevel serverLevel) {
			this.definitions.forEach(holder -> ((TestEnvironmentDefinition)holder.value()).teardown(serverLevel));
		}

		@Override
		public MapCodec<TestEnvironmentDefinition.AllOf> codec() {
			return CODEC;
		}
	}

	public record Functions(Optional<ResourceLocation> setupFunction, Optional<ResourceLocation> teardownFunction) implements TestEnvironmentDefinition {
		private static final Logger LOGGER = LogUtils.getLogger();
		public static final MapCodec<TestEnvironmentDefinition.Functions> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					ResourceLocation.CODEC.optionalFieldOf("setup").forGetter(TestEnvironmentDefinition.Functions::setupFunction),
					ResourceLocation.CODEC.optionalFieldOf("teardown").forGetter(TestEnvironmentDefinition.Functions::teardownFunction)
				)
				.apply(instance, TestEnvironmentDefinition.Functions::new)
		);

		@Override
		public void setup(ServerLevel serverLevel) {
			this.setupFunction.ifPresent(resourceLocation -> run(serverLevel, resourceLocation));
		}

		@Override
		public void teardown(ServerLevel serverLevel) {
			this.teardownFunction.ifPresent(resourceLocation -> run(serverLevel, resourceLocation));
		}

		private static void run(ServerLevel serverLevel, ResourceLocation resourceLocation) {
			MinecraftServer minecraftServer = serverLevel.getServer();
			ServerFunctionManager serverFunctionManager = minecraftServer.getFunctions();
			Optional<CommandFunction<CommandSourceStack>> optional = serverFunctionManager.get(resourceLocation);
			if (optional.isPresent()) {
				CommandSourceStack commandSourceStack = minecraftServer.createCommandSourceStack().withPermission(2).withSuppressedOutput().withLevel(serverLevel);
				serverFunctionManager.execute((CommandFunction<CommandSourceStack>)optional.get(), commandSourceStack);
			} else {
				LOGGER.error("Test Batch failed for non-existent function {}", resourceLocation);
			}
		}

		@Override
		public MapCodec<TestEnvironmentDefinition.Functions> codec() {
			return CODEC;
		}
	}

	public record SetGameRules(
		List<TestEnvironmentDefinition.SetGameRules.Entry<Boolean, GameRules.BooleanValue>> boolRules,
		List<TestEnvironmentDefinition.SetGameRules.Entry<Integer, GameRules.IntegerValue>> intRules
	) implements TestEnvironmentDefinition {
		public static final MapCodec<TestEnvironmentDefinition.SetGameRules> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					TestEnvironmentDefinition.SetGameRules.Entry.codec(GameRules.BooleanValue.class, Codec.BOOL)
						.listOf()
						.fieldOf("bool_rules")
						.forGetter(TestEnvironmentDefinition.SetGameRules::boolRules),
					TestEnvironmentDefinition.SetGameRules.Entry.codec(GameRules.IntegerValue.class, Codec.INT)
						.listOf()
						.fieldOf("int_rules")
						.forGetter(TestEnvironmentDefinition.SetGameRules::intRules)
				)
				.apply(instance, TestEnvironmentDefinition.SetGameRules::new)
		);

		@Override
		public void setup(ServerLevel serverLevel) {
			GameRules gameRules = serverLevel.getGameRules();
			MinecraftServer minecraftServer = serverLevel.getServer();

			for (TestEnvironmentDefinition.SetGameRules.Entry<Boolean, GameRules.BooleanValue> entry : this.boolRules) {
				gameRules.getRule(entry.key()).set(entry.value(), minecraftServer);
			}

			for (TestEnvironmentDefinition.SetGameRules.Entry<Integer, GameRules.IntegerValue> entry : this.intRules) {
				gameRules.getRule(entry.key()).set(entry.value(), minecraftServer);
			}
		}

		@Override
		public void teardown(ServerLevel serverLevel) {
			GameRules gameRules = serverLevel.getGameRules();
			MinecraftServer minecraftServer = serverLevel.getServer();

			for (TestEnvironmentDefinition.SetGameRules.Entry<Boolean, GameRules.BooleanValue> entry : this.boolRules) {
				gameRules.getRule(entry.key()).setFrom(GameRules.getType(entry.key()).createRule(), minecraftServer);
			}

			for (TestEnvironmentDefinition.SetGameRules.Entry<Integer, GameRules.IntegerValue> entry : this.intRules) {
				gameRules.getRule(entry.key()).setFrom(GameRules.getType(entry.key()).createRule(), minecraftServer);
			}
		}

		@Override
		public MapCodec<TestEnvironmentDefinition.SetGameRules> codec() {
			return CODEC;
		}

		public static <S, T extends GameRules.Value<T>> TestEnvironmentDefinition.SetGameRules.Entry<S, T> entry(GameRules.Key<T> key, S object) {
			return new TestEnvironmentDefinition.SetGameRules.Entry<>(key, object);
		}

		public record Entry<S, T extends GameRules.Value<T>>(GameRules.Key<T> key, S value) {
			public static <S, T extends GameRules.Value<T>> Codec<TestEnvironmentDefinition.SetGameRules.Entry<S, T>> codec(Class<T> class_, Codec<S> codec) {
				return RecordCodecBuilder.create(
					instance -> instance.group(
							GameRules.keyCodec(class_).fieldOf("rule").forGetter(TestEnvironmentDefinition.SetGameRules.Entry::key),
							codec.fieldOf("value").forGetter(TestEnvironmentDefinition.SetGameRules.Entry::value)
						)
						.apply(instance, TestEnvironmentDefinition.SetGameRules.Entry::new)
				);
			}
		}
	}

	public record TimeOfDay(int time) implements TestEnvironmentDefinition {
		public static final MapCodec<TestEnvironmentDefinition.TimeOfDay> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(ExtraCodecs.NON_NEGATIVE_INT.fieldOf("time").forGetter(TestEnvironmentDefinition.TimeOfDay::time))
				.apply(instance, TestEnvironmentDefinition.TimeOfDay::new)
		);

		@Override
		public void setup(ServerLevel serverLevel) {
			serverLevel.setDayTime(this.time);
		}

		@Override
		public MapCodec<TestEnvironmentDefinition.TimeOfDay> codec() {
			return CODEC;
		}
	}

	public record Weather(TestEnvironmentDefinition.Weather.Type weather) implements TestEnvironmentDefinition {
		public static final MapCodec<TestEnvironmentDefinition.Weather> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(TestEnvironmentDefinition.Weather.Type.CODEC.fieldOf("weather").forGetter(TestEnvironmentDefinition.Weather::weather))
				.apply(instance, TestEnvironmentDefinition.Weather::new)
		);

		@Override
		public void setup(ServerLevel serverLevel) {
			this.weather.apply(serverLevel);
		}

		@Override
		public void teardown(ServerLevel serverLevel) {
			serverLevel.resetWeatherCycle();
		}

		@Override
		public MapCodec<TestEnvironmentDefinition.Weather> codec() {
			return CODEC;
		}

		public static enum Type implements StringRepresentable {
			CLEAR("clear", 100000, 0, false, false),
			RAIN("rain", 0, 100000, true, false),
			THUNDER("thunder", 0, 100000, true, true);

			public static final Codec<TestEnvironmentDefinition.Weather.Type> CODEC = StringRepresentable.fromEnum(TestEnvironmentDefinition.Weather.Type::values);
			private final String id;
			private final int clearTime;
			private final int rainTime;
			private final boolean raining;
			private final boolean thundering;

			private Type(final String string2, final int j, final int k, final boolean bl, final boolean bl2) {
				this.id = string2;
				this.clearTime = j;
				this.rainTime = k;
				this.raining = bl;
				this.thundering = bl2;
			}

			void apply(ServerLevel serverLevel) {
				serverLevel.setWeatherParameters(this.clearTime, this.rainTime, this.raining, this.thundering);
			}

			@Override
			public String getSerializedName() {
				return this.id;
			}
		}
	}
}
