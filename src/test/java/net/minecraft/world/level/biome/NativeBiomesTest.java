package net.minecraft.world.level.biome;

import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Biomes decoded by Rust (`content/biome`): every vanilla biome loads through
 * the native file path, and save and network encodings decode back natively
 * to the same biome. Frozen parity: DevUtils/tests/content (ContentReference,
 * BiomeFuzzReference).
 */
class NativeBiomesTest {
    static RegistryAccess registries;

    @BeforeAll static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers = RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), tags);
        var worldgen = net.minecraft.resources.RegistryDataLoader.load(resources, lookups, net.minecraft.resources.RegistryDataLoader.WORLDGEN_REGISTRIES);
        registries = layers.replaceFrom(RegistryLayer.WORLDGEN, worldgen).compositeAccess();
    }

    static List<Biome> biomes() {
        return registries.lookupOrThrow(Registries.BIOME).listElements().map(h -> h.value()).toList();
    }

    @Test void everyLoadedBiomeProjectsANativeDefinition() {
        assertEquals(68, biomes().size());
        for (Biome biome : biomes()) assertNotEquals(0L, biome.nativeHandle());
        assertTrue(net.minecraft.resources.NativeData.liveCount() >= 68);
    }

    @Test void saveAndNetworkFormsDecodeNativelyToTheSameBiome() {
        var json = registries.createSerializationContext(JsonOps.INSTANCE);
        var nbt = registries.createSerializationContext(NbtOps.INSTANCE);
        for (Biome biome : biomes()) {
            var direct = Biome.DIRECT_CODEC.encodeStart(json, biome).getOrThrow();
            Biome fromTree = Biome.DIRECT_CODEC.parse(json, direct).getOrThrow();
            assertEquals(direct, Biome.DIRECT_CODEC.encodeStart(json, fromTree).getOrThrow());
            Biome fromFile = ((net.minecraft.resources.NativeData.DataCodec<Biome>) Biome.DIRECT_CODEC).decodeFile(json, direct.toString().getBytes(StandardCharsets.UTF_8)).getOrThrow();
            assertEquals(direct, Biome.DIRECT_CODEC.encodeStart(json, fromFile).getOrThrow());
            var network = Biome.NETWORK_CODEC.encodeStart(nbt, biome).getOrThrow();
            Biome synced = Biome.NETWORK_CODEC.parse(nbt, network).getOrThrow();
            assertEquals(network, Biome.NETWORK_CODEC.encodeStart(nbt, synced).getOrThrow());
            assertNotEquals(0L, synced.nativeHandle());
        }
    }

    /** Java-built biomes (no stored definition) encode as the loaded originals. */
    @Test void javaBuiltCopiesEncodeAsLoadedOriginals() {
        var json = registries.createSerializationContext(JsonOps.INSTANCE);
        var nbt = registries.createSerializationContext(NbtOps.INSTANCE);
        for (Biome biome : biomes()) {
            Biome copy = new Biome(biome.climateSettings, biome.getSpecialEffects(), biome.getGenerationSettings(), biome.getMobSettings());
            assertEquals(0L, copy.nativeHandle());
            assertEquals(Biome.DIRECT_CODEC.encodeStart(json, biome).getOrThrow(), Biome.DIRECT_CODEC.encodeStart(json, copy).getOrThrow());
            assertEquals(Biome.NETWORK_CODEC.encodeStart(nbt, biome).getOrThrow(), Biome.NETWORK_CODEC.encodeStart(nbt, copy).getOrThrow());
        }
    }

    @Test void unknownFieldsAreIgnoredAndMalformedFilesRejected() {
        var json = registries.createSerializationContext(JsonOps.INSTANCE);
        var codec = (net.minecraft.resources.NativeData.DataCodec<Biome>) Biome.DIRECT_CODEC;
        Biome plains = registries.lookupOrThrow(Registries.BIOME).getValue(Biomes.PLAINS);
        var direct = Biome.DIRECT_CODEC.encodeStart(json, plains).getOrThrow().getAsJsonObject();
        direct.addProperty("custom_field", 7);
        Biome extended = codec.decodeFile(json, direct.toString().getBytes(StandardCharsets.UTF_8)).getOrThrow();
        direct.remove("custom_field");
        assertEquals(direct, Biome.DIRECT_CODEC.encodeStart(json, extended).getOrThrow());
        // Malformed text fails as the loader's own parser fails (Gson's exception).
        assertThrows(com.google.gson.JsonSyntaxException.class, () -> codec.decodeFile(json, "{\"temperature\": 0.5,}".getBytes(StandardCharsets.UTF_8)));
        direct.addProperty("has_precipitation", 1);
        assertTrue(codec.decodeFile(json, direct.toString().getBytes(StandardCharsets.UTF_8)).isError());
    }
}
