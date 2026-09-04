package net.sodium.client.render.chunk.occlusion;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.Level;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.lists.RenderSectionVisitor;
import net.sodium.client.render.viewport.CameraTransform;
import net.sodium.client.render.viewport.Viewport;
import net.sodium.client.util.collections.DoubleBufferedQueue;
import net.sodium.client.util.collections.ReadQueue;
import net.sodium.client.util.collections.WriteQueue;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryUtil;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class OcclusionCuller {
    public static final long NULL_VISIBILITY = 0L;

    private static final int OK = 0;
    private static final int VISIBILITY_MATRIX_LENGTH = GraphDirection.COUNT * GraphDirection.COUNT;
    private static final ThreadLocal<NativeScratch> NATIVE_SCRATCH = ThreadLocal.withInitial(NativeScratch::new);

    private static final MethodHandle VERIFY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_occlusion_verify",
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
    private static final MethodHandle ENCODE_VISIBILITY = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_occlusion_encode_visibility",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle CONNECTIONS = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_occlusion_connections",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
	private static final MethodHandle CAMERA_CONNECTIONS = NativeLibraryLoader.downcallHandle("mattmc_rust",
			"mattmc_sodium_occlusion_connections_for_camera",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
					ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle CONNECTIONS_BATCH = NativeLibraryLoader.downcallHandle("mattmc_rust",
            "mattmc_sodium_occlusion_connections_batch",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT));
    private static final int VERIFY_STATUS = invokeVerify();

    private final Long2ReferenceMap<RenderSection> sections;
    private final Level level;

    private final DoubleBufferedQueue<RenderSection> queue = new DoubleBufferedQueue<>();

    public OcclusionCuller(Long2ReferenceMap<RenderSection> sections, Level level) {
        this.sections = sections;
        this.level = level;
    }

    public void findVisible(RenderSectionVisitor visitor,
                            Viewport viewport,
                            float searchDistance,
                            boolean useOcclusionCulling,
                            int frame)
    {
        final var queues = this.queue;
        queues.reset();

        this.init(visitor, queues.write(), viewport, searchDistance, useOcclusionCulling, frame);

        while (queues.flip()) {
            processQueue(visitor, viewport, searchDistance, useOcclusionCulling, frame, queues.read(), queues.write());
        }

        this.addNearbySections(visitor, viewport, searchDistance, frame);
    }

    private static void processQueue(RenderSectionVisitor visitor,
                                     Viewport viewport,
                                     float searchDistance,
                                     boolean useOcclusionCulling,
                                     int frame,
                                     ReadQueue<RenderSection> readQueue,
                                     WriteQueue<RenderSection> writeQueue)
    {
        RenderSection section;
        NativeScratch scratch = useOcclusionCulling ? NATIVE_SCRATCH.get() : null;
        if (scratch != null) {
            scratch.clear();
        }

        while ((section = readQueue.dequeue()) != null) {
            if (!isSectionVisible(section, viewport, searchDistance)) {
                continue;
            }

            if (useOcclusionCulling) {
                scratch.add(section, viewport);
            } else {
                visitor.visit(section);
                visitNeighbors(writeQueue, section,
                        GraphDirectionSet.ALL & getOutwardDirections(viewport.getChunkCoord(), section), frame);
            }
        }

        if (!useOcclusionCulling || scratch.count == 0) {
            return;
        }

        computeOcclusionConnectionsBatch(scratch);
        for (int index = 0; index < scratch.count; index++) {
            section = scratch.sections[index];
            visitor.visit(section);

            // When using occlusion culling, Rust calculates all outgoing paths for this BFS wave in one native call.
            int connections = scratch.connections.getInt(index * Integer.BYTES);
            connections &= getOutwardDirections(viewport.getChunkCoord(), section);

            visitNeighbors(writeQueue, section, connections, frame);
            scratch.sections[index] = null;
        }
    }

    private static boolean isSectionVisible(RenderSection section, Viewport viewport, float maxDistance) {
        return isWithinRenderDistance(viewport.getTransform(), section, maxDistance) && isWithinFrustum(viewport, section);
    }

    private static void visitNeighbors(final WriteQueue<RenderSection> queue, RenderSection section, int outgoing, int frame) {
        // Only traverse into neighbors which are actually present.
        // This avoids a null-check on each invocation to enqueue, and since the compiler will see that a null
        // is never encountered (after profiling), it will optimize it away.
        outgoing &= section.getAdjacentMask();

        // Check if there are any valid connections left, and if not, early-exit.
        if (outgoing == GraphDirectionSet.NONE) {
            return;
        }

        // This helps the compiler move the checks for some invariants upwards.
        queue.ensureCapacity(6);

        if (GraphDirectionSet.contains(outgoing, GraphDirection.DOWN)) {
            visitNode(queue, section.adjacentDown, GraphDirectionSet.of(GraphDirection.UP), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.UP)) {
            visitNode(queue, section.adjacentUp, GraphDirectionSet.of(GraphDirection.DOWN), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.NORTH)) {
            visitNode(queue, section.adjacentNorth, GraphDirectionSet.of(GraphDirection.SOUTH), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.SOUTH)) {
            visitNode(queue, section.adjacentSouth, GraphDirectionSet.of(GraphDirection.NORTH), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.WEST)) {
            visitNode(queue, section.adjacentWest, GraphDirectionSet.of(GraphDirection.EAST), frame);
        }

        if (GraphDirectionSet.contains(outgoing, GraphDirection.EAST)) {
            visitNode(queue, section.adjacentEast, GraphDirectionSet.of(GraphDirection.WEST), frame);
        }
    }

    private static void visitNode(final WriteQueue<RenderSection> queue, @NotNull RenderSection render, int incoming, int frame) {
        if (render.getLastVisibleFrame() != frame) {
            // This is the first time we are visiting this section during the given frame, so we must
            // reset the state.
            render.setLastVisibleFrame(frame);
            render.setIncomingDirections(GraphDirectionSet.NONE);

            queue.enqueue(render);
        }

        render.addIncomingDirections(incoming);
    }

    private static int getOutwardDirections(SectionPos origin, RenderSection section) {
        int planes = 0;

        planes |= section.getChunkX() <= origin.getX() ? 1 << GraphDirection.WEST  : 0;
        planes |= section.getChunkX() >= origin.getX() ? 1 << GraphDirection.EAST  : 0;

        planes |= section.getChunkY() <= origin.getY() ? 1 << GraphDirection.DOWN  : 0;
        planes |= section.getChunkY() >= origin.getY() ? 1 << GraphDirection.UP    : 0;

        planes |= section.getChunkZ() <= origin.getZ() ? 1 << GraphDirection.NORTH : 0;
        planes |= section.getChunkZ() >= origin.getZ() ? 1 << GraphDirection.SOUTH : 0;

        return planes;
    }

    private static boolean isWithinRenderDistance(CameraTransform camera, RenderSection section, float maxDistance) {
        // origin point of the chunk's bounding box (in view space)
        int ox = section.getOriginX() - camera.intX;
        int oy = section.getOriginY() - camera.intY;
        int oz = section.getOriginZ() - camera.intZ;

        // coordinates of the point to compare (in view space)
        // this is the closest point within the bounding box to the center (0, 0, 0)
        // the bounding box is expanded by 1 block in each direction due to the maximum allowed size of block models.
        float dx = nearestToZero(ox - 1, ox + 17) - camera.fracX;
        float dy = nearestToZero(oy - 1, oy + 17) - camera.fracY;
        float dz = nearestToZero(oz - 1, oz + 17) - camera.fracZ;

        // vanilla's "cylindrical fog" algorithm
        // max(length(distance.xz), abs(distance.y))
        return (((dx * dx) + (dz * dz)) < (maxDistance * maxDistance)) && (Math.abs(dy) < maxDistance);
    }

    @SuppressWarnings("ManualMinMaxCalculation") // we know what we are doing.
    private static int nearestToZero(int min, int max) {
        // this compiles to slightly better code than Math.min(Math.max(0, min), max)
        int clamped = 0;
        if (min > 0) { clamped = min; }
        if (max < 0) { clamped = max; }
        return clamped;
    }

    // The bounding box of a chunk section must be large enough to contain all possible geometry within it. Block models
    // can extend outside a block volume by +/- 1.0 blocks on all axis. Additionally, we make use of a small epsilon
    // to deal with floating point imprecision during a frustum check (see GH#2132).
    public static final float CHUNK_SECTION_RADIUS = 8.0f /* chunk bounds */;
    public static final float CHUNK_SECTION_MARGIN = 1.0f /* maximum model extent */ + 0.125f /* epsilon */;
    public static final float CHUNK_SECTION_SIZE = CHUNK_SECTION_RADIUS + CHUNK_SECTION_MARGIN;

    public static boolean isWithinFrustum(Viewport viewport, RenderSection section) {
        return viewport.isBoxVisible(section.getCenterX(), section.getCenterY(), section.getCenterZ(),
                CHUNK_SECTION_SIZE, CHUNK_SECTION_SIZE, CHUNK_SECTION_SIZE);
    }

    // this bigger chunk section size is only used for frustum-testing nearby sections with large models
    // CPU visibility convention shared with independent semantic producers.
    // This exposes no render-list or backend ownership.
    public static final float CHUNK_SECTION_SIZE_NEARBY = CHUNK_SECTION_RADIUS + 2.0f /* bigger model extent */ + 0.125f /* epsilon */;
    
    public static boolean isWithinNearbySectionFrustum(Viewport viewport, RenderSection section) {
        return viewport.isBoxVisible(section.getCenterX(), section.getCenterY(), section.getCenterZ(),
                CHUNK_SECTION_SIZE_NEARBY, CHUNK_SECTION_SIZE_NEARBY, CHUNK_SECTION_SIZE_NEARBY);
    }

    // This method visits sections near the origin that are not in the path of the graph traversal
    // but have bounding boxes that may intersect with the frustum. It does this additional check
    // for all neighboring, even diagonally neighboring, sections around the origin to render them
    // if their extended bounding box is visible, and they may render large models that extend
    // outside the 16x16x16 base volume of the section.
    private void addNearbySections(RenderSectionVisitor visitor, Viewport viewport, float searchDistance, int frame) {
        var origin = viewport.getChunkCoord();
        var originX = origin.getX();
        var originY = origin.getY();
        var originZ = origin.getZ();

        for (var dx = -1; dx <= 1; dx++) {
            for (var dy = -1; dy <= 1; dy++) {
                for (var dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    var section = this.getRenderSection(originX + dx, originY + dy, originZ + dz);

                    // additionally render not yet visited but visible sections
                    if (section != null && section.getLastVisibleFrame() != frame && isWithinNearbySectionFrustum(viewport, section)) {
                        // reset state on first visit, but don't enqueue
                        section.setLastVisibleFrame(frame);

                        visitor.visit(section);
                    }
                }
            }
        }
    }

    private void init(RenderSectionVisitor visitor,
                      WriteQueue<RenderSection> queue,
                      Viewport viewport,
                      float searchDistance,
                      boolean useOcclusionCulling,
                      int frame)
    {
        var origin = viewport.getChunkCoord();

        if (origin.getY() < this.level.getMinSectionY()) {
            // below the level
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.level.getMinSectionY(), GraphDirection.DOWN);
        } else if (origin.getY() > this.level.getMaxSectionY()) {
            // above the level
            this.initOutsideWorldHeight(queue, viewport, searchDistance, frame,
                    this.level.getMaxSectionY(), GraphDirection.UP);
        } else {
            this.initWithinWorld(visitor, queue, viewport, useOcclusionCulling, frame);
        }
    }

    private void initWithinWorld(RenderSectionVisitor visitor, WriteQueue<RenderSection> queue, Viewport viewport, boolean useOcclusionCulling, int frame) {
        var origin = viewport.getChunkCoord();
        var section = this.getRenderSection(origin.getX(), origin.getY(), origin.getZ());

        if (section == null) {
            return;
        }

        section.setLastVisibleFrame(frame);
        section.setIncomingDirections(GraphDirectionSet.NONE);

        visitor.visit(section);

        int outgoing;

        if (useOcclusionCulling) {
            // Since the camera is located inside this chunk, there are no "incoming" directions. So we need to instead
            // find any possible paths out of this chunk and enqueue those neighbors.
            outgoing = getVisibilityConnections(section.getVisibilityData(), GraphDirectionSet.NONE, false);
        } else {
            // Occlusion culling is disabled, so we can traverse into any neighbor.
            outgoing = GraphDirectionSet.ALL;
        }

        visitNeighbors(queue, section, outgoing, frame);
    }

    // Enqueues sections that are inside the viewport using diamond spiral iteration to avoid sorting and ensure a
    // consistent order. Innermost layers are enqueued first. Within each layer, iteration starts at the northernmost
    // section and proceeds counterclockwise (N->W->S->E).
    private void initOutsideWorldHeight(WriteQueue<RenderSection> queue,
                                        Viewport viewport,
                                        float searchDistance,
                                        int frame,
                                        int height,
                                        int direction)
    {
        var origin = viewport.getChunkCoord();
        var radius = Mth.floor(searchDistance / 16.0f);

        // Layer 0
        this.tryVisitNode(queue, origin.getX(), height, origin.getZ(), direction, frame, viewport);

        // Complete layers, excluding layer 0
        for (int layer = 1; layer <= radius; layer++) {
            for (int z = -layer; z < layer; z++) {
                int x = Math.abs(z) - layer;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = layer; z > -layer; z--) {
                int x = layer - Math.abs(z);
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }
        }

        // Incomplete layers
        for (int layer = radius + 1; layer <= 2 * radius; layer++) {
            int l = layer - radius;

            for (int z = -radius; z <= -l; z++) {
                int x = -z - layer;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = l; z <= radius; z++) {
                int x = z - layer;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = radius; z >= l; z--) {
                int x = layer - z;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }

            for (int z = -l; z >= -radius; z--) {
                int x = layer + z;
                this.tryVisitNode(queue, origin.getX() + x, height, origin.getZ() + z, direction, frame, viewport);
            }
        }
    }

    private void tryVisitNode(WriteQueue<RenderSection> queue, int x, int y, int z, int direction, int frame, Viewport viewport) {
        RenderSection section = this.getRenderSection(x, y, z);

        if (section == null || !isWithinFrustum(viewport, section)) {
            return;
        }

        visitNode(queue, section, GraphDirectionSet.of(direction), frame);
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sections.get(SectionPos.asLong(x, y, z));
    }

    public static long encodeVisibility(VisibilitySet occlusionData) {
        check(VERIFY_STATUS, "native occlusion verification");

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment matrix = arena.allocate(ValueLayout.JAVA_BYTE, VISIBILITY_MATRIX_LENGTH);

            for (int from = 0; from < GraphDirection.COUNT; from++) {
                Direction fromDirection = GraphDirection.toEnum(from);
                for (int to = 0; to < GraphDirection.COUNT; to++) {
                    matrix.setAtIndex(ValueLayout.JAVA_BYTE, (from * GraphDirection.COUNT) + to,
                            (byte)(occlusionData.visibilityBetween(fromDirection, GraphDirection.toEnum(to)) ? 1 : 0));
                }
            }

            return invokeEncodeVisibility(matrix, VISIBILITY_MATRIX_LENGTH);
        }
    }

    /**
     * Returns the outgoing portal directions for immutable CPU section
     * visibility data. This is deliberately independent of render regions,
     * command lists, and GPU state so semantic terrain producers can use the
     * same culling contract without constructing the OpenGL renderer.
     */
    public static int getVisibilityConnections(long visibilityData, int incoming, boolean useIncoming) {
        check(VERIFY_STATUS, "native occlusion verification");
        int connections = invokeConnections(visibilityData, incoming, useIncoming ? 1 : 0);
        if (connections < 0) {
            check(connections, "native occlusion connection calculation");
        }
        return connections;
    }

	public static int getVisibilityConnectionsForCamera(long visibilityData, int incoming,
			double cameraDeltaX, double cameraDeltaY, double cameraDeltaZ) {
		check(VERIFY_STATUS, "native occlusion verification");
		int connections = invokeCameraConnections(visibilityData, incoming, cameraDeltaX, cameraDeltaY, cameraDeltaZ);
		if (connections < 0) check(connections, "native camera occlusion connection calculation");
		return connections;
	}

    private static void computeOcclusionConnectionsBatch(NativeScratch scratch) {
        check(VERIFY_STATUS, "native occlusion verification");
        check(invokeConnectionsBatch(
                MemoryUtil.memAddress(scratch.visibilityData),
                scratch.count,
                MemoryUtil.memAddress(scratch.incomingDirections),
                scratch.count,
                MemoryUtil.memAddress(scratch.cameraDeltas),
                scratch.count * 3,
                MemoryUtil.memAddress(scratch.connections),
                scratch.count), "native batched occlusion connection calculation");
    }

    private static int invokeVerify() {
        try {
            return (int)VERIFY.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust occlusion verification downcall failed", throwable);
        }
    }

    private static long invokeEncodeVisibility(MemorySegment matrix, int matrixLength) {
        try {
            return (long)ENCODE_VISIBILITY.invokeExact(matrix, matrixLength);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust visibility encoding downcall failed", throwable);
        }
    }

    private static int invokeConnections(long visibilityData, int incoming, int useIncoming) {
        try {
            return (int)CONNECTIONS.invokeExact(visibilityData, incoming, useIncoming);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust occlusion connection downcall failed", throwable);
        }
    }

	private static int invokeCameraConnections(long visibilityData, int incoming, double x, double y, double z) {
		try { return (int)CAMERA_CONNECTIONS.invokeExact(visibilityData, incoming, x, y, z); }
		catch (Throwable throwable) { throw new IllegalStateException("Rust camera occlusion connection downcall failed", throwable); }
	}

    private static int invokeConnectionsBatch(long visibilityDataAddress, int visibilityDataCount,
            long incomingAddress, int incomingCount, long cameraDeltaAddress, int cameraDeltaCount,
            long outputAddress, int outputCount) {
        try {
            return (int)CONNECTIONS_BATCH.invokeExact(
                    MemorySegment.ofAddress(visibilityDataAddress),
                    visibilityDataCount,
                    MemorySegment.ofAddress(incomingAddress),
                    incomingCount,
                    MemorySegment.ofAddress(cameraDeltaAddress),
                    cameraDeltaCount,
                    MemorySegment.ofAddress(outputAddress),
                    outputCount);
        } catch (Throwable throwable) {
            throw new IllegalStateException("Rust batched occlusion connection downcall failed", throwable);
        }
    }

    private static void check(int status, String operation) {
        if (status != OK) {
            throw new IllegalStateException(operation + " failed with native status " + status);
        }
    }

    private static final class NativeScratch {
        private RenderSection[] sections = new RenderSection[256];
        private ByteBuffer visibilityData = allocate(Long.BYTES * this.sections.length);
        private ByteBuffer incomingDirections = allocate(Integer.BYTES * this.sections.length);
        private ByteBuffer cameraDeltas = allocate(Double.BYTES * 3 * this.sections.length);
        private ByteBuffer connections = allocate(Integer.BYTES * this.sections.length);
        private int count;

        void clear() {
            this.count = 0;
        }

        void add(RenderSection section, Viewport viewport) {
            this.ensureCapacity(this.count + 1);

            int index = this.count++;
            this.sections[index] = section;
            this.visibilityData.putLong(index * Long.BYTES, section.getVisibilityData());
            this.incomingDirections.putInt(index * Integer.BYTES, section.getIncomingDirections());

            CameraTransform transform = viewport.getTransform();
            int deltaOffset = index * 3 * Double.BYTES;
            this.cameraDeltas.putDouble(deltaOffset, transform.x - section.getCenterX());
            this.cameraDeltas.putDouble(deltaOffset + Double.BYTES, transform.y - section.getCenterY());
            this.cameraDeltas.putDouble(deltaOffset + 2 * Double.BYTES, transform.z - section.getCenterZ());
        }

        private void ensureCapacity(int capacity) {
            if (capacity <= this.sections.length) {
                return;
            }

            int newCapacity = Math.max(capacity, this.sections.length << 1);
            RenderSection[] newSections = new RenderSection[newCapacity];
            System.arraycopy(this.sections, 0, newSections, 0, this.count);

            this.sections = newSections;
            this.visibilityData = grow(this.visibilityData, Long.BYTES, newCapacity, this.count);
            this.incomingDirections = grow(this.incomingDirections, Integer.BYTES, newCapacity, this.count);
            this.cameraDeltas = grow(this.cameraDeltas, Double.BYTES * 3, newCapacity, this.count);
            this.connections = allocate(Integer.BYTES * newCapacity);
        }

        private static ByteBuffer allocate(int bytes) {
            return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        }

        private static ByteBuffer grow(ByteBuffer oldBuffer, int stride, int newCapacity, int count) {
            ByteBuffer newBuffer = allocate(stride * newCapacity);
            for (int offset = 0, limit = stride * count; offset < limit; offset++) {
                newBuffer.put(offset, oldBuffer.get(offset));
            }
            return newBuffer;
        }
    }

}
