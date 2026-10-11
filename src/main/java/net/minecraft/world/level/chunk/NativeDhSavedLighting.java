package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/** Saved nibble spans are borrowed synchronously; Rust owns decoding and outputs. */
public final class NativeDhSavedLighting {
    private NativeDhSavedLighting() {}
    private static MethodHandle handle(String symbol, FunctionDescriptor descriptor, Linker.Option... options) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", symbol, descriptor, options);
    }
    private static final MethodHandle CREATE = handle("mattmc_dh_saved_light_create", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle ADD = handle("mattmc_dh_saved_light_add", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), Linker.Option.critical(true));
    private static final MethodHandle FINISH = handle("mattmc_dh_saved_light_finish", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = handle("mattmc_dh_saved_light_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    /** Malformed/custom inputs decline before publishing; callers retain their original diagnostics. */
    @Nullable public static NativeDhLighting.Field read(ChunkAccess chunk, CompoundTag tag) {
        if (chunk == null || tag == null || tag.getClass() != CompoundTag.class
            || chunk.getClass() != LevelChunk.class && chunk.getClass() != ProtoChunk.class) return null;
        int min = chunk.getMinY(), height = chunk.getHeight();
        if (min % 16 != 0 || Math.abs((long)min) > 1_000_000 || height < 16 || height > 4096 || height % 16 != 0) return null;
        var sections = tag.get("sections");
        if (!(sections instanceof ListTag list) || list.getClass() != ListTag.class || list.size() > 4096) return null;
        MemorySegment builder = MemorySegment.NULL;
        try {
            builder = (MemorySegment)CREATE.invokeExact(min, height / 16, list.size());
            if (builder.address() == 0) return null;
            for (int index = 0; index < list.size(); index++) {
                var element = list.get(index);
                if (!(element instanceof CompoundTag row) || row.getClass() != CompoundTag.class) return null;
                byte[] block = row.getByteArray("BlockLight").orElse(null);
                byte[] sky = row.getByteArray("SkyLight").orElse(null);
                if (block != null && block.length != 0 && block.length < 2048
                    || sky != null && sky.length != 0 && sky.length < 2048) return null;
                int result = (int)ADD.invokeExact(builder, index, borrow(block), length(block), borrow(sky), length(sky));
                if (result != 1) throw new IllegalStateException("Cannot import saved DH light row");
            }
            var pointer = (MemorySegment)FINISH.invokeExact(builder);
            if (pointer.address() == 0) throw new IllegalStateException("Incomplete saved DH lighting");
            return NativeDhLighting.adoptSaved(pointer);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot import saved DH lighting", e); }
        finally {
            if (builder.address() != 0) release(builder);
            Reference.reachabilityFence(tag);
        }
    }
    private static int length(byte[] bytes) { return bytes == null ? -1 : bytes.length == 0 ? 0 : 2048; }
    private static MemorySegment borrow(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? MemorySegment.NULL : MemorySegment.ofArray(bytes).asSlice(0, 2048);
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); }
        catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot release saved DH light builder", e); }
    }
}
