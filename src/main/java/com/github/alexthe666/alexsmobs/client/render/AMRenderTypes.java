package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Simplified render types for AlexsMobs entities
 */
public class AMRenderTypes {
    
    /**
     * Creates an entity translucent render type for the given texture
     */
    public static RenderType entityTranslucent(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
