package net.vulkanic.world;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.NativeLibraryLoader;
import net.vulkanic.bridge.VulkanicGalBridge;
import net.vulkanic.bridge.VulkanicGalBridge.*;
import org.joml.Matrix4f;

/** Frame-scoped CPU model storage. The render producer serializes writes;
 * completion/abort closes the owner after the last Java receipt has read it.
 * VulkanicGAL still owns all GPU work and copies semantic data at submission. */
public final class NativeModelFrame implements AutoCloseable {
    private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_model_frame_" + name, descriptor);
    }
    private static final MethodHandle CREATE = bind("create", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle RELEASE = bind("release", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle RESERVE = bind("reserve_poses", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle COMMIT = bind("commit_poses", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle APPEND = bind("append", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle VARIANT = bind("variant", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_FLOAT));
    private static final MethodHandle ROLLBACK = bind("rollback", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle SEAL = bind("seal", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final Struct LAYOUT = Struct.WORLD_MESH_INSTANCE_RECORD;
    // Java extraction, projection and completion all run on the render thread.
    // Rust's worker borrows CPU pointers until join; it never accesses Java scopes.
    private final Arena arena = Arena.ofConfined();
    private boolean sealed;
    private final MemorySegment owner;
    private final MemorySegment input;
    private MemorySegment records = MemorySegment.NULL, poses = MemorySegment.NULL, versions = MemorySegment.NULL;

    public NativeModelFrame() {
        try {
            var pointer = (MemorySegment)CREATE.invokeExact();
            try { owner = pointer.reinterpret(48, arena, NativeModelFrame::release); }
            catch (Throwable failure) { release(pointer); throw failure; }
            input = arena.allocate(280, 8);
        } catch (Throwable failure) { arena.close(); throw failure(failure); }
    }
    private static RuntimeException failure(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Native model frame call failed", failure);
    }
    private static void release(MemorySegment owner) {
        try { RELEASE.invokeExact(owner); } catch (Throwable failure) { throw failure(failure); }
    }
    @Override public void close() { if (arena.scope().isAlive()) arena.close(); }
    public boolean isAlive() { return arena.scope().isAlive(); }
    public int instanceCount() { return owner.get(ValueLayout.JAVA_INT, 24); }
    public int poseCount() { return owner.get(ValueLayout.JAVA_INT, 28); }

    private MemorySegment refresh(MemorySegment previous, long pointer, long size, boolean readOnly) {
        if (previous.address() == pointer && previous.byteSize() == size) return previous;
        var next = MemorySegment.ofAddress(pointer).reinterpret(size, arena, null);
        return readOnly ? next.asReadOnly() : next;
    }
    private void refreshInstances() {
        records = refresh(records, owner.get(ValueLayout.JAVA_LONG, 0),
            Integer.toUnsignedLong(owner.get(ValueLayout.JAVA_INT, 16)) * LAYOUT.byteSize(), true);
        versions = refresh(versions, owner.get(ValueLayout.JAVA_LONG, 32),
            Integer.toUnsignedLong(owner.get(ValueLayout.JAVA_INT, 40)) * 8, true);
    }
    private void refreshPoses() {
        poses = refresh(poses, owner.get(ValueLayout.JAVA_LONG, 8),
            Integer.toUnsignedLong(owner.get(ValueLayout.JAVA_INT, 20)) * 40, false);
    }
    private void refresh() { refreshInstances(); refreshPoses(); }
    public void rollback(int instances, int poseCount) {
        try {
            if ((int)ROLLBACK.invokeExact(owner, instances, poseCount) == 0)
                throw new IllegalArgumentException("model frame checkpoint is outside its producer scope");
            refresh();
        } catch (Throwable failure) { throw failure(failure); }
    }
    public void seal() {
        try { SEAL.invokeExact(owner); refresh(); sealed = true; } catch (Throwable failure) { throw failure(failure); }
    }
    public MemorySegment poseData() {
        if (!sealed) throw new IllegalStateException("model pose views require a sealed frame");
        return poses.asSlice(0, (long)poseCount() * 40).asReadOnly();
    }

    public WorldMeshInstance append(ModelPart[] nodes, long rig, Matrix4f transform,
        int depth, int cull, int color, int entityColor, int outline, int light,
        int width, int height, int flags, StandardItemFoilRecord foil) {
        try {
            int first = (int)RESERVE.invokeExact(owner, nodes.length);
            refreshPoses();
            if (first < 0) throw new IllegalArgumentException("model pose reservation exceeds its frame scope");
            for (int i = 0; i < nodes.length; i++) {
                ModelPart part = nodes[i];
                // One checked node slice makes every scalar field offset constant.
                MemorySegment pose = poses.asSlice((long)(first + i) * 40, 40);
                pose.set(ValueLayout.JAVA_FLOAT, 0, part.x);
                pose.set(ValueLayout.JAVA_FLOAT, 4, part.y);
                pose.set(ValueLayout.JAVA_FLOAT, 8, part.z);
                pose.set(ValueLayout.JAVA_FLOAT, 12, part.xRot);
                pose.set(ValueLayout.JAVA_FLOAT, 16, part.yRot);
                pose.set(ValueLayout.JAVA_FLOAT, 20, part.zRot);
                pose.set(ValueLayout.JAVA_FLOAT, 24, part.xScale);
                pose.set(ValueLayout.JAVA_FLOAT, 28, part.yScale);
                pose.set(ValueLayout.JAVA_FLOAT, 32, part.zScale);
                pose.set(ValueLayout.JAVA_INT, 36, (part.visible ? 1 : 0) | (part.skipDraw ? 2 : 0));
            }
            if ((int)COMMIT.invokeExact(owner, first, nodes.length) == 0)
                throw new IllegalStateException("model pose reservation changed before commit");
            input.fill((byte)0);
            input.set(ValueLayout.JAVA_LONG, 0, rig);
            put(8, first); put(12, flags); put(16, depth); put(20, cull);
            put(24, color); put(28, entityColor); put(32, outline); put(36, light);
            put(40, width); put(44, height);
            int blockId = VulkanicGalBridge.activeSemanticBlockEntityId(); put(48, blockId);
            Integer order = VulkanicGalBridge.activeSemanticModelOrder();
            if (order != null) { put(52, order); put(56, 1); }
            if (foil != null) {
                put(60, foil.kind().wireValue()); input.set(ValueLayout.JAVA_LONG, 64, foil.clockMillis());
                input.set(ValueLayout.JAVA_DOUBLE, 72, foil.speed()); input.set(ValueLayout.JAVA_FLOAT, 80, foil.strength());
            }
            // Matrix components are semantic facts, written once without a Java array.
            input.set(ValueLayout.JAVA_FLOAT, 88, transform.m00());
            input.set(ValueLayout.JAVA_FLOAT, 92, transform.m01());
            input.set(ValueLayout.JAVA_FLOAT, 96, transform.m02());
            input.set(ValueLayout.JAVA_FLOAT, 100, transform.m03());
            input.set(ValueLayout.JAVA_FLOAT, 104, transform.m10());
            input.set(ValueLayout.JAVA_FLOAT, 108, transform.m11());
            input.set(ValueLayout.JAVA_FLOAT, 112, transform.m12());
            input.set(ValueLayout.JAVA_FLOAT, 116, transform.m13());
            input.set(ValueLayout.JAVA_FLOAT, 120, transform.m20());
            input.set(ValueLayout.JAVA_FLOAT, 124, transform.m21());
            input.set(ValueLayout.JAVA_FLOAT, 128, transform.m22());
            input.set(ValueLayout.JAVA_FLOAT, 132, transform.m23());
            input.set(ValueLayout.JAVA_FLOAT, 136, transform.m30());
            input.set(ValueLayout.JAVA_FLOAT, 140, transform.m31());
            input.set(ValueLayout.JAVA_FLOAT, 144, transform.m32());
            input.set(ValueLayout.JAVA_FLOAT, 148, transform.m33());
            var culling = blockId == -1 ? VulkanicGalBridge.activeSemanticEntityCulling() : null;
            if (culling != null) {
                put(152, 1); put(156, culling.flags());
                writeBounds(160, culling.bounds());
                if (culling.leashHolderBounds() != null) writeBounds(208, culling.leashHolderBounds());
                input.set(ValueLayout.JAVA_DOUBLE, 256, culling.cameraX());
                input.set(ValueLayout.JAVA_DOUBLE, 264, culling.cameraY());
                input.set(ValueLayout.JAVA_DOUBLE, 272, culling.cameraZ());
            }
            int index = (int)APPEND.invokeExact(owner, input);
            refreshInstances();
            if (index < 0) throw new IllegalArgumentException("invalid or over-capacity native model instance");
            return new Instance(index);
        } catch (Throwable failure) { throw failure(failure); }
    }
    private void put(long offset, int value) { input.set(ValueLayout.JAVA_INT, offset, value); }
    private void writeBounds(long offset, WorldAabbRecord bounds) {
        input.set(ValueLayout.JAVA_DOUBLE, offset, bounds.minX()); input.set(ValueLayout.JAVA_DOUBLE, offset + 8, bounds.minY());
        input.set(ValueLayout.JAVA_DOUBLE, offset + 16, bounds.minZ()); input.set(ValueLayout.JAVA_DOUBLE, offset + 24, bounds.maxX());
        input.set(ValueLayout.JAVA_DOUBLE, offset + 32, bounds.maxY()); input.set(ValueLayout.JAVA_DOUBLE, offset + 40, bounds.maxZ());
    }

    private final class Instance implements WorldMeshInstance {
        private final int index;
        private final long base;
        private final long version;
        Instance(int index) { this.index = index; base = (long)index * LAYOUT.byteSize(); version = versions.get(ValueLayout.JAVA_LONG, index * 8L); }
        private void check() {
            if (index >= instanceCount() || versions.get(ValueLayout.JAVA_LONG, index * 8L) != version)
                throw new IllegalStateException("model instance was rolled back");
        }
        private int i(int field) { check(); return records.get(ValueLayout.JAVA_INT, base + LAYOUT.offset(field)); }
        private long l(int field) { check(); return records.get(ValueLayout.JAVA_LONG, base + LAYOUT.offset(field)); }
        private double d(int field) { check(); return records.get(ValueLayout.JAVA_DOUBLE, base + LAYOUT.offset(field)); }
        @Override public int stratum() { return i(1); }
        @Override public int meshSectionIndex() { return i(2); }
        @Override public int depthPolicy() { return i(3); }
        @Override public int cullPolicy() { return i(4); }
        @Override public int winding() { return i(5); }
        @Override public int colorArgb() { return i(6); }
        @Override public int viewportWidth() { return i(7); }
        @Override public int viewportHeight() { return i(8); }
        @Override public long meshKey() { return l(9); }
        @Override public long meshGeneration() { return l(10); }
        @Override public int entityId() { return i(11); }
        @Override public int entityColorArgb() { return i(12); }
        @Override public float[] transform() { check(); return records.asSlice(base + LAYOUT.offset(13), 64).toArray(ValueLayout.JAVA_FLOAT); }
        @Override public int outlineColorArgb() { return i(14); }
        @Override public int flags() { return i(15); }
        @Override public int blockEntityId() { return i(16); }
        @Override public TerrainSectionPlacement terrainPlacement() { return null; }
        @Override public WorldDecalFoilRecord decalFoil() { return null; }
        @Override public Integer modelSubmissionOrder() { return i(28) == 0 ? null : i(29); }
        @Override public int packedLight() { return i(30); }
        @Override public NativeItemLayerTransform.WorldPose nativeItemPose() { return null; }
        @Override public StandardItemFoilRecord itemFoil() {
            int mode = i(20); if (mode == 0) return null;
            return new StandardItemFoilRecord(l(21), d(22), records.get(ValueLayout.JAVA_FLOAT, base + LAYOUT.offset(23)),
                switch (mode) { case 1 -> StandardFoilKind.ITEM; case 2 -> StandardFoilKind.ENTITY;
                    case 3 -> StandardFoilKind.ARMOR; case 4 -> StandardFoilKind.ARMOR_ORTHOGRAPHIC;
                    default -> throw new IllegalStateException("invalid native foil mode"); });
        }
        private WorldAabbRecord bounds(int field) {
            long p = base + LAYOUT.offset(field);
            return new WorldAabbRecord(records.get(ValueLayout.JAVA_DOUBLE, p), records.get(ValueLayout.JAVA_DOUBLE, p + 8),
                records.get(ValueLayout.JAVA_DOUBLE, p + 16), records.get(ValueLayout.JAVA_DOUBLE, p + 24),
                records.get(ValueLayout.JAVA_DOUBLE, p + 32), records.get(ValueLayout.JAVA_DOUBLE, p + 40));
        }
        @Override public WorldEntityCullingRecord entityCulling() {
            if (i(31) == 0) return null;
            long p = base + LAYOUT.offset(35); int flags = i(32);
            return new WorldEntityCullingRecord(flags, bounds(33), (flags & WorldEntityCullingRecord.LEASH_HOLDER) != 0 ? bounds(34) : null,
                records.get(ValueLayout.JAVA_DOUBLE, p), records.get(ValueLayout.JAVA_DOUBLE, p + 8), records.get(ValueLayout.JAVA_DOUBLE, p + 16));
        }
        @Override public void encodeInto(MemorySegment target, TerrainFrameCamera camera) {
            check();
            MemorySegment.copy(records, base, target, 0, LAYOUT.byteSize());
        }
        private WorldMeshInstance variant(int kind, long a, long b, double speed, float strength) {
            check();
            try {
                int next = (int)VARIANT.invokeExact(owner, index, kind, a, b, speed, strength);
                refreshInstances();
                if (next < 0) throw new IllegalArgumentException("invalid or over-capacity native model variant");
                return new Instance(next);
            } catch (Throwable failure) { throw failure(failure); }
        }
        @Override public WorldMeshInstance withViewport(int width, int height) { return variant(1, width, height, 0, 0); }
        @Override public WorldMeshInstance withPackedLight(int light) { return variant(2, light, 0, 0, 0); }
        @Override public WorldMeshInstance withModelSubmissionOrder(Integer order) { return variant(3, order == null ? 0 : order, order == null ? 0 : 1, 0, 0); }
        @Override public WorldMeshInstance asShadowOnlyEntityCaster() { return variant(4, 0, 0, 0, 0); }
        @Override public WorldMeshInstance withItemFoil(StandardItemFoilRecord foil) { return variant(5, foil.kind().wireValue(), foil.clockMillis(), foil.speed(), foil.strength()); }
        @Override public WorldMeshInstance withNativeDecalFoil() { throw new IllegalArgumentException("native decal requires a native item pose"); }
        @Override public WorldMeshInstance withDecalFoil(WorldDecalFoilRecord decal) { throw new IllegalArgumentException("model rigs cannot carry decal foil"); }
        @Override public WorldMeshInstance withTerrainPlacement(TerrainSectionPlacement placement) { throw new IllegalArgumentException("model rigs cannot carry terrain placement"); }
    }
}
