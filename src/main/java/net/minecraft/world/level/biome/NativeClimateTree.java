package net.minecraft.world.level.biome;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import net.minecraft.util.NativeLibraryLoader;

/** Immutable preorder tree; native calls borrow its arena-backed storage.
 * Java retains leaf identities and the caller's per-thread previous result. */
final class NativeClimateTree<T> {
    static final int BATCH_SIZE = 64;
    private static final int NODE_BYTES = 136;
    private static final MethodHandle VALIDATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_climate_validate",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle BATCH = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_climate_batch",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle SMALL_BATCH = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_climate_batch",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        java.lang.foreign.Linker.Option.critical(false));
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private final MemorySegment nodes;
    private final Climate.RTree.Leaf<T>[] leaves;

    @SuppressWarnings("unchecked")
    NativeClimateTree(Climate.RTree.Node<T> root) {
        var order = new ArrayList<Climate.RTree.Node<T>>();
        var ends = new ArrayList<Integer>();
        flatten(root, order, ends);
        leaves = (Climate.RTree.Leaf<T>[])new Climate.RTree.Leaf<?>[order.size()];
        MemorySegment data = Arena.ofAuto().allocate(Math.multiplyExact((long)order.size(), NODE_BYTES), 8);
        for (int i = 0; i < order.size(); i++) {
            var node = order.get(i);
            long base = (long)i * NODE_BYTES;
            for (int axis = 0; axis < 7; axis++) {
                data.set(ValueLayout.JAVA_LONG, base + axis * 8, node.parameterSpace[axis].min());
                data.set(ValueLayout.JAVA_LONG, base + 64 + axis * 8, node.parameterSpace[axis].max());
            }
            data.set(ValueLayout.JAVA_INT, base + 128, ends.get(i));
            if (node instanceof Climate.RTree.Leaf<T> leaf) {
                data.set(ValueLayout.JAVA_INT, base + 132, 1);
                leaf.nativeIndex = i;
                leaves[i] = leaf;
            }
        }
        nodes = data.asReadOnly();
        try {
            int status = (int)VALIDATE.invokeExact(nodes, leaves.length);
            if (status != 0) throw new IllegalArgumentException("Invalid native climate tree: " + status);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable t) { throw new IllegalStateException("Climate tree validation failed", t); }
    }

    private static <T> void flatten(Climate.RTree.Node<T> node,
            ArrayList<Climate.RTree.Node<T>> order, ArrayList<Integer> ends) {
        int index = order.size();
        order.add(node);
        ends.add(0);
        if (node instanceof Climate.RTree.SubTree<T> branch) {
            for (var child : branch.children) flatten(child, order, ends);
        }
        ends.set(index, order.size());
    }

    Climate.RTree.Leaf<T> search(Climate.TargetPoint point, Climate.RTree.Leaf<T> previous) {
        Scratch scratch = SCRATCH.get();
        scratch.put(0, point);
        invoke(scratch, 1, previous);
        return leaf(scratch, 0);
    }

    /** The caller has checked that sampling cannot invoke extension callbacks. */
    void searchSection(Climate.Sampler sampler, int x, int y, int z, T[] output,
            ThreadLocal<Climate.RTree.Leaf<T>> previous) {
        if (leaves.length == 1) {
            int index = 0;
            for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 4; dy++) for (int dz = 0; dz < 4; dz++) {
                sampler.sample(x + dx, y + dy, z + dz);
                if (index == 0) previous.set(leaves[0]);
                output[index++] = leaves[0].value;
            }
            return;
        }
        Scratch scratch = SCRATCH.get();
        int index = 0;
        for (int dx = 0; dx < 4; dx++) for (int dy = 0; dy < 4; dy++) for (int dz = 0; dz < 4; dz++) {
            // Consume records immediately; no batch array retains 64 sample objects.
            scratch.put(index++, sampler.sample(x + dx, y + dy, z + dz));
        }
        int completed = invoke(scratch, BATCH_SIZE, previous.get());
        writeResults(scratch, output, 0, completed, previous);
    }

    void search(Climate.TargetPoint[] points, T[] output, ThreadLocal<Climate.RTree.Leaf<T>> previous) {
        if (leaves.length == 1) {
            for (int i = 0; i < points.length; i++) {
                java.util.Objects.requireNonNull(points[i]);
                if (i == 0) previous.set(leaves[0]);
                output[i] = leaves[0].value;
            }
            return;
        }
        Scratch scratch = SCRATCH.get();
        int offset = 0;
        while (offset < points.length) {
            int count = 0;
            while (count < BATCH_SIZE && offset + count < points.length && points[offset + count] != null) {
                scratch.put(count, points[offset + count]);
                count++;
            }
            if (count == 0) {
                // Match the scalar null-input failure before changing previous-result state.
                points[offset].toParameterArray();
            }
            int completed = invoke(scratch, count, previous.get());
            writeResults(scratch, output, offset, completed, previous);
            offset += completed;
        }
    }

    private void writeResults(Scratch scratch, T[] output, int offset, int count,
            ThreadLocal<Climate.RTree.Leaf<T>> previous) {
        for (int i = 0; i < count; i++) {
            var leaf = leaf(scratch, i);
            previous.set(leaf);
            output[offset + i] = leaf.value; // Also preserves Java's null-result failure.
        }
    }

    private Climate.RTree.Leaf<T> leaf(Scratch scratch, int index) {
        int id = scratch.results.getAtIndex(ValueLayout.JAVA_INT, index);
        return id < 0 ? null : leaves[id];
    }

    private int invoke(Scratch scratch, int count, Climate.RTree.Leaf<T> previous) {
        int seed = previous == null ? -1 : previous.nativeIndex;
        try {
            // Only tightly bounded small trees use a critical call. Larger trees
            // remain ordinary downcalls; all buffers are native, never pinned heap.
            int completed = (long)leaves.length * count <= 1024
                ? (int)SMALL_BATCH.invokeExact(nodes, leaves.length, scratch.points, scratch.results, count, seed)
                : (int)BATCH.invokeExact(nodes, leaves.length, scratch.points, scratch.results, count, seed);
            if (completed < 1 || completed > count) throw new IllegalStateException("Native climate search: " + completed);
            return completed;
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable t) { throw new IllegalStateException("Native climate search failed", t); }
    }

    private static final class Scratch {
        final MemorySegment points;
        final MemorySegment results;
        Scratch() {
            Arena arena = Arena.ofAuto();
            points = arena.allocate(BATCH_SIZE * 8L * Long.BYTES, 8);
            results = arena.allocate(BATCH_SIZE * (long)Integer.BYTES, 4);
        }
        void put(int i, Climate.TargetPoint p) {
            long base = i * 64L;
            points.set(ValueLayout.JAVA_LONG, base, p.temperature());
            points.set(ValueLayout.JAVA_LONG, base + 8, p.humidity());
            points.set(ValueLayout.JAVA_LONG, base + 16, p.continentalness());
            points.set(ValueLayout.JAVA_LONG, base + 24, p.erosion());
            points.set(ValueLayout.JAVA_LONG, base + 32, p.depth());
            points.set(ValueLayout.JAVA_LONG, base + 40, p.weirdness());
            // Offset coordinate and SIMD padding stay zero in the zero-filled arena.
        }
    }
}
