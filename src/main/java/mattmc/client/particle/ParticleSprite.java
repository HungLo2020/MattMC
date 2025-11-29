package mattmc.client.particle;

/**
 * Represents a sprite in the particle texture atlas.
 * 
 * <p>This class stores the UV coordinates for a single sprite within the
 * particle atlas, allowing particles to render specific textures.
 * 
 * <p>Mirrors Minecraft's TextureAtlasSprite for particle use.
 */
public class ParticleSprite {
    private final float u0;
    private final float v0;
    private final float u1;
    private final float v1;
    private final String name;
    
    /**
     * Create a sprite with the given UV coordinates.
     * 
     * @param name the sprite name/identifier
     * @param u0 left U coordinate (0-1)
     * @param v0 top V coordinate (0-1)
     * @param u1 right U coordinate (0-1)
     * @param v1 bottom V coordinate (0-1)
     */
    public ParticleSprite(String name, float u0, float v0, float u1, float v1) {
        this.name = name;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
    }
    
    /**
     * Get the left U coordinate.
     */
    public float getU0() {
        return u0;
    }
    
    /**
     * Get the right U coordinate.
     */
    public float getU1() {
        return u1;
    }
    
    /**
     * Get the top V coordinate.
     */
    public float getV0() {
        return v0;
    }
    
    /**
     * Get the bottom V coordinate.
     */
    public float getV1() {
        return v1;
    }
    
    /**
     * Get the sprite name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the U coordinate at a relative position (0-16 for MC-style texture coordinates).
     * 
     * @param offset the offset (0-16)
     * @return the interpolated U coordinate
     */
    public float getU(double offset) {
        float width = u1 - u0;
        return u0 + width * (float) (offset / 16.0);
    }
    
    /**
     * Get the V coordinate at a relative position (0-16 for MC-style texture coordinates).
     * 
     * @param offset the offset (0-16)
     * @return the interpolated V coordinate
     */
    public float getV(double offset) {
        float height = v1 - v0;
        return v0 + height * (float) (offset / 16.0);
    }
    
    @Override
    public String toString() {
        return "ParticleSprite{name='" + name + "', uv=[" + u0 + "," + v0 + "," + u1 + "," + v1 + "]}";
    }
}
