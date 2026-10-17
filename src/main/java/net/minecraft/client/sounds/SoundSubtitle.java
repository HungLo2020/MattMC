package net.minecraft.client.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/**
 * One subtitle to draw this frame: its translation key (or the event path
 * with `DEBUG_SUBTITLES`), the played-at position closest to the listener,
 * and how long ago that play was.
 */
@Environment(EnvType.CLIENT)
public record SoundSubtitle(String text, double x, double y, double z, long ageMillis) {
}
