package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Immutable Rust-owned world states. Java callbacks borrow a read-only CPU view, without per-block FFI. */
public final class NativeBlockSectionSnapshot {
    private static final Class<?> BLOCK_STRATEGY = Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY).getClass();
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_snapshot_create",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_snapshot_release",
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle PADDED = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_chunk_snapshot_padded",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private final MemorySegment states;

    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment words = arena.allocate(1024 * 8, 8);
        final MemorySegment palette = arena.allocate(257 * 4, 4);
        final MemorySegment sections = arena.allocate(27 * 8, 8);
        boolean inUse;
    }

    private NativeBlockSectionSnapshot(MemorySegment owner, Arena arena) {
        try {
            // Allocate the read-only wrapper before arena adoption, so a failed
            // wrapper allocation cannot leave both cleanup and this catch owning the box.
            this.states = owner.asReadOnly().reinterpret(4096 * 2, arena, NativeBlockSectionSnapshot::release);
        } catch (Throwable failure) {
            release(owner);
            throw failure;
        }
    }

    private static void release(MemorySegment owner) {
        try { RELEASE.invokeExact(owner); }
        catch (Throwable failure) { throw new IllegalStateException("Cannot release native block capture", failure); }
    }

    public static @Nullable NativeBlockSectionSnapshot capture(PalettedContainer<BlockState> source) {
        return capture(source, Arena.ofAuto());
    }

    // The caller uses the same exclusion rules as PalettedContainer.copy(). Only immutable data escapes.
    static @Nullable NativeBlockSectionSnapshot capture(PalettedContainer<BlockState> source, Arena lifetime) {
        if (source.getClass() != PalettedContainer.class) return null;
        var strategy = source.strategyForNativeSnapshot();
        if (strategy.getClass() != BLOCK_STRATEGY || strategy.globalMap() != Block.BLOCK_STATE_REGISTRY) return null;
        var data = source.dataForNativeScan();
        var storage = data.storage();
        var palette = data.palette();
        if (storage.getSize() != 4096 || storage.getBits() > 16
                || (storage.getClass() != ZeroBitStorage.class && storage.getClass() != SimpleBitStorage.class)) return null;
        boolean global = palette == strategy.globalPalette();
        if (!global && palette.getClass() != SingleValuePalette.class && palette.getClass() != LinearPalette.class
                && palette.getClass() != HashMapPalette.class) return null;
        int count = global ? 0 : palette.getSize();
        if ((!global && (count < 1 || count > 257)) || Block.BLOCK_STATE_REGISTRY.size() > 65535) return null;
        var scratch = SCRATCH.get();
        if (scratch.inUse) return null;
        scratch.inUse = true;
        try {
            for (int i = 0; i < count; i++) {
                BlockState state;
                try { state = palette.valueFor(i); }
                catch (MissingPaletteEntryException | IllegalStateException malformed) { return null; }
                if (state == null) return null;
                int id = Block.getId(state);
                if (id < 0 || Block.BLOCK_STATE_REGISTRY.byId(id) != state) return null;
                scratch.palette.setAtIndex(ValueLayout.JAVA_INT, i, id);
            }
            var words = storage.getRaw();
            MemorySegment.copy(MemorySegment.ofArray(words), 0, scratch.words, 0, words.length * 8L);
            var owner = (MemorySegment) CREATE.invokeExact(scratch.words, words.length, storage.getBits(),
                    scratch.palette, count, Block.BLOCK_STATE_REGISTRY.size());
            return owner.address() == 0 ? null : new NativeBlockSectionSnapshot(owner, lifetime);
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot capture native block section", failure); }
        finally { scratch.inUse = false; }
    }

    public int stateId(int index) {
        return Short.toUnsignedInt(this.states.getAtIndex(ValueLayout.JAVA_SHORT, index));
    }

    public BlockState state(int index) {
        return Block.BLOCK_STATE_REGISTRY.byId(stateId(index));
    }

    /** Null sections represent air. Each owner and arena stays reachable throughout the bulk call. */
    public static void writePadded(NativeBlockSectionSnapshot[] captures, MemorySegment output) {
        if (captures.length != 27 || output.byteSize() != 18 * 18 * 18 * 4) throw new IllegalArgumentException("Invalid snapshot neighbourhood");
        var scratch = SCRATCH.get();
        if (scratch.inUse) throw new IllegalStateException("Reentrant snapshot neighbourhood read");
        scratch.inUse = true;
        try {
            for (int i = 0; i < 27; i++) {
                var capture = captures[i];
                if (capture != null && !capture.states.scope().isAlive()) throw new IllegalStateException("Released block capture");
                scratch.sections.setAtIndex(ValueLayout.ADDRESS, i, capture == null ? MemorySegment.NULL : capture.states);
            }
            int result = (int) PADDED.invokeExact(scratch.sections, output, 18 * 18 * 18, Block.getId(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
            if (result != 0) throw new IllegalStateException("Invalid native snapshot neighbourhood");
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot read native snapshot neighbourhood", failure); }
        finally { scratch.inUse = false; Reference.reachabilityFence(captures); }
    }
}
