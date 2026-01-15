package net.sodium.api.vertex.serializer;

import net.blaze3d.vertex.VertexFormat;
import net.sodium.api.internal.DependencyInjection;

public interface VertexSerializerRegistry {
    VertexSerializerRegistry INSTANCE = DependencyInjection.load(VertexSerializerRegistry.class,
            "net.caffeinemc.mods.sodium.client.render.vertex.serializers.VertexSerializerRegistryImpl");

    @SuppressWarnings("SameReturnValue")
    static VertexSerializerRegistry instance() {
        return INSTANCE;
    }

    VertexSerializer get(VertexFormat srcFormat, VertexFormat dstFormat);

    void registerSerializer(VertexFormat srcFormat, VertexFormat dstFormat, VertexSerializer serializer);
}
