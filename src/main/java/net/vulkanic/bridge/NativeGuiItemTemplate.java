package net.vulkanic.bridge;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.NativeLibraryLoader;

/** Lease for immutable Rust CPU item layers. Its identity is not a GPU handle
 * or native address. Acquired frame geometry remains owned after release. */
public final class NativeGuiItemTemplate implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_vulkanic_gal_gui_item_template_create", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust",
        "mattmc_vulkanic_gal_gui_item_template_release", FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG));
    private static final class Lease implements Runnable {
        private final AtomicLong id;
        private Lease(long id) { this.id = new AtomicLong(id); }
        @Override public void run() {
            long released = id.getAndSet(0);
            if (released == 0) return;
            try { RELEASE.invokeExact(released); }
            catch (Throwable failure) { throw new IllegalStateException("Cannot release GUI item template", failure); }
        }
    }
    private final Lease lease;
    private final Cleaner.Cleanable cleanable;
    private final int batchCount, sourceLayers;
    private final long vertexCount, indexCount;
    private final long[] assetIds;
    private NativeGuiItemTemplate(long id, int batches, int layers, long vertices, long indices, long[] assets) {
        lease = new Lease(id);
        batchCount = batches;
        sourceLayers = layers;
        vertexCount = vertices; indexCount = indices; assetIds = assets.clone();
        cleanable = CLEANER.register(this, lease);
    }
    static NativeGuiItemTemplate create(MemorySegment batches, int count, int[] selectors, int sourceLayers, long vertices, long indices, long[] assets) {
        try (Arena input = Arena.ofConfined()) {
            MemorySegment out = input.allocate(ValueLayout.JAVA_LONG);
            int status = (int) CREATE.invokeExact(VulkanicGalBridge.ABI_VERSION, batches, (long) count,
                input.allocateFrom(ValueLayout.JAVA_INT, selectors), sourceLayers, out);
            if (status == -14) return null; // bounded retention full; use the existing semantic input
            if (status != 0) throw new IllegalArgumentException("GUI item template rejected with status " + status);
            long id = out.get(ValueLayout.JAVA_LONG, 0);
            try { return new NativeGuiItemTemplate(id, count, sourceLayers, vertices, indices, assets); }
            catch (Throwable failure) {
                RELEASE.invokeExact(id);
                throw failure;
            }
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot create GUI item template", failure); }
    }
    public long identity() {
        long id = lease.id.get();
        if (id == 0) throw new IllegalStateException("GUI item template is closed");
        return id;
    }
    public int batchCount() { return batchCount; }
    public int sourceLayers() { return sourceLayers; }
    public long vertexCount() { return vertexCount; }
    public long indexCount() { return indexCount; }
    public long[] assetIds() { return assetIds.clone(); }
    /** Visit immutable semantic dependencies without a per-frame array copy. */
    public void forEachAssetId(java.util.function.LongConsumer consumer) {
        for (long assetId : assetIds) consumer.accept(assetId);
    }
    @Override public void close() { cleanable.clean(); }
}
