package net.minecraft.world.item;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.entity.PotDecorations;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Items projected from Rust (`content/item`) against Frozen's recorded items
 * (DevUtils/tests/content/ContentReference.java, section "items", same format).
 */
class NativeItemsTest {
    private static RegistryOps<Tag> ops;

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ops = RegistryOps.create(NbtOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static String encoded(DataComponentType type, Object value) {
        if (type.isTransient()) return "transient " + value;
        var tag = ((Codec) type.codecOrThrow()).encodeStart(ops, value).getOrThrow();
        var back = ((Codec) type.codecOrThrow()).parse(ops, tag);
        String mark = back.isError() ? "UNDECODABLE " : back.getOrThrow().equals(value) ? "" : "UNEQUAL ";
        return mark + tag;
    }

    /** The observer's "items" section. */
    static List<String> items() {
        var out = new ArrayList<String>();
        for (var item : BuiltInRegistries.ITEM) {
            int id = BuiltInRegistries.ITEM.getId(item);
            var remainder = item.getCraftingRemainder();
            String block = item instanceof BlockItem blockItem ? BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString() : "none";
            out.add("item " + id + " " + BuiltInRegistries.ITEM.getKey(item) + " class=" + item.getClass().getName()
                + " desc=" + item.getDescriptionId() + " remainder=" + (remainder.isEmpty() ? "none" : BuiltInRegistries.ITEM.getKey(remainder.getItem()) + "x" + remainder.getCount())
                + " features=" + new java.util.TreeSet<>(FeatureFlags.REGISTRY.toNames(item.requiredFeatures())) + " block=" + block);
            var components = new TreeMap<Integer, String>();
            for (var typed : item.components()) {
                components.put(BuiltInRegistries.DATA_COMPONENT_TYPE.getId(typed.type()),
                    BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(typed.type()) + " " + encoded(typed.type(), typed.value()));
            }
            for (var component : components.values()) out.add("item_component " + id + " " + component);
        }
        var aliases = new TreeMap<Integer, String>();
        for (var entry : Item.BY_BLOCK.entrySet())
            aliases.put(BuiltInRegistries.BLOCK.getId(entry.getKey()), BuiltInRegistries.BLOCK.getKey(entry.getKey()) + " " + BuiltInRegistries.ITEM.getKey(entry.getValue()));
        for (var alias : aliases.values()) out.add("block_item " + alias);
        return out;
    }

    static List<String> frozen() throws Exception {
        try (InputStream in = new GZIPInputStream(Objects.requireNonNull(NativeItemsTest.class.getResourceAsStream("/content/frozen-items.txt.gz")))) {
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
        }
    }

    @Test void itemsMatchFrozen() throws Exception {
        var expected = frozen();
        var actual = items();
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) assertEquals(expected.get(i), actual.get(i), "row " + i);
        assertEquals(expected.size(), actual.size());
    }

    /** Every stored component decodes and re-encodes to exactly the stored SNBT. */
    @Test void storedComponentsRoundTrip() {
        int checked = 0;
        for (var definition : NativeItems.definitions()) {
            var item = BuiltInRegistries.ITEM.getValue(ResourceLocation.withDefaultNamespace(definition.name()));
            for (var component : definition.components()) {
                var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ResourceLocation.withDefaultNamespace(component.type()));
                // Bare data-driven registry keys cannot decode against frozen registries; the observer marks them.
                String actual = encoded(type, item.components().get(type)).replaceFirst("^UNDECODABLE ", "");
                // PotDecorations.EMPTY saves as four bricks; it is stored as the equivalent empty list.
                if (component.type().equals("pot_decorations") && component.snbt().equals("[]")) {
                    assertEquals(PotDecorations.EMPTY, item.components().get(type));
                    actual = "[]";
                }
                assertEquals(component.snbt(), actual, definition.name() + " " + component.type());
                checked++;
            }
        }
        assertEquals(1728, checked);
    }

    /** eggs() lists egg items without reading components; each must spawn a distinct type, as Frozen's index held. */
    @Test void everySpawnEggSpawnsADistinctType() {
        int eggs = 0;
        for (SpawnEggItem egg : SpawnEggItem.eggs()) {
            var data = egg.components().get(DataComponents.ENTITY_DATA);
            assertNotNull(data, egg.getDescriptionId());
            assertSame(egg, SpawnEggItem.byId(data.type()));
            eggs++;
        }
        assertEquals(158, eggs);
    }

    /** Rust's SNBT parse of every catalog value equals Java's TagParser. */
    @Test void rustParsesCatalogSnbtAsJavaDoes() throws Exception {
        var parser = net.minecraft.nbt.TagParser.create(NbtOps.INSTANCE);
        int checked = 0;
        for (var definition : NativeItems.definitions()) {
            for (var component : definition.components()) {
                assertEquals(parser.parseFully(component.snbt()), component.tag(), definition.name() + " " + component.type());
                checked++;
            }
        }
        assertEquals(1728, checked);
    }

    @Test void outOfOrderRegistrationIsRejected() {
        var key = ResourceKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("stone"));
        assertThrows(IllegalStateException.class, () -> NativeItems.definition(2, key));
        assertThrows(IllegalStateException.class, () -> NativeItems.definition(NativeItems.definitions().size(), key));
        assertEquals("stone", NativeItems.definition(1, key).name());
    }

    /** Ordinary save and network contexts still reject a bare key; only definition decoding keeps it. */
    @Test void unresolvedKeysStayRejectedOutsideDefinitions() {
        var tag = StringTag.valueOf("minecraft:13");
        var playable = DataComponents.JUKEBOX_PLAYABLE.codecOrThrow();
        assertTrue(playable.parse(ops, tag).isError());
        var decoded = NativeItems.decode(DataComponents.JUKEBOX_PLAYABLE, tag, "minecraft:13");
        assertEquals(new JukeboxPlayable(new EitherHolder<>(ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.withDefaultNamespace("13")))), decoded);
    }
}
