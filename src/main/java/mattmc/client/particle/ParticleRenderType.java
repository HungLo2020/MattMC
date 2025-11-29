package mattmc.client.particle;

/**
 * Defines how a group of particles should be rendered.
 * 
 * <p>Each render type controls the blending mode, texture, and depth settings
 * for a batch of particles. Particles are grouped by render type to minimize
 * state changes during rendering.
 * 
 * <p>This mirrors Minecraft's ParticleRenderType interface.
 */
public enum ParticleRenderType {
    /**
     * Particles rendered from the block texture atlas.
     * Used for terrain/block-based particles.
     */
    TERRAIN_SHEET("TERRAIN_SHEET", true, true, false),
    
    /**
     * Opaque particles from the particle texture atlas.
     * No blending, depth writing enabled.
     */
    PARTICLE_SHEET_OPAQUE("PARTICLE_SHEET_OPAQUE", false, true, false),
    
    /**
     * Translucent particles from the particle texture atlas.
     * Alpha blending enabled.
     */
    PARTICLE_SHEET_TRANSLUCENT("PARTICLE_SHEET_TRANSLUCENT", true, true, false),
    
    /**
     * Lit particles (affected by world lighting).
     * Similar to opaque but uses light sampling.
     */
    PARTICLE_SHEET_LIT("PARTICLE_SHEET_LIT", false, true, true),
    
    /**
     * Custom render type for special particles.
     * The particle handles its own rendering state.
     */
    CUSTOM("CUSTOM", true, true, false),
    
    /**
     * No rendering (invisible particles, used for emitters).
     */
    NO_RENDER("NO_RENDER", false, false, false);
    
    private final String name;
    private final boolean useBlending;
    private final boolean useDepthTest;
    private final boolean useLighting;
    
    ParticleRenderType(String name, boolean useBlending, boolean useDepthTest, boolean useLighting) {
        this.name = name;
        this.useBlending = useBlending;
        this.useDepthTest = useDepthTest;
        this.useLighting = useLighting;
    }
    
    /**
     * Whether this render type uses alpha blending.
     */
    public boolean usesBlending() {
        return useBlending;
    }
    
    /**
     * Whether this render type uses depth testing.
     */
    public boolean usesDepthTest() {
        return useDepthTest;
    }
    
    /**
     * Whether this render type uses world lighting.
     */
    public boolean usesLighting() {
        return useLighting;
    }
    
    /**
     * Whether this render type uses the terrain (block) texture atlas.
     */
    public boolean usesTerrainAtlas() {
        return this == TERRAIN_SHEET;
    }
    
    /**
     * Whether this render type uses the particle texture atlas.
     */
    public boolean usesParticleAtlas() {
        return this == PARTICLE_SHEET_OPAQUE || 
               this == PARTICLE_SHEET_TRANSLUCENT || 
               this == PARTICLE_SHEET_LIT;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
