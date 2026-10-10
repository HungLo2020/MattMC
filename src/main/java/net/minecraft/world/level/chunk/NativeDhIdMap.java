package net.minecraft.world.level.chunk;

import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.core.dataObjects.BlockBiomeWrapperPair;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.Block;

/** Rust owns ordered identity/index/hash policy; Java retains wrapper bindings. */
public final class NativeDhIdMap {
    private static MethodHandle handle(String name, FunctionDescriptor signature) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_id_map_" + name, signature);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle ADD = handle("add", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle MERGE = handle("merge", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle HASH = handle("hash", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle INDEXED = handle("indexed", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private final MemorySegment owner;
    public NativeDhIdMap() {
        MemorySegment pointer = MemorySegment.NULL;
        try {
            pointer = (MemorySegment)CREATE.invokeExact();
            if (pointer.address() == 0) throw new OutOfMemoryError("DH identity owner");
            owner = pointer.reinterpret(1, Arena.ofAuto(), NativeDhIdMap::release);
        } catch (RuntimeException | Error e) { if (pointer.address() != 0) release(pointer); throw e; }
        catch (Throwable e) { if (pointer.address() != 0) release(pointer); throw new IllegalStateException(e); }
    }
    public static boolean supports(BlockBiomeWrapperPair pair) {
        return pair.getClass() == BlockBiomeWrapperPair.class && pair.biome != null && pair.blockState != null
            && pair.biome.getClass() == BiomeWrapper.class && pair.blockState.getClass() == BlockStateWrapper.class;
    }
    public int add(BlockBiomeWrapperPair pair, int mode) {
        try (var arena = Arena.ofConfined()) {
            String biome = pair.biome.getSerialString(), state = pair.blockState.getSerialString();
            var b = arena.allocateFrom(ValueLayout.JAVA_CHAR, biome.toCharArray());
            var s = arena.allocateFrom(ValueLayout.JAVA_CHAR, state.toCharArray());
            var wrapper = (BlockStateWrapper) pair.blockState;
            int stateId = wrapper == BlockStateWrapper.AIR ? 0 : wrapper.blockState == null ? -1 : Block.getId(wrapper.blockState);
            if (stateId >= 0 && wrapper != BlockStateWrapper.AIR && Block.stateById(stateId) != wrapper.blockState) stateId = -1;
            int result = (int) ADD.invokeExact(owner, b, biome.length(), s, state.length(), stateId, mode);
            if (result == -2) throw new OutOfMemoryError("DH identity storage");
            if (result < 0) throw new IllegalStateException("Invalid DH identity input");
            return result;
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(pair); Reference.reachabilityFence(this); }
    }
    /** The result carries the completed prefix so its bindings precede OOM. */
    public static final class Merge {
        private final int[] ids; private int completed;
        private Merge(int count) { ids = new int[count]; }
        public int[] ids() { return ids; }
        public int completed() { return completed; }
    }
    public Merge merge(NativeDhIdMap source, int count, boolean append) {
        var result = new Merge(count);
        try (var arena = Arena.ofConfined()) {
            var output = arena.allocate((long)count * 4, 4);
            int completed = (int) MERGE.invokeExact(owner, source.owner, output, count, append ? 1 : 0);
            if (completed < 0 || completed > count) throw new IllegalStateException("Invalid DH identity merge");
            MemorySegment.copy(output, ValueLayout.JAVA_INT, 0, result.ids, 0, completed);
            result.completed = completed; return result;
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(source); Reference.reachabilityFence(this); }
    }
    MemorySegment stageOwner() { return owner; }
    public int hash(long position) {
        try { return (int) HASH.invokeExact(owner, position); }
        catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
    public boolean indexed(int index) {
        try {
            int value = (int) INDEXED.invokeExact(owner, index);
            if (value < 0) throw new IllegalStateException("DH identity index");
            return value != 0;
        } catch (RuntimeException | Error e) { throw e; } catch (Throwable e) { throw new IllegalStateException(e); }
        finally { Reference.reachabilityFence(this); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable e) { throw new IllegalStateException(e); }
    }
}
