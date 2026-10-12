package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.objects.DataCorruptedException;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import java.io.IOException;
import java.io.EOFException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.NativeLibraryLoader;

/** Bounded byte IO only; Rust owns saved-point parsing and primary-column writes. */
public final class NativeDhCodec {
    private static MethodHandle handle(String name, FunctionDescriptor type) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_codec_" + name, type);
    }
    private static final MethodHandle INITIALIZE = handle("initialize", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle DECODER = handle("decoder", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle ENCODER = handle("encoder", FunctionDescriptor.of(ValueLayout.ADDRESS,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle FEED = handle("feed", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle NEXT = handle("next", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle DECODER_RELEASE = handle("decoder_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle ENCODER_RELEASE = handle("encoder_release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    public static final AtomicLong DECODED = new AtomicLong(), ENCODED = new AtomicLong(), INITIALIZED = new AtomicLong();
    static volatile boolean enabled = !Boolean.getBoolean("mattmc.dh.javaCodec");
    private NativeDhCodec() {}
    static final class Input {
        final byte[] bytes = new byte[16384];
        IOException pending;
        int read(DhDataInputStream stream) throws IOException {
            if (stream.codecByteArrayInput()) {
                int count = stream.read(bytes, 0, bytes.length);
                if (count < 0) { pending = new EOFException(); return 0; }
                return count;
            }
            int count = 0;
            try { while (count < bytes.length) { bytes[count] = stream.readByte(); count++; } }
            catch (IOException e) { pending = e; }
            return count;
        }
    }
    private static boolean stock(FullDataSourceV2 source) {
        return enabled && source.getClass() == FullDataSourceV2.class && source.nativeSource().isLive();
    }
    public static boolean initialize(FullDataSourceV2 input) {
        if (!stock(input) || !input.nativeSource().codecWritable()) return false;
        var source = input.nativeSource();
        try (var arena = Arena.ofConfined()) {
            var projection = arena.allocate(4096 * 5 * 8L, 8);
            int result;
            try { result = (int)INITIALIZE.invokeExact(source.stageOwner()); }
            finally { source.projectCodec(projection); }
            if (result == 0) return false;
            if (result == -2) throw new OutOfMemoryError("Cannot initialize DH source columns");
            if (result != 1) throw new IllegalStateException("Invalid DH codec initialization: " + result);
            INITIALIZED.incrementAndGet(); return true;
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot initialize DH source columns", e); }
        finally { Reference.reachabilityFence(source); }
    }
    public static boolean decode(FullDataSourceV2 input, DhDataInputStream stream,
        int minX, int maxX, int minZ, int maxZ, boolean v1, boolean validate) throws IOException, DataCorruptedException {
        if (!stock(input) || !input.nativeSource().codecWritable()) return false;
        var source = input.nativeSource();
        MemorySegment owner = MemorySegment.NULL;
        try (var arena = Arena.ofConfined()) {
            owner = (MemorySegment)DECODER.invokeExact(source.stageOwner(), minX, maxX, minZ, maxZ, v1 ? 1 : 0, validate ? 1 : 0);
            if (owner.address() == 0) return false;
            var inputBytes = new Input(); var bytes = inputBytes.bytes; var buffer = arena.allocate(bytes.length, 1);
            var result = arena.allocate(32, 8); var projection = arena.allocate(4096 * 5 * 8L, 8);
            try {
                FEED.invokeExact(owner, buffer, 0, result);
                while (result.getAtIndex(ValueLayout.JAVA_LONG, 0) == 0) {
                    int count = inputBytes.read(stream);
                    // readByte preserves the original decompressor exceptions. A
                    // pending error is raised only if the parser needs more bytes.
                    MemorySegment.copy(bytes, 0, buffer, ValueLayout.JAVA_BYTE, 0, count);
                    FEED.invokeExact(owner, buffer, count, result);
                    if (result.getAtIndex(ValueLayout.JAVA_LONG, 0) == 0 && inputBytes.pending != null) throw inputBytes.pending;
                }
                source.projectCodec(projection);
                check(result, input);
                DECODED.incrementAndGet(); return true;
            } finally { source.projectCodec(projection); }
        } catch (IOException | DataCorruptedException | RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot decode DH source points", e); }
        finally {
            if (owner.address() != 0) release(DECODER_RELEASE, owner);
            Reference.reachabilityFence(source);
        }
    }
    public static boolean encode(FullDataSourceV2 input, DhDataOutputStream stream,
        int minX, int maxX, int minZ, int maxZ) throws IOException {
        if (!stock(input)) return false;
        var source = input.nativeSource(); MemorySegment owner = MemorySegment.NULL;
        try (var arena = Arena.ofConfined()) {
            owner = (MemorySegment)ENCODER.invokeExact(source.stageOwner(), minX, maxX, minZ, maxZ);
            if (owner.address() == 0) return false;
            var bytes = new byte[16384]; var buffer = arena.allocate(bytes.length, 1); var result = arena.allocate(32, 8);
            do {
                NEXT.invokeExact(owner, buffer, bytes.length, result);
                int count = (int)result.getAtIndex(ValueLayout.JAVA_LONG, 3);
                MemorySegment.copy(buffer, ValueLayout.JAVA_BYTE, 0, bytes, 0, count);
                stream.write(bytes, 0, count);
                check(result, input);
            } while (result.getAtIndex(ValueLayout.JAVA_LONG, 0) == 0);
            ENCODED.incrementAndGet(); return true;
        } catch (IOException | RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot encode DH source points", e); }
        finally { if (owner.address() != 0) release(ENCODER_RELEASE, owner); Reference.reachabilityFence(source); }
    }
    private static void check(MemorySegment result, FullDataSourceV2 source) throws IOException, DataCorruptedException {
        int status = (int)result.getAtIndex(ValueLayout.JAVA_LONG, 0);
        int index = (int)result.getAtIndex(ValueLayout.JAVA_LONG, 1);
        long value = result.getAtIndex(ValueLayout.JAVA_LONG, 2);
        switch (status) {
            case 0, 1 -> {}
            case 2 -> throw new IOException("invalid varint");
            case 4 -> throw new DataCorruptedException("Read DataSource Blob data at index [" + index + "], column length [" + value + "] should be greater than zero.");
            case 5 -> { FullDataPointUtil.validateDatapoint(value); throw new IllegalStateException("Invalid DH point accepted"); }
            case 6 -> { int encoded = (int)value; FullDataPointUtil.encode(encoded >> 2, 1, encoded & 1, (byte)(encoded & 2), (byte)0); throw new IllegalStateException("Invalid DH ID accepted"); }
            case 7 -> throw new IllegalArgumentException("varint given [" + value + "], varint only accepts positive values.");
            case 8 -> throw new OutOfMemoryError("Cannot resize DH source column");
            default -> throw new IllegalStateException("Invalid DH codec status: " + status);
        }
    }
    private static void release(MethodHandle release, MemorySegment owner) {
        try { release.invokeExact(owner); }
        catch (Throwable e) { throw new IllegalStateException("Cannot release DH codec", e); }
    }
}
