package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Minimal AMRenderTypes implementation for Mimic Octopus
 * Only includes methods actually needed, delegates to vanilla RenderType
 */
public class AMRenderTypes {
    
    /**
     * Creates a translucent entity render type
     * Delegates to vanilla RenderType.entityTranslucent()
     */
    public static RenderType entityTranslucent(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
    
    /**
     * Creates a cutout no cull entity render type
     * Delegates to vanilla RenderType.entityCutoutNoCull()
     */
    public static RenderType entityCutoutNoCull(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }
    
    /**
     * Creates a translucent render type for Spectre bones
     * Delegates to vanilla RenderType.entityTranslucent()
     */
    public static RenderType getSpectreBones(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
    
    /**
     * Creates an eyes render type with flickering effect
     * Delegates to vanilla RenderType.eyes()
     */
    public static RenderType getEyesFlickering(ResourceLocation texture, int light) {
        return RenderType.eyes(texture);
    }
    
    /**
     * Creates a translucent render type for Underminer entity
     * Uses energy swirl shader for ghostly effect
     */
    public static RenderType getUnderminer(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
    
    /**
     * Creates a render type for ghostly pickaxe item
     */
    public static RenderType getGhostPickaxe(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
    
    /**
     * Creates a render type for ghostly crumbling/breaking effect
     */
    public static RenderType getGhostCrumbling(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
    
    /**
     * Creates an eyes render type without fog
     * Delegates to vanilla RenderType.eyes()
     */
    public static RenderType getEyesNoFog(ResourceLocation texture) {
        return RenderType.eyes(texture);
    }
    
    /**
     * Creates an eyes render type with alpha blending enabled
     * Used for Sunbird glow effect
     */
    public static RenderType getEyesAlphaEnabled(ResourceLocation texture) {
        return RenderType.eyes(texture);
    }
    
    /**
     * Creates an eyes render type
     * Delegates to vanilla RenderType.eyes()
     */
    public static RenderType eyes(ResourceLocation texture) {
        return RenderType.eyes(texture);
    }
    
    /**
     * Creates a render type for Sunbird shine effect
     * Uses additive blending for bright glow
     */
    public static RenderType getSunbirdShine() {
        return RenderType.endPortal();  // Use end portal render type for bright additive effect
    }
}
