package mattmc.client.sounds;

/**
 * Simple implementation of SoundInstance for common use cases.
 * Mirroring Minecraft's SimpleSoundInstance.
 * <p>
 * Provides factory methods for common sound types:
 * <ul>
 *   <li>UI sounds (click, button press)</li>
 *   <li>Music playback</li>
 *   <li>Record/jukebox sounds</li>
 *   <li>Ambient sounds</li>
 *   <li>Positional world sounds</li>
 * </ul>
 */
public final class SimpleSoundInstance extends AbstractSoundInstance {

    // === Factory Methods ===

    /**
     * Create a UI sound (non-positional, no attenuation).
     * @param event Sound event to play
     * @param pitch Pitch multiplier (typically 1.0)
     */
    public static SimpleSoundInstance forUI(SoundEvent event, float pitch) {
        return forUI(event, pitch, 0.25f);
    }

    /**
     * Create a UI sound with specified volume.
     * @param event Sound event to play
     * @param pitch Pitch multiplier
     * @param volume Volume (0.0 - 1.0)
     */
    public static SimpleSoundInstance forUI(SoundEvent event, float pitch, float volume) {
        return new SimpleSoundInstance(
            event.getLocation(), SoundSource.MASTER, volume, pitch,
            false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true
        );
    }

    /**
     * Create a music sound (non-positional, no attenuation, full volume).
     * @param event Sound event for the music track
     */
    public static SimpleSoundInstance forMusic(SoundEvent event) {
        return new SimpleSoundInstance(
            event.getLocation(), SoundSource.MUSIC, 1.0f, 1.0f,
            false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true
        );
    }

    /**
     * Create a record/jukebox sound (positional, with attenuation).
     * @param event Sound event for the record
     * @param x World X position
     * @param y World Y position
     * @param z World Z position
     */
    public static SimpleSoundInstance forRecord(SoundEvent event, double x, double y, double z) {
        return new SimpleSoundInstance(
            event.getLocation(), SoundSource.RECORDS, 4.0f, 1.0f,
            false, 0, Attenuation.LINEAR, x, y, z, false
        );
    }

    /**
     * Create a local ambient sound (non-positional, relative to listener).
     * @param event Sound event
     * @param volume Volume multiplier
     * @param pitch Pitch multiplier
     */
    public static SimpleSoundInstance forLocalAmbience(SoundEvent event, float volume, float pitch) {
        return new SimpleSoundInstance(
            event.getLocation(), SoundSource.AMBIENT, volume, pitch,
            false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true
        );
    }

    /**
     * Create a standard ambient addition sound.
     * @param event Sound event
     */
    public static SimpleSoundInstance forAmbientAddition(SoundEvent event) {
        return forLocalAmbience(event, 1.0f, 1.0f);
    }

    /**
     * Create a positional world sound (with linear attenuation).
     * @param event Sound event
     * @param source Sound category
     * @param volume Volume multiplier
     * @param pitch Pitch multiplier
     * @param x World X position
     * @param y World Y position
     * @param z World Z position
     */
    public static SimpleSoundInstance forWorld(SoundEvent event, SoundSource source,
                                                float volume, float pitch,
                                                double x, double y, double z) {
        return new SimpleSoundInstance(
            event.getLocation(), source, volume, pitch,
            false, 0, Attenuation.LINEAR, x, y, z, false
        );
    }

    /**
     * Create a positional block sound.
     * @param event Sound event
     * @param volume Volume multiplier
     * @param pitch Pitch multiplier
     * @param x Block X position
     * @param y Block Y position
     * @param z Block Z position
     */
    public static SimpleSoundInstance forBlock(SoundEvent event, float volume, float pitch,
                                                int x, int y, int z) {
        // Center on block
        return forWorld(event, SoundSource.BLOCKS, volume, pitch,
            x + 0.5, y + 0.5, z + 0.5);
    }

    // === Constructor ===

    /**
     * Full constructor with all parameters.
     */
    public SimpleSoundInstance(String location, SoundSource source,
                                float volume, float pitch,
                                boolean looping, int delay,
                                Attenuation attenuation,
                                double x, double y, double z,
                                boolean relative) {
        super(location, source);
        this.volume = volume;
        this.pitch = pitch;
        this.looping = looping;
        this.delay = delay;
        this.attenuation = attenuation;
        this.x = x;
        this.y = y;
        this.z = z;
        this.relative = relative;
    }
}
