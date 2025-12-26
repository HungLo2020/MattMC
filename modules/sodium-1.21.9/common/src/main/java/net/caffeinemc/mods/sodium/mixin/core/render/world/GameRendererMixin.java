package net.caffeinemc.mods.sodium.mixin.core.render.world;

import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.util.FogStorage;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public class GameRendererMixin implements FogStorage {
    @Override
    public FogParameters sodium$getFogParameters() {
        // Use the hook-based fog parameter storage instead of mixin
        return net.caffeinemc.mods.sodium.fabric.SodiumFogRenderHook.getFogParameters();
    }
}
