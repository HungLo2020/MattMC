package mattmc.client.sounds;

/**
 * Interface for sound instances that can be played through the sound engine.
 * Mirroring Minecraft's SoundInstance interface.
 * <p>
 * A SoundInstance contains all the information needed to play a sound:
 * location, volume, pitch, position, attenuation, looping, etc.
 */
public interface SoundInstance {

    /**
     * Get the resource location of the sound to play.
     */
    String getLocation();

    /**
     * Get the sound category this instance belongs to.
     */
    SoundSource getSource();

    /**
     * Whether this sound should loop.
     */
    boolean isLooping();

    /**
     * Whether this sound's position is relative to the listener.
     * If true, position (0,0,0) means at the listener's position.
     * UI sounds and music are typically relative.
     */
    boolean isRelative();

    /**
     * Delay in ticks before this sound starts playing.
     */
    int getDelay();

    /**
     * Get the volume of this sound (0.0 - 1.0+).
     */
    float getVolume();

    /**
     * Get the pitch of this sound (0.5 - 2.0).
     */
    float getPitch();

    /**
     * Get the X position of this sound in the world.
     */
    double getX();

    /**
     * Get the Y position of this sound in the world.
     */
    double getY();

    /**
     * Get the Z position of this sound in the world.
     */
    double getZ();

    /**
     * Get the attenuation type for this sound.
     */
    Attenuation getAttenuation();

    /**
     * Whether this sound can start even if it would be silent (volume = 0).
     * Default is false.
     */
    default boolean canStartSilent() {
        return false;
    }

    /**
     * Whether this sound is allowed to play.
     * Default is true.
     */
    default boolean canPlaySound() {
        return true;
    }

    /**
     * Sound attenuation modes.
     */
    enum Attenuation {
        /**
         * No distance attenuation - sound is same volume everywhere.
         * Used for UI sounds and music.
         */
        NONE,

        /**
         * Linear distance attenuation - sound fades with distance.
         * Used for positional world sounds.
         */
        LINEAR
    }
}
