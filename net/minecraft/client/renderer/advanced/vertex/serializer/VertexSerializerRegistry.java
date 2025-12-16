package net.minecraft.client.renderer.advanced.vertex.serializer;

import org.jetbrains.annotations.ApiStatus;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.advanced.internal.DependencyInjection;

public interface VertexSerializerRegistry {
    VertexSerializerRegistry INSTANCE = DependencyInjection.load(VertexSerializerRegistry.class,
            "net.minecraft.client.renderer.vertex.advanced.serializers.VertexSerializerRegistryImpl");

    static VertexSerializerRegistry instance() {
        return INSTANCE;
    }

    VertexSerializer get(VertexFormat srcFormat, VertexFormat dstFormat);

    void registerSerializer(VertexFormat srcFormat, VertexFormat dstFormat, VertexSerializer serializer);
}
