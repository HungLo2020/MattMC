package mattmc.client.sounds;

import org.joml.Vector3f;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 3D audio listener management, mirroring Minecraft's Listener.
 * <p>
 * The listener represents the "ears" of the player in 3D space.
 * All positional sounds are attenuated based on their distance from
 * and direction to the listener.
 */
public final class Listener {

    private static final Logger logger = LoggerFactory.getLogger(Listener.class);

    private float gain = 1.0f;
    private double x, y, z;

    /**
     * Set the listener position in world coordinates.
     */
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        AL10.alListener3f(AL10.AL_POSITION, (float) x, (float) y, (float) z);
        OpenALUtils.checkALError("Set listener position");
    }

    /**
     * Get the listener's current X position.
     */
    public double getX() {
        return x;
    }

    /**
     * Get the listener's current Y position.
     */
    public double getY() {
        return y;
    }

    /**
     * Get the listener's current Z position.
     */
    public double getZ() {
        return z;
    }

    /**
     * Calculate squared distance from listener to a point.
     */
    public double distanceSquaredTo(double px, double py, double pz) {
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Set the listener orientation.
     * @param lookX Look direction X component
     * @param lookY Look direction Y component
     * @param lookZ Look direction Z component
     * @param upX Up vector X component
     * @param upY Up vector Y component
     * @param upZ Up vector Z component
     */
    public void setOrientation(float lookX, float lookY, float lookZ,
                                float upX, float upY, float upZ) {
        float[] orientation = new float[] {
            lookX, lookY, lookZ,  // Look direction ("at" vector)
            upX, upY, upZ         // Up vector
        };
        AL10.alListenerfv(AL10.AL_ORIENTATION, orientation);
        OpenALUtils.checkALError("Set listener orientation");
    }

    /**
     * Set the listener orientation using JOML vectors.
     * @param look The look/forward direction vector
     * @param up The up vector
     */
    public void setOrientation(Vector3f look, Vector3f up) {
        setOrientation(look.x, look.y, look.z, up.x, up.y, up.z);
    }

    /**
     * Set the master gain (volume) for the listener.
     * This affects all sounds heard by the listener.
     * @param gain Gain value (0.0 = silent, 1.0 = normal)
     */
    public void setGain(float gain) {
        this.gain = gain;
        AL10.alListenerf(AL10.AL_GAIN, gain);
        OpenALUtils.checkALError("Set listener gain");
    }

    /**
     * Get the current listener gain.
     */
    public float getGain() {
        return gain;
    }

    /**
     * Reset the listener to default state (origin, looking at -Z, gain 1.0).
     */
    public void reset() {
        setPosition(0, 0, 0);
        setOrientation(0, 0, -1, 0, 1, 0);
        setGain(1.0f);
    }
}
