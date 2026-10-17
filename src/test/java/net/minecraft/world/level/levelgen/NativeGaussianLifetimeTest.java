package net.minecraft.world.level.levelgen;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import net.minecraft.util.NativeLibraryLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real pending source/cache consumers after VM facade and native-anchor GC. */
final class NativeGaussianLifetimeTest {
    static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_gaussian_" + name, descriptor);
    }
    static final MethodHandle START = handle("draw_new", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    static final MethodHandle NEXT = handle("draw_next", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    static final MethodHandle END = handle("draw_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle WATCH = handle("weak_new", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    static final MethodHandle COUNT = handle("weak_count", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    static final MethodHandle UNWATCH = handle("weak_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    static final MethodHandle HAS = handle("has", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));

    record Pending(long frame, long cache, int kind, long sourceWatch, long cacheWatch,
                   long sourceStrong, long cacheStrong, WeakReference<XoroshiroRandomSource> source,
                   WeakReference<MarsagliaPolarGaussian> gaussian, WeakReference<NativeXoroshiroSource> producer,
                   WeakReference<Object> cacheFacade) {
        boolean facadesCollected() {
            return source.refersTo(null) && gaussian.refersTo(null) && producer.refersTo(null) && cacheFacade.refersTo(null);
        }
    }
    static long count(long watch) throws Throwable { return (long) COUNT.invokeExact(watch); }

    static Pending pending(boolean embedded) throws Throwable {
        var source = new XoroshiroRandomSource(42);
        var gaussian = embedded ? source.nativeGaussian() : new MarsagliaPolarGaussian(source);
        var producer = XoroshiroRandomSource.nativeGaussianProducer(source);
        Object cacheFacade;
        long cache;
        int kind = embedded ? 1 : 0;
        if (embedded) {
            cacheFacade = producer;
            cache = producer.handle();
        } else {
            gaussian.nativeSet(false, 0);
            var detached = MarsagliaPolarGaussian.class.getDeclaredField("detached");
            detached.setAccessible(true);
            cacheFacade = detached.get(gaussian);
            var id = NativeGaussian.class.getDeclaredField("id");
            id.setAccessible(true);
            cache = id.getLong(cacheFacade);
        }
        long frame = 0, sourceWatch = 0, cacheWatch = 0;
        boolean complete = false;
        try {
            frame = (long) START.invokeExact(cache, kind, producer.handle());
            assertNotEquals(0, frame);
            sourceWatch = (long) WATCH.invokeExact(producer.handle(), 1);
            cacheWatch = (long) WATCH.invokeExact(cache, kind);
            assertNotEquals(0, sourceWatch); assertNotEquals(0, cacheWatch);
            long sourceStrong = count(sourceWatch), cacheStrong = count(cacheWatch);
            assertTrue(sourceStrong > 1); assertTrue(cacheStrong > 1);
            var result = new Pending(frame, cache, kind, sourceWatch, cacheWatch, sourceStrong, cacheStrong,
                new WeakReference<>(source), new WeakReference<>(gaussian), new WeakReference<>(producer), new WeakReference<>(cacheFacade));
            complete = true;
            return result;
        } finally {
            try {
                if (!complete) {
                    if (frame != 0) END.invokeExact(frame);
                    if (sourceWatch != 0) UNWATCH.invokeExact(sourceWatch);
                    if (cacheWatch != 0) UNWATCH.invokeExact(cacheWatch);
                }
            } finally {
                Reference.reachabilityFence(source); Reference.reachabilityFence(gaussian);
                Reference.reachabilityFence(producer); Reference.reachabilityFence(cacheFacade);
            }
        }
    }
    static void verify(boolean embedded) throws Throwable {
        var pending = pending(embedded);
        long frame = pending.frame;
        boolean safe = true;
        try {
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (true) {
                long source = count(pending.sourceWatch), cache = count(pending.cacheWatch);
                if (source == 0 || cache == 0) {
                    safe = false; // Fail before dereferencing a wrongly retired native owner.
                    fail("pending consumer lost its actual native source/cache");
                }
                if (pending.facadesCollected() && source == pending.sourceStrong - 1 && cache == pending.cacheStrong - 1) break;
                assertTrue(System.nanoTime() < deadline, "VM facades or actual native anchors did not retire within 5s");
                System.gc(); Thread.sleep(20);
            }
            assertEquals(1, (int) NEXT.invokeExact(frame, 0L));
            var words = MemorySegment.ofAddress(frame).reinterpret(16).asReadOnly();
            assertEquals(2L, words.getAtIndex(ValueLayout.JAVA_LONG, 0));
            double logInput = Double.longBitsToDouble(words.getAtIndex(ValueLayout.JAVA_LONG, 1));
            assertTrue(logInput > 0 && logInput < 1);
            assertEquals(embedded ? 1L : 0L, count(pending.sourceWatch));
            assertTrue(count(pending.cacheWatch) > 0);
            assertEquals(0, (int) NEXT.invokeExact(frame, Double.doubleToRawLongBits(Math.log(logInput))));
            // Observed identically on original Frozen and complete pre-f5, seed42.
            assertEquals(4609290630594566862L, words.getAtIndex(ValueLayout.JAVA_LONG, 1));
            assertEquals(1, (int) HAS.invokeExact(pending.cache, pending.kind));
            END.invokeExact(frame); frame = 0;
            assertEquals(0, count(pending.sourceWatch)); assertEquals(0, count(pending.cacheWatch));
        } finally {
            try { if (frame != 0 && safe) END.invokeExact(frame); }
            finally { UNWATCH.invokeExact(pending.sourceWatch); UNWATCH.invokeExact(pending.cacheWatch); }
        }
    }
    @Test void actualEmbeddedCacheOwnsItsSourceThroughTheResultCommitAfterVmGc() throws Throwable { verify(true); }
    @Test void actualDetachedCacheOutlivesVmGcAndItsIndependentSourceLastDraw() throws Throwable { verify(false); }
}
