package net.minecraft.stats;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class ServerStatsCounter extends StatsCounter {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Codec<Map<Stat<?>, Integer>> STATS_CODEC = Codec.dispatchedMap(
			BuiltInRegistries.STAT_TYPE.byNameCodec(), Util.memoize(ServerStatsCounter::createTypedStatsCodec)
		)
		.xmap(map -> {
			Map<Stat<?>, Integer> map2 = new HashMap();
			map.forEach((statType, map2x) -> map2.putAll(map2x));
			return map2;
		}, map -> (Map)map.entrySet().stream().collect(Collectors.groupingBy(entry -> ((Stat)entry.getKey()).getType(), Util.toMap())));
	private final MinecraftServer server;
	private final File file;
	private final Set<Stat<?>> dirty = Sets.<Stat<?>>newHashSet();

	private static <T> Codec<Map<Stat<?>, Integer>> createTypedStatsCodec(StatType<T> statType) {
		Codec<T> codec = statType.getRegistry().byNameCodec();
		Codec<Stat<?>> codec2 = codec.<Stat<?>>flatComapMap(
			t -> statType.get(t),
			stat -> stat.getType() == statType
				? DataResult.success((T)stat.getValue())
				: DataResult.error(() -> "Expected type " + statType + ", but got " + stat.getType())
		);
		return Codec.unboundedMap(codec2, Codec.INT);
	}

	public ServerStatsCounter(MinecraftServer minecraftServer, File file) {
		this.server = minecraftServer;
		this.file = file;
		if (file.isFile()) {
			try {
				this.parseLocal(minecraftServer.getFixerUpper(), FileUtils.readFileToString(file));
			} catch (IOException var4) {
				LOGGER.error("Couldn't read statistics file {}", file, var4);
			} catch (JsonParseException var5) {
				LOGGER.error("Couldn't parse statistics file {}", file, var5);
			}
		}
	}

	public void save() {
		try {
			FileUtils.writeStringToFile(this.file, this.toJson());
		} catch (IOException var2) {
			LOGGER.error("Couldn't save stats", (Throwable)var2);
		}
	}

	@Override
	public void setValue(Player player, Stat<?> stat, int i) {
		super.setValue(player, stat, i);
		this.dirty.add(stat);
	}

	private Set<Stat<?>> getDirty() {
		Set<Stat<?>> set = Sets.<Stat<?>>newHashSet(this.dirty);
		this.dirty.clear();
		return set;
	}

	public void parseLocal(DataFixer dataFixer, String string) {
		try {
			JsonElement jsonElement = StrictJsonParser.parse(string);
			if (jsonElement.isJsonNull()) {
				LOGGER.error("Unable to parse Stat data from {}", this.file);
				return;
			}

			Dynamic<JsonElement> dynamic = new Dynamic<>(JsonOps.INSTANCE, jsonElement);
			dynamic = DataFixTypes.STATS.updateToCurrentVersion(dataFixer, dynamic, NbtUtils.getDataVersion(dynamic, 1343));
			this.stats
				.putAll(
					(Map)STATS_CODEC.parse(dynamic.get("stats").orElseEmptyMap())
						.resultOrPartial(stringx -> LOGGER.error("Failed to parse statistics for {}: {}", this.file, stringx))
						.orElse(Map.of())
				);
		} catch (JsonParseException var5) {
			LOGGER.error("Unable to parse Stat data from {}", this.file, var5);
		}
	}

	protected String toJson() {
		JsonObject jsonObject = new JsonObject();
		jsonObject.add("stats", STATS_CODEC.encodeStart(JsonOps.INSTANCE, this.stats).getOrThrow());
		jsonObject.addProperty("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
		return jsonObject.toString();
	}

	public void markAllDirty() {
		this.dirty.addAll(this.stats.keySet());
	}

	public void sendStats(ServerPlayer serverPlayer) {
		Object2IntMap<Stat<?>> object2IntMap = new Object2IntOpenHashMap<>();

		for (Stat<?> stat : this.getDirty()) {
			object2IntMap.put(stat, this.getValue(stat));
		}

		serverPlayer.connection.send(new ClientboundAwardStatsPacket(object2IntMap));
	}
}
