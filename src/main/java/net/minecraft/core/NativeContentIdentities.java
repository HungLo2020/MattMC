package net.minecraft.core;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.NativeLibraryLoader;

/**
 * Rust-owned content identities (`content/component`, `content/entity`):
 * data component types, entity data serializers and every entity class's
 * synchronized data fields. Table 3 holds item definitions; see
 * {@code net.minecraft.world.item.NativeItems}. Java registries and accessors are checked
 * projections of these tables.
 */
public final class NativeContentIdentities {
    private NativeContentIdentities() {}

    public record ComponentType(String name, boolean persistent, boolean cached) {}
    /** An entity class's synchronized fields: IDs firstId .. firstId + serializers.size() - 1. */
    public record SynchedClass(String className, String base, int firstId, List<String> serializers) {}

    private static final MethodHandle EXPORT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_content_identity_export",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

    private static final class Tables {
        static final List<ComponentType> COMPONENT_TYPES = componentTypes();
        static final List<String> SERIALIZERS = table(1);
        static final Map<String, SynchedClass> SYNCHED = synched();
    }

    /** One Rust content table's lines: see {@code mattmc_content_identity_export}. */
    public static List<String> table(int table) {
        try (var arena = Arena.ofConfined()) {
            long size = (long) EXPORT.invokeExact(table, MemorySegment.NULL, 0L);
            if (size < 0) throw new IllegalStateException("Unknown Rust content table " + table);
            var text = arena.allocate(Math.max(1, size));
            if ((long) EXPORT.invokeExact(table, text, size) != size) throw new IllegalStateException("Rust content table changed size");
            // A direct-buffer copy: heap segments would pollute hot segment-access profiles.
            byte[] bytes = new byte[(int) size];
            text.asByteBuffer().get(bytes);
            String all = new String(bytes, StandardCharsets.UTF_8);
            return all.isEmpty() ? List.of() : List.of(all.split("\n"));
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot read Rust content table " + table, e); }
    }

    private static List<ComponentType> componentTypes() {
        var types = new ArrayList<ComponentType>();
        for (String line : table(0)) {
            String[] parts = line.split(" ");
            types.add(new ComponentType(parts[0], parts[1].equals("1"), parts[2].equals("1")));
        }
        return List.copyOf(types);
    }

    private static Map<String, SynchedClass> synched() {
        var classes = new HashMap<String, SynchedClass>();
        for (String line : table(2)) {
            String[] parts = line.split(" ");
            var serializers = List.of(parts).subList(3, parts.length);
            classes.put(parts[0], new SynchedClass(parts[0], parts[1].equals("-") ? null : parts[1], Integer.parseInt(parts[2]), List.copyOf(serializers)));
        }
        return Map.copyOf(classes);
    }

    /** The component type registered at {@code id}; it must be the type named {@code name}. */
    public static ComponentType componentType(int id, String name) {
        var types = Tables.COMPONENT_TYPES;
        if (id < 0 || id >= types.size() || !types.get(id).name().equals(name)) {
            throw new IllegalStateException("Data component type " + name + " registered at ID " + id + " differs from the Rust catalog");
        }
        return types.get(id);
    }

    public static int componentTypeCount() { return Tables.COMPONENT_TYPES.size(); }

    /** Entity data serializer names, by network ID. */
    public static List<String> serializers() { return Tables.SERIALIZERS; }

    private static final Map<Class<?>, Integer> DEFINED = new ConcurrentHashMap<>();

    /** The nearest class from {@code type} upwards that defines synchronized fields, or null. */
    private static SynchedClass nearest(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            var found = Tables.SYNCHED.get(c.getName());
            if (found != null) return found;
        }
        return null;
    }

    /**
     * The ID of {@code holder}'s next synchronized field, which must use
     * {@code serializer}. Fields are defined in the order the catalog lists them.
     */
    public static synchronized int defineSynched(Class<?> holder, String serializer) {
        var definition = Tables.SYNCHED.get(holder.getName());
        if (definition == null) throw new IllegalStateException("No Rust synchronized data definition for " + holder.getName());
        var base = holder.getSuperclass() == null ? null : nearest(holder.getSuperclass());
        String actualBase = base == null ? null : base.className();
        if (!java.util.Objects.equals(actualBase, definition.base())) {
            throw new IllegalStateException("Synchronized data of " + holder.getName() + " continues after " + actualBase
                + ", but the Rust catalog says " + definition.base());
        }
        int index = DEFINED.merge(holder, 1, Integer::sum) - 1;
        if (index >= definition.serializers().size() || !definition.serializers().get(index).equals(serializer)) {
            throw new IllegalStateException("Synchronized field " + index + " of " + holder.getName() + " uses " + serializer
                + ", but the Rust catalog says " + (index < definition.serializers().size() ? definition.serializers().get(index) : "nothing"));
        }
        return definition.firstId() + index;
    }

    /** How many synchronized fields an instance of {@code holder} has. */
    public static int synchedCount(Class<?> holder) {
        var definition = nearest(holder);
        return definition == null ? 0 : definition.firstId() + definition.serializers().size();
    }
}
