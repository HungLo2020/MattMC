package net.minecraft.world.level.block.state.properties;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import net.minecraft.sounds.NativeSoundDefinitions;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Temporary immutable CPU views of Rust-owned block-family configuration. */
public final class NativeBlockFamilies {
    public enum Kind {
        NONE, DOOR, TRAPDOOR, BUTTON, PRESSURE_PLATE, WEIGHTED_PLATE,
        FENCE_GATE, STANDING_SIGN, WALL_SIGN, CEILING_HANGING_SIGN, WALL_HANGING_SIGN
    }
    private static final MethodHandle BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_block_family_buffer",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    // Raw tables must not construct BlockSetType/WoodType while either class is
    // initializing its aliases. Each class creates its own canonical views.
    private static final Data DATA = load();
    private record Data(String[] sets, String[] woods, MemorySegment setRows, MemorySegment woodRows, MemorySegment blocks) { }

    private NativeBlockFamilies() { }

    static BlockSetType[] createBlockSets() {
        var result = new BlockSetType[DATA.sets.length];
        var sensitivity = BlockSetType.PressurePlateSensitivity.values();
        for (int id = 0; id < result.length; id++) {
            int at = id * 15;
            var r = DATA.setRows;
            result[id] = new BlockSetType(DATA.sets[id], word(r, at + 2) != 0, word(r, at + 3) != 0,
                word(r, at + 4) != 0, sensitivity[word(r, at + 5)], SoundType.nativeView(word(r, at + 6)),
                NativeSoundDefinitions.event(word(r, at + 7)), NativeSoundDefinitions.event(word(r, at + 8)),
                NativeSoundDefinitions.event(word(r, at + 9)), NativeSoundDefinitions.event(word(r, at + 10)),
                NativeSoundDefinitions.event(word(r, at + 11)), NativeSoundDefinitions.event(word(r, at + 12)),
                NativeSoundDefinitions.event(word(r, at + 13)), NativeSoundDefinitions.event(word(r, at + 14)));
        }
        return result;
    }

    static WoodType[] createWoods() {
        var result = new WoodType[DATA.woods.length];
        for (int id = 0; id < result.length; id++) {
            int at = id * 7;
            var r = DATA.woodRows;
            result[id] = new WoodType(DATA.woods[id], BlockSetType.nativeView(word(r, at + 2)),
                SoundType.nativeView(word(r, at + 3)), SoundType.nativeView(word(r, at + 4)),
                NativeSoundDefinitions.event(word(r, at + 5)), NativeSoundDefinitions.event(word(r, at + 6)));
        }
        return result;
    }

    private static int binding(BlockBehaviour.Properties properties, Kind expected) {
        var definition = properties.nativeDefinition();
        if (definition == null) throw new IllegalArgumentException("Native family constructor requires registered block properties");
        long count = DATA.blocks.byteSize() / (3 * Integer.BYTES);
        if (definition.id < 0 || definition.id >= count) throw new IllegalStateException("Native block family ID out of bounds");
        int at = definition.id * 3;
        if (word(DATA.blocks, at) != expected.ordinal()) throw new IllegalStateException("Wrong native block family for " + definition.name);
        return at;
    }

    public static BlockSetType set(BlockBehaviour.Properties properties, Kind expected) {
        if (expected.ordinal() < Kind.DOOR.ordinal() || expected.ordinal() > Kind.WEIGHTED_PLATE.ordinal())
            throw new IllegalArgumentException("Family has no block-set configuration");
        return BlockSetType.nativeView(word(DATA.blocks, binding(properties, expected) + 1));
    }

    public static WoodType wood(BlockBehaviour.Properties properties, Kind expected) {
        if (expected.ordinal() < Kind.FENCE_GATE.ordinal()) throw new IllegalArgumentException("Family has no wood configuration");
        return WoodType.nativeView(word(DATA.blocks, binding(properties, expected) + 1));
    }

    public static int parameter(BlockBehaviour.Properties properties, Kind expected) {
        if (expected != Kind.BUTTON && expected != Kind.WEIGHTED_PLATE) throw new IllegalArgumentException("Family has no numeric parameter");
        return word(DATA.blocks, binding(properties, expected) + 2);
    }

    private static int word(MemorySegment data, int index) { return data.getAtIndex(ValueLayout.JAVA_INT, index); }
    private static MemorySegment buffer(int kind, int expected, int size, Arena arena) throws Throwable {
        var length = arena.allocate(ValueLayout.JAVA_INT);
        MemorySegment address = (MemorySegment) BUFFER.invokeExact(kind, length);
        int count = length.get(ValueLayout.JAVA_INT, 0);
        if (address.address() == 0 || count <= 0 || count > 65535 * 15 || (expected >= 0 && count != expected))
            throw new IllegalStateException("Native family buffer mismatch: " + kind);
        return address.reinterpret((long) count * size, Arena.global(), null).asReadOnly();
    }
    private static String[] names(MemorySegment rows, int stride, int count, MemorySegment strings) {
        String[] result = new String[count];
        var unique = new HashSet<String>();
        for (int id = 0; id < count; id++) {
            int start = word(rows, id * stride), size = word(rows, id * stride + 1);
            if (start < 0 || size <= 0 || (long) start + size > strings.byteSize()) throw new IllegalStateException("Invalid native family name");
            result[id] = new String(strings.asSlice(start, size).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
            if (!unique.add(result[id])) throw new IllegalStateException("Duplicate native family name");
        }
        return result;
    }
    private static void index(int value, int count) {
        if (value < 0 || value >= count) throw new IllegalStateException("Invalid native family reference");
    }
    private static Data load() {
        try (Arena arena = Arena.ofConfined()) {
            var header = buffer(0, 4, Integer.BYTES, arena);
            int sets = word(header, 1), woods = word(header, 2), bytes = word(header, 3);
            if (word(header, 0) != 1 || sets <= 0 || sets > 256 || woods <= 0 || woods > 256 || bytes <= 0 || bytes > 65535)
                throw new IllegalStateException("Unsupported native family schema");
            var setRows = buffer(1, sets * 15, Integer.BYTES, arena);
            var woodRows = buffer(2, woods * 7, Integer.BYTES, arena);
            var blocks = buffer(3, -1, Integer.BYTES, arena);
            if (blocks.byteSize() % (3 * Integer.BYTES) != 0 || blocks.byteSize() / (3 * Integer.BYTES) > 65535)
                throw new IllegalStateException("Invalid native family block count");
            var strings = buffer(4, bytes, 1, arena);
            for (int id = 0; id < sets; id++) {
                int at = id * 15;
                for (int offset = 2; offset <= 5; offset++) index(word(setRows, at + offset), 2);
                index(word(setRows, at + 6), NativeSoundDefinitions.typeCount());
                for (int offset = 7; offset < 15; offset++) index(word(setRows, at + offset), NativeSoundDefinitions.eventCount());
            }
            for (int id = 0; id < woods; id++) {
                int at = id * 7;
                index(word(woodRows, at + 2), sets);
                index(word(woodRows, at + 3), NativeSoundDefinitions.typeCount());
                index(word(woodRows, at + 4), NativeSoundDefinitions.typeCount());
                index(word(woodRows, at + 5), NativeSoundDefinitions.eventCount());
                index(word(woodRows, at + 6), NativeSoundDefinitions.eventCount());
            }
            int kindCount = Kind.values().length;
            for (int at = 0; at < blocks.byteSize() / Integer.BYTES; at += 3) {
                int kind = word(blocks, at), type = word(blocks, at + 1), parameter = word(blocks, at + 2);
                index(kind, kindCount);
                index(type, kind == 0 ? 1 : kind <= 5 ? sets : woods);
                if (kind == 3 || kind == 5) {
                    if (parameter < 1 || parameter > 1024) throw new IllegalStateException("Invalid native family parameter");
                } else if (parameter != 0) throw new IllegalStateException("Unexpected native family parameter");
            }
            return new Data(names(setRows, 15, sets, strings), names(woodRows, 7, woods, strings), setRows, woodRows, blocks);
        } catch (RuntimeException | Error e) { throw e; }
          catch (Throwable e) { throw new IllegalStateException("Cannot load native block families", e); }
    }
}
