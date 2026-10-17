package net.minecraft.server.level;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Actual public producers, controlled original transport, escaped futures and weak captures. */
public final class ThreadedRequestLifetimes {
    static MethodHandle nativeLive;
    record Queued(ThreadedRequestFailures.Fixture fixture, CompletableFuture<ChunkAccess> future,
                  WeakReference<ThreadedRequestFailures.ProbeChunk> chunk) {}

    static long live(int which) throws Throwable {
        return nativeLive == null ? 0 : (long)nativeLive.invokeExact(which);
    }

    static Queued queue(boolean initialize, int rejection) throws Exception {
        var f = new ThreadedRequestFailures.Fixture();
        var chunk = new ThreadedRequestFailures.ProbeChunk();
        f.map.failAt = rejection;
        CompletableFuture<ChunkAccess> future = null;
        try {
            future = initialize ? f.engine.initializeLight(chunk, true) : f.engine.lightChunk(chunk, true);
            if (rejection != 0) throw new AssertionError("missing submission rejection");
            if (!future.cancel(false)) throw new AssertionError("future cancellation failed");
        } catch (RejectedExecutionException expected) {
            if (rejection == 0) throw expected;
        }
        return new Queued(f, future, new WeakReference<>(chunk));
    }

    static boolean awaitChunkRelease(Queued queued) throws InterruptedException {
        for (int i=0; i<200; i++) {
            System.gc();
            if (queued.chunk.get() == null) return true;
            Thread.sleep(10);
        }
        return false;
    }

    static boolean awaitRelease(Queued queued, long owners, long frames) throws Throwable {
        for (int i=0; i<200; i++) {
            System.gc();
            if (queued.chunk.get() == null && live(0) <= owners && live(1) <= frames) return true;
            Thread.sleep(10);
        }
        return false;
    }

    public static void main(String[] args) throws Throwable {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();VerifyThreadedLighting.pinClasses();
        if (Boolean.getBoolean("threadedLight.nativeRequests")) {
            // Reflection keeps these exact observer bytes portable to unchanged Frozen.
            var loader = Class.forName("net.minecraft.util.NativeLibraryLoader");
            nativeLive = (MethodHandle)loader.getMethod("downcallHandle", String.class, String.class, FunctionDescriptor.class)
                .invoke(null, "mattmc_rust", "mattmc_light_request_live", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        }
        var events = new ArrayList<String>();
        for (boolean initialize : new boolean[]{true,false}) for (int rejection : new int[]{0,1,2}) {
            System.gc();long owners=live(0),frames=live(1);
            var queued=queue(initialize,rejection);
            try (var f=queued.fixture) {
                System.gc();
                boolean chunkRetained = rejection != 1 && (initialize || rejection == 0);
                if (chunkRetained && queued.chunk.get()==null) throw new AssertionError("queued producer lost original chunk capture: " + initialize + "/" + rejection);
                if (!chunkRetained && !awaitChunkRelease(queued)) throw new AssertionError("PRE unexpectedly retains chunk after rejected POST: " + initialize + "/" + rejection);
                if (nativeLive != null && rejection != 1 && live(0)<=owners) throw new AssertionError("no actual request allocation retained by queued producer");
                f.transport();f.update();
                if (f.pending()!=0) throw new AssertionError("retired request remains queued");
                if (queued.future!=null && !queued.future.isCancelled()) throw new AssertionError("escaped cancelled future changed");
                if (!awaitRelease(queued,owners,frames)) throw new AssertionError("retired chunk or native request/frame survives while engine is held");
                events.add("released " + initialize + " " + rejection + " " + (queued.future==null || queued.future.isCancelled()));
                java.lang.ref.Reference.reachabilityFence(f.engine);
                java.lang.ref.Reference.reachabilityFence(queued.future);
            }
        }
        Files.write(Path.of(args[0]),events);
        System.out.println("REQUEST_LIFETIME_CONTRACTS 6 native_allocations_observed=" + (nativeLive!=null));
    }
}
