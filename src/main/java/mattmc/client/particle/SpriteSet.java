package mattmc.client.particle;

import java.util.List;
import java.util.Random;

/**
 * A set of sprites for particle animation or random selection.
 * 
 * <p>Sprite sets can be used to:
 * <ul>
 *   <li>Select a random sprite when the particle is created</li>
 *   <li>Animate through sprites based on particle age</li>
 * </ul>
 * 
 * <p>Mirrors Minecraft's SpriteSet interface.
 */
public interface SpriteSet {
    
    /**
     * Get a sprite based on particle age and lifetime.
     * Used for animated particles that change appearance over their lifetime.
     * 
     * @param age current particle age in ticks
     * @param lifetime total particle lifetime in ticks
     * @return the appropriate sprite for this age
     */
    ParticleSprite get(int age, int lifetime);
    
    /**
     * Get a random sprite from the set.
     * Used for particles that have a random appearance but don't animate.
     * 
     * @param random the random source
     * @return a randomly selected sprite
     */
    ParticleSprite get(Random random);
    
    /**
     * Simple implementation backed by a list of sprites.
     */
    class Simple implements SpriteSet {
        private final List<ParticleSprite> sprites;
        
        public Simple(List<ParticleSprite> sprites) {
            this.sprites = sprites;
        }
        
        @Override
        public ParticleSprite get(int age, int lifetime) {
            if (sprites.isEmpty()) return null;
            // Map age to sprite index
            int index = age * (sprites.size() - 1) / Math.max(1, lifetime);
            return sprites.get(Math.min(index, sprites.size() - 1));
        }
        
        @Override
        public ParticleSprite get(Random random) {
            if (sprites.isEmpty()) return null;
            return sprites.get(random.nextInt(sprites.size()));
        }
        
        /**
         * Get the number of sprites in this set.
         */
        public int size() {
            return sprites.size();
        }
        
        /**
         * Rebind this sprite set with new sprites.
         * Used during resource reloading.
         */
        public void rebind(List<ParticleSprite> newSprites) {
            this.sprites.clear();
            this.sprites.addAll(newSprites);
        }
    }
}
