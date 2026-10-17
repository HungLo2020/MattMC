package net.vulkanic.world;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.vulkanic.bridge.VulkanicGalBridge.WorldMeshTextureAssetRecord;

/** Bounded immutable CPU foil assets. Resource reload must clear this cache. */
final class StandardFoilTextureCache {
    private static final int MAX_ENTRIES = 2;
    private final int maximumPngBytes;
    private final Map<ResourceLocation, WorldMeshTextureAssetRecord> assets = new LinkedHashMap<>(2, 0.75F, true);

    StandardFoilTextureCache(int maximumPngBytes) {
        if (maximumPngBytes < 1) throw new IllegalArgumentException("positive foil resource bound required");
        this.maximumPngBytes = maximumPngBytes;
    }

    synchronized WorldMeshTextureAssetRecord get(ResourceProvider resources, ResourceLocation identity, int textureId) throws IOException {
        var cached = assets.get(identity);
        if (cached != null && cached.textureId() == textureId) return cached;
        var copied = copy(textureId, resources.getResourceOrThrow(identity), maximumPngBytes);
        // Publish only a complete copy. Failed reads/metadata must remain retryable.
        assets.put(identity, copied);
        if (assets.size() > MAX_ENTRIES) assets.remove(assets.keySet().iterator().next());
        return copied;
    }

    synchronized void clear() {
        assets.clear();
    }

    static WorldMeshTextureAssetRecord copy(int textureId, Resource resource, int maximumPngBytes) throws IOException {
        try (var input = resource.open()) {
            byte[] payload = input.readNBytes(Math.addExact(maximumPngBytes, 1));
            if (payload.length > maximumPngBytes) {
                throw new IOException("standard item foil texture exceeds the " + maximumPngBytes + " byte bound");
            }
            var metadata = resource.metadata().getSection(net.minecraft.client.resources.metadata.texture.TextureMetadataSection.TYPE);
            return new WorldMeshTextureAssetRecord(textureId, payload).withTextureMetadata(
                metadata.map(net.minecraft.client.resources.metadata.texture.TextureMetadataSection::blur).orElse(false),
                metadata.map(net.minecraft.client.resources.metadata.texture.TextureMetadataSection::clamp).orElse(false)).withMipLevels(1);
        }
    }
}
