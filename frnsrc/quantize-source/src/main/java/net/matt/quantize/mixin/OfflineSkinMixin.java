package net.matt.quantize.mixin;

import net.matt.quantize.Quantize;
import net.matt.quantize.modules.config.QClientConfig;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class OfflineSkinMixin {

    /* ----------------------- CONSTANTS ----------------------- */

    /** Built-in fallback (all lower-case!). */
    private static final ResourceLocation DEFAULT_SKIN =
            new ResourceIdentifier("textures/model/entity/player/steve.png");

    /* ----------------------- HELPER -------------------------- */

    /** True when the client is *not* authenticated against Mojang/Xbox Live. */
    private static boolean isClientOffline() {
        User user = Minecraft.getInstance().getUser();
        return user.getAccessToken().isEmpty()          // "" or "0" in offline launchers
                || "0".equals(user.getAccessToken())
                || user.getType() == User.Type.LEGACY;
    }

    /**
     * Returns the skin specified in the QClientConfig or the default skin if none is found.
     */
    private static ResourceLocation getConfigSkin() {
        String selectedSkin = QClientConfig.CLIENT.SELECTED_SKIN.get();
        if (selectedSkin == null || selectedSkin.isEmpty()) {
            Quantize.LOGGER.warn("SELECTED_SKIN is not set or empty, using default skin.");
            return DEFAULT_SKIN;
        }

        ResourceLocation configSkin = new ResourceIdentifier("textures/model/entity/player/" + selectedSkin + ".png");
        //Quantize.LOGGER.info("Using skin from config: {}", configSkin);
        return configSkin;
    }

    /* ----------------------- MIXIN --------------------------- */

    /**
     * If the client is offline, replace every skin lookup with the skin from QClientConfig
     * (if available) or the default Steve skin.
     */
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void quantize$overrideOfflineSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        if (!isClientOffline())
            return;                     // online → let vanilla fetch real skins

        ResourceLocation configSkin = getConfigSkin();
        cir.setReturnValue(configSkin);
    }
}