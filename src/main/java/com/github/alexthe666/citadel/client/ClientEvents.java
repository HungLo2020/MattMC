package com.github.alexthe666.citadel.client;

import com.github.alexthe666.citadel.client.shader.CitadelInternalShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
// TODO: Replace with Fabric
// import net.neoforged.api.distmarker.Dist;
// TODO: Replace with Fabric
// import net.neoforged.bus.api.SubscribeEvent;
// TODO: Replace with Fabric
// import net.neoforged.fml.common.EventBusSubscriber;
// TODO: Replace with Fabric
// import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientEvents {
    @SubscribeEvent
    public static void registerShaders(final RegisterShadersEvent e) {
        try {
            e.registerShader(new ShaderInstance(e.getResourceProvider(), ResourceLocation.parse("citadel:rendertype_rainbow_aura"), DefaultVertexFormat.POSITION_TEX_COLOR), CitadelInternalShaders::setRenderTypeRainbowAura);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
