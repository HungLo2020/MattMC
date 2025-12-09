package net.minecraft.client.renderer.shader.program;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

import java.util.HashMap;
import java.util.Map;

/**
 * Enum of shader program types used in shader packs.
 * Maps program names to their types for easier handling.
 */
@Environment(EnvType.CLIENT)
public enum ShaderProgramType {
    // Geometry buffer programs
    GBUFFERS_BASIC("gbuffers_basic"),
    GBUFFERS_TEXTURED("gbuffers_textured"),
    GBUFFERS_TEXTURED_LIT("gbuffers_textured_lit"),
    GBUFFERS_TERRAIN("gbuffers_terrain"),
    GBUFFERS_TERRAIN_SOLID("gbuffers_terrain_solid"),
    GBUFFERS_TERRAIN_CUTOUT("gbuffers_terrain_cutout"),
    GBUFFERS_TERRAIN_CUTOUT_MIPPED("gbuffers_terrain_cutout_mipped"),
    GBUFFERS_DAMAGED_BLOCK("gbuffers_damaged_block"),
    GBUFFERS_SKYBASIC("gbuffers_skybasic"),
    GBUFFERS_SKYTEXTURED("gbuffers_skytextured"),
    GBUFFERS_CLOUDS("gbuffers_clouds"),
    GBUFFERS_ENTITIES("gbuffers_entities"),
    GBUFFERS_ENTITIES_GLOWING("gbuffers_entities_glowing"),
    GBUFFERS_ENTITIES_TRANSLUCENT("gbuffers_entities_translucent"),
    GBUFFERS_ARMOR_GLINT("gbuffers_armor_glint"),
    GBUFFERS_SPIDER_EYES("gbuffers_spider_eyes"),
    GBUFFERS_HAND("gbuffers_hand"),
    GBUFFERS_HAND_WATER("gbuffers_hand_water"),
    GBUFFERS_WEATHER("gbuffers_weather"),
    GBUFFERS_BLOCK("gbuffers_block"),
    GBUFFERS_BEACON_BEAM("gbuffers_beaconbeam"),
    GBUFFERS_PARTICLES("gbuffers_particles"),
    GBUFFERS_WATER("gbuffers_water"),
    
    // Shadow programs
    SHADOW("shadow"),
    SHADOW_SOLID("shadow_solid"),
    SHADOW_CUTOUT("shadow_cutout"),
    
    // Deferred programs
    DEFERRED("deferred"),
    DEFERRED1("deferred1"),
    DEFERRED2("deferred2"),
    DEFERRED3("deferred3"),
    DEFERRED4("deferred4"),
    DEFERRED5("deferred5"),
    DEFERRED6("deferred6"),
    DEFERRED7("deferred7"),
    
    // Composite programs
    PREPARE("prepare"),
    COMPOSITE("composite"),
    COMPOSITE1("composite1"),
    COMPOSITE2("composite2"),
    COMPOSITE3("composite3"),
    COMPOSITE4("composite4"),
    COMPOSITE5("composite5"),
    COMPOSITE6("composite6"),
    COMPOSITE7("composite7"),
    COMPOSITE8("composite8"),
    COMPOSITE9("composite9"),
    COMPOSITE10("composite10"),
    COMPOSITE11("composite11"),
    COMPOSITE12("composite12"),
    COMPOSITE13("composite13"),
    COMPOSITE14("composite14"),
    COMPOSITE15("composite15"),
    
    // Final program
    FINAL("final");
    
    private static final Map<String, ShaderProgramType> BY_NAME = new HashMap<>();
    
    static {
        for (ShaderProgramType type : values()) {
            BY_NAME.put(type.name, type);
        }
    }
    
    private final String name;
    
    ShaderProgramType(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    /**
     * Gets a shader program type by its name, or null if not found.
     */
    public static ShaderProgramType fromName(String name) {
        return BY_NAME.get(name);
    }
    
    /**
     * Checks if a program name is a valid shader program type.
     */
    public static boolean isValidProgramName(String name) {
        return BY_NAME.containsKey(name);
    }
}
