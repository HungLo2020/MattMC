package mattmc.world.level.block;

import mattmc.client.resources.ResourceManager;
import mattmc.world.level.Level;
import mattmc.world.phys.shapes.VoxelShape;
import mattmc.util.MathUtils;

import java.util.Map;
import java.util.Random;

/**
 * Represents a single block in the world.
 * Similar to MattMC's Block class.
 * 
 * Each block has properties like solidity and texture paths.
 * Blocks are registered in the Blocks class with unique identifiers.
 * Texture paths are loaded from blockstate and model JSON files.
 * If texture loading fails, a fallback magenta color (0xFF00FF) is used.
 */
public class Block {
    // Fallback color used when texture is missing or fails to load
    private static final int FALLBACK_COLOR = 0xFF00FF; // Magenta
    
    private final boolean solid;
    private final String identifier;
    private final int lightEmission; // Legacy light level emitted by this block (0-15)
    private final int lightEmissionR; // Red channel light emission (0-15)
    private final int lightEmissionG; // Green channel light emission (0-15)
    private final int lightEmissionB; // Blue channel light emission (0-15)
    private Map<String, String> texturePaths; // Lazily loaded from JSON (top, bottom, side, overlay, etc.)
    
    /**
     * Create a new block with the given properties.
     * Texture path will be loaded from blockstate/model JSON files.
     * 
     * @param solid Whether the block is solid (has collision)
     */
    public Block(boolean solid) {
        this(solid, 0, 0, 0, 0);
    }
    
    /**
     * Create a new block with white light emission (legacy).
     * Sets all RGB channels to the same value for white light.
     * 
     * @param solid Whether the block is solid (has collision)
     * @param lightEmission Light level emitted by this block (0-15)
     */
    public Block(boolean solid, int lightEmission) {
        this(solid, lightEmission, lightEmission, lightEmission, lightEmission);
    }
    
    /**
     * Create a new block with RGB light emission.
     * 
     * @param solid Whether the block is solid (has collision)
     * @param lightEmissionR Red channel light emission (0-15)
     * @param lightEmissionG Green channel light emission (0-15)
     * @param lightEmissionB Blue channel light emission (0-15)
     */
    public Block(boolean solid, int lightEmissionR, int lightEmissionG, int lightEmissionB) {
        this(solid, Math.max(lightEmissionR, Math.max(lightEmissionG, lightEmissionB)), 
             lightEmissionR, lightEmissionG, lightEmissionB);
    }
    
    /**
     * Create a new block with explicit emission level and RGB light values.
     * This is the most flexible constructor for colored lights.
     * 
     * @param solid Whether the block is solid (has collision)
     * @param lightEmission Overall light emission level (0-15), used for intensity
     * @param lightEmissionR Red channel light emission (0-15)
     * @param lightEmissionG Green channel light emission (0-15)
     * @param lightEmissionB Blue channel light emission (0-15)
     */
    public Block(boolean solid, int lightEmission, int lightEmissionR, int lightEmissionG, int lightEmissionB) {
        this.solid = solid;
        this.lightEmission = MathUtils.clamp(lightEmission, 0, 15);
        this.lightEmissionR = MathUtils.clamp(lightEmissionR, 0, 15);
        this.lightEmissionG = MathUtils.clamp(lightEmissionG, 0, 15);
        this.lightEmissionB = MathUtils.clamp(lightEmissionB, 0, 15);
        this.identifier = null; // Will be set during registration
    }
    
    /**
     * Internal constructor used during registration to set the identifier.
     * 
     * ISSUE-007 fix: Eagerly loads texture paths at registration time instead of lazily.
     * This eliminates HashMap lookups and String.equals() calls in the hot rendering path.
     */
    Block(boolean solid, int lightEmission, int lightEmissionR, int lightEmissionG, int lightEmissionB, String identifier) {
        this.solid = solid;
        this.lightEmission = MathUtils.clamp(lightEmission, 0, 15);
        this.lightEmissionR = MathUtils.clamp(lightEmissionR, 0, 15);
        this.lightEmissionG = MathUtils.clamp(lightEmissionG, 0, 15);
        this.lightEmissionB = MathUtils.clamp(lightEmissionB, 0, 15);
        this.identifier = identifier;
        
        // ISSUE-007 fix: Eagerly load texture paths at registration time
        if (identifier != null) {
            String blockName = identifier.contains(":") ? identifier.substring(identifier.indexOf(':') + 1) : identifier;
            this.texturePaths = ResourceManager.getBlockTexturePaths(blockName);
        }
    }
    
    /**
     * Get the fallback color used when texture is missing.
     * This is a magenta color (0xFF00FF) to make missing textures obvious.
     * 
     * @return The fallback color (RGB hex value)
     */
    public int getFallbackColor() {
        return FALLBACK_COLOR;
    }
    
    public boolean isSolid() {
        return solid;
    }
    
    /**
     * Get the texture paths for this block.
     * 
     * ISSUE-007 fix: Texture paths are now eagerly loaded at block registration time,
     * eliminating the lazy-load check from the hot rendering path. This method simply
     * returns the pre-cached paths.
     * 
     * @return A map of texture keys to paths (e.g., "top" -> "assets/textures/block/grass_block_top.png")
     */
    public Map<String, String> getTexturePaths() {
        // ISSUE-007: Texture paths are now eagerly loaded during registration.
        // The lazy loading is kept only as a fallback for blocks not created via registration.
        if (texturePaths == null && identifier != null) {
            // Fallback: lazy load if not already cached (shouldn't happen with proper registration)
            String blockName = identifier.contains(":") ? identifier.substring(identifier.indexOf(':') + 1) : identifier;
            texturePaths = ResourceManager.getBlockTexturePaths(blockName);
        }
        return texturePaths;
    }
    
    /**
     * Get the texture path for a specific face.
     * Falls back to "all" texture if face-specific texture is not available.
     * 
     * @param face The face name (e.g., "top", "bottom", "side", "north", etc.)
     * @return The texture path, or null if no texture is available
     */
    public String getTexturePath(String face) {
        Map<String, String> paths = getTexturePaths();
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        
        // Try to get face-specific texture
        String path = paths.get(face);
        if (path != null) {
            return path;
        }
        
        // Map directional faces (north/south/east/west) to "side" texture
        if ("north".equals(face) || "south".equals(face) || "west".equals(face) || "east".equals(face)) {
            path = paths.get("side");
            if (path != null) {
                return path;
            }
        }
        
        // Fall back to "all" texture (for simple cube_all models)
        return paths.get("all");
    }

    /**
     * Get the texture path for this block (backward compatibility).
     * Returns the "all" texture, or the first available texture.
     * 
     * @return The texture path, or null if no texture is available
     */
    public String getTexturePath() {
        Map<String, String> paths = getTexturePaths();
        if (paths == null || paths.isEmpty()) {
            return null;
        }
        
        // Try "all" first (for simple blocks)
        String path = paths.get("all");
        if (path != null) {
            return path;
        }
        
        // Return first available texture
        return paths.values().iterator().next();
    }
    
    public boolean hasTexture() {
        Map<String, String> paths = getTexturePaths();
        return paths != null && !paths.isEmpty();
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public boolean isAir() {
        return this == Blocks.AIR;
    }
    
    /**
     * Check if this block is opaque and stops skylight propagation.
     * Opaque blocks prevent skylight from passing through them.
     * 
     * @return true if the block is opaque (blocks skylight)
     */
    public boolean isOpaque() {
        // Air is not opaque; solid blocks are opaque
        return solid;
    }
    
    /**
     * Check if this block can occlude neighboring block faces for face culling.
     * This is used during rendering to determine if adjacent block faces should be hidden.
     * 
     * A block that can occlude will hide the faces of adjacent blocks that touch it.
     * Transparent blocks like leaves, glass, etc. should NOT occlude because you can
     * see through them - so the adjacent block's face should still be rendered.
     * 
     * This is separate from isSolid() which determines collision behavior.
     * For example, leaves are solid (have collision) but don't occlude (transparent).
     * 
     * Based on Minecraft's canOcclude() from BlockBehaviour.Properties.
     * 
     * @return true if this block occludes neighboring faces (default: same as solid)
     */
    public boolean canOcclude() {
        // By default, solid blocks occlude neighboring faces
        return solid;
    }
    
    /**
     * Get the RED channel light level emitted by this block.
     * 
     * @return Red light emission level (0-15)
     */
    public int getLightEmissionR() {
        return lightEmissionR;
    }
    
    /**
     * Get the GREEN channel light level emitted by this block.
     * 
     * @return Green light emission level (0-15)
     */
    public int getLightEmissionG() {
        return lightEmissionG;
    }
    
    /**
     * Get the BLUE channel light emission level emitted by this block.
     * 
     * @return Blue light emission level (0-15)
     */
    public int getLightEmissionB() {
        return lightEmissionB;
    }
    
    /**
     * Get the opacity of this block (how much it blocks light).
     * 
     * @return Opacity level (0-15), where 0 means fully transparent and 15 means fully opaque
     */
    public int getOpacity() {
        // By default, solid blocks are fully opaque (15), air and non-solid blocks are transparent (0)
        return solid ? 15 : 0;
    }
    
    /**
     * Get the collision shape for this block.
     * Override this method in subclasses to provide custom collision shapes.
     * 
     * @return The collision shape for this block
     */
    public VoxelShape getCollisionShape() {
        // Default: full block collision
        return isSolid() ? VoxelShape.block() : VoxelShape.empty();
    }
    
    /**
     * Check if this block uses custom rendering (not a simple cube).
     * Override this method in subclasses that need custom geometry.
     * 
     * @return true if this block uses custom rendering
     */
    public boolean hasCustomRendering() {
        return false;
    }
    
    /**
     * Get the blockstate for placement.
     * Override in subclasses that need placement logic (e.g., stairs).
     * 
     * @param playerX Player X position
     * @param playerY Player Y position
     * @param playerZ Player Z position
     * @param blockX Block X position being placed
     * @param blockY Block Y position being placed
     * @param blockZ Block Z position being placed
     * @param hitFace The face that was clicked (0=bottom, 1=top, 2=north, 3=south, 4=west, 5=east)
     * @param hitX The exact X position where the ray hit the block face
     * @param hitY The exact Y position where the ray hit the block face
     * @param hitZ The exact Z position where the ray hit the block face
     * @return BlockState for this placement, or null if no state needed
     */
    public mattmc.world.level.block.state.BlockState getPlacementState(
            float playerX, float playerY, float playerZ,
            int blockX, int blockY, int blockZ, int hitFace,
            float hitX, float hitY, float hitZ) {
        return null;  // Most blocks don't need placement state
    }
    
    /**
     * Whether this block should receive random ticks for particle effects.
     * Override in subclasses that spawn particles (like torches or cherry leaves).
     * 
     * @return true if this block spawns particles on random ticks
     */
    public boolean hasRandomTick() {
        return false;
    }
    
    /**
     * Called periodically client-side on blocks near the player to show effects
     * (like torch flames, furnace fire particles, falling cherry leaves, etc.).
     * 
     * <p>This is NOT the same as randomTick which is server-side for game logic.
     * animateTick is purely visual and runs on the client.
     * 
     * <p>Mirrors Minecraft's Block.animateTick method.
     * 
     * @param level the level the block is in
     * @param x block X position
     * @param y block Y position
     * @param z block Z position
     * @param random random source for particle effects
     * @param particleSpawner callback to spawn particles
     */
    public void animateTick(Level level, int x, int y, int z, Random random, 
                           ParticleSpawner particleSpawner) {
        // Default: do nothing - override in subclasses that need particle effects
    }
    
    /**
     * Functional interface for spawning particles.
     * This allows blocks to spawn particles without depending on the particle system directly.
     */
    @FunctionalInterface
    public interface ParticleSpawner {
        /**
         * Spawn a particle at the given position.
         * 
         * @param particleType the particle type identifier (e.g., "smoke", "flame")
         * @param x spawn X position
         * @param y spawn Y position  
         * @param z spawn Z position
         * @param xSpeed initial X velocity
         * @param ySpeed initial Y velocity
         * @param zSpeed initial Z velocity
         */
        void spawn(String particleType, double x, double y, double z, 
                   double xSpeed, double ySpeed, double zSpeed);
    }
}
