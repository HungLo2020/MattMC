package net.sodium.client.render.chunk.compile.tasks;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bounded cached geometry export. Rust owns policy and consumes world IDs directly. */
final class NativeTerrainCulling {
    private static final int CELLS = 18 * 18 * 18;
    private static final class Bindings {
        static final MethodHandle INSTALL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_face_policy_install",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        static final MethodHandle ADMISSION = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_face_policy_admission",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        static final MethodHandle QUERIES = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_face_policy_queries",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        static final MethodHandle GRID = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_face_policy_admit_grid",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    }
    private static final class Holder {
        static final MemorySegment ADMISSION = install();
    }
    private record Geometry(boolean fullIdentity, List<AABB> boxes) {}

    private NativeTerrainCulling() {}
    private static RuntimeException failure(Throwable error) {
        if (error instanceof Error e) throw e;
        if (error instanceof RuntimeException e) return e;
        return new IllegalStateException(error);
    }
    /** Source method identity only; no skipRendering or tag/hook answer is sampled. */
    private static int implementation(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                current.getDeclaredMethod("skipRendering", BlockState.class, BlockState.class, Direction.class);
                if (current == BlockBehaviour.class) return 0;
                if (current == HalfTransparentBlock.class) return 1;
                if (current == PowderSnowBlock.class) return 2;
                if (current == IronBarsBlock.class) return 3;
                if (current == MangroveRootsBlock.class) return 4;
                if (current == LiquidBlock.class) return 5;
                if (current == LeavesBlock.class) return 6;
                return 7;
            } catch (NoSuchMethodException absent) {
                // The inherited declaration identifies the original CPU implementation.
            }
        }
        return 7;
    }
    private static MemorySegment install() {
        if (!NativeBlockRegistry.ready()) return null;
        int count = Block.BLOCK_STATE_REGISTRY.size();
        int[] states = new int[count * 8];
        var seen = new IdentityHashMap<VoxelShape, Integer>();
        var unique = new LinkedHashMap<Geometry, Integer>();
        var implementations = new HashMap<Class<?>, Integer>();
        var geometries = new ArrayList<Geometry>();
        java.util.function.ToIntFunction<VoxelShape> face = shape -> {
            Integer existing = seen.get(shape);
            if (existing != null) return existing;
            var geometry = new Geometry(shape == Shapes.block(), List.copyOf(shape.toAabbs()));
            int id = unique.computeIfAbsent(geometry, value -> { int next = geometries.size(); geometries.add(value); return next; });
            seen.put(shape, id);
            return id;
        };
        int empty = face.applyAsInt(Shapes.empty());
        Direction[] directions = Direction.values();
        for (int id = 0; id < count; id++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
            boolean canonical = state.getClass() == BlockState.class;
            states[id * 8] = canonical ? implementations.computeIfAbsent(state.getBlock().getClass(), NativeTerrainCulling::implementation) : 7;
            for (int d = 0; d < 6; d++) states[id * 8 + d + 1] = canonical ? face.applyAsInt(state.getFaceOcclusionShape(directions[d])) : empty;
            states[id * 8 + 7] = canonical ? 1 : 0;
        }
        if (geometries.size() > 1024) return null; // Unsupported geometry stays on callbacks.
        int boxCount = geometries.stream().mapToInt(g -> g.boxes.size()).sum();
        if (boxCount > 8192 || geometries.stream().anyMatch(g -> g.boxes.size() > 256)) return null;
        int[] shapeRows = new int[geometries.size() * 3];
        double[] boxes = new double[boxCount * 6];
        int at = 0;
        for (int id = 0; id < geometries.size(); id++) {
            Geometry g = geometries.get(id);
            shapeRows[id * 3] = (g.fullIdentity ? 1 : 0) | (g.boxes.isEmpty() ? 2 : 0);
            shapeRows[id * 3 + 1] = at / 6;
            shapeRows[id * 3 + 2] = g.boxes.size();
            for (AABB box : g.boxes) {
                boxes[at++] = box.minX; boxes[at++] = box.minY; boxes[at++] = box.minZ;
                boxes[at++] = box.maxX; boxes[at++] = box.maxY; boxes[at++] = box.maxZ;
            }
        }
        try (Arena call = Arena.ofConfined()) {
            int result = (int)Bindings.INSTALL.invokeExact(call.allocateFrom(ValueLayout.JAVA_INT, states), states.length,
                call.allocateFrom(ValueLayout.JAVA_INT, shapeRows), shapeRows.length,
                call.allocateFrom(ValueLayout.JAVA_DOUBLE, boxes), boxes.length);
            if (result != 1) throw new IllegalStateException("Native face policy rejected canonical geometry: " + result);
            MemorySegment length = call.allocate(ValueLayout.JAVA_INT);
            MemorySegment pointer = (MemorySegment)Bindings.ADMISSION.invokeExact(length);
            if (pointer.address() == 0 || length.get(ValueLayout.JAVA_INT, 0) != count)
                throw new IllegalStateException("Missing native face admission metadata");
            // The immutable process-wide Rust registry owns this CPU buffer.
            return pointer.asReadOnly().reinterpret(count, Arena.global(), null);
        } catch (Throwable error) { throw failure(error); }
    }
    static boolean admitsGrid(MemorySegment ids) {
        if (ids.byteSize() != (long)CELLS * Integer.BYTES) throw new IllegalArgumentException("Invalid face-policy world grid");
        if (Holder.ADMISSION == null) return false;
        try {
            int result = (int)Bindings.GRID.invokeExact(ids, CELLS);
            if (result < 0) throw new IllegalArgumentException("Rejected face-policy world IDs: " + result);
            return result == 1;
        } catch (Throwable error) { throw failure(error); }
    }
    static boolean admitsState(int id, BlockState state) {
        MemorySegment admission = Holder.ADMISSION;
        return admission != null && id >= 0 && id < admission.byteSize()
            && Block.BLOCK_STATE_REGISTRY.byId(id) == state
            && admission.get(ValueLayout.JAVA_BYTE, id) != 0;
    }
    static byte[] queryForVerification(int[] triples) {
        if (triples.length % 3 != 0) throw new IllegalArgumentException("Invalid face query triples");
        if (Holder.ADMISSION == null) throw new IllegalStateException("No face policy for verification");
        try (Arena call = Arena.ofConfined()) {
            MemorySegment output = call.allocate(triples.length / 3);
            int result = (int)Bindings.QUERIES.invokeExact(call.allocateFrom(ValueLayout.JAVA_INT, triples),
                triples.length, output, triples.length / 3);
            if (result != 0) throw new IllegalArgumentException("Rejected face verification input: " + result);
            return output.toArray(ValueLayout.JAVA_BYTE);
        } catch (Throwable error) { throw failure(error); }
    }

}
