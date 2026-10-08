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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Installs the Rust block registry ({@code content/block}) from the frozen
 * block and block-state registries, once. Bridges that need per-state facts
 * pass state IDs and let Rust derive their tables from it; when it is not
 * {@link #ready()} they keep their Java paths. Layout: {@code content/block/export.rs}. */
public final class NativeBlockRegistry {
    private static final int FORMAT = 4;
    private static final int MAX_STATES = 65535;
    // Flag bits, shared with content/block/mod.rs StateFlags.
    static final int AIR = 1, BLOCKS_MOTION = 2, HAS_FLUID = 4, RANDOM_TICKS = 8, LIGHT_EMPTY_SHAPE = 16, LEAVES = 32, CUSTOM = 64,
        SOLID_RENDER = 128, CAN_OCCLUDE = 256, BLOCK_ENTITY = 512, FLUID_FALLING = 1024;
    private static final MethodHandle INSTALL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_block_registry_install",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
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
                    arena.allocateFrom(ValueLayout.JAVA_CHAR, export.chars), export.chars.length,
                    arena.allocateFrom(ValueLayout.JAVA_BYTE, export.bytes), export.bytes.length);
                return result == 1;
            }
        } catch (Throwable error) {
            return false;
        }
    }

    record Export(int[] ints, char[] chars, byte[] bytes) {}

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
        var properties = new IdentityHashMap<Property<?>, Integer>();
        var propertyList = new ArrayList<Property<?>>();
        var blocks = new ArrayList<Block>();
        int next = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (BuiltInRegistries.BLOCK.getId(block) != blocks.size()) return null;
            blocks.add(block);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (Block.BLOCK_STATE_REGISTRY.getId(state) != next || Block.BLOCK_STATE_REGISTRY.byId(next) != state) return null;
                next++;
            }
            for (Property<?> property : block.getStateDefinition().getProperties()) {
                if (!properties.containsKey(property)) {
                    properties.put(property, propertyList.size());
                    propertyList.add(property);
                }
            }
        }
        if (next != stateCount) return null;

        var ints = new Ints();
        var chars = new StringBuilder();
        for (int v : new int[]{FORMAT, propertyList.size(), blocks.size(), stateCount, 0}) ints.add(v);
        for (Property<?> property : propertyList) {
            ints.add(property.nativeDefinitionId());
            if (property.nativeDefinitionId() >= 0) continue;
            ints.add(property.getName().length());
            chars.append(property.getName());
            var values = valueNames(property);
            ints.add(values.size());
            for (String value : values) ints.add(value.length());
            for (String value : values) chars.append(value);
        }
        for (Block block : blocks) {
            String name = BuiltInRegistries.BLOCK.getKey(block).toString();
            var possible = block.getStateDefinition().getPossibleStates();
            ints.add(name.length());
            chars.append(name);
            ints.add(Block.BLOCK_STATE_REGISTRY.getId(block.defaultBlockState()) - Block.BLOCK_STATE_REGISTRY.getId(possible.get(0)));
            ints.add(Float.floatToRawIntBits(block.defaultBlockState().sodium$getMaxHorizontalOffset()));
            ints.add(Float.floatToRawIntBits(block.defaultBlockState().sodium$getMaxVerticalOffset()));
            var blockProperties = block.getStateDefinition().getProperties();
            ints.add(blockProperties.size());
            for (Property<?> property : blockProperties) ints.add(properties.get(property));
        }
        for (Block block : blocks) {
            var blockProperties = block.getStateDefinition().getProperties();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                for (Property<?> property : blockProperties) ints.add(property.getPossibleValues().indexOf(state.getValue(property)));
            }
        }
        // Light occlusion faces, interned by exact box list; the empty face is 0.
        var seen = new IdentityHashMap<VoxelShape, Integer>();
        var ids = new HashMap<List<AABB>, Integer>();
        var shapes = new ArrayList<VoxelShape>();
        face(Shapes.empty(), seen, ids, shapes);
        var bytes = new ByteArrayOutputStream(stateCount * 3);
        Direction[] directions = Direction.values();
        for (int id = 0; id < stateCount; id++) {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
            for (Direction direction : directions) ints.add(face(LightEngine.getOcclusionShape(state, direction), seen, ids, shapes));
            if (shapes.size() > 65535) return null;
            ints.add(flags(state));
            FluidState fluid = state.getFluidState();
            int fluidId = fluid.nativeStateId();
            if (fluidId < 0 || Fluid.FLUID_STATE_REGISTRY.byId(fluidId) != fluid) return null;
            ints.add(fluidId);
            bytes.write(state.getLightBlock());
            bytes.write(state.getLightEmission());
            bytes.write(state.sodium$getOffsetType().ordinal());
        }
        int faces = shapes.size();
        ints.set(4, faces);
        // The original shape operation supplies the immutable truth table.
        for (int from = 0; from < faces; from++) {
            for (int to = 0; to < faces; to++) bytes.write(Shapes.faceShapeOccludes(shapes.get(from), shapes.get(to)) ? 1 : 0);
        }
        char[] text = new char[chars.length()];
        chars.getChars(0, text.length, text, 0);
        return new Export(java.util.Arrays.copyOf(ints.values, ints.size), text, bytes.toByteArray());
    }

    static int flags(BlockState state) {
        int flags = 0;
        if (state.isAir()) flags |= AIR;
        if (state.blocksMotion()) flags |= BLOCKS_MOTION;
        if (state.isRandomlyTicking()) flags |= RANDOM_TICKS;
        // LightEngine.isEmptyShape.
        if (!state.canOcclude() || !state.useShapeForLightOcclusion()) flags |= LIGHT_EMPTY_SHAPE;
        if (state.getBlock() instanceof LeavesBlock) flags |= LEAVES;
        if (state.getClass() != BlockState.class) flags |= CUSTOM;
        if (state.isSolidRender()) flags |= SOLID_RENDER;
        if (state.canOcclude()) flags |= CAN_OCCLUDE;
        if (state.hasBlockEntity()) flags |= BLOCK_ENTITY;
        return flags;
    }

    private static <T extends Comparable<T>> List<String> valueNames(Property<T> property) {
        var names = new ArrayList<String>();
        for (T value : property.getPossibleValues()) names.add(property.getName(value));
        return names;
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
