package net.minecraft.client.resources.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * A tickable whose `tick()` body runs in Rust (`audio/policy/rules.rs`).
 * Each tick the engine asks it for the entity facts that body reads instead
 * of calling {@link #tick()}; Rust keeps the volume, pitch, position and
 * counters.
 */
@Environment(EnvType.CLIENT)
public interface RuledSoundInstance extends TickableSoundInstance {
	int RULE_ENTITY_BOUND = 1;
	int RULE_MINECART = 2;
	int RULE_RIDING_MINECART = 3;
	int RULE_ELYTRA = 4;
	int RULE_BEE = 5;
	int RULE_GUARDIAN_ATTACK = 6;
	int RULE_RIDING_HAPPY_GHAST = 7;
	int RULE_SNIFFER = 8;

	int SAMPLE_REMOVED = 1;
	int SAMPLE_RIDING = 1 << 1;
	int SAMPLE_UNDERWATER = 1 << 2;
	int SAMPLE_OFF_RAILS = 1 << 3;
	int SAMPLE_RUNS_NORMALLY = 1 << 4;
	int SAMPLE_FALL_FLYING = 1 << 5;
	int SAMPLE_SWITCH = 1 << 6;
	int SAMPLE_BABY = 1 << 7;
	int SAMPLE_HAS_TARGET = 1 << 8;
	int SAMPLE_CAN_DIG = 1 << 9;

	/** One of the `RULE_*` kinds. */
	int soundRule();

	/** The rule's constant parameter (the riding minecart's underwater variant). */
	default int soundRuleParameter() {
		return 0;
	}

	/** The instance's own volume and pitch before the sound's factors. */
	float ruleBaseVolume();

	float ruleBasePitch();

	/** Writes this tick's sampled facts. */
	void sampleSoundRule(Sample sample);

	/** Rust's rule stopped the instance. */
	void markSoundRuleStopped();

	/** A bee switched to its alternative sound this tick. */
	default void onSoundRuleSwitched() {
	}

	/** One tick's facts: `SAMPLE_*` flags, a position and one measured value. */
	@Environment(EnvType.CLIENT)
	final class Sample {
		public int flags;
		public double x;
		public double y;
		public double z;
		public float value;

		public void clear() {
			this.flags = 0;
			this.x = 0.0;
			this.y = 0.0;
			this.z = 0.0;
			this.value = 0.0F;
		}

		public void flag(int flag, boolean set) {
			if (set) {
				this.flags |= flag;
			}
		}

		public void position(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
}
