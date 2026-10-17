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

    static com.mojang.serialization.DataResult<Object> parse(NativeData.Kind kind, String json) {
        return codecs(kind)[0].parse(registries.createSerializationContext(JsonOps.INSTANCE), com.google.gson.JsonParser.parseString(json));
    }

    // Java's codecs run while Rust decodes, once each, in field order, and
    // their errors compose with Rust's as DFU's do; a thrown exception stops
    // the decode, with no later callback and no handle left behind.
    @Test void delegatedFieldsDecodeInPlaceAndCompose() {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var sound = net.minecraft.sounds.SoundEvent.CODEC;
        String song = "{\"sound_event\":\"minecraft:nope\",\"description\":5,\"length_in_seconds\":-1,\"comparator_output\":3}";
        var calls = new java.util.ArrayList<Codec<?>>();
        long live = NativeData.liveCount();
        NativeValuesTest.withDelegates(codec -> NativeValuesTest.watched(codec, () -> { calls.add(codec); return null; }), () -> {
            String message = parse(NativeData.Kind.JUKEBOX_SONG, song).error().orElseThrow().message();
            for (String part : List.of(
                sound.parse(ops, new com.google.gson.JsonPrimitive("minecraft:nope")).error().orElseThrow().message(),
                net.minecraft.network.chat.ComponentSerialization.CODEC.parse(ops, new com.google.gson.JsonPrimitive(5)).error().orElseThrow().message(),
                "Value must be positive: -1.0")) {
                assertTrue(message.contains(part), message + " lacks " + part);
            }
        });
        // The sound is Java's; the description is the native text schema itself.
        assertEquals(List.of(sound), calls);
        assertEquals(live, NativeData.liveCount());

        // An invalid optional field leaves a partial, projected and encodable.
        var painting = parse(NativeData.Kind.PAINTING_VARIANT, "{\"width\":2,\"height\":3,\"asset_id\":\"minecraft:a\",\"title\":5}");
        var partial = assertInstanceOf(net.minecraft.world.entity.decoration.PaintingVariant.class, painting.resultOrPartial().orElseThrow());
        assertEquals(2, partial.width());
        assertTrue(partial.title().isEmpty());
        assertTrue(codecs(NativeData.Kind.PAINTING_VARIANT)[0].encodeStart(ops, partial).isSuccess());

        // Every field's codec runs once, in order, even after an error, and an
        // answer larger than Rust's buffer is retried, not decoded again.
        String wolf = "{\"ambient_sound\":\"minecraft:a\",\"death_sound\":\"minecraft:b\",\"growl_sound\":\"minecraft:c\","
            + "\"hurt_sound\":\"minecraft:d\",\"pant_sound\":\"minecraft:e\",\"whine_sound\":\"minecraft:f\"}";
        var inputs = new java.util.ArrayList<String>();
        String longMessage = "x".repeat(4000);
        NativeValuesTest.withDelegates(codec -> new Codec<Object>() {
            @Override public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<Object, T>> decode(com.mojang.serialization.DynamicOps<T> o, T input) {
                inputs.add(String.valueOf(input));
                return inputs.size() == 1 ? com.mojang.serialization.DataResult.error(() -> longMessage) : codec.decode(o, input);
            }
            @Override public <T> com.mojang.serialization.DataResult<T> encode(Object input, com.mojang.serialization.DynamicOps<T> o, T prefix) {
                return codec.encode(input, o, prefix);
            }
        }, () -> assertTrue(parse(NativeData.Kind.WOLF_SOUND_VARIANT, wolf).error().orElseThrow().message().contains(longMessage)));
        assertEquals(List.of("\"minecraft:a\"", "\"minecraft:b\"", "\"minecraft:c\"", "\"minecraft:d\"", "\"minecraft:e\"", "\"minecraft:f\""), inputs);

        // A thrown exception leaves the decode unchanged and stops later callbacks.
        calls.clear();
        Throwable thrown = new IllegalStateException("sound");
        long before = NativeData.liveCount();
        NativeValuesTest.withDelegates(codec -> NativeValuesTest.watched(codec, () -> {
            calls.add(codec);
            if (calls.size() == 2) throw NativeValuesTest.sneaky(thrown);
            return null;
        }), () -> assertSame(thrown, assertThrows(Throwable.class, () -> parse(NativeData.Kind.WOLF_SOUND_VARIANT, wolf))));
        assertEquals(2, calls.size());
        assertEquals(before, NativeData.liveCount());
    }

    // A partial keeps its native definition while it is reachable, encodes
    // after collections, and releases it once its last owner is gone.
    @Test void retainedPartialsKeepTheirDefinitionUntilCollected() throws InterruptedException {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Object partial = parse(NativeData.Kind.PAINTING_VARIANT, "{\"width\":2,\"height\":3,\"asset_id\":\"minecraft:a\",\"title\":5}")
            .resultOrPartial().orElseThrow();
        long handle = NativeData.handleOf(partial);
        assertNotEquals(0, handle);
        String encoded = codecs(NativeData.Kind.PAINTING_VARIANT)[0].encodeStart(ops, partial).getOrThrow().toString();
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertTrue(NativeData.stored(handle));
        assertEquals(encoded, codecs(NativeData.Kind.PAINTING_VARIANT)[0].encodeStart(ops, partial).getOrThrow().toString());
        java.lang.ref.Reference.reachabilityFence(partial);
        partial = null;
        for (int i = 0; i < 200 && NativeData.stored(handle); i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertFalse(NativeData.stored(handle));
    }

    // Inline text is the text codec itself: nested Java-owned item components
    // inside it run once each, in order, on the file and tree paths alike,
    // and their errors compose with the definition's own.
    @Test void inlineTextComposesWithDataFieldsAndNestedComponents() {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        @SuppressWarnings("unchecked")
        var codec = (NativeData.DataCodec<Object>)(Object)codecs(NativeData.Kind.PAINTING_VARIANT)[0];
        Codec<?> consumable = net.minecraft.core.component.DataComponents.CONSUMABLE.codecOrThrow();
        Codec<?> tool = net.minecraft.core.component.DataComponents.TOOL.codecOrThrow();
        String title = "{\"text\":\"a\",\"hover_event\":{\"action\":\"show_item\",\"id\":\"minecraft:stone\",\"components\":"
            + "{\"minecraft:consumable\":{\"consume_seconds\":\"x\"},\"minecraft:tool\":{\"rules\":5}}}}";
        String file = "{\"width\":99,\"height\":3,\"asset_id\":\"minecraft:a\",\"title\":" + title + "}";
        String textError = net.minecraft.network.chat.ComponentSerialization.CODEC.parse(ops, com.google.gson.JsonParser.parseString(title))
            .error().orElseThrow().message();
        var calls = new java.util.ArrayList<Object>();
        String[] messages = new String[2];
        NativeValuesTest.withDelegates(c -> NativeValuesTest.watched(c, () -> { calls.add(c); return null; }), () -> {
            messages[0] = codec.decodeFile(ops, file.getBytes(java.nio.charset.StandardCharsets.UTF_8)).error().orElseThrow().message();
            messages[1] = codec.parse(ops, com.google.gson.JsonParser.parseString(file)).error().orElseThrow().message();
        });
        assertEquals(messages[1], messages[0]);
        assertTrue(messages[0].contains(textError), messages[0]);
        assertTrue(messages[0].contains("99"), messages[0]);
        assertEquals(List.of(consumable, tool, consumable, tool), calls);

        // A nested answer larger than Rust's buffer is retried, not decoded again.
        calls.clear();
        String longMessage = "y".repeat(5000);
        NativeValuesTest.withDelegates(c -> c != consumable ? c : NativeValuesTest.watched(c, () -> {
            calls.add(c);
            return com.mojang.serialization.DataResult.error(() -> longMessage);
        }), () -> assertTrue(codec.decodeFile(ops, file.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .error().orElseThrow().message().contains(longMessage)));
        assertEquals(List.of(consumable), calls);
    }
}
