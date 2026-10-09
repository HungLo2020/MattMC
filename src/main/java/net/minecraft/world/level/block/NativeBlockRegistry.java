package net.minecraft.world.level.block;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.NativeBlockDefinitions;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Installs the Rust block registry ({@code content/block}) from the frozen
 * block and block-state registries, once. Bridges that need per-state facts
 * pass state IDs and let Rust derive their tables from it; when it is not
 * {@link #ready()} they keep their Java paths. Layout: {@code content/block/export.rs}. */
public final class NativeBlockRegistry {
    private static final int FORMAT = 8;
    private static final int MAX_STATES = 65535;
    // Flag bits, shared with content/block/mod.rs StateFlags.
    static final int AIR = 1, BLOCKS_MOTION = 2, HAS_FLUID = 4, RANDOM_TICKS = 8, LIGHT_EMPTY_SHAPE = 16, LEAVES = 32, CUSTOM = 64,
        SOLID_RENDER = 128, CAN_OCCLUDE = 256, BLOCK_ENTITY = 512, FLUID_FALLING = 1024;
    private static final MethodHandle INSTALL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_block_registry_install",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle COLUMN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_block_registry_column",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private NativeBlockRegistry() {}

    private static final class Holder {
        static final boolean READY = install();
    }

    /** True once Rust holds a registry equal to Java's. Every state is then
     * a distinct {@code BlockState} whose ID equals its position. */
    public static boolean ready() {
        return Holder.READY;
    }

    private static boolean install() {
        try {
            Export export = export();
            if (export == null) return false;
            try (Arena arena = Arena.ofConfined()) {
                int result = (int)INSTALL.invokeExact(arena.allocateFrom(ValueLayout.JAVA_INT, export.ints), export.ints.length,
                    arena.allocateFrom(ValueLayout.JAVA_BYTE, export.bytes), export.bytes.length);
                return result == 1;
            }
        } catch (Throwable error) {
            return false;
        }
    }

    record Export(int[] ints, byte[] bytes) {}

    private static final class Ints {
        int[] values = new int[1 << 16];
        int size;

        void add(int value) {
            if (this.size == this.values.length) this.values = java.util.Arrays.copyOf(this.values, this.size * 2);
            this.values[this.size++] = value;
        }

        void set(int at, int value) {
            this.values[at] = value;
        }
    }

    /** Null when the registries do not have the layout Rust models: every
     * block's states contiguous in block order, each a distinct object. */
    @Nullable
    static Export export() {
        int stateCount = Block.BLOCK_STATE_REGISTRY.size();
        if (stateCount < 1 || stateCount > MAX_STATES) return null;
        var blocks = new ArrayList<Block>();
        int next = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (BuiltInRegistries.BLOCK.getId(block) != blocks.size()) return null;
            blocks.add(block);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (Block.BLOCK_STATE_REGISTRY.getId(state) != next || Block.BLOCK_STATE_REGISTRY.byId(next) != state) return null;
                next++;
            }
            NativeBlockDefinitions.Definition definition = block.nativeDefinition();
            if (definition == null || definition.id != blocks.size() - 1
                || definition.firstState + definition.stateCount() != next
                || block.defaultBlockState() != block.getStateDefinition().getPossibleStates().get(definition.defaultLocalState())) return null;
        }
        if (next != stateCount) return null;

        var ints = new Ints();
        for (int v : new int[]{FORMAT, blocks.size(), stateCount, 0}) ints.add(v);
        // Light occlusion faces, interned by exact box list; the empty face is 0.
        var seen = new IdentityHashMap<VoxelShape, Integer>();
        var ids = new HashMap<List<AABB>, Integer>();
        var shapes = new ArrayList<VoxelShape>();
        face(Shapes.empty(), seen, ids, shapes);
        var bytes = new ByteArrayOutputStream(stateCount);
        Direction[] directions = Direction.values();
        for (int id = 0; id < stateCount; id++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
            for (Direction direction : directions) ints.add(face(LightEngine.getOcclusionShape(state, direction), seen, ids, shapes));
            if (shapes.size() > 65535) return null;
            ints.add(flags(state));
            bytes.write(state.getLightBlock());
        }
        int faces = shapes.size();
        ints.set(3, faces);
        // The original shape operation supplies the immutable truth table.
        for (int from = 0; from < faces; from++) {
            for (int to = 0; to < faces; to++) bytes.write(Shapes.faceShapeOccludes(shapes.get(from), shapes.get(to)) ? 1 : 0);
        }
        return new Export(java.util.Arrays.copyOf(ints.values, ints.size), bytes.toByteArray());
    }

    // AIR/CAN_OCCLUDE and fluid flags are derived by their native owners.
    static int flags(BlockState state) {
        int flags = 0;
        if (state.blocksMotion()) flags |= BLOCKS_MOTION;
        if (state.isRandomlyTicking()) flags |= RANDOM_TICKS;
        // LightEngine.isEmptyShape.
        if (!state.canOcclude() || !state.useShapeForLightOcclusion()) flags |= LIGHT_EMPTY_SHAPE;
        if (state.getBlock() instanceof LeavesBlock) flags |= LEAVES;
        if (state.getClass() != BlockState.class) flags |= CUSTOM;
        if (state.isSolidRender()) flags |= SOLID_RENDER;
        if (state.hasBlockEntity()) flags |= BLOCK_ENTITY;
        return flags;
    }



    private static int face(VoxelShape shape, IdentityHashMap<VoxelShape, Integer> seen, HashMap<List<AABB>, Integer> ids, ArrayList<VoxelShape> shapes) {
        Integer known = seen.get(shape);
        if (known != null) return known;
        // Exact box lists; no coordinate quantization or shape approximation.
        var key = List.copyOf(shape.toAabbs());
        Integer id = ids.get(key);
        if (id == null) {
            id = shapes.size();
            ids.put(key, id);
            shapes.add(shape);
        }
        seen.put(shape, id);
        return id;
    }

    /** A column of the installed registry, for verification (kinds in
     * {@code content/block/ffi.rs}); null when not installed. */
    @Nullable
    static int[] column(int kind) {
        if (!ready()) return null;
        try {
            int count = (int)COLUMN.invokeExact(kind, MemorySegment.NULL, 0);
            if (count < 0) return null;
            int[] values = new int[count];
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment out = arena.allocate(Math.max(1, count) * 4L, 4);
                if ((int)COLUMN.invokeExact(kind, out, count) != count) return null;
                MemorySegment.copy(out, ValueLayout.JAVA_INT, 0, values, 0, count);
            }
            return values;
        } catch (Throwable error) {
            throw new IllegalStateException("Block registry column " + kind, error);
        }
    }
}
