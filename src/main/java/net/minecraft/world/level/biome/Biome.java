package net.minecraft.world.level.biome;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.ColorMapColorUtil;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import net.sodium.client.world.biome.BiomeColorMaps;
import org.jetbrains.annotations.Nullable;

public final class Biome implements net.irisshaders.iris.mixinterface.ExtendedBiome {
	public static final Codec<Biome> DIRECT_CODEC = net.minecraft.resources.NativeData.codec(
		net.minecraft.resources.NativeData.Kind.BIOME, false, NativeBiomes::project, NativeBiomes::unproject
	);
	public static final Codec<Biome> NETWORK_CODEC = net.minecraft.resources.NativeData.codec(
		net.minecraft.resources.NativeData.Kind.BIOME, true, NativeBiomes::project, NativeBiomes::unproject
	);
	public static final Codec<Holder<Biome>> CODEC = RegistryFileCodec.create(Registries.BIOME, DIRECT_CODEC);
	public static final Codec<HolderSet<Biome>> LIST_CODEC = RegistryCodecs.homogeneousList(Registries.BIOME, DIRECT_CODEC);
	private static final PerlinSimplexNoise TEMPERATURE_NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(1234L)), ImmutableList.of(0));
	static final PerlinSimplexNoise FROZEN_TEMPERATURE_NOISE = new PerlinSimplexNoise(
		new WorldgenRandom(new LegacyRandomSource(3456L)), ImmutableList.of(-2, -1, 0)
	);
	public static final PerlinSimplexNoise BIOME_INFO_NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(2345L)), ImmutableList.of(0));
	// VoxelMap: Made accessible
	public final Biome.ClimateSettings climateSettings;
	private final BiomeGenerationSettings generationSettings;
	private final MobSpawnSettings mobSettings;
	private final BiomeSpecialEffects specialEffects;
	// Iris: Track biome category
	private int biomeCategory = -1;
	// Sodium: Biome color caching fields (merged from BiomeMixin)
	private boolean hasCustomGrassColor;
	private int customGrassColor;
	private boolean hasCustomFoliageColor;
	private int customFoliageColor;
	private int defaultColorIndex;
	private BiomeSpecialEffects cachedSpecialEffects;
	private final ThreadLocal<NativeBiomeTemperature> temperatureCache = ThreadLocal.withInitial(NativeBiomeTemperature::new);

	Biome(
		Biome.ClimateSettings climateSettings,
		BiomeSpecialEffects biomeSpecialEffects,
		BiomeGenerationSettings biomeGenerationSettings,
		MobSpawnSettings mobSpawnSettings
	) {
		this.climateSettings = climateSettings;
		this.generationSettings = biomeGenerationSettings;
		this.mobSettings = mobSpawnSettings;
		this.specialEffects = biomeSpecialEffects;
		// Sodium: Initialize biome colors (merged from BiomeMixin)
		sodium$setupColors();
	}

	/** The Rust-stored definition this biome projects (`content/data`), or 0 for biomes built in Java. */
	public long nativeHandle() {
		return ((net.minecraft.resources.NativeData.DataCodec<Biome>)DIRECT_CODEC).nativeHandle(this);
	}

	public int getSkyColor() {
		return this.specialEffects.getSkyColor();
	}

	public MobSpawnSettings getMobSettings() {
		return this.mobSettings;
	}

	public boolean hasPrecipitation() {
		return this.climateSettings.hasPrecipitation();
	}

	public Biome.Precipitation getPrecipitationAt(BlockPos blockPos, int seaLevel) {
        return switch (NativeBiomeWeather.evaluate(this, blockPos, null, 0, seaLevel, false)) {
            case 0 -> Biome.Precipitation.NONE;
            case 1 -> Biome.Precipitation.RAIN;
            case 2 -> Biome.Precipitation.SNOW;
            default -> throw new IllegalStateException("Invalid native precipitation value");
        };
    }

    void bindWeatherTemperature(long driver) {
        NativeBiomeTemperature cache = this.temperatureCache.get();
        Biome.TemperatureModifier modifier = this.climateSettings.temperatureModifier();
        cache.bindWeather(driver, modifier == null ? -1 : modifier.ordinal());
    }

	private boolean temperatureTest(BlockPos blockPos, int seaLevel, int mode) {
		long key = blockPos.asLong();
		return this.temperatureCache.get().test(this, blockPos, key, seaLevel, mode);
	}

	float invokeTemperatureModifier(BlockPos blockPos, float base) {
		return this.climateSettings.temperatureModifier.modifyTemperature(blockPos, base);
	}

	/** Immutable synthesis views are bound only when the native policy requests them. */
	static net.minecraft.world.level.levelgen.synth.NativeNoiseState temperatureNoise(int kind) {
		return switch (kind) {
			case 0 -> FROZEN_TEMPERATURE_NOISE.nativeState();
			case 1 -> BIOME_INFO_NOISE.nativeState();
			case 2 -> TEMPERATURE_NOISE.nativeState();
			default -> throw new IllegalArgumentException("Unknown temperature synthesis binding " + kind);
		};
	}

	public boolean shouldFreeze(LevelReader levelReader, BlockPos blockPos) {
        return this.shouldFreeze(levelReader, blockPos, true);
    }

    public boolean shouldFreeze(LevelReader levelReader, BlockPos blockPos, boolean edge) {
        return NativeBiomeWeather.evaluate(this, blockPos, levelReader, 1, 0, edge) != 0;
    }

	public boolean coldEnoughToSnow(BlockPos blockPos, int i) {
		return !this.warmEnoughToRain(blockPos, i);
	}

	public boolean warmEnoughToRain(BlockPos blockPos, int i) {
		return this.temperatureTest(blockPos, i, 0);
	}

	public boolean shouldMeltFrozenOceanIcebergSlightly(BlockPos blockPos, int i) {
		return this.temperatureTest(blockPos, i, 1);
	}

	public boolean shouldSnow(LevelReader levelReader, BlockPos blockPos) {
        return NativeBiomeWeather.evaluate(this, blockPos, levelReader, 2, 0, false) != 0;
    }

	public BiomeGenerationSettings getGenerationSettings() {
		return this.generationSettings;
	}

	public int getFogColor() {
		return this.specialEffects.getFogColor();
	}

	public int getGrassColor(double d, double e) {
		// Sodium: Optimized grass color calculation (merged from BiomeMixin)
		if (this.specialEffects != this.cachedSpecialEffects) {
			sodium$setupColors();
		}

		int color;

		if (this.hasCustomGrassColor) {
			color = this.customGrassColor;
		} else {
			color = BiomeColorMaps.getGrassColor(this.defaultColorIndex);
		}

		var modifier = this.cachedSpecialEffects.getGrassColorModifier();

		if (modifier != BiomeSpecialEffects.GrassColorModifier.NONE) {
			color = modifier.modifyColor(d, e, color);
		}

		return color;
	}

	private int getBaseGrassColor() {
		Optional<Integer> optional = this.specialEffects.getGrassColorOverride();
		return optional.isPresent() ? (Integer)optional.get() : this.getGrassColorFromTexture();
	}

	private int getGrassColorFromTexture() {
		double d = Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
		double e = Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);
		return GrassColor.get(d, e);
	}

	public int getFoliageColor() {
		// Sodium: Optimized foliage color calculation (merged from BiomeMixin)
		if (this.specialEffects != this.cachedSpecialEffects) {
			sodium$setupColors();
		}

		int color;

		if (this.hasCustomFoliageColor) {
			color = this.customFoliageColor;
		} else {
			color = BiomeColorMaps.getFoliageColor(this.defaultColorIndex);
		}

		return color;
	}

	private int getFoliageColorFromTexture() {
		double d = Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
		double e = Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);
		return FoliageColor.get(d, e);
	}

	public int getDryFoliageColor() {
		return (Integer)this.specialEffects.getDryFoliageColorOverride().orElseGet(this::getDryFoliageColorFromTexture);
	}

	private int getDryFoliageColorFromTexture() {
		double d = Mth.clamp(this.climateSettings.temperature, 0.0F, 1.0F);
		double e = Mth.clamp(this.climateSettings.downfall, 0.0F, 1.0F);
		return ColorMapColorUtil.getDryFoliage(d, e);
	}

	public float getBaseTemperature() {
		return this.climateSettings.temperature;
	}

	public BiomeSpecialEffects getSpecialEffects() {
		return this.specialEffects;
	}

	public int getWaterColor() {
		return this.specialEffects.getWaterColor();
	}

	public int getWaterFogColor() {
		return this.specialEffects.getWaterFogColor();
	}

	public Optional<AmbientParticleSettings> getAmbientParticle() {
		return this.specialEffects.getAmbientParticleSettings();
	}

	public Optional<Holder<SoundEvent>> getAmbientLoop() {
		return this.specialEffects.getAmbientLoopSoundEvent();
	}

	public Optional<AmbientMoodSettings> getAmbientMood() {
		return this.specialEffects.getAmbientMoodSettings();
	}

	public Optional<AmbientAdditionsSettings> getAmbientAdditions() {
		return this.specialEffects.getAmbientAdditionsSettings();
	}

	public Optional<WeightedList<Music>> getBackgroundMusic() {
		return this.specialEffects.getBackgroundMusic();
	}

	public float getBackgroundMusicVolume() {
		return this.specialEffects.getBackgroundMusicVolume();
	}

	public static class BiomeBuilder {
		private boolean hasPrecipitation = true;
		@Nullable
		private Float temperature;
		private Biome.TemperatureModifier temperatureModifier = Biome.TemperatureModifier.NONE;
		@Nullable
		private Float downfall;
		@Nullable
		private BiomeSpecialEffects specialEffects;
		@Nullable
		private MobSpawnSettings mobSpawnSettings;
		@Nullable
		private BiomeGenerationSettings generationSettings;

		public Biome.BiomeBuilder hasPrecipitation(boolean bl) {
			this.hasPrecipitation = bl;
			return this;
		}

		public Biome.BiomeBuilder temperature(float f) {
			this.temperature = f;
			return this;
		}

		public Biome.BiomeBuilder downfall(float f) {
			this.downfall = f;
			return this;
		}

		public Biome.BiomeBuilder specialEffects(BiomeSpecialEffects biomeSpecialEffects) {
			this.specialEffects = biomeSpecialEffects;
			return this;
		}

		public Biome.BiomeBuilder mobSpawnSettings(MobSpawnSettings mobSpawnSettings) {
			this.mobSpawnSettings = mobSpawnSettings;
			return this;
		}

		public Biome.BiomeBuilder generationSettings(BiomeGenerationSettings biomeGenerationSettings) {
			this.generationSettings = biomeGenerationSettings;
			return this;
		}

		public Biome.BiomeBuilder temperatureAdjustment(Biome.TemperatureModifier temperatureModifier) {
			this.temperatureModifier = temperatureModifier;
			return this;
		}

		public Biome build() {
			if (this.temperature != null && this.downfall != null && this.specialEffects != null && this.mobSpawnSettings != null && this.generationSettings != null) {
				return new Biome(
					new Biome.ClimateSettings(this.hasPrecipitation, this.temperature, this.temperatureModifier, this.downfall),
					this.specialEffects,
					this.generationSettings,
					this.mobSpawnSettings
				);
			} else {
				throw new IllegalStateException("You are missing parameters to build a proper biome\n" + this);
			}
		}

		public String toString() {
			return "BiomeBuilder{\nhasPrecipitation="
				+ this.hasPrecipitation
				+ ",\ntemperature="
				+ this.temperature
				+ ",\ntemperatureModifier="
				+ this.temperatureModifier
				+ ",\ndownfall="
				+ this.downfall
				+ ",\nspecialEffects="
				+ this.specialEffects
				+ ",\nmobSpawnSettings="
				+ this.mobSpawnSettings
				+ ",\ngenerationSettings="
				+ this.generationSettings
				+ ",\n}";
		}
	}

	public record ClimateSettings(boolean hasPrecipitation, float temperature, Biome.TemperatureModifier temperatureModifier, float downfall) {
	}

	public static enum Precipitation implements StringRepresentable {
		NONE("none"),
		RAIN("rain"),
		SNOW("snow");

		public static final Codec<Biome.Precipitation> CODEC = StringRepresentable.fromEnum(Biome.Precipitation::values);
		private final String name;

		private Precipitation(final String string2) {
			this.name = string2;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public static enum TemperatureModifier implements StringRepresentable {
		NONE("none") {
			@Override public float modifyTemperature(BlockPos blockPos, float value) {
				return NativeBiomeTemperature.modifier(0, blockPos, value);
			}
		},
		FROZEN("frozen") {
			@Override public float modifyTemperature(BlockPos blockPos, float value) {
				return NativeBiomeTemperature.modifier(1, blockPos, value);
			}
		};

		private final String name;

		public abstract float modifyTemperature(BlockPos blockPos, float f);

		TemperatureModifier(final String string2) {
			this.name = string2;
		}

		public String getName() {
			return this.name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
	
	// Iris: Implementation of ExtendedBiome interface
	@Override
	public int getBiomeCategory() {
		return this.biomeCategory;
	}
	
	@Override
	public void setBiomeCategory(int biomeCategory) {
		this.biomeCategory = biomeCategory;
	}
	
	@Override
	public float getDownfall() {
		return this.climateSettings.downfall();
	}

	// Sodium: Setup color caching (merged from BiomeMixin)
	private void sodium$setupColors() {
		this.cachedSpecialEffects = specialEffects;

		var grassColor = this.cachedSpecialEffects.getGrassColorOverride();

		if (grassColor.isPresent()) {
			this.hasCustomGrassColor = true;
			this.customGrassColor = grassColor.get();
		} else {
			this.hasCustomGrassColor = false;
		}

		var foliageColor = this.cachedSpecialEffects.getFoliageColorOverride();

		if (foliageColor.isPresent()) {
			this.hasCustomFoliageColor = true;
			this.customFoliageColor = foliageColor.get();
		} else {
			this.hasCustomFoliageColor = false;
		}

		this.defaultColorIndex = sodium$getDefaultColorIndex();
	}

	private int sodium$getDefaultColorIndex() {
		double temperature = Mth.clamp(this.climateSettings.temperature(), 0.0F, 1.0F);
		double humidity = Mth.clamp(this.climateSettings.downfall(), 0.0F, 1.0F);

		return BiomeColorMaps.getIndex(temperature, humidity);
	}
}
