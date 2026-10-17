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
        byte[] worldFlags = new byte[count];
        int[] worldStates = new int[count]; Arrays.fill(worldStates, -1);
        record Grid(int geometry, List<Double> x, List<Double> y, List<Double> z) {}
        var worldGrids = new LinkedHashMap<Grid, Integer>();
        var providers = new HashMap<Class<?>, Boolean>();
        var unique = new LinkedHashMap<List<AABB>, Integer>();
        var seen = new IdentityHashMap<VoxelShape, Integer>();
        for (int id = 0; id < count; id++) {
            var state = Block.BLOCK_STATE_REGISTRY.byId(id);
            if (state.getClass() != BlockState.class) continue;
            worldFlags[id] = (byte)(16 | (state.hasLargeCollisionShape() ? 2 : 0)
                | (state.is(Blocks.MOVING_PISTON) ? 8 : 0));
            if (state.getBlock().hasDynamicShape()) continue;
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
            if (providers.computeIfAbsent(state.getBlock().getClass(), NativeCollisionGeometry::contextFreeProvider)) {
                var x = List.copyOf(shape.getCoords(net.minecraft.core.Direction.Axis.X));
                var y = List.copyOf(shape.getCoords(net.minecraft.core.Direction.Axis.Y));
                var z = List.copyOf(shape.getCoords(net.minecraft.core.Direction.Axis.Z));
                if (x.size() > 65 || y.size() > 65 || z.size() > 65) continue;
                var grid = new Grid(geometry, x, y, z);
                worldStates[id] = worldGrids.computeIfAbsent(grid, ignored -> worldGrids.size());
                worldFlags[id] |= (byte)(1 | (shape == Shapes.block() ? 4 : 0));
            }
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
            int[] gridRows = new int[worldGrids.size() * 7];
            var coordinates = new ArrayList<Double>();
            for (var entry : worldGrids.entrySet()) {
                int row = entry.getValue() * 7;
                var grid = entry.getKey(); gridRows[row++] = grid.geometry();
                for (var axis : List.of(grid.x(), grid.y(), grid.z())) {
                    gridRows[row++] = coordinates.size(); gridRows[row++] = axis.size();
                    coordinates.addAll(axis);
                }
            }
            double[] gridCoordinates = coordinates.stream().mapToDouble(Double::doubleValue).toArray();
            var worldInstall = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_collision_world_install",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            int worldResult = (int)worldInstall.invokeExact(call.allocateFrom(ValueLayout.JAVA_BYTE, worldFlags),
                call.allocateFrom(ValueLayout.JAVA_INT, worldStates), worldFlags.length,
                call.allocateFrom(ValueLayout.JAVA_INT, gridRows), gridRows.length,
                call.allocateFrom(ValueLayout.JAVA_DOUBLE, gridCoordinates), gridCoordinates.length);
            if (worldResult != 1) throw new IllegalStateException("Rejected native world collision facts: " + worldResult);
            // DH solidity/opacity for every state, including states without
            // cached geometry: the same context-free policy BlockStateWrapper uses.
            byte[] solid = new byte[count], opacity = new byte[count];
            for (int id = 0; id < count; id++) {
                var state = Block.BLOCK_STATE_REGISTRY.byId(id);
                solid[id] = (byte)(dhSolid(state) ? 1 : 0); opacity[id] = (byte)dhOpacity(state);
            }
            var dhInstall = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_collision_dh_install",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            int dhResult = (int)dhInstall.invokeExact(call.allocateFrom(ValueLayout.JAVA_BYTE, solid),
                call.allocateFrom(ValueLayout.JAVA_BYTE, opacity), count);
            if (dhResult != 1) throw new IllegalStateException("Rejected native DH block facts");
            return true;
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Cannot install native collision geometry", e); }
    }
    /** DH solidity: a non-air state with a non-empty context-free collision shape. */
    public static boolean dhSolid(BlockState state) {
        return state != null && !state.isAir() && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }
    /**
     * DH opacity from context-free state queries: air 0, a non-occluding fluid
     * 1 (translucent), a non-occluding sky-light-passing block 0, otherwise 16.
     */
    public static int dhOpacity(BlockState state) {
        if (state == null || state.isAir()) return 0;
        boolean canOcclude = state.canOcclude();
        if (!state.getFluidState().isEmpty() && !canOcclude) return 1;
        if (state.propagatesSkylightDown() && !canOcclude) return 0;
        return 16;
    }
    /** Audited stock providers that read only state, never the world or context. */
    private static boolean contextFreeProvider(Class<?> type) {
        Class<?> collision = declaring(type, "getCollisionShape");
        if (collision == SnowLayerBlock.class || type == WallBlock.class) return true;
        if (collision != net.minecraft.world.level.block.state.BlockBehaviour.class) return false;
        Class<?> shape = declaring(type, "getShape");
        return shape == net.minecraft.world.level.block.state.BlockBehaviour.class || shape == AirBlock.class
            || shape == SlabBlock.class || shape == StairBlock.class;
    }
    private static Class<?> declaring(Class<?> type, String method) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try { c.getDeclaredMethod(method, BlockState.class, net.minecraft.world.level.BlockGetter.class,
                BlockPos.class, CollisionContext.class); return c; }
            catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

}
