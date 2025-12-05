package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.random.WeightedList;
import org.jetbrains.annotations.Nullable;

public class BiomeSpecialEffects {
	public static final Codec<BiomeSpecialEffects> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.INT.fieldOf("fog_color").forGetter(biomeSpecialEffects -> biomeSpecialEffects.fogColor),
				Codec.INT.fieldOf("water_color").forGetter(biomeSpecialEffects -> biomeSpecialEffects.waterColor),
				Codec.INT.fieldOf("water_fog_color").forGetter(biomeSpecialEffects -> biomeSpecialEffects.waterFogColor),
				Codec.INT.fieldOf("sky_color").forGetter(biomeSpecialEffects -> biomeSpecialEffects.skyColor),
				Codec.INT.optionalFieldOf("foliage_color").forGetter(biomeSpecialEffects -> biomeSpecialEffects.foliageColorOverride),
				Codec.INT.optionalFieldOf("dry_foliage_color").forGetter(biomeSpecialEffects -> biomeSpecialEffects.dryFoliageColorOverride),
				Codec.INT.optionalFieldOf("grass_color").forGetter(biomeSpecialEffects -> biomeSpecialEffects.grassColorOverride),
				BiomeSpecialEffects.GrassColorModifier.CODEC
					.optionalFieldOf("grass_color_modifier", BiomeSpecialEffects.GrassColorModifier.NONE)
					.forGetter(biomeSpecialEffects -> biomeSpecialEffects.grassColorModifier),
				AmbientParticleSettings.CODEC.optionalFieldOf("particle").forGetter(biomeSpecialEffects -> biomeSpecialEffects.ambientParticleSettings),
				SoundEvent.CODEC.optionalFieldOf("ambient_sound").forGetter(biomeSpecialEffects -> biomeSpecialEffects.ambientLoopSoundEvent),
				AmbientMoodSettings.CODEC.optionalFieldOf("mood_sound").forGetter(biomeSpecialEffects -> biomeSpecialEffects.ambientMoodSettings),
				AmbientAdditionsSettings.CODEC.optionalFieldOf("additions_sound").forGetter(biomeSpecialEffects -> biomeSpecialEffects.ambientAdditionsSettings),
				WeightedList.codec(Music.CODEC).optionalFieldOf("music").forGetter(biomeSpecialEffects -> biomeSpecialEffects.backgroundMusic),
				Codec.FLOAT.fieldOf("music_volume").orElse(1.0F).forGetter(biomeSpecialEffects -> biomeSpecialEffects.backgroundMusicVolume)
			)
			.apply(instance, BiomeSpecialEffects::new)
	);
	private final int fogColor;
	private final int waterColor;
	private final int waterFogColor;
	private final int skyColor;
	private final Optional<Integer> foliageColorOverride;
	private final Optional<Integer> dryFoliageColorOverride;
	private final Optional<Integer> grassColorOverride;
	private final BiomeSpecialEffects.GrassColorModifier grassColorModifier;
	private final Optional<AmbientParticleSettings> ambientParticleSettings;
	private final Optional<Holder<SoundEvent>> ambientLoopSoundEvent;
	private final Optional<AmbientMoodSettings> ambientMoodSettings;
	private final Optional<AmbientAdditionsSettings> ambientAdditionsSettings;
	private final Optional<WeightedList<Music>> backgroundMusic;
	private final float backgroundMusicVolume;

	BiomeSpecialEffects(
		int i,
		int j,
		int k,
		int l,
		Optional<Integer> optional,
		Optional<Integer> optional2,
		Optional<Integer> optional3,
		BiomeSpecialEffects.GrassColorModifier grassColorModifier,
		Optional<AmbientParticleSettings> optional4,
		Optional<Holder<SoundEvent>> optional5,
		Optional<AmbientMoodSettings> optional6,
		Optional<AmbientAdditionsSettings> optional7,
		Optional<WeightedList<Music>> optional8,
		float f
	) {
		this.fogColor = i;
		this.waterColor = j;
		this.waterFogColor = k;
		this.skyColor = l;
		this.foliageColorOverride = optional;
		this.dryFoliageColorOverride = optional2;
		this.grassColorOverride = optional3;
		this.grassColorModifier = grassColorModifier;
		this.ambientParticleSettings = optional4;
		this.ambientLoopSoundEvent = optional5;
		this.ambientMoodSettings = optional6;
		this.ambientAdditionsSettings = optional7;
		this.backgroundMusic = optional8;
		this.backgroundMusicVolume = f;
	}

	public int getFogColor() {
		return this.fogColor;
	}

	public int getWaterColor() {
		return this.waterColor;
	}

	public int getWaterFogColor() {
		return this.waterFogColor;
	}

	public int getSkyColor() {
		return this.skyColor;
	}

	public Optional<Integer> getFoliageColorOverride() {
		return this.foliageColorOverride;
	}

	public Optional<Integer> getDryFoliageColorOverride() {
		return this.dryFoliageColorOverride;
	}

	public Optional<Integer> getGrassColorOverride() {
		return this.grassColorOverride;
	}

	public BiomeSpecialEffects.GrassColorModifier getGrassColorModifier() {
		return this.grassColorModifier;
	}

	public Optional<AmbientParticleSettings> getAmbientParticleSettings() {
		return this.ambientParticleSettings;
	}

	public Optional<Holder<SoundEvent>> getAmbientLoopSoundEvent() {
		return this.ambientLoopSoundEvent;
	}

	public Optional<AmbientMoodSettings> getAmbientMoodSettings() {
		return this.ambientMoodSettings;
	}

	public Optional<AmbientAdditionsSettings> getAmbientAdditionsSettings() {
		return this.ambientAdditionsSettings;
	}

	public Optional<WeightedList<Music>> getBackgroundMusic() {
		return this.backgroundMusic;
	}

	public float getBackgroundMusicVolume() {
		return this.backgroundMusicVolume;
	}

	public static class Builder {
		private OptionalInt fogColor = OptionalInt.empty();
		private OptionalInt waterColor = OptionalInt.empty();
		private OptionalInt waterFogColor = OptionalInt.empty();
		private OptionalInt skyColor = OptionalInt.empty();
		private Optional<Integer> foliageColorOverride = Optional.empty();
		private Optional<Integer> dryFoliageColorOverride = Optional.empty();
		private Optional<Integer> grassColorOverride = Optional.empty();
		private BiomeSpecialEffects.GrassColorModifier grassColorModifier = BiomeSpecialEffects.GrassColorModifier.NONE;
		private Optional<AmbientParticleSettings> ambientParticle = Optional.empty();
		private Optional<Holder<SoundEvent>> ambientLoopSoundEvent = Optional.empty();
		private Optional<AmbientMoodSettings> ambientMoodSettings = Optional.empty();
		private Optional<AmbientAdditionsSettings> ambientAdditionsSettings = Optional.empty();
		private Optional<WeightedList<Music>> backgroundMusic = Optional.empty();
		private float backgroundMusicVolume = 1.0F;

		public BiomeSpecialEffects.Builder fogColor(int i) {
			this.fogColor = OptionalInt.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder waterColor(int i) {
			this.waterColor = OptionalInt.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder waterFogColor(int i) {
			this.waterFogColor = OptionalInt.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder skyColor(int i) {
			this.skyColor = OptionalInt.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder foliageColorOverride(int i) {
			this.foliageColorOverride = Optional.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder dryFoliageColorOverride(int i) {
			this.dryFoliageColorOverride = Optional.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder grassColorOverride(int i) {
			this.grassColorOverride = Optional.of(i);
			return this;
		}

		public BiomeSpecialEffects.Builder grassColorModifier(BiomeSpecialEffects.GrassColorModifier grassColorModifier) {
			this.grassColorModifier = grassColorModifier;
			return this;
		}

		public BiomeSpecialEffects.Builder ambientParticle(AmbientParticleSettings ambientParticleSettings) {
			this.ambientParticle = Optional.of(ambientParticleSettings);
			return this;
		}

		public BiomeSpecialEffects.Builder ambientLoopSound(Holder<SoundEvent> holder) {
			this.ambientLoopSoundEvent = Optional.of(holder);
			return this;
		}

		public BiomeSpecialEffects.Builder ambientMoodSound(AmbientMoodSettings ambientMoodSettings) {
			this.ambientMoodSettings = Optional.of(ambientMoodSettings);
			return this;
		}

		public BiomeSpecialEffects.Builder ambientAdditionsSound(AmbientAdditionsSettings ambientAdditionsSettings) {
			this.ambientAdditionsSettings = Optional.of(ambientAdditionsSettings);
			return this;
		}

		public BiomeSpecialEffects.Builder backgroundMusic(@Nullable Music music) {
			if (music == null) {
				this.backgroundMusic = Optional.empty();
				return this;
			} else {
				this.backgroundMusic = Optional.of(WeightedList.of(music));
				return this;
			}
		}

		public BiomeSpecialEffects.Builder silenceAllBackgroundMusic() {
			return this.backgroundMusic(WeightedList.of()).backgroundMusicVolume(0.0F);
		}

		public BiomeSpecialEffects.Builder backgroundMusic(WeightedList<Music> weightedList) {
			this.backgroundMusic = Optional.of(weightedList);
			return this;
		}

		public BiomeSpecialEffects.Builder backgroundMusicVolume(float f) {
			this.backgroundMusicVolume = f;
			return this;
		}

		public BiomeSpecialEffects build() {
			return new BiomeSpecialEffects(
				this.fogColor.orElseThrow(() -> new IllegalStateException("Missing 'fog' color.")),
				this.waterColor.orElseThrow(() -> new IllegalStateException("Missing 'water' color.")),
				this.waterFogColor.orElseThrow(() -> new IllegalStateException("Missing 'water fog' color.")),
				this.skyColor.orElseThrow(() -> new IllegalStateException("Missing 'sky' color.")),
				this.foliageColorOverride,
				this.dryFoliageColorOverride,
				this.grassColorOverride,
				this.grassColorModifier,
				this.ambientParticle,
				this.ambientLoopSoundEvent,
				this.ambientMoodSettings,
				this.ambientAdditionsSettings,
				this.backgroundMusic,
				this.backgroundMusicVolume
			);
		}
	}

	public static enum GrassColorModifier implements StringRepresentable {
		NONE("none") {
			@Override
			public int modifyColor(double d, double e, int i) {
				return i;
			}
		},
		DARK_FOREST("dark_forest") {
			@Override
			public int modifyColor(double d, double e, int i) {
				return (i & 16711422) + 2634762 >> 1;
			}
		},
		SWAMP("swamp") {
			@Override
			public int modifyColor(double d, double e, int i) {
				double f = Biome.BIOME_INFO_NOISE.getValue(d * 0.0225, e * 0.0225, false);
				return f < -0.1 ? 5011004 : 6975545;
			}
		};

		private final String name;
		public static final Codec<BiomeSpecialEffects.GrassColorModifier> CODEC = StringRepresentable.fromEnum(BiomeSpecialEffects.GrassColorModifier::values);

		public abstract int modifyColor(double d, double e, int i);

		GrassColorModifier(final String string2) {
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
}
