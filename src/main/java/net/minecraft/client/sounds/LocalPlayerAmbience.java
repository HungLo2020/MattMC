package net.minecraft.client.sounds;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.AmbientAdditionsSettings;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;

/**
 * The local player's ambient sounds: vanilla's underwater, bubble column and
 * biome ambience handlers (with Iris's constant moodiness). Rust
 * (`audio/policy/ambient.rs`) runs their timing, selection, loops and mood;
 * this samples the player, its biome and the light levels each tick and
 * lends Rust the level random so the shared sequence is unchanged.
 */
@Environment(EnvType.CLIENT)
public final class LocalPlayerAmbience {
	private static final long STATE_MASK = (1L << 48) - 1L;
	private static final AtomicLong OWNERS = new AtomicLong();
	/** Identifies this player to Rust; the first tick or play takes over. */
	private final long owner = OWNERS.incrementAndGet();
	private final LocalPlayer player;
	private final BiomeManager biomeManager;
	private final Map<Biome, Integer> biomeKeys = new IdentityHashMap<>();
	private Biome previousBiome;
	private float moodiness;
	private float constantMoodiness;

	/**
	 * A player's handlers start fresh, as vanilla builds new ones, once it
	 * first ticks or plays; a player that never ticks takes nothing over.
	 */
	public LocalPlayerAmbience(LocalPlayer player, BiomeManager biomeManager) {
		this.player = player;
		this.biomeManager = biomeManager;
	}

	/** `LocalPlayer.updateIsUnderwater` entering water: the underwater loop. */
	public void playUnderwaterLoop() {
		NativeSoundClient.playUnderwaterLoop(this.owner);
	}

	/** `BiomeAmbientSoundsHandler.getMoodiness`. */
	public float getMoodiness() {
		return this.moodiness;
	}

	/** Iris's `BiomeAmbienceInterface.getConstantMood`. */
	public float getConstantMood() {
		return this.constantMoodiness;
	}

	public void tick() {
		LocalPlayer player = this.player;
		Level level = player.level();
		int flags = 0;
		if (player.isUnderWater()) {
			flags |= NativeSoundClient.AMBIENT_UNDERWATER;
		}

		if (player.isSpectator()) {
			flags |= NativeSoundClient.AMBIENT_SPECTATOR;
		}

		BlockState column = level.getBlockStatesIfLoaded(player.getBoundingBox().inflate(0.0, -0.4F, 0.0).deflate(1.0E-6))
			.filter(blockState -> blockState.is(Blocks.BUBBLE_COLUMN))
			.findFirst()
			.orElse(null);
		if (column != null) {
			flags |= column.getValue(BubbleColumnBlock.DRAG_DOWN) ? NativeSoundClient.AMBIENT_BUBBLE_DOWN : NativeSoundClient.AMBIENT_BUBBLE_UP;
		}

		Biome biome = this.biomeManager.getNoiseBiomeAtPosition(player.getX(), player.getY(), player.getZ()).value();
		String ambientLoop = null;
		String mood = null;
		String additions = null;
		int moodTickDelay = 0;
		int moodExtent = 0;
		double moodOffset = 0.0;
		double additionsChance = 0.0;
		int biomeKey = this.biomeKeys.computeIfAbsent(biome, key -> this.biomeKeys.size());
		if (biome != this.previousBiome) {
			this.previousBiome = biome;
			flags |= NativeSoundClient.AMBIENT_BIOME_CHANGED;
			Optional<Holder<SoundEvent>> loop = biome.getAmbientLoop();
			if (loop.isPresent()) {
				flags |= NativeSoundClient.AMBIENT_HAS_LOOP;
				ambientLoop = loop.get().value().location().toString();
			}

			Optional<AmbientMoodSettings> moodSettings = biome.getAmbientMood();
			if (moodSettings.isPresent()) {
				AmbientMoodSettings settings = moodSettings.get();
				flags |= NativeSoundClient.AMBIENT_HAS_MOOD;
				mood = settings.getSoundEvent().value().location().toString();
				moodTickDelay = settings.getTickDelay();
				moodExtent = settings.getBlockSearchExtent();
				moodOffset = settings.getSoundPositionOffset();
			}

			Optional<AmbientAdditionsSettings> additionsSettings = biome.getAmbientAdditions();
			if (additionsSettings.isPresent()) {
				flags |= NativeSoundClient.AMBIENT_HAS_ADDITIONS;
				additions = additionsSettings.get().getSoundEvent().value().location().toString();
				additionsChance = additionsSettings.get().getTickChance();
			}
		}

		BlockPos playerPos = player.blockPosition();
		RandomSource random = level.getRandom();
		NativeSoundClient.AmbientOutput output = NativeSoundClient.ambientTick(
			this.owner, player.getX(), player.getY(), player.getEyeY(), player.getZ(), flags, biomeKey,
			level.getBrightness(LightLayer.SKY, playerPos), level.getBrightness(LightLayer.BLOCK, playerPos),
			ambientLoop, mood, moodTickDelay, moodExtent, moodOffset, additions, additionsChance, exportRandom(random)
		);
		if (output.hasSample()) {
			BlockPos sample = new BlockPos(output.sampleX(), output.sampleY(), output.sampleZ());
			output = NativeSoundClient.ambientFinish(level.getBrightness(LightLayer.SKY, sample), level.getBrightness(LightLayer.BLOCK, sample));
		}

		importRandom(random, output.random());
		this.moodiness = output.moodiness();
		this.constantMoodiness = output.constantMoodiness();
	}

	/**
	 * The level random's scrambled state. Another source (not vanilla) seeds
	 * a fresh sequence from one draw instead.
	 */
	private static long exportRandom(RandomSource random) {
		if (random instanceof ThreadSafeLegacyRandomSource threadSafe) {
			return threadSafe.rawSeed();
		} else if (random instanceof LegacyRandomSource legacy) {
			return legacy.rawSeed();
		} else {
			return (random.nextLong() ^ 25214903917L) & STATE_MASK;
		}
	}

	private static void importRandom(RandomSource random, long state) {
		if (random instanceof ThreadSafeLegacyRandomSource threadSafe) {
			threadSafe.setRawSeed(state);
		} else if (random instanceof LegacyRandomSource legacy) {
			legacy.setRawSeed(state);
		}
	}
}
