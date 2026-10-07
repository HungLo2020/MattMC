package net.minecraft.client.dev;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphicsFrameBenchmarkReadinessTest {
    @Test void successfulReadinessRetiresAnExpiredProducerDeadline() throws Exception {
        try (var state = new SavedState()) {
            long expired = System.nanoTime() - field("READINESS_TIMEOUT_NANOS").getLong(null) - 1;
            field("producerWorkloadStartNanos").setLong(null, expired);
            field("producerWorkloadWaitFrames").setLong(null, 17);
            var ready = GraphicsFrameBenchmark.class.getDeclaredMethod("producerWorkloadReady", Minecraft.class);
            ready.setAccessible(true);
            assertEquals(true, ready.invoke(null, new Object[] {null}));
            assertEquals(-1L, field("producerWorkloadStartNanos").getLong(null),
                "A later transient producer wait must get its own timeout period");
            assertEquals(17L, field("producerWorkloadWaitFrames").getLong(null),
                "Cumulative wait diagnostics must survive successful readiness");
        }
    }

    @Test void measurementRestartDiscardsSamplesAndStartsANewProducerWait() throws Exception {
        try (var state = new SavedState()) {
            field("producerWorkloadStartNanos").setLong(null, 1L);
            field("producerWorkloadWaitFrames").setLong(null, 29L);
            field("settledFrameIndex").setLong(null, 100L);
            @SuppressWarnings("unchecked")
            var samples = (Collection<Long>) field("FRAME_NANOS").get(null);
            samples.add(1000L);
            var restart = GraphicsFrameBenchmark.class.getDeclaredMethod("restartMeasurementAfterReadinessLoss");
            restart.setAccessible(true);
            restart.invoke(null);
            assertTrue(samples.isEmpty());
            assertEquals(-1L, field("settledFrameIndex").getLong(null));
            assertEquals(-1L, field("producerWorkloadStartNanos").getLong(null),
                "The new readiness cycle must not inherit the previous wait deadline");
            assertEquals(29L, field("producerWorkloadWaitFrames").getLong(null));
        }
    }

    private static Field field(String name) throws Exception {
        var field = GraphicsFrameBenchmark.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** Restore the actual benchmark's mutable state without constructing a client. */
    private static final class SavedState implements AutoCloseable {
        private final Map<Field, Object> values = new LinkedHashMap<>();
        private final Map<Collection<Object>, ArrayList<Object>> collections = new java.util.IdentityHashMap<>();
        private final Map<Map<Object, Object>, Map<Object, Object>> maps = new java.util.IdentityHashMap<>();

        @SuppressWarnings("unchecked")
        SavedState() throws Exception {
            for (var field : GraphicsFrameBenchmark.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                var value = field.get(null);
                if (!Modifier.isFinal(field.getModifiers())) values.put(field, value);
                // Immutable constants (e.g. the GC bean list) need no restore.
                if (value instanceof Collection<?> collection && !immutable(collection)) {
                    collections.put((Collection<Object>) collection, new ArrayList<>(collection));
                } else if (value instanceof Map<?, ?> map) {
                    maps.put((Map<Object, Object>) map, new LinkedHashMap<>((Map<Object, Object>) map));
                }
            }
        }

        private static boolean immutable(Collection<?> collection) {
            return collection.getClass().getName().startsWith("java.util.ImmutableCollections");
        }

        @Override public void close() throws Exception {
            for (var entry : values.entrySet()) entry.getKey().set(null, entry.getValue());
            for (var entry : collections.entrySet()) {
                entry.getKey().clear();
                entry.getKey().addAll(entry.getValue());
            }
            for (var entry : maps.entrySet()) {
                entry.getKey().clear();
                entry.getKey().putAll(entry.getValue());
            }
        }
    }
}
