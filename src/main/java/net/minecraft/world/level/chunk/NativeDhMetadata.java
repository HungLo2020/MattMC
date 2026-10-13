package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;

/** One CPU lifetime for the source's Rust metadata and its retained list views. */
final class NativeDhMetadata {
    private static MethodHandle handle(String name, FunctionDescriptor type) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_metadata_" + name, type);
    }
    private static final MethodHandle RETAIN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_source_metadata",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle VIEW = handle("view", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle HASH = handle("hash", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    final Arena arena = Arena.ofAuto();
    final MemorySegment owner;
    final NativeDhMetadataList generation, compression;
    /** Uniformity, and the Rust-maintained footprint presence plane (read-only to Java). */
    final MemorySegment uniform, footprints;
    NativeDhMetadata(MemorySegment source) {
        MemorySegment pointer = MemorySegment.NULL; boolean attached = false;
        try {
            pointer = (MemorySegment)RETAIN.invokeExact(source);
            if (pointer.address() == 0) throw new IllegalStateException("Missing DH metadata");
            owner = pointer.reinterpret(1, arena, NativeDhMetadata::release); attached = true;
            generation = new NativeDhMetadataList(this, 0);
            compression = new NativeDhMetadataList(this, 1);
            uniform = view(2);
            footprints = view(3).asReadOnly();
        } catch (RuntimeException | Error e) { if (!attached && pointer.address()!=0) release(pointer); throw e; }
        catch (Throwable e) { if (!attached && pointer.address()!=0) release(pointer); throw new IllegalStateException(e); }
    }
    MemorySegment view(int kind) {
        try (var scratch = Arena.ofConfined()) {
            var out = scratch.allocate(16, 8);
            if ((int)VIEW.invokeExact(owner, kind, out) != 1) throw new IllegalStateException("Invalid DH metadata view");
            long count=out.getAtIndex(ValueLayout.JAVA_LONG,1);
            if(count<0||count>Integer.MAX_VALUE)throw new IllegalStateException("DH metadata extent");
            return out.getAtIndex(ValueLayout.ADDRESS,0).reinterpret(count,arena,null);
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
    int hash(int kind) {
        try { return (int)HASH.invokeExact(owner,kind); }
        catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
