package com.github.alexthe666.citadel.client.shader;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

// Citadel: Simplified shader render types for 1.21
// Custom shader system completely redesigned in 1.21 - using vanilla render types instead
// Full custom shader support would require resource pack based shader system
// No longer extends RenderType as it's not meant to be extended in 1.21
public class CitadelShaderRenderTypes {

    // Citadel: Rainbow aura render type - simplified to use vanilla translucent type
    // Full custom shader support would require adapting to 1.21's resource pack based shader system
    public static RenderType getRainbowAura(ResourceLocation locationIn) {
        // TODO: Implement proper rainbow aura shader using 1.21's resource pack based shader system
        // For now, return translucent render type as fallback
        return RenderType.translucent();
    }
}
