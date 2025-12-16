package net.minecraft.client.renderer.sodium.api.vertex.format;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.sodium.api.internal.DependencyInjection;

public interface VertexFormatRegistry {
    VertexFormatRegistry INSTANCE = DependencyInjection.load(VertexFormatRegistry.class,
            "net.minecraft.client.renderer.sodium.render.vertex.VertexFormatRegistryImpl");

    static VertexFormatRegistry instance() {
        return INSTANCE;
    }

    int allocateGlobalId(VertexFormat format);
}