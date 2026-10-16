package net.minecraft.server.level;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.Reference;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Models the legal heap-published/native-uncommitted prefix with the actual
 * native reserve/commit operations. No production pause hook or raw memory write. */
class NativeStatusPublicationTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static final class Foreign extends ChunkStatus {
        Foreign() { super(ChunkStatus.EMPTY, EnumSet.noneOf(Heightmap.Types.class), ChunkType.PROTOCHUNK); }
        @Override public String getName() { return "test:opaque_status"; }
        @Override public int getIndex() { throw new AssertionError("publication must not query foreign index"); }
    }

    static MethodHandle binding(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true);
        return (MethodHandle)field.get(null);
    }

    static void publicationPrefix(int kind, boolean retainOld) throws Throwable {
        if (kind == 1) { allowedPublicationPrefix(retainOld); return; }
        Foreign a = new Foreign(), b = new Foreign();
        Object owner;
        Supplier<ChunkStatus> read;
        if (kind == 0) {
            var status = new NativeChunkGeneration.Status(); status.set(a);
            owner = status; read = status::get;
        } else {
            var holder = new NativeChunkGeneration.Holder();
            if (kind == 1) { holder.allowed(a); read = holder::allowed; }
            else { assertNull(holder.bump(null, a)); read = holder::started; }
            owner = holder;
        }
        Class<?> rowType = Class.forName("net.minecraft.server.level.NativeChunkGeneration$StatusReference");
        String name = kind == 0 ? "reference" : kind == 1 ? "allowedReference" : "startedReference";
        VarHandle root = MethodHandles.privateLookupIn(owner.getClass(), MethodHandles.lookup()).findVarHandle(owner.getClass(), name, rowType);
        var constructor = rowType.getDeclaredConstructor(long.class, ChunkStatus.class, rowType); constructor.setAccessible(true);
        var idField = owner.getClass().getDeclaredField("id"); idField.setAccessible(true);
        long id = idField.getLong(owner);
        int slot = kind == 1 ? 0 : 1;
        Class<?> bindings = kind == 0 ? Class.forName("net.minecraft.server.level.NativeChunkGeneration$StatusBindings") : NativeChunkGeneration.class;
        MethodHandle reserve = binding(bindings, kind == 0 ? "RESERVE" : "STATUS_RESERVE");
        MethodHandle commit = binding(bindings, kind == 0 ? "SET" : "STATUS_CHANGE");
        long token = kind == 0 ? (long)reserve.invokeExact(id, 65535) : (long)reserve.invokeExact(id, slot, 65535);
        assertNotEquals(0L, token);
        Object previous = root.getVolatile(owner);
        Object settled = constructor.newInstance(token, b, null);
        Object pending = constructor.newInstance(token, b, retainOld ? previous : null);
        root.setVolatile(owner, pending);
        CountDownLatch entered = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> { entered.countDown(); return read.get(); });
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                System.gc();
                if (retainOld) assertSame(a, future.get(2, TimeUnit.SECONDS), "a paused producer must leave the old published identity readable");
                else assertThrows(TimeoutException.class, () -> future.get(150, TimeUnit.MILLISECONDS),
                    "negative control: discarding the old root incorrectly makes its reader wait for producer progress");
            } finally {
                // Also releases a negative-control reader before closing its executor.
                if (kind == 0) commit.invokeExact(id, token);
                else assertEquals(1, (int)commit.invokeExact(id, slot, 0L, token, 0));
                Reference.reachabilityFence(owner);
            }
            if (!retainOld) assertSame(b, future.get(2, TimeUnit.SECONDS));
            assertSame(b, read.get(), "new word resolves the pending new leaf before settlement");
            root.setVolatile(owner, settled);
            assertSame(b, read.get());
        } finally { Reference.reachabilityFence(owner); }
    }

    /** Model the actual native-selected double binding. The negative retires
     * the still-published old location before native commit, forcing progress. */
    static void allowedPublicationPrefix(boolean retainOld) throws Throwable {
        Foreign a = new Foreign(), b = new Foreign();
        var holder = new NativeChunkGeneration.Holder(); holder.allowed(a);
        var refsField = holder.getClass().getDeclaredField("references"); refsField.setAccessible(true);
        var tagsField = holder.getClass().getDeclaredField("publicationTags"); tagsField.setAccessible(true);
        Object[] roots = (Object[])refsField.get(holder);
        long[] tags = (long[])tagsField.get(holder);
        VarHandle root = MethodHandles.arrayElementVarHandle(Object[].class);
        VarHandle tag = MethodHandles.arrayElementVarHandle(long[].class);
        int full = ChunkStatus.getStatusList().size() - 1;
        int statuses = roots.length - 2;
        assertEquals(tags.length, statuses, "the two allowed-status leaves follow every native-selected binding location");
        int oldRow = tags[full * 2] != 0 ? full * 2 : full * 2 + 1;
        long before = (long)tag.getAcquire(tags, oldRow);
        assertNotEquals(0L, before);
        var idField = holder.getClass().getDeclaredField("id"); idField.setAccessible(true);
        long id = idField.getLong(holder);
        MethodHandle reserve = binding(NativeChunkGeneration.class, "ALLOWED_RESERVE");
        MethodHandle commit = binding(NativeChunkGeneration.class, "FUTURE_PUBLISH");
        long token = (long)reserve.invokeExact(id, 65535);
        assertNotEquals(0L, token);
        int nextRow = full * 2 + (int)((token >>> 3) & 1);
        assertNotEquals(oldRow, nextRow);
        root.setRelease(roots, nextRow, null);
        root.setRelease(roots, statuses + (nextRow & 1), b);
        tag.setRelease(tags, nextRow, token);
        if (!retainOld) {
            tag.setRelease(tags, oldRow, 0L);
            root.setRelease(roots, oldRow, null);
            root.setRelease(roots, statuses + (oldRow & 1), null);
        }
        CountDownLatch entered = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> { entered.countDown(); return holder.allowed(); });
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                System.gc();
                if (retainOld) assertSame(a, future.get(2, TimeUnit.SECONDS), "a paused producer must leave the old published identity readable");
                else assertThrows(TimeoutException.class, () -> future.get(150, TimeUnit.MILLISECONDS),
                    "negative control: discarding the old root incorrectly makes its reader wait for producer progress");
            } finally {
                assertEquals(1, (int)commit.invokeExact(id, full, before, token));
                tag.setRelease(tags, oldRow, 0L);
                root.setRelease(roots, oldRow, null);
                root.setRelease(roots, statuses + (oldRow & 1), null);
                Reference.reachabilityFence(holder);
            }
            if (!retainOld) assertSame(b, future.get(2, TimeUnit.SECONDS));
            assertSame(b, holder.allowed(), "new word resolves the prepared new opaque status");
            assertNull(holder.future(full), "the status publication retains actual null future membership");
        } finally { Reference.reachabilityFence(holder); }
    }

    @Test void pausedPublicationKeepsOldAndPendingLeavesReadable() throws Throwable {
        for (int kind = 0; kind < 3; kind++) publicationPrefix(kind, true);
    }

    @Test void missingOldLeafNegativeControlRequiresProducerProgress() throws Throwable {
        for (int kind = 0; kind < 3; kind++) publicationPrefix(kind, false);
    }
}
