package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.NativeLibraryLoader;

/** Bulk big-endian long arrays. Java retains buffer ownership and indexes. */
final class NativeLongArray {
    static final int MIN_VALUES = 128;
    private static final int MAX_VALUES = 8192;
    private static final Set<Class<?>> BUFFER_TYPES = bufferTypes();
    private static final Set<Class<?>> VIEW_TYPES = types("UnpooledSlicedByteBuf", "UnpooledDuplicatedByteBuf",
        "PooledSlicedByteBuf", "PooledDuplicatedByteBuf",
        "AbstractPooledDerivedByteBuf$PooledNonRetainedSlicedByteBuf", "AbstractPooledDerivedByteBuf$PooledNonRetainedDuplicateByteBuf",
        "SimpleLeakAwareByteBuf");
    private static final FunctionDescriptor ENCODE_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
    private static final MethodHandle ENCODE = NativeLibraryLoader.downcallHandle(
        "mattmc_rust", "mattmc_long_array_encode", ENCODE_DESCRIPTOR, Linker.Option.critical(true));
    private static final MethodHandle DECODE = NativeLibraryLoader.downcallHandle(
        "mattmc_rust", "mattmc_long_array_decode", ENCODE_DESCRIPTOR, Linker.Option.critical(true));
    private static final MethodHandle INITIALIZE = NativeLibraryLoader.downcallHandle(
        "mattmc_rust", "mattmc_long_array_initialize", FunctionDescriptor.of(ValueLayout.JAVA_INT));
    static {
        try { int dispatch = (int)INITIALIZE.invokeExact(); }
        catch (Throwable e) { throw new ExceptionInInitializerError(e); }
    }

    private NativeLongArray() {}

    private static Set<Class<?>> bufferTypes() {
        return types("UnpooledHeapByteBuf", "UnpooledUnsafeHeapByteBuf", "PooledHeapByteBuf", "PooledUnsafeHeapByteBuf",
            "UnpooledUnsafeDirectByteBuf", "UnpooledUnsafeNoCleanerDirectByteBuf", "PooledUnsafeDirectByteBuf",
            "UnpooledByteBufAllocator$InstrumentedUnpooledHeapByteBuf", "UnpooledByteBufAllocator$InstrumentedUnpooledUnsafeHeapByteBuf",
            "UnpooledByteBufAllocator$InstrumentedUnpooledUnsafeDirectByteBuf", "UnpooledByteBufAllocator$InstrumentedUnpooledUnsafeNoCleanerDirectByteBuf");
    }

    private static Set<Class<?>> types(String... names) {
        var types = new HashSet<Class<?>>();
        for (String name : names) {
            try { types.add(Class.forName("io.netty.buffer." + name, false, ByteBuf.class.getClassLoader())); }
            catch (ClassNotFoundException ignored) { /* Optional Netty unsafe implementation. */ }
        }
        return Set.copyOf(types);
    }

    private static ByteBuf supported(ByteBuf buffer) {
        if (buffer == null) return null;
        for (int depth = 0; depth < 8 && buffer != null; depth++) {
            Class<?> type = buffer.getClass();
            if (type == FriendlyByteBuf.class || type == RegistryFriendlyByteBuf.class) { buffer = buffer.unwrap(); continue; }
            return trusted(buffer) ? buffer : null;
        }
        return null;
    }

    private static boolean trusted(ByteBuf buffer) {
        ByteBuf region = buffer;
        for (int depth = 0; depth < 8; depth++) {
            Class<?> type = buffer.getClass();
            if (BUFFER_TYPES.contains(type)) {
                if (region == buffer) return true;
                if (region.refCnt() == 0 || buffer.refCnt() == 0) return false;
                long offset = region.hasArray() ? (long)region.arrayOffset() - buffer.arrayOffset()
                    : region.memoryAddress() - buffer.memoryAddress();
                // A retained view can outlive a parent capacity change. Its
                // declared capacity alone does not establish a live region.
                return offset >= 0 && offset + region.capacity() <= buffer.capacity();
            }
            if (!VIEW_TYPES.contains(type) && type != FriendlyByteBuf.class && type != RegistryFriendlyByteBuf.class) return false;
            buffer = buffer.unwrap();
        }
        return false;
    }

    static boolean read(ByteBuf buffer, long[] values) {
        var storage = supported(buffer);
        long bytes = values.length * 8L;
        if (storage == null || bytes > storage.readableBytes() || storage.refCnt() == 0) return false;
        int index = storage.readerIndex();
        var source = region(storage, index, bytes);
        var destination = MemorySegment.ofArray(values);
        try { decode(source, destination, values.length); storage.readerIndex(index + (int)bytes); }
        finally { Reference.reachabilityFence(storage); }
        return true;
    }

    static boolean write(ByteBuf buffer, long[] values) {
        var storage = supported(buffer);
        long bytes = values.length * 8L;
        if (storage == null || bytes > (long)storage.capacity() - storage.writerIndex() || storage.refCnt() == 0) return false;
        int index = storage.writerIndex();
        var destination = region(storage, index, bytes);
        var source = MemorySegment.ofArray(values);
        try { encode(source, destination, values.length); storage.writerIndex(index + (int)bytes); }
        finally { Reference.reachabilityFence(storage); }
        return true;
    }

    // Each critical call borrows at most 64 KiB per array. Rust validates the
    // bound; no allocation, feature probing, blocking or callbacks occur there.
    private static MemorySegment region(ByteBuf buffer, int index, long bytes) {
        return buffer.hasArray() ? MemorySegment.ofArray(buffer.array()).asSlice(buffer.arrayOffset() + (long)index, bytes)
            : MemorySegment.ofAddress(buffer.memoryAddress() + index).reinterpret(bytes);
    }

    private static void encode(MemorySegment source, MemorySegment destination, int count) {
        try {
            for (int offset = 0; offset < count;) {
                int batch = Math.min(count - offset, MAX_VALUES), bytes = batch * 8;
                var input = source.asSlice(offset * 8L, bytes);
                var output = destination.asSlice(offset * 8L, bytes);
                int status = (int)ENCODE.invokeExact(input, batch, output, bytes);
                if (status != 0) throw new IllegalStateException("Invalid native long array: " + status);
                offset += batch;
            }
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Native long array conversion failed", e); }
    }

    private static void decode(MemorySegment source, MemorySegment destination, int count) {
        try {
            for (int offset = 0; offset < count;) {
                int batch = Math.min(count - offset, MAX_VALUES), bytes = batch * 8;
                int status = (int)DECODE.invokeExact(source.asSlice(offset * 8L, bytes), bytes,
                    destination.asSlice(offset * 8L, bytes), batch);
                if (status != 0) throw new IllegalStateException("Invalid native long array: " + status);
                offset += batch;
            }
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Native long array conversion failed", e); }
    }
}
