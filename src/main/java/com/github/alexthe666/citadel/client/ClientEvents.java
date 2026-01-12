package com.github.alexthe666/citadel.client;

import com.github.alexthe666/citadel.client.shader.CitadelInternalShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

// Citadel: Shader registration moved to different system in 1.21
// Shaders are now registered through resource packs and CoreShaders
// TODO: Wire to Fabric's ResourceManagerHelper when shader support is needed
@Environment(EnvType.CLIENT)
public class ClientEvents {
    // Placeholder - shader registration happens differently in 1.21
    public static void registerShaders() {
        // In 1.21, shaders are loaded from resource packs automatically
        // Custom shaders need to be registered through CoreShaders
        // This is a placeholder for future implementation
    }
}
