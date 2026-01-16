package com.seibel.distanthorizons.fabric.hooks;

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.hooks.MinecraftLevelHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Hook implementation for Minecraft level updates.
 * Replaces MixinMinecraft.updateLevelInEngines.
 */
public class DhMinecraftLevelHook implements MinecraftLevelHooks {
    private ClientLevel lastLevel = null;

    @Override
    public void onLevelUpdateInEngines(@Nullable ClientLevel newLevel) {
        if (this.lastLevel != null && newLevel != this.lastLevel) {
            ClientApi.INSTANCE.clientLevelUnloadEvent(ClientLevelWrapper.getWrapper(this.lastLevel));
        }

        if (newLevel != null) {
            ClientApi.INSTANCE.clientLevelLoadEvent(ClientLevelWrapper.getWrapper(newLevel, true));
        }

        this.lastLevel = newLevel;
    }
}
