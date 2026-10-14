package net.minecraft.world.level.biome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.NativeData;
import net.minecraft.sounds.Music;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;

/**
 * Builds {@link Biome} projections of Rust-decoded biome definitions
 * (`content/data`, schema `BIOME`); see {@link NativeData}.
 */
public final class NativeBiomes {
	private static final Logger LOGGER = LogUtils.getLogger();

	private NativeBiomes() {
	}

	private static final com.mojang.serialization.Codec<HolderSet<ConfiguredWorldCarver<?>>> CARVERS = ConfiguredWorldCarver.LIST_CODEC.promotePartial(Util.prefix("Carver: ", LOGGER::error));
	private static final com.mojang.serialization.Codec<List<HolderSet<PlacedFeature>>> FEATURES = PlacedFeature.LIST_OF_LISTS_CODEC.promotePartial(Util.prefix("Features: ", LOGGER::error));

	static Biome project(Object value, NativeData.Context context) {
		Object[] biome = NativeData.record(value);
		Object[] climate = NativeData.record(biome[0]);
		Biome.ClimateSettings climateSettings = new Biome.ClimateSettings(
			(Boolean)climate[0], (Float)climate[1], NativeData.named(Biome.TemperatureModifier.class, climate[2]), (Float)climate[3]
		);
		BiomeSpecialEffects effects = effects(NativeData.record(biome[1]), context);
		BiomeGenerationSettings generation = BiomeGenerationSettings.EMPTY;
		MobSpawnSettings mobs = MobSpawnSettings.EMPTY;
		if (biome.length > 2) {
			Object[] gen = NativeData.record(biome[2]);
			HolderSet<ConfiguredWorldCarver<?>> carvers = context.holderSet(gen[0], Registries.CONFIGURED_CARVER, CARVERS);
			List<HolderSet<PlacedFeature>> features = context.holderSetList(gen[1], Registries.PLACED_FEATURE, FEATURES);
			generation = new BiomeGenerationSettings(carvers, features);
			mobs = mobs(NativeData.record(biome[3]));
		}
		return new Biome(climateSettings, effects, generation, mobs);
	}

	private static BiomeSpecialEffects effects(Object[] e, NativeData.Context context) {
		Optional<AmbientParticleSettings> particle = Optional.ofNullable(e[8]).map(p -> {
			Object[] fields = NativeData.record(p);
			return new AmbientParticleSettings(context.slot(fields[0], ParticleTypes.CODEC), (Float)fields[1]);
		});
		Optional<AmbientMoodSettings> mood = Optional.ofNullable(e[10]).map(m -> {
			Object[] fields = NativeData.record(m);
			return new AmbientMoodSettings(context.sound(fields[0]), (Integer)fields[1], (Integer)fields[2], (Double)fields[3]);
		});
		Optional<AmbientAdditionsSettings> additions = Optional.ofNullable(e[11]).map(a -> {
			Object[] fields = NativeData.record(a);
			return new AmbientAdditionsSettings(context.sound(fields[0]), (Double)fields[1]);
		});
		Optional<WeightedList<Music>> music = Optional.ofNullable(e[12]).map(list -> {
			List<Weighted<Music>> entries = new ArrayList<>();
			for (Object entry : NativeData.list(list)) {
				Object[] weighted = NativeData.record(entry);
				Object[] data = NativeData.record(weighted[0]);
				entries.add(new Weighted<>(new Music(context.sound(data[0]), (Integer)data[1], (Integer)data[2], (Boolean)data[3]), (Integer)weighted[1]));
			}
			return WeightedList.of(entries);
		});
		return new BiomeSpecialEffects(
			(Integer)e[0],
			(Integer)e[1],
			(Integer)e[2],
			(Integer)e[3],
			Optional.ofNullable((Integer)e[4]),
			Optional.ofNullable((Integer)e[5]),
			Optional.ofNullable((Integer)e[6]),
			NativeData.named(BiomeSpecialEffects.GrassColorModifier.class, e[7]),
			particle,
			Optional.ofNullable(e[9]).map(context::sound),
			mood,
			additions,
			music,
			(Float)e[13]
		);
	}

	private static MobSpawnSettings mobs(Object[] m) {
		Map<MobCategory, WeightedList<MobSpawnSettings.SpawnerData>> spawners = new LinkedHashMap<>();
		for (Map.Entry<String, Object> category : NativeData.map(m[1]).entrySet()) {
			List<Weighted<MobSpawnSettings.SpawnerData>> entries = new ArrayList<>();
			for (Object entry : NativeData.list(category.getValue())) {
				Object[] s = NativeData.record(entry);
				entries.add(new Weighted<>(new MobSpawnSettings.SpawnerData(entityType(s[0]), (Integer)s[1], (Integer)s[2]), (Integer)s[3]));
			}
			spawners.put(NativeData.named(MobCategory.class, category.getKey()), WeightedList.of(entries));
		}
		Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> costs = new LinkedHashMap<>();
		for (Map.Entry<String, Object> cost : NativeData.map(m[2]).entrySet()) {
			Object[] c = NativeData.record(cost.getValue());
			costs.put(entityType(cost.getKey()), new MobSpawnSettings.MobSpawnCost((Double)c[0], (Double)c[1]));
		}
		return new MobSpawnSettings((Float)m[0], spawners, costs);
	}

	static Object unproject(Biome biome, NativeData.Builder b) {
		Biome.ClimateSettings c = biome.climateSettings;
		Object climate = new Object[]{c.hasPrecipitation(), c.temperature(), c.temperatureModifier().getSerializedName(), c.downfall()};
		BiomeSpecialEffects e = biome.getSpecialEffects();
		Object[] effects = new Object[]{
			e.getFogColor(),
			e.getWaterColor(),
			e.getWaterFogColor(),
			e.getSkyColor(),
			e.getFoliageColorOverride().orElse(null),
			e.getDryFoliageColorOverride().orElse(null),
			e.getGrassColorOverride().orElse(null),
			e.getGrassColorModifier().getSerializedName(),
			e.getAmbientParticleSettings().map(p -> new Object[]{b.slot(p.getOptions(), ParticleTypes.CODEC), p.probability()}).orElse(null),
			e.getAmbientLoopSoundEvent().map(b::sound).orElse(null),
			e.getAmbientMoodSettings().map(m -> new Object[]{b.sound(m.getSoundEvent()), m.getTickDelay(), m.getBlockSearchExtent(), m.getSoundPositionOffset()}).orElse(null),
			e.getAmbientAdditionsSettings().map(a -> new Object[]{b.sound(a.getSoundEvent()), a.getTickChance()}).orElse(null),
			e.getBackgroundMusic().map(list -> {
				List<Object> entries = new ArrayList<>();
				for (Weighted<Music> w : list.unwrap()) {
					Music m = w.value();
					entries.add(new Object[]{new Object[]{b.sound(m.event()), m.minDelay(), m.maxDelay(), m.replaceCurrentMusic()}, w.weight()});
				}
				return (Object)entries;
			}).orElse(null),
			e.getBackgroundMusicVolume()
		};
		BiomeGenerationSettings g = biome.getGenerationSettings();
		Object generation = new Object[]{
			b.slot(g.carverSet(), CARVERS),
			b.slot(g.featureSets(), FEATURES)
		};
		MobSpawnSettings mobs = biome.getMobSettings();
		Map<String, Object> spawners = new LinkedHashMap<>();
		mobs.spawnerMap().forEach((category, list) -> {
			List<Object> entries = new ArrayList<>();
			for (Weighted<MobSpawnSettings.SpawnerData> w : list.unwrap()) {
				MobSpawnSettings.SpawnerData data = w.value();
				entries.add(new Object[]{BuiltInRegistries.ENTITY_TYPE.getKey(data.type()).toString(), data.minCount(), data.maxCount(), w.weight()});
			}
			spawners.put(category.getSerializedName(), entries);
		});
		Map<String, Object> costs = new LinkedHashMap<>();
		mobs.costMap().forEach((type, cost) -> costs.put(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), new Object[]{cost.energyBudget(), cost.charge()}));
		Object mobSettings = new Object[]{mobs.getCreatureProbability(), spawners, costs};
		return new Object[]{climate, effects, generation, mobSettings};
	}

	private static final Map<Object, EntityType<?>> ENTITY_TYPES = new java.util.concurrent.ConcurrentHashMap<>();

	private static EntityType<?> entityType(Object id) {
		return ENTITY_TYPES.computeIfAbsent(id, key -> BuiltInRegistries.ENTITY_TYPE.getOptional(NativeData.location(key))
			.orElseThrow(() -> new IllegalStateException("Rust accepted unknown entity type " + key)));
	}
}
