package net.minecraft.world.level.block;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Export immutable intrinsic CPU geometry once; Rust derives consumer policy. */
public final class NativeCollisionGeometry {
    private NativeCollisionGeometry() {}
    private static final class Holder { static final boolean READY = install(); }
    public static boolean ready() { return NativeBlockRegistry.ready() && Holder.READY; }
    private static boolean install() {
        int count = Block.BLOCK_STATE_REGISTRY.size();
        int[] states = new int[count]; Arrays.fill(states, -1);
        var unique = new LinkedHashMap<List<AABB>, Integer>();
        var seen = new IdentityHashMap<VoxelShape, Integer>();
        for (int id = 0; id < count; id++) {
            var state = Block.BLOCK_STATE_REGISTRY.byId(id);
            if (state.getClass() != BlockState.class || state.getBlock().hasDynamicShape()) continue;
            // The native registry's light-block fact proves this relation for
            // Frozen states. Other cached provider policies retain callbacks.
            if (!state.isAir() && !state.canOcclude() && state.getFluidState().isEmpty()
                && state.propagatesSkylightDown() != (state.getLightBlock() == 0)) continue;
            VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (!shape.hasCanonicalBoxGeometry()) continue;
            Integer geometry = seen.get(shape);
            if (geometry == null) {
                List<AABB> boxes = List.copyOf(shape.toAabbs());
                if (boxes.size() > 256) continue;
                geometry = unique.computeIfAbsent(boxes, ignored -> unique.size());
                seen.put(shape, geometry);
            }
            states[id] = geometry;
        }
        int boxCount = unique.keySet().stream().mapToInt(List::size).sum();
        if (unique.size() > 1024 || boxCount > 8192) return false;
        int[] rows = new int[unique.size() * 2]; double[] boxes = new double[boxCount * 6];
        int at = 0;
        for (var entry : unique.entrySet()) {
            int id = entry.getValue(); rows[id * 2] = at / 6; rows[id * 2 + 1] = entry.getKey().size();
            for (AABB box : entry.getKey()) {
                boxes[at++] = box.minX; boxes[at++] = box.minY; boxes[at++] = box.minZ;
                boxes[at++] = box.maxX; boxes[at++] = box.maxY; boxes[at++] = box.maxZ;
            }
        }
        MethodHandle install = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_collision_install",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        try (Arena call = Arena.ofConfined()) {
            int result = (int)install.invokeExact(call.allocateFrom(ValueLayout.JAVA_INT, states), states.length,
                call.allocateFrom(ValueLayout.JAVA_INT, rows), rows.length,
                call.allocateFrom(ValueLayout.JAVA_DOUBLE, boxes), boxes.length);
            if (result != 1) throw new IllegalStateException("Rejected native collision geometry: " + result);
            return true;
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot install native collision geometry", e); }
    }
}
