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
}
