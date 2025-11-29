package net.matt.quantize.mixin;

import net.matt.quantize.modules.dark.DClientProxy;
import net.matt.quantize.utils.ClassUtil;
import net.matt.quantize.modules.dark.ShaderConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FastColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Font.class)
public class FontMixin {

    @Inject(method = "adjustColor", at = @At(value = "HEAD", target = "Lnet/minecraft/client/gui/Font;adjustColor(I)I"), cancellable = true)
    private static void darkModeEverywhere$adjustColorA(int color, CallbackInfoReturnable<Integer> cir) {
        if (DClientProxy.SELECTED_SHADER_VALUE != null && Minecraft.getInstance().screen != null) {
            var callerClassName = ClassUtil.getCallerClassName();
            if (callerClassName != null && DClientProxy.isElementNameBlacklisted(callerClassName)) {
                return;
            }
            int threshold = 65;
            ShaderConfig.ShaderValue shaderValue = DClientProxy.SELECTED_SHADER_VALUE;
            if (shaderValue.darkColorReplacement == -1) return;
            if (ChatFormatting.GRAY.getColor().equals(color) || ChatFormatting.DARK_GRAY.getColor().equals(color)) {
                cir.setReturnValue(0xFF000000 | shaderValue.darkColorReplacement);
                return;
            }
            if (FastColor.ARGB32.red(color) < threshold && FastColor.ARGB32.green(color)  < threshold && FastColor.ARGB32.blue(color)  < threshold){
                cir.setReturnValue(0xFF000000 | shaderValue.darkColorReplacement);
                return;
            }
        }
    }
}