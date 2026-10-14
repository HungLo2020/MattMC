package net.minecraft.resources;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.RecordComponent;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Data-driven entity and item registries decoded by Rust (`content/data`):
 * loaded elements project native definitions, and Java-built copies (no
 * stored definition) encode exactly as the loaded originals. Frozen parity:
 * DevUtils/tests/content (ContentReference, DataFuzzReference).
 */
class NativeDataTest {
    static RegistryAccess registries;

    @BeforeAll static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(net.minecraft.server.packs.PackType.SERVER_DATA,
            List.of(net.minecraft.server.packs.repository.ServerPacksSource.createVanillaPackSource()));
        var layers = RegistryLayer.createRegistryAccess();
        var tags = net.minecraft.tags.TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
        var lookups = net.minecraft.tags.TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLDGEN), tags);
        var worldgen = RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLDGEN_REGISTRIES);
        registries = layers.replaceFrom(RegistryLayer.WORLDGEN, worldgen).compositeAccess();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Codec<Object>[] codecs(NativeData.Kind kind) {
        Codec direct = null, network = null;
        for (var data : RegistryDataLoader.WORLDGEN_REGISTRIES) if (data.key().equals(NativeData.registry(kind))) direct = data.elementCodec();
        for (var data : RegistryDataLoader.SYNCHRONIZED_REGISTRIES) if (data.key().equals(NativeData.registry(kind))) network = data.elementCodec();
        return new Codec[]{direct, network == null ? direct : network};
    }

    /** A new instance with the same components, through the canonical constructor. */
    static Object copy(Object record) throws ReflectiveOperationException {
        RecordComponent[] components = record.getClass().getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            components[i].getAccessor().setAccessible(true);
            values[i] = components[i].getAccessor().invoke(record);
            types[i] = components[i].getType();
        }
        var constructor = record.getClass().getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(values);
    }

    @Test void javaBuiltCopiesEncodeAsLoadedOriginals() throws Exception {
        var json = registries.createSerializationContext(JsonOps.INSTANCE);
        var nbt = registries.createSerializationContext(NbtOps.INSTANCE);
        int checked = 0;
        for (NativeData.Kind kind : NativeData.Kind.values()) {
            if (kind == NativeData.Kind.BIOME) continue;
            Codec<Object>[] codecs = codecs(kind);
            for (var holder : registries.lookupOrThrow(NativeData.registry(kind)).listElements().toList()) {
                Object original = holder.value();
                assertNotEquals(0L, ((NativeData.DataCodec<Object>) (Object) codecs[0]).nativeHandle(original), holder.key().toString());
                Object copy = copy(original);
                assertEquals(codecs[0].encodeStart(json, original).getOrThrow(), codecs[0].encodeStart(json, copy).getOrThrow(), holder.key().toString());
                assertEquals(codecs[1].encodeStart(nbt, original).getOrThrow(), codecs[1].encodeStart(nbt, copy).getOrThrow(), holder.key().toString());
                checked++;
            }
        }
        assertTrue(checked > 250, "checked " + checked);
    }

    @Test void invalidJavaBuiltDefinitionsDoNotEncode() {
        var invalid = new net.minecraft.world.entity.decoration.PaintingVariant(0, 1, ResourceLocation.withDefaultNamespace("x"), java.util.Optional.empty(), java.util.Optional.empty());
        assertTrue(net.minecraft.world.entity.decoration.PaintingVariant.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, invalid).isError());
    }

    /**
     * Data generator bootstraps load the data pack through Rust. Biomes are
     * left out: their files name placed features the Java worldgen generator
     * does not define (worldgen migration), as Frozen's generator already failed.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test void dataGeneratorRegistriesLoadFromTheDataPack() {
        var builder = new net.minecraft.core.RegistrySetBuilder();
        for (NativeData.Kind kind : NativeData.Kind.values()) {
            if (kind != NativeData.Kind.BIOME) builder.add((net.minecraft.resources.ResourceKey) NativeData.registry(kind), NativeData.bootstrap(kind, (Codec) codecs(kind)[0]));
        }
        // Spawn conditions name loaded biomes and structures.
        var context = new RegistryAccess.ImmutableRegistryAccess(java.util.stream.Stream.concat(
            net.minecraft.core.registries.BuiltInRegistries.REGISTRY.stream(),
            java.util.stream.Stream.of(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME), registries.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE))
        ).toList());
        var lookup = builder.build(context);
        for (NativeData.Kind kind : NativeData.Kind.values()) {
            if (kind == NativeData.Kind.BIOME) continue;
            assertEquals(registries.lookupOrThrow(NativeData.registry(kind)).listElementIds().count(), lookup.lookupOrThrow(NativeData.registry(kind)).listElementIds().count(), kind.toString());
        }
    }
}
