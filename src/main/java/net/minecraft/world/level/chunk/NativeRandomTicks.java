package net.minecraft.world.level.chunk;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Primary world position sequence; Rust selects from the live section owner. */
public final class NativeRandomTicks {
    private static MethodHandle handle(String name, FunctionDescriptor descriptor, boolean critical) {
        return critical ? NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_random_ticks_" + name,
            descriptor, Linker.Option.critical(false))
            : NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_random_ticks_" + name, descriptor);
    }
    private static final MethodHandle CREATE = handle("create", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT), false);
    private static final MethodHandle RELEASE = handle("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS), false);
    private static final MethodHandle NEXT = handle("next", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS), true);
    private static final MethodHandle SELECT = handle("select", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), true);
    private static final MethodHandle CHUNK = handle("chunk", FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), true);
    private static final MethodHandle BIND = handle("bind", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS), false);
    private static final MethodHandle UNBIND = handle("unbind", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS), false);
    private static final ThreadLocal<MemorySegment> OUTPUT = ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(16, 4));
    private static final class Cursors {
        private final java.util.ArrayList<MemorySegment> rows = new java.util.ArrayList<>();
        private int depth;
        MemorySegment acquire() {
            if (depth == rows.size()) rows.add(Arena.ofAuto().allocate(16, 4));
            return rows.get(depth++);
        }
        void release() { depth--; }
    }
    private static final ThreadLocal<Cursors> CURSORS = ThreadLocal.withInitial(Cursors::new);
    private final MemorySegment owner;
    /** Strong leases to primary generations, never copied block values. */
    static final class ChunkView {
        final MemorySegment owner;
        private ChunkView(MemorySegment pointer) {
            boolean attached = false;
            try { owner = pointer.reinterpret(1, Arena.ofAuto(), ChunkView::release); attached = true; }
            finally { if (!attached) release(pointer); }
        }
        static ChunkView create(NativeChunkInputs inputs) {
            try {
                var pointer = (MemorySegment)BIND.invokeExact(inputs.stageOwner());
                return pointer.address() == 0 ? null : new ChunkView(pointer);
            } catch (Throwable t) { throw failure(t); }
            finally { Reference.reachabilityFence(inputs); }
        }
        private static void release(MemorySegment pointer) {
            try { UNBIND.invokeExact(pointer); } catch (Throwable t) { throw failure(t); }
        }
    }

    public NativeRandomTicks(int seed) {
        try {
            var pointer = (MemorySegment)CREATE.invokeExact(seed);
            boolean attached = false;
            try {
                owner = pointer.reinterpret(1, Arena.ofAuto(), NativeRandomTicks::release);
                attached = true;
            } finally { if (!attached) release(pointer); }
        } catch (Throwable t) { throw failure(t); }
    }
    private static void release(MemorySegment pointer) {
        try { RELEASE.invokeExact(pointer); } catch (Throwable t) { throw failure(t); }
    }
    private static RuntimeException failure(Throwable t) {
        if (t instanceof RuntimeException r) return r;
        if (t instanceof Error e) throw e;
        return new IllegalStateException("Native random tick selection failed", t);
    }
    public int nextBits() {
        try { return (int)NEXT.invokeExact(owner); }
        catch (Throwable t) { throw failure(t); }
        finally { Reference.reachabilityFence(this); }
    }

    /** Retained chunk selection spans empty sections and non-ticking attempts.
     * Escaped arrays resume the original captured-section/callback traversal. */
    public void tickChunk(ServerLevel level, LevelChunk chunk, int x, int z, int speed, ProfilerFiller profiler) {
        if (speed <= 0) return;
        var sections = chunk.getSectionsForRead();
        NativeChunkInputs inputs = level.getClass() == ServerLevel.class && chunk.getClass() == LevelChunk.class
            && chunk.levelHeightAccessor == level && !chunk.hasEscapedSectionArray() && profiler == InactiveProfiler.INSTANCE
            ? chunk.nativeCollisionSections(false) : null;
        ChunkView view = inputs == null ? null : inputs.randomTickView(false);
        var cursors = CURSORS.get();
        var cursor = cursors.acquire();
        try {
            cursor.set(ValueLayout.JAVA_INT, 0, 0); cursor.set(ValueLayout.JAVA_INT, 4, -1);
            LevelChunkSection held = null;
            int heldIndex = -1;
            while (true) {
                if (view == null || chunk.hasEscapedSectionArray()) {
                    int index = cursor.get(ValueLayout.JAVA_INT, 0), attempted = cursor.get(ValueLayout.JAVA_INT, 4);
                    for (; index < sections.length; index++) {
                        var section = heldIndex == index ? held : sections[index];
                        if (attempted >= 0 || section.isRandomlyTicking()) {
                            int y = chunk.getSectionYFromSectionIndex(index) << 4;
                            tickSection(level, section, x, y, z, speed - Math.max(0, attempted), profiler);
                        }
                        heldIndex = -1; attempted = -1;
                    }
                    return;
                }
                long result;
                try { result = (long)CHUNK.invokeExact(owner, view.owner, speed, cursor); }
                catch (Throwable t) { throw failure(t); }
                finally { Reference.reachabilityFence(view); Reference.reachabilityFence(this); }
                if (result < 0) {
                    inputs = !chunk.hasEscapedSectionArray() ? chunk.nativeCollisionSections(true) : null;
                    view = inputs == null ? null : inputs.randomTickView(true);
                    continue;
                }
                if (result == 0) {
                    if (cursor.get(ValueLayout.JAVA_INT, 0) >= sections.length) return;
                    continue;
                }
                int bits = (int)result, id = ((int)(result >>> 32) & 65535) - 1;
                heldIndex = bits >>> 24;
                held = sections[heldIndex];
                int y = inputs.minY() + (heldIndex << 4);
                var position = new BlockPos(x + (bits & 15), y + (bits >> 16 & 15), z + (bits >> 8 & 15));
                var state = id < 0 ? held.getBlockState(bits & 15, bits >> 16 & 15, bits >> 8 & 15) : Block.stateById(id);
                tick(level, state, position);
            }
        } finally { cursors.release(); }
    }

    /** The section is captured once, as in ServerLevel's original inner loop. */
    public void tickSection(ServerLevel level, LevelChunkSection section, int x, int y, int z,
        int attempts, ProfilerFiller profiler) {
        int remaining = attempts;
        // Custom position/section/profiler callbacks retain their exact ordering.
        boolean nativeSelection = level.getClass() == ServerLevel.class && profiler == InactiveProfiler.INSTANCE;
        while (remaining > 0) {
            var blocks = nativeSelection ? section.nativeGenerationInput() : null;
            if (blocks == null) {
                BlockPos position = level.getBlockRandomPos(x, y, z, 15);
                profiler.push("randomTick");
                BlockState state = section.getBlockState(position.getX() - x, position.getY() - y, position.getZ() - z);
                tick(level, state, position);
                profiler.pop();
                remaining--;
                continue;
            }
            MemorySegment output = OUTPUT.get();
            int status;
            try { status = (int)SELECT.invokeExact(owner, blocks.stageOwner(), remaining, output); }
            catch (Throwable t) { throw failure(t); }
            finally { Reference.reachabilityFence(blocks); Reference.reachabilityFence(this); }
            if (status < 0) { nativeSelection = false; continue; }
            remaining -= output.get(ValueLayout.JAVA_INT, 0);
            if (status == 0) continue;
            int bits = output.get(ValueLayout.JAVA_INT, 4);
            int id = output.get(ValueLayout.JAVA_INT, 8);
            BlockPos position = new BlockPos(x + (bits & 15), y + (bits >> 16 & 15), z + (bits >> 8 & 15));
            BlockState state = id < 0 ? section.getBlockState(bits & 15, bits >> 16 & 15, bits >> 8 & 15) : Block.stateById(id);
            tick(level, state, position);
        }
    }
    private static void tick(ServerLevel level, BlockState state, BlockPos position) {
        if (state.isRandomlyTicking()) state.randomTick(level, position, level.random);
        // Capture the fluid from the selected state AFTER the block callback,
        // not from a fresh world lookup; callbacks may have replaced that cell.
        var fluid = state.getFluidState();
        if (fluid.isRandomlyTicking()) fluid.randomTick(level, position, level.random);
    }
}
