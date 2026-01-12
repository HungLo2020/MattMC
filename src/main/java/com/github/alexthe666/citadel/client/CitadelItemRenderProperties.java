package com.github.alexthe666.citadel.client;

// Citadel: IClientItemExtensions and BlockEntityWithoutLevelRenderer don't exist in vanilla 1.21
// This class is not used in vanilla 1.21 - item rendering handled differently
// Kept as placeholder for API compatibility
public class CitadelItemRenderProperties {

    private final CitadelItemstackRenderer renderer = new CitadelItemstackRenderer();

    public CitadelItemstackRenderer getCustomRenderer() {
        return renderer;
    }
}
