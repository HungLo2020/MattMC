package mattmc.client.particle;

import mattmc.world.level.Level;

/**
 * A particle that renders using a sprite from a texture atlas.
 * 
 * <p>This class stores a reference to a texture atlas sprite and provides
 * UV coordinates for rendering. The sprite can be set directly or picked
 * randomly from a SpriteSet.
 * 
 * <p>Mirrors Minecraft's TextureSheetParticle.
 */
public abstract class TextureSheetParticle extends SingleQuadParticle {
    
    /** The sprite used for rendering. */
    protected ParticleSprite sprite;
    
    protected TextureSheetParticle(Level level, double x, double y, double z) {
        super(level, x, y, z);
    }
    
    protected TextureSheetParticle(Level level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
    }
    
    /**
     * Set the sprite for this particle.
     */
    protected void setSprite(ParticleSprite sprite) {
        this.sprite = sprite;
    }
    
    /**
     * Pick a random sprite from a sprite set.
     */
    public void pickSprite(SpriteSet spriteSet) {
        this.setSprite(spriteSet.get(this.random));
    }
    
    /**
     * Set sprite based on particle age (for animated sprites).
     */
    public void setSpriteFromAge(SpriteSet spriteSet) {
        if (!this.removed) {
            this.setSprite(spriteSet.get(this.age, this.lifetime));
        }
    }
    
    @Override
    protected float getU0() {
        return sprite != null ? sprite.getU0() : 0.0f;
    }
    
    @Override
    protected float getU1() {
        return sprite != null ? sprite.getU1() : 1.0f;
    }
    
    @Override
    protected float getV0() {
        return sprite != null ? sprite.getV0() : 0.0f;
    }
    
    @Override
    protected float getV1() {
        return sprite != null ? sprite.getV1() : 1.0f;
    }
}
