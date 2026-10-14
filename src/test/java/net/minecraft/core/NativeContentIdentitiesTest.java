package net.minecraft.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.EntityType;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Data component types, entity data serializers and every synchronized data
 * field, projected from Rust, against Frozen's recorded identities
 * (DevUtils/tests/content/ContentReference.java, same dump format).
 */
class NativeContentIdentitiesTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static Object read(Object owner, String name) throws ReflectiveOperationException {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException e) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    static void componentTypes(List<String> out) throws ReflectiveOperationException {
        for (var type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            int id = BuiltInRegistries.DATA_COMPONENT_TYPE.getId(type);
            var key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            boolean persistent = !type.isTransient();
            boolean cached = persistent && type.codec().getClass().getName().contains("EncoderCache");
            out.add("component_type " + id + " " + key + " persistent=" + persistent + " cached=" + cached
                + " stream=" + (type.streamCodec() != null));
        }
    }

    static Map<EntityDataSerializer<?>, String> serializerNames() throws ReflectiveOperationException {
        var names = new java.util.IdentityHashMap<EntityDataSerializer<?>, String>();
        for (Field field : EntityDataSerializers.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && EntityDataSerializer.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                names.put((EntityDataSerializer<?>) field.get(null), field.getName().toLowerCase());
            }
        }
        return names;
    }

    static void serializers(List<String> out) throws ReflectiveOperationException {
        var names = serializerNames();
        for (int id = 0; ; id++) {
            var serializer = EntityDataSerializers.getSerializer(id);
            if (serializer == null) break;
            out.add("serializer " + id + " " + names.get(serializer));
        }
    }

    /** Every class of a registered entity type, and its superclasses, with their accessor fields. */
    static void synchedData(List<String> out) throws ReflectiveOperationException {
        var names = serializerNames();
        var classes = new java.util.TreeSet<String>();
        var byName = new java.util.HashMap<String, Class<?>>();
        for (Field field : EntityType.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != EntityType.class) continue;
            if (!(field.getGenericType() instanceof ParameterizedType generic)) continue;
            if (!(generic.getActualTypeArguments()[0] instanceof Class<?> entity)) continue;
            for (Class<?> type = entity; type != null && type != Object.class; type = type.getSuperclass()) {
                classes.add(type.getName()); byName.put(type.getName(), type);
            }
        }
        for (String name : classes) {
            Class<?> type = byName.get(name);
            var fields = new TreeMap<Integer, String>();
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != EntityDataAccessor.class) continue;
                field.setAccessible(true);
                var accessor = (EntityDataAccessor<?>) field.get(null);
                String previous = fields.put(accessor.id(), field.getName() + " " + names.get(accessor.serializer()));
                if (previous != null) throw new IllegalStateException("Duplicate synched data id in " + name);
            }
            if (fields.isEmpty()) continue;
            // The nearest superclass that defines fields: IDs continue after its last.
            String base = "none";
            for (Class<?> parent = type.getSuperclass(); parent != null && parent != Object.class; parent = parent.getSuperclass()) {
                if (definesAccessors(parent)) { base = parent.getName(); break; }
            }
            for (var entry : fields.entrySet())
                out.add("synched " + name + " base=" + base + " " + entry.getKey() + " " + entry.getValue());
        }
    }

    static boolean definesAccessors(Class<?> type) {
        for (Field field : type.getDeclaredFields())
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == EntityDataAccessor.class) return true;
        return false;
    }


    @Test void identitiesMatchFrozen() throws Exception {
        String expected;
        try (InputStream in = new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/content/frozen-content-identities.txt.gz")))) {
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var rows = new ArrayList<String>();
        componentTypes(rows);
        serializers(rows);
        synchedData(rows);
        var expectedRows = expected.split("\n");
        assertEquals(expectedRows.length, rows.size());
        for (int i = 0; i < expectedRows.length; i++) assertEquals(expectedRows[i], rows.get(i), "row " + i);
        assertEquals(NativeContentIdentities.componentTypeCount(), BuiltInRegistries.DATA_COMPONENT_TYPE.size());
    }

    @Test void undeclaredSynchronizedDataIsRejected() {
        assertThrows(IllegalStateException.class, () -> NativeContentIdentities.defineSynched(String.class, "int"));
        assertThrows(IllegalStateException.class, () -> NativeContentIdentities.componentType(0, "max_damage"));
    }
}
