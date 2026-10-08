package net.minecraft.world.level.block.state.properties;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.StringRepresentable;

/** Startup-only CPU projections. Rust selects the names, domains and order;
 * Java classes only bind native values to temporary gameplay/codec objects. */
public final class NativePropertyDefinitions {
    private static final MethodHandle BUFFER = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_property_definitions_buffer",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final Map<String, Definition> DEFINITIONS = load();
    private static final Property<?>[] VIEWS = new Property<?>[DEFINITIONS.size()];

    public static synchronized Property<?> view(int id) {
        if (id < 0 || id >= VIEWS.length || VIEWS[id] == null) throw new IllegalStateException("Native property view not initialized: " + id);
        return VIEWS[id];
    }

    private static synchronized <P extends Property<?>> P remember(P property) {
        int id = property.nativeDefinitionId();
        if (VIEWS[id] != null) throw new IllegalStateException("Native property projected twice: " + id);
        VIEWS[id] = property;
        return property;
    }

    record Definition(int id, String name, int kind, List<String> values) {}

    private static Definition require(String key, int kind) {
        Definition d = DEFINITIONS.get(key);
        if (d == null || d.kind() != kind) throw new IllegalStateException("Missing/mismatched native property: " + key);
        return d;
    }

    public static BooleanProperty booleanProperty(String key) {
        Definition d = require(key, 0);
        if (!d.values().equals(List.of("true", "false"))) throw new IllegalStateException("Invalid native boolean domain: " + key);
        return remember(new BooleanProperty(d.name(), d.id()));
    }

    public static IntegerProperty integerProperty(String key) {
        Definition d = require(key, 1);
        int[] values = d.values().stream().mapToInt(Integer::parseInt).toArray();
        return remember(new IntegerProperty(d.name(), values, d.id()));
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> enumProperty(String key, Class<T> type) {
        Definition d = require(key, 2);
        Map<String, T> available = new HashMap<>();
        for (T value : type.getEnumConstants()) {
            if (available.put(value.getSerializedName(), value) != null) throw new IllegalStateException("Ambiguous enum binding: " + key);
        }
        List<T> values = new ArrayList<>(d.values().size());
        for (String name : d.values()) {
            T value = available.get(name);
            if (value == null) throw new IllegalStateException("Missing enum binding: " + key + "/" + name);
            values.add(value);
        }
        return remember(new EnumProperty<>(d.name(), type, values, d.id()));
    }

    private static MemorySegment buffer(int kind, int expected, int elementBytes, Arena inputs) throws Throwable {
        MemorySegment length = inputs.allocate(ValueLayout.JAVA_INT);
        MemorySegment pointer = (MemorySegment) BUFFER.invokeExact(kind, length);
        if (pointer.address() == 0 || length.get(ValueLayout.JAVA_INT, 0) != expected) throw new IllegalStateException("Native property buffer mismatch: " + kind);
        return pointer.reinterpret((long) expected * elementBytes, Arena.global(), null).asReadOnly();
    }
    private static int word(MemorySegment table, int index) { return table.getAtIndex(ValueLayout.JAVA_INT, index); }
    private static String string(MemorySegment text, MemorySegment ranges, int index) {
        int start = word(ranges, index), length = word(ranges, index + 1);
        if (start < 0 || length <= 0 || (long) start + length > text.byteSize()) throw new IllegalStateException("Invalid native property string");
        return new String(text.asSlice(start, length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
    }
    private static Map<String, Definition> load() {
        try (Arena inputs = Arena.ofConfined()) {
            MemorySegment header = buffer(0, 6, Integer.BYTES, inputs);
            int count = word(header, 1), valueCount = word(header, 3), textBytes = word(header, 5);
            if (word(header, 0) != 1 || word(header, 2) != 7 || word(header, 4) != 2
                || count <= 0 || count > 65535 || valueCount <= 0 || valueCount > 1048576 || textBytes <= 0 || textBytes > 16777216) {
                throw new IllegalStateException("Unsupported native property schema");
            }
            MemorySegment rows = buffer(1, count * 7, Integer.BYTES, inputs);
            MemorySegment values = buffer(2, valueCount * 2, Integer.BYTES, inputs);
            MemorySegment text = buffer(3, textBytes, 1, inputs);
            Map<String, Definition> result = new HashMap<>();
            int nextValue = 0;
            for (int id = 0; id < count; id++) {
                int base = id * 7, kind = word(rows, base + 4), start = word(rows, base + 5), size = word(rows, base + 6);
                if (kind < 0 || kind > 2 || start != nextValue || size <= 0 || size > 65535 || (long) start + size > valueCount) {
                    throw new IllegalStateException("Invalid native property row: " + id);
                }
                List<String> names = new ArrayList<>(size);
                for (int i = start; i < start + size; i++) names.add(string(text, values, i * 2));
                if (new HashSet<>(names).size() != size) throw new IllegalStateException("Duplicate native property values");
                String key = string(text, rows, base);
                if (result.put(key, new Definition(id, string(text, rows, base + 2), kind, List.copyOf(names))) != null) {
                    throw new IllegalStateException("Duplicate native property key: " + key);
                }
                nextValue += size;
            }
            if (nextValue != valueCount) throw new IllegalStateException("Incomplete native property domains");
            return Map.copyOf(result);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot load Rust property definitions", error);
        }
    }
}
