package com.github.alexthe666.citadel.client;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;

// Citadel: IClientItemExtensions doesn't exist in vanilla 1.21 - this class is not used
// Item rendering is now handled directly through BlockEntityWithoutLevelRenderer
// which is registered via Item.Properties in item registration
public class CitadelItemRenderProperties {

    private final BlockEntityWithoutLevelRenderer renderer = new CitadelItemstackRenderer();

    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return renderer;
    }
}
