package mattmc.client.sounds;

/**
 * Abstract base implementation of SoundInstance.
 * Mirroring Minecraft's AbstractSoundInstance.
 * <p>
 * Provides common fields and implementations that concrete sound instance
 * classes can build upon.
 */
public abstract class AbstractSoundInstance implements SoundInstance {

    protected final String location;
    protected final SoundSource source;
    protected float volume = 1.0f;
    protected float pitch = 1.0f;
    protected double x;
    protected double y;
    protected double z;
    protected boolean looping;
    protected int delay;
    protected Attenuation attenuation = Attenuation.NONE;
    protected boolean relative;

    protected AbstractSoundInstance(String location, SoundSource source) {
        this.location = location;
        this.source = source;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public SoundSource getSource() {
        return source;
    }

    @Override
    public boolean isLooping() {
        return looping;
    }

    @Override
    public boolean isRelative() {
        return relative;
    }

    @Override
    public int getDelay() {
        return delay;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getZ() {
        return z;
    }

    @Override
    public Attenuation getAttenuation() {
        return attenuation;
    }
}
