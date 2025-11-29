package net.matt.quantize.mixin;

import net.matt.quantize.modules.config.QClientConfig;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(PanoramaRenderer.class)
public class PanoramaRendererMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void injectCustomCubeMap(CubeMap pCubeMap, CallbackInfo ci) {
        String selectedPanorama = QClientConfig.CLIENT.SELECTED_PANORAMA.get();
        boolean useBlurred = QClientConfig.CLIENT.PANORAMA_BLUR.get();

        String subdirectory = useBlurred ? "background/blurred/" : "background/";
        ResourceLocation customPanorama = ResourceLocation.tryParse("quantize:textures/gui/title/" + subdirectory + selectedPanorama);

        try {
            Field cubeMapField = null;
            for (Field field : PanoramaRenderer.class.getDeclaredFields()) {
                if (field.getType() == CubeMap.class) {
                    cubeMapField = field;
                    break;
                }
            }
            if (cubeMapField == null) {
                throw new NoSuchFieldException("CubeMap field not found in PanoramaRenderer");
            }
            cubeMapField.setAccessible(true);
            cubeMapField.set(this, new CubeMap(customPanorama));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject custom CubeMap", e);
        }
    }
}