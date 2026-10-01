package net.minecraft.world.level.chunk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;

/** Local block palette growth only. New palette/storage remain unpublished until copying finishes. */
final class NativePaletteResize {
    private static final Class<?> BLOCK_STRATEGY =
            Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY).getClass();
    private static final MethodHandle USED = NativeLibraryLoader.downcallHandle(
            "mattmc_rust", "mattmc_palette_resize_used", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle REMAP = NativeLibraryLoader.downcallHandle(
            "mattmc_rust", "mattmc_palette_resize_remap", FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private NativePaletteResize() {}

    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment words = arena.allocate(1024 * 8, 8);
        final MemorySegment order = arena.allocate(256 * 4, 4);
        final MemorySegment map = arena.allocate(256 * 4, 4);
        final MemorySegment output = arena.allocate(2048 * 8, 8);
        final int[] ids = new int[256];
        final int[] mapping = new int[256];
        final Object[] values = new Object[256];
        boolean inUse;
    }

    /** False selects the unchanged per-entry copy. Called from onResize under its existing ownership rules. */
    static <T> boolean copy(Strategy<T> owner, PalettedContainer.Data<T> source, PalettedContainer.Data<T> target) {
        if (owner == null || owner.getClass() != BLOCK_STRATEGY
                || owner.globalMap() != Block.BLOCK_STATE_REGISTRY || source == target) return false;
        var input = source.storage();
        var destination = target.storage();
        var from = source.palette();
        var to = target.palette();
        if (input == null || destination == null || from == null || to == null
                || (input.getClass() != SimpleBitStorage.class && input.getClass() != ZeroBitStorage.class)
                || destination.getClass() != SimpleBitStorage.class
                || (from.getClass() != SingleValuePalette.class && from.getClass() != LinearPalette.class
                    && from.getClass() != HashMapPalette.class)
                || input.getSize() != 4096 || destination.getSize() != 4096
                || input.getBits() > 8 || destination.getBits() <= input.getBits()
                || destination.getBits() > 16) return false;
        boolean global = to == owner.globalPalette();
        if (!global && to.getClass() != LinearPalette.class && to.getClass() != HashMapPalette.class) return false;
        if (!global && to.getSize() != 0) return false;
        // The new destination was created by the owner. Validate its configuration
        // too, so even direct callers cannot mismatch a palette's capacity/storage.
        var config = target.configuration();
        if ((config.getClass() != Configuration.Simple.class && config.getClass() != Configuration.Global.class)
                || !config.equals(owner.getConfigurationForBitCount(config.bitsInStorage()))
                || config.bitsInMemory() != destination.getBits()) return false;
        int count = from.getSize();
        // HashMapPalette inserts the triggering value before requesting growth.
        // Its old 8-bit storage still contains IDs 0..255 even when size is 257.
        if (count < 1 || count > 257 || count > (1 << destination.getBits())) return false;
        if (global && Block.BLOCK_STATE_REGISTRY.size() > (1 << destination.getBits())) return false;
        var scratch = SCRATCH.get();
        if (scratch.inUse) return false;
        scratch.inUse = true;
        int used = 0;
        try {
            var raw = input.getRaw();
            MemorySegment.copy(MemorySegment.ofArray(raw), 0, scratch.words, 0, raw.length * 8L);
            int readable = Math.min(count, 256);
            used = (int) USED.invokeExact(scratch.words, raw.length, input.getBits(), readable, scratch.order, 256);
            if (used < 1) return false;
            MemorySegment.copy(scratch.order, 0, MemorySegment.ofArray(scratch.ids), 0, used * 4L);
            // Preflight before any destination mutation: malformed/null used entries
            // must fall back with the original partial writes and exception behavior.
            for (int i = 0; i < used; i++) {
                try { scratch.values[i] = from.valueFor(scratch.ids[i]); }
                catch (MissingPaletteEntryException | IllegalStateException e) { return false; }
                if (scratch.values[i] == null) return false;
            }
            Arrays.fill(scratch.mapping, 0);
            var noResize = PaletteResize.<T>noResizeExpected();
            for (int i = 0; i < used; i++) {
                @SuppressWarnings("unchecked") T value = (T) scratch.values[i];
                scratch.mapping[scratch.ids[i]] = to.idFor(value, noResize);
            }
            MemorySegment.copy(MemorySegment.ofArray(scratch.mapping), 0, scratch.map, 0, 256 * 4L);
            var out = destination.getRaw();
            int status = (int) REMAP.invokeExact(scratch.words, raw.length, input.getBits(),
                    scratch.map, 256, destination.getBits(), scratch.output, out.length);
            if (status < 0) throw new IllegalStateException("Invalid native palette resize result: " + status);
            MemorySegment.copy(scratch.output, 0, MemorySegment.ofArray(out), 0, out.length * 8L);
            return true;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Native palette resize failed", e);
        } finally {
            Arrays.fill(scratch.values, 0, Math.max(used, 0), null);
            scratch.inUse = false;
        }
    }
}
