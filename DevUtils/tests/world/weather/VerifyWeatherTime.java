package net.minecraft.server.level;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * Actual ServerLevel weather/time/sleep-threshold methods on disconnected level
 * shells (constructors bypassed; no chunks, entities or players). Runs the same
 * scripted workload on any classpath and prints a per-tick trace digest.
 * Usage: VerifyWeatherTime <label> <trace-output> [benchmark]
 */
public final class VerifyWeatherTime {
	static Field field(Class<?> type, String name) throws Exception {
		Field f = type.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}
	static void set(Class<?> type, Object target, String name, Object value) throws Exception { field(type, name).set(target, value); }
	@SuppressWarnings("unchecked")
	static <T> T allocate(Class<T> type) throws Exception {
		var unsafe = Class.forName("sun.misc.Unsafe");
		return (T)unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type);
	}
	static Method method(String name, Class<?>... args) throws Exception {
		Method m = ServerLevel.class.getDeclaredMethod(name, args);
		m.setAccessible(true);
		return m;
	}
	static void image(String name) throws Exception {
		try (var in = ClassLoader.getSystemResourceAsStream(name.replace('.', '/') + ".class")) {
			if (in == null) throw new AssertionError(name);
			System.out.println("CLASS " + name + " " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
		}
	}

	/** Records every broadcast in order with its audience. */
	static final class CapturePlayerList extends net.minecraft.server.dedicated.DedicatedPlayerList {
		final List<String> packets = new ArrayList<>();
		CapturePlayerList() { super(null, null, null); }
		@Override public void broadcastAll(Packet<?> packet) { this.packets.add(describe(packet) + "@all"); }
		@Override public void broadcastAll(Packet<?> packet, ResourceKey<Level> key) { this.packets.add(describe(packet) + "@" + key.location()); }
		static String describe(Packet<?> packet) {
			if (packet instanceof ClientboundGameEventPacket event) {
				try {
					return "event" + field(ClientboundGameEventPacket.Type.class, "id").getInt(event.getEvent()) + ":" + Integer.toHexString(Float.floatToRawIntBits(event.getParam()));
				} catch (Exception e) { throw new RuntimeException(e); }
			}
			return packet.getClass().getName();
		}
	}

	static MethodHandle advanceWeather, tickTime, resetWeather;
	static Method weatherView;
	static ServerLevel level(MinecraftServer server, ServerLevelData data, String name, boolean skyLight, boolean ceiling, long seed) throws Exception {
		ServerLevel level = allocate(ServerLevel.class);
		var dimension = new DimensionType(OptionalLong.empty(), skyLight, ceiling, false, true, 1.0, true, false,
			-64, 384, 384, null, null, 0, Optional.empty(), null);
		set(Level.class, level, "dimensionTypeRegistration", Holder.direct(dimension));
		set(Level.class, level, "levelData", data);
		set(Level.class, level, "random", RandomSource.create(seed));
		set(Level.class, level, "dimension", ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.ResourceLocation.withDefaultNamespace(name)));
		set(ServerLevel.class, level, "serverLevelData", data);
		set(ServerLevel.class, level, "server", server);
		set(ServerLevel.class, level, "tickTime", true);
		set(ServerLevel.class, level, "sleepStatus", new SleepStatus());
		if (weatherView != null) set(ServerLevel.class, level, "weather", weatherView.invoke(null, data));
		Method prepare = Level.class.getDeclaredMethod("prepareWeather");
		prepare.setAccessible(true);
		prepare.invoke(level);
		return level;
	}

	/** A TimerCallback on any side (its other abstract methods differ between trees). */
	@SuppressWarnings("unchecked")
	static net.minecraft.world.level.timers.TimerCallback<MinecraftServer> callback(Runnable action) {
		return (net.minecraft.world.level.timers.TimerCallback<MinecraftServer>)java.lang.reflect.Proxy.newProxyInstance(
			VerifyWeatherTime.class.getClassLoader(), new Class<?>[]{net.minecraft.world.level.timers.TimerCallback.class}, (proxy, m, a) -> {
				if (m.getName().equals("handle")) { action.run(); return null; }
				if (m.getName().equals("hashCode")) return System.identityHashCode(proxy);
				if (m.getName().equals("equals")) return proxy == a[0];
				if (m.getName().equals("toString")) return "rule_flip";
				throw new UnsupportedOperationException(m.getName());
			});
	}

	/** One scripted run; returns the trace digest. */
	static String run(PrintWriter trace, int ticks) throws Exception {
		var settings = new LevelSettings("weather", GameType.SURVIVAL, false, Difficulty.NORMAL, true, new GameRules(FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT);
		var primary = new PrimaryLevelData(settings, new WorldOptions(1L, false, false), PrimaryLevelData.SpecialWorldProperty.NONE, Lifecycle.stable());
		// PrimaryLevelData copies its settings; flip the rules the levels actually read.
		var rules = primary.getGameRules();
		primary.setClearWeatherTime(0);
		var derived = new DerivedLevelData(primary, primary);
		MinecraftServer server = allocate(net.minecraft.server.dedicated.DedicatedServer.class);
		var players = allocate(CapturePlayerList.class);
		set(CapturePlayerList.class, players, "packets", new ArrayList<String>());
		set(MinecraftServer.class, server, "playerList", players);
		ServerLevel[] levels = {
			level(server, primary, "overworld", true, false, 11),
			level(server, derived, "derived_sky", true, false, 12),
			level(server, derived, "derived_ceiling", true, true, 13),
			level(server, derived, "derived_dark", false, false, 14),
		};
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		Field skyDarken = field(Level.class, "skyDarken");
		for (int tick = 0; tick < ticks; tick++) {
			// Command and rule events at fixed ticks, applied between level ticks.
			switch (tick % 50_000) {
				case 1_000 -> levels[0].setWeatherParameters(0, 6000, true, true);
				case 8_000 -> levels[0].setWeatherParameters(3000, 0, false, false);
				case 12_000 -> rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
				case 14_000 -> rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(true, null);
				case 15_000 -> { try { resetWeather.invokeExact(levels[0]); } catch (Throwable t) { throw new RuntimeException(t); } }
				case 16_000 -> rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
				case 17_000 -> rules.getRule(GameRules.RULE_DAYLIGHT).set(true, null);
				case 18_000 -> { for (ServerLevel level : levels) level.setDayTime(level.getDayTime() + 1000); } // /time add
				case 19_000 -> { for (ServerLevel level : levels) level.setDayTime(-5); } // /time set
				case 20_000 -> levels[1].setWeatherParameters(0, 99, true, true); // ignored derived write
				case 21_000 -> primary.getScheduledEvents().schedule("rule_flip", primary.getGameTime() + 1, callback(() -> {
					// Scheduled function: changes time and the daylight rule before the day phase reads them.
					primary.setDayTime(primary.getDayTime() + 777);
					rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
				}));
				case 21_100 -> rules.getRule(GameRules.RULE_DAYLIGHT).set(true, null);
				case 22_000 -> { primary.setGameTime(Long.MAX_VALUE - 5); primary.setDayTime(Long.MAX_VALUE - 5); }
				default -> {}
			}
			for (ServerLevel level : levels) {
				try {
					advanceWeather.invokeExact(level);
					level.updateSkyBrightness();
					tickTime.invokeExact(level);
				} catch (Throwable t) {
					throw new RuntimeException(t);
				}
			}
			if (trace == null) {
				players.packets.clear();
				continue;
			}
			StringBuilder row = new StringBuilder().append(tick);
			row.append(' ').append(primary.getGameTime()).append(' ').append(primary.getDayTime()).append(' ').append(primary.getClearWeatherTime())
				.append(' ').append(primary.getRainTime()).append(' ').append(primary.isRaining()).append(' ').append(primary.getThunderTime()).append(' ').append(primary.isThundering());
			for (ServerLevel level : levels) {
				row.append(" | ").append(Integer.toHexString(Float.floatToRawIntBits(level.getRainLevel(0.25F))))
					.append(' ').append(Integer.toHexString(Float.floatToRawIntBits(level.getThunderLevel(0.75F))))
					.append(' ').append(level.isRaining()).append(' ').append(level.isThundering()).append(' ').append(skyDarken.getInt(level));
			}
			for (String packet : players.packets) row.append(" ").append(packet);
			players.packets.clear();
			String line = row.toString();
			digest.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			trace.println(line);
		}
		// Equal next draws prove equal RNG consumption per level.
		StringBuilder tail = new StringBuilder("rng");
		for (ServerLevel level : levels) tail.append(' ').append(level.getRandom().nextLong());
		var sleep = new SleepStatus();
		for (int active : new int[]{0, 1, 2, 3, 7, 10, 64, 21_474_837, Integer.MAX_VALUE}) {
			set(SleepStatus.class, sleep, "activePlayers", active);
			for (int percentage : new int[]{Integer.MIN_VALUE, -1, 0, 1, 33, 50, 99, 100, 101, Integer.MAX_VALUE}) tail.append(' ').append(sleep.sleepersNeeded(percentage));
		}
		digest.update(tail.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		if (trace != null) trace.println(tail);
		return HexFormat.of().formatHex(digest.digest());
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		var lookup = MethodHandles.lookup();
		advanceWeather = lookup.unreflect(method("advanceWeatherCycle"));
		tickTime = lookup.unreflect(method("tickTime"));
		resetWeather = lookup.unreflect(method("resetWeatherCycle"));
		try {
			weatherView = method("weatherView", ServerLevelData.class);
		} catch (NoSuchMethodException e) {
			weatherView = null;
		}
		System.out.println("NATIVE_WEATHER " + (weatherView != null));
		for (String name : List.of("net.minecraft.server.level.ServerLevel", "net.minecraft.world.level.Level", "net.minecraft.world.level.storage.PrimaryLevelData",
			"net.minecraft.world.level.storage.DerivedLevelData", "net.minecraft.server.players.SleepStatus")) image(name);
		int ticks = 100_000;
		try (var trace = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
			System.out.println("TRACE " + run(trace, ticks) + " " + ticks);
		}
		if (args.length > 2 && args[2].equals("benchmark")) {
			int warmups = args.length > 3 ? Integer.parseInt(args[3]) : 3, samples = args.length > 4 ? Integer.parseInt(args[4]) : 10;
			for (int i = 0; i < warmups; i++) run(null, ticks);
			for (int sample = 0; sample < samples; sample++) {
				long start = System.nanoTime();
				run(null, ticks);
				System.out.println("TIMING " + sample + " " + (System.nanoTime() - start));
			}
		}
	}
}
