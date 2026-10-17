package net.minecraft.client.sounds;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the Rust ambient handlers against the vanilla Java handler bodies
 * (underwater, bubble column, biome mood and additions, Iris's constant
 * mood) on the same level random, with the engine closed so no sound plays.
 * Every tick must leave the random, the sample position and both mood values
 * identical.
 */
class NativeAmbienceParityTest {
	private record Mood(int tickDelay, int extent, double offset) {
	}

	/** The vanilla handlers' random-consuming and mood logic, as in 1.21.10. */
	private static final class VanillaHandlers {
		int underwaterTickDelay;
		boolean wasInBubbleColumn;
		boolean firstTick = true;
		Optional<Mood> mood = Optional.empty();
		Optional<Double> additionsChance = Optional.empty();
		float moodiness;
		float constantMoodiness;
		int[] sample;

		void tick(RandomSource random, boolean underwater, boolean spectator, int bubble, boolean biomeChanged, Optional<Mood> mood,
			Optional<Double> additions, double x, double eyeY, double z, int skyAtPlayer, int blockAtPlayer, int[][] lights) {
			this.underwaterTickDelay--;
			if (this.underwaterTickDelay <= 0 && underwater) {
				float f = random.nextFloat();
				if (f < 1.0E-4F || f < 0.001F || f < 0.01F) {
					this.underwaterTickDelay = 0;
				}
			}

			if (bubble != 0) {
				if (!this.wasInBubbleColumn && !this.firstTick && !spectator) {
					random.nextLong();
				}

				this.wasInBubbleColumn = true;
			} else {
				this.wasInBubbleColumn = false;
			}

			this.firstTick = false;
			if (biomeChanged) {
				this.mood = mood;
				this.additionsChance = additions;
			}

			this.additionsChance.ifPresent(chance -> random.nextDouble());
			if (skyAtPlayer > 0) {
				this.constantMoodiness -= (float)skyAtPlayer / (float)15 * 0.001F;
			} else {
				this.constantMoodiness -= (float)(blockAtPlayer - 1) / 6000.0F;
			}

			this.constantMoodiness = Mth.clamp(this.constantMoodiness, 0.0F, 1.0F);
			this.sample = null;
			this.mood.ifPresent(settings -> {
				int i = settings.extent() * 2 + 1;
				int bx = Mth.floor(x + random.nextInt(i) - settings.extent());
				int by = Mth.floor(eyeY + random.nextInt(i) - settings.extent());
				int bz = Mth.floor(z + random.nextInt(i) - settings.extent());
				this.sample = new int[]{bx, by, bz};
				int j = light(lights, 0, this.sample);
				if (j > 0) {
					this.moodiness -= j / 15.0F * 0.001F;
				} else {
					this.moodiness = this.moodiness - (float)(light(lights, 1, this.sample) - 1) / settings.tickDelay();
				}

				if (this.moodiness >= 1.0F) {
					this.moodiness = 0.0F;
				} else {
					this.moodiness = Math.max(this.moodiness, 0.0F);
				}
			});
		}
	}

	/** A deterministic sky (layer 0) or block (layer 1) light at a position. */
	private static int light(int[][] lights, int layer, int[] position) {
		int hash = position[0] * 73856093 ^ position[1] * 19349663 ^ position[2] * 83492791;
		return lights[layer][Math.floorMod(hash, lights[layer].length)];
	}

	@Test
	void ambientHandlersMatchVanillaOverRandomTicks() {
		long owner = System.nanoTime();
		LegacyRandomSource level = new LegacyRandomSource(1234L);
		LegacyRandomSource reference = new LegacyRandomSource(1234L);
		RandomSource scenario = RandomSource.create(99L);
		VanillaHandlers vanilla = new VanillaHandlers();
		// Mostly dark samples so the mood rises and resets.
		int[][] lights = {{0, 0, 0, 0, 0, 0, 3, 15}, {0, 0, 1, 4, 7, 15}};
		int biome = 0;
		for (int tick = 0; tick < 4000; tick++) {
			boolean underwater = scenario.nextInt(4) == 0;
			boolean spectator = scenario.nextInt(20) == 0;
			int bubble = scenario.nextInt(6) == 0 ? 1 + scenario.nextInt(2) : 0;
			boolean biomeChanged = tick == 0 || scenario.nextInt(300) == 0;
			Optional<Mood> mood = Optional.empty();
			Optional<Double> additions = Optional.empty();
			if (biomeChanged) {
				biome++;
				if (scenario.nextInt(5) != 0) {
					mood = Optional.of(new Mood(1 + scenario.nextInt(6000), scenario.nextInt(9), scenario.nextDouble() * 4.0));
				}
				if (scenario.nextInt(2) == 0) {
					additions = Optional.of(scenario.nextDouble() * 0.02);
				}
			}

			double x = scenario.nextDouble() * 2000.0 - 1000.0;
			double eyeY = scenario.nextDouble() * 300.0 - 60.0;
			double z = scenario.nextDouble() * 2000.0 - 1000.0;
			int sky = scenario.nextInt(16);
			int block = scenario.nextInt(16);
			vanilla.tick(reference, underwater, spectator, bubble, biomeChanged, mood, additions, x, eyeY, z, sky, block, lights);

			int flags = (underwater ? NativeSoundClient.AMBIENT_UNDERWATER : 0)
				| (spectator ? NativeSoundClient.AMBIENT_SPECTATOR : 0)
				| (bubble == 1 ? NativeSoundClient.AMBIENT_BUBBLE_UP : bubble == 2 ? NativeSoundClient.AMBIENT_BUBBLE_DOWN : 0)
				| (biomeChanged ? NativeSoundClient.AMBIENT_BIOME_CHANGED : 0)
				| (mood.isPresent() ? NativeSoundClient.AMBIENT_HAS_MOOD : 0)
				| (additions.isPresent() ? NativeSoundClient.AMBIENT_HAS_ADDITIONS : 0);
			NativeSoundClient.AmbientOutput output = NativeSoundClient.ambientTick(
				owner, x, eyeY - 1.62, eyeY, z, flags, biome, sky, block,
				null, mood.isPresent() ? "minecraft:ambient.cave" : null,
				mood.map(Mood::tickDelay).orElse(0), mood.map(Mood::extent).orElse(0), mood.map(Mood::offset).orElse(0.0),
				additions.isPresent() ? "minecraft:ambient.basalt_deltas.additions" : null, additions.orElse(0.0),
				level.rawSeed()
			);
			if (output.hasSample()) {
				int[] sample = {output.sampleX(), output.sampleY(), output.sampleZ()};
				assertEquals(java.util.Arrays.toString(vanilla.sample), java.util.Arrays.toString(sample), "sample at tick " + tick);
				output = NativeSoundClient.ambientFinish(light(lights, 0, sample), light(lights, 1, sample));
			} else {
				assertEquals(null, vanilla.sample, "no sample at tick " + tick);
			}

			level.setRawSeed(output.random());
			assertEquals(reference.rawSeed(), level.rawSeed(), "level random at tick " + tick);
			assertEquals(vanilla.moodiness, output.moodiness(), "moodiness at tick " + tick);
			assertEquals(vanilla.constantMoodiness, output.constantMoodiness(), "constant moodiness at tick " + tick);
		}
	}
}
