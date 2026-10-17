package net.minecraft.world.entity;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.phys.Vec3;

/**
 * The Rust entity type catalog (`content/entity`), read once. Every registered
 * {@link EntityType} is built from its definition; Java declares only the
 * behavior factory.
 */
final class NativeEntityTypes {
    private NativeEntityTypes() {}

    record Definition(String name, MobCategory category, boolean serialize, boolean summon, boolean fireImmune,
        boolean spawnFar, boolean peaceful, boolean lootTable, int trackingRange, int updateInterval,
        float width, float height, float eyeHeight, float spawnScale, List<String> requiredFeatures,
        List<String> immuneTo, Map<EntityAttachment, List<Vec3>> attachments) {}

    private static final MethodHandle EXPORT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_entity_types_export",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final List<Definition> DEFINITIONS = load();

    /** The definition registered at {@code id}; it must be the type named {@code name}. */
    static Definition definition(int id, String name) {
        if (id < 0 || id >= DEFINITIONS.size() || !DEFINITIONS.get(id).name().equals(name)) {
            throw new IllegalStateException("Entity type " + name + " registered at ID " + id + " differs from the Rust catalog ("
                + (id >= 0 && id < DEFINITIONS.size() ? DEFINITIONS.get(id).name() : "no entry") + ")");
        }
        return DEFINITIONS.get(id);
    }

    static int count() { return DEFINITIONS.size(); }

    private static List<Definition> load() {
        try (var arena = Arena.ofConfined()) {
            long size = (long) EXPORT.invokeExact(MemorySegment.NULL, 0L);
            if (size <= 12) throw new IllegalStateException("Invalid Rust entity type table");
            var table = arena.allocate(size);
            if ((long) EXPORT.invokeExact(table, size) != size) throw new IllegalStateException("Rust entity type table changed size");
            return parse(table.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN));
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot read the Rust entity type table", e); }
    }

    private static String text(ByteBuffer in) {
        byte[] bytes = new byte[Short.toUnsignedInt(in.getShort())];
        in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<String> texts(ByteBuffer in) {
        int count = Byte.toUnsignedInt(in.get());
        var values = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) values.add(text(in));
        return List.copyOf(values);
    }

    private static List<Definition> parse(ByteBuffer in) {
        if (in.getInt() != 0x4d455459 || in.getInt() != 1) throw new IllegalStateException("Unsupported Rust entity type table");
        int count = in.getInt();
        var categories = new java.util.HashMap<String, MobCategory>();
        for (MobCategory category : MobCategory.values()) categories.put(category.getSerializedName(), category);
        var definitions = new ArrayList<Definition>(count);
        for (int i = 0; i < count; i++) {
            String name = text(in);
            MobCategory category = categories.get(text(in));
            if (category == null) throw new IllegalStateException("Unknown mob category for " + name);
            int flags = Byte.toUnsignedInt(in.get());
            int tracking = in.getInt(), interval = in.getInt();
            float width = in.getFloat(), height = in.getFloat(), eye = in.getFloat(), spawnScale = in.getFloat();
            List<String> features = texts(in);
            List<String> immune = texts(in);
            var attachments = new EnumMap<EntityAttachment, List<Vec3>>(EntityAttachment.class);
            for (EntityAttachment kind : EntityAttachment.values()) {
                int points = Short.toUnsignedInt(in.getShort());
                var list = new ArrayList<Vec3>(points);
                for (int p = 0; p < points; p++) list.add(new Vec3(in.getDouble(), in.getDouble(), in.getDouble()));
                attachments.put(kind, List.copyOf(list));
            }
            definitions.add(new Definition(name, category, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0,
                (flags & 8) != 0, (flags & 16) != 0, (flags & 32) != 0, tracking, interval, width, height, eye,
                spawnScale, features, immune, attachments));
        }
        if (in.hasRemaining()) throw new IllegalStateException("Trailing data in the Rust entity type table");
        return List.copyOf(definitions);
    }
}
