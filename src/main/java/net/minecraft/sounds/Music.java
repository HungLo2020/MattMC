package net.minecraft.sounds;

import net.minecraft.core.Holder;

public record Music(Holder<SoundEvent> event, int minDelay, int maxDelay, boolean replaceCurrentMusic) {
}
