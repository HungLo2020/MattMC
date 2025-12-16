package net.minecraft.client.renderer.advanced.vertex.format;

import org.jetbrains.annotations.ApiStatus;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.advanced.internal.DependencyInjection;

public interface VertexFormatRegistry {
    VertexFormatRegistry INSTANCE = DependencyInjection.load(VertexFormatRegistry.class,
            "net.minecraft.client.renderer.sodium.render.vertex.VertexFormatRegistryImpl");

    static VertexFormatRegistry instance() {
        return INSTANCE;
    }

    int allocateGlobalId(VertexFormat format);
}
