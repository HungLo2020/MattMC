package net.sodium.api.vertex.format;

import net.blaze3d.vertex.VertexFormat;
import net.sodium.api.internal.DependencyInjection;

public interface VertexFormatRegistry {
    VertexFormatRegistry INSTANCE = DependencyInjection.load(VertexFormatRegistry.class,
            "net.sodium.client.render.vertex.VertexFormatRegistryImpl");

    @SuppressWarnings("SameReturnValue")
    static VertexFormatRegistry instance() {
        return INSTANCE;
    }

    int allocateGlobalId(VertexFormat format);
}