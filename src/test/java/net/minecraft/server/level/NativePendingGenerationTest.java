package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The actual JDK ArrayList is the oracle for callback mutation/throw timing. */
class NativePendingGenerationTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    interface Pending {
        void add(ChunkGenerationTask task);
        void clear();
        void forEach(Consumer<ChunkGenerationTask> action);
    }
    static Pending javaList() {
        var list = new ArrayList<ChunkGenerationTask>();
        return new Pending() {
            public void add(ChunkGenerationTask task) { list.add(task); }
            public void clear() { list.clear(); }
            public void forEach(Consumer<ChunkGenerationTask> action) { list.forEach(action); }
        };
    }
    static Pending nativeList() {
        var list = new NativeChunkGeneration.Pending();
        return new Pending() {
            public void add(ChunkGenerationTask task) { list.add(task); }
            public void clear() { list.clear(); }
            public void forEach(Consumer<ChunkGenerationTask> action) { list.forEach(action); }
        };
    }
    static List<String> scenario(Pending list, int mode) {
        var a = mock(ChunkGenerationTask.class);
        var b = mock(ChunkGenerationTask.class);
        var c = mock(ChunkGenerationTask.class);
        List<String> trace = new ArrayList<>();
        list.add(a); list.add(b);
        RuntimeException failure = new RuntimeException("callback");
        Consumer<ChunkGenerationTask> identify = task -> trace.add(task == a ? "a" : task == b ? "b" : task == c ? "c" : "null");
        try {
            list.forEach(task -> {
                identify.accept(task);
                if (mode == 0 && task == a || mode == 1 && task == b) list.add(c);
                if (mode == 2 && task == a) throw failure;
                if (mode == 3 && task == a) {
                    list.clear(); list.add(c); throw failure;
                }
                if (mode == 4 && task == a) list.forEach(identify);
                if (mode == 5 && task == a) {
                    list.clear(); list.add(c);
                    assertSame(a, task, "retiring a row cannot invalidate the callback's strong reference");
                }
            });
            trace.add("normal");
        } catch (RuntimeException error) {
            if (mode == 2 || mode == 3) assertSame(failure, error);
            trace.add(error.getClass().getName());
        }
        trace.add("retry");
        list.forEach(identify);
        list.clear();
        list.add(null);
        list.forEach(identify);
        list.clear();
        list.forEach(task -> fail("clear left an entry"));
        return trace;
    }
    @Test void nativeLivePassMatchesJdkAppendThrowNestedAndRetirementSemantics() {
        for (int mode = 0; mode < 6; mode++) assertEquals(scenario(javaList(), mode), scenario(nativeList(), mode), "mode " + mode);
    }
    @Test void nativeRootsGrowAndRetireWithoutChangingOrder() {
        Pending list = nativeList();
        List<ChunkGenerationTask> expected = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            for (int index = 0; index < 97; index++) { var task = mock(ChunkGenerationTask.class); expected.add(task); list.add(task); }
            List<ChunkGenerationTask> actual = new ArrayList<>();
            list.forEach(actual::add);
            assertEquals(expected, actual);
            list.clear(); expected.clear();
        }
    }
    @Test void nullConsumerFailsBeforeAnyPassEffects() {
        Pending list = nativeList();
        assertThrows(NullPointerException.class, () -> list.forEach(null));
        list.add(null);
        assertThrows(NullPointerException.class, () -> list.forEach(null));
        List<ChunkGenerationTask> actual = new ArrayList<>();
        list.forEach(actual::add);
        assertEquals(1, actual.size());
        assertNull(actual.getFirst());
    }
}
