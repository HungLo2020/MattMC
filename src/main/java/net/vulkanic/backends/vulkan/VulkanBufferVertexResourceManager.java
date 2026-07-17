package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicBuffer;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns legacy OpenGL-style buffer and vertex-array compatibility state for the
 * Vulkan backend.
 *
 * <p>This class deliberately does not execute Vulkan work. It owns mutable GL
 * compatibility state and publishes immutable snapshots/plans for NativeSpine
 * and {@link VulkanDrawExecutionCoordinator}. NativeSpine remains responsible
 * for allocation, mapping, copies, barriers, native binds, draw commands, and
 * destruction.</p>
 */
final class VulkanBufferVertexResourceManager {
    private final AtomicInteger nextLegacyBufferId = new AtomicInteger(1);
    private final AtomicInteger nextVirtualVaoId = new AtomicInteger(1);
    private final Map<Integer, LegacyBufferObject> legacyBuffers = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> legacyBufferBindings = new ConcurrentHashMap<>();
    private final Map<Integer, LegacyMappedBufferView> legacyBufferMappedViews = new ConcurrentHashMap<>();
    private final Set<Integer> virtualVaos = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Integer, VirtualVaoState> virtualVaoStates = new ConcurrentHashMap<>();
    private volatile int boundVirtualVao;

    int createLegacyBuffer() {
        int id = nextLegacyBufferId.getAndIncrement();
        legacyBuffers.put(id, new LegacyBufferObject(id));
        return id;
    }

    void createLegacyBuffers(int[] buffers) {
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = createLegacyBuffer();
        }
    }

    void setLegacyBufferExplicitUsage(int bufferId, int usage) {
        requireLegacyBuffer(bufferId).explicitUsage = usage;
    }

    void setLegacyBufferDebugLabel(int bufferId, @Nullable String debugLabel) {
        requireLegacyBuffer(bufferId).debugLabel = debugLabel;
    }

    BufferDeletionSnapshot deleteLegacyBuffer(int bufferId) {
        if (bufferId == 0) {
            return BufferDeletionSnapshot.empty(bufferId);
        }

        LegacyMappedBufferView mappedView = legacyBufferMappedViews.remove(bufferId);
        LegacyBufferObject legacy = legacyBuffers.remove(bufferId);
        VulkanBuffer previousStorage = null;
        if (legacy != null) {
            previousStorage = legacy.buffer;
            legacy.buffer = null;
            legacy.logicalSizeBytes = 0;
        }

        List<Integer> unboundTargets = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : new ArrayList<>(legacyBufferBindings.entrySet())) {
            if (entry.getValue() == bufferId) {
                legacyBufferBindings.remove(entry.getKey());
                unboundTargets.add(entry.getKey());
            }
        }
        int invalidatedVertexReferences = invalidateVertexArrayBufferReferences(bufferId);
        return new BufferDeletionSnapshot(
            bufferId,
            previousStorage,
            mappedView,
            List.copyOf(unboundTargets),
            invalidatedVertexReferences
        );
    }

    void bindLegacyBuffer(int target, int bufferId) {
        if (bufferId == 0) {
            legacyBufferBindings.remove(target);
            return;
        }

        LegacyBufferObject legacy = legacyBuffers.computeIfAbsent(bufferId, LegacyBufferObject::new);
        legacy.lastTarget = target;
        legacyBufferBindings.put(target, bufferId);
    }

    int boundLegacyBufferId(int target) {
        return legacyBufferBindings.getOrDefault(target, 0);
    }

    boolean containsLegacyBuffer(int bufferId) {
        return bufferId != 0 && legacyBuffers.containsKey(bufferId);
    }

    BufferStorageSnapshot requireLegacyBufferSnapshot(int bufferId) {
        LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
        return legacy.snapshot();
    }

    @Nullable
    BufferStorageSnapshot legacyBufferSnapshot(int bufferId) {
        LegacyBufferObject legacy = legacyBuffers.get(bufferId);
        return legacy == null ? null : legacy.snapshot();
    }

    BufferStorageSnapshot requireBoundLegacyBufferSnapshot(int target) {
        int bufferId = boundLegacyBufferId(target);
        if (bufferId == 0) {
            throw new IllegalStateException("No Vulkan legacy buffer bound for target " + target);
        }
        return requireLegacyBufferSnapshot(bufferId);
    }

    BufferStorageSnapshot requireAllocatedLegacyBufferSnapshot(int bufferId, String operation) {
        BufferStorageSnapshot snapshot = requireLegacyBufferSnapshot(bufferId);
        if (snapshot.buffer() == null || snapshot.logicalSizeBytes() <= 0 || snapshot.buffer().isClosed()) {
            throw new IllegalStateException(operation + " requires allocated legacy buffer storage for handle " + bufferId);
        }
        return snapshot;
    }

    BufferStorageSnapshot requireAllocatedBoundLegacyBufferSnapshot(int target, String operation) {
        return requireAllocatedLegacyBufferSnapshot(requireBoundLegacyBufferSnapshot(target).id(), operation);
    }

    @Nullable
    BufferStorageSnapshot optionalAllocatedBoundLegacyBufferSnapshot(int target) {
        int bufferId = boundLegacyBufferId(target);
        if (bufferId == 0) {
            return null;
        }
        BufferStorageSnapshot snapshot = requireLegacyBufferSnapshot(bufferId);
        VulkanBuffer buffer = snapshot.buffer();
        if (buffer == null || buffer.isClosed() || snapshot.logicalSizeBytes() <= 0) {
            return null;
        }
        return snapshot;
    }

    BufferStorageReplacementPlan beginStorageReplacement(int bufferId, int target, int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0, got: " + size);
        }
        LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
        LegacyMappedBufferView mappedView = legacyBufferMappedViews.remove(bufferId);
        VulkanBuffer previousStorage = legacy.buffer;
        legacy.buffer = null;
        legacy.logicalSizeBytes = size;
        legacy.lastTarget = target;
        return new BufferStorageReplacementPlan(
            bufferId,
            target,
            size,
            legacy.explicitUsage,
            legacy.debugLabel,
            previousStorage,
            mappedView
        );
    }

    void publishStorage(int bufferId, int target, int size, @Nullable VulkanBuffer buffer) {
        LegacyBufferObject legacy = requireLegacyBuffer(bufferId);
        legacy.buffer = buffer;
        legacy.logicalSizeBytes = size;
        legacy.lastTarget = target;
    }

    boolean isLegacyBufferMapped(int bufferId) {
        return legacyBufferMappedViews.containsKey(bufferId);
    }

    BufferMapRequest validateMapRequest(int bufferId, long offset, long length, int access, int readBit) {
        BufferStorageSnapshot snapshot = requireLegacyBufferSnapshot(bufferId);
        if (offset < 0L || length < 0L || length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid map range offset=" + offset + ", length=" + length);
        }
        if (offset + length > snapshot.logicalSizeBytes()) {
            throw new IllegalArgumentException("Map range exceeds legacy buffer size");
        }
        if (legacyBufferMappedViews.containsKey(bufferId)) {
            throw new IllegalStateException("Legacy buffer is already mapped: " + bufferId);
        }
        boolean write = (access & VulkanicAPI.GL_MAP_WRITE_BIT) != 0;
        boolean read = (access & readBit) != 0 || !write;
        return new BufferMapRequest(bufferId, (int) offset, (int) length, read, write);
    }

    void publishMappedView(int bufferId, VulkanicBuffer.MappedView view, VulkanBuffer buffer, int offset, int length, boolean write) {
        legacyBufferMappedViews.put(bufferId, new LegacyMappedBufferView(view, buffer, offset, length, write));
    }

    @Nullable
    LegacyMappedBufferView removeMappedView(int bufferId) {
        return legacyBufferMappedViews.remove(bufferId);
    }

    void validateFlushMappedRange(int bufferId, long offset, long length) {
        if (offset < 0L || length < 0L) {
            throw new IllegalArgumentException("offset/length must be >= 0");
        }
        BufferStorageSnapshot snapshot = requireLegacyBufferSnapshot(bufferId);
        if (offset + length > snapshot.logicalSizeBytes()) {
            throw new IllegalArgumentException("Flush range exceeds legacy buffer size");
        }
        if (!legacyBufferMappedViews.containsKey(bufferId)) {
            throw new IllegalStateException("Cannot flush unmapped legacy buffer: " + bufferId);
        }
    }

    BufferCopyPlan validateCopy(int readBufferId, int writeBufferId, long readOffset, long writeOffset, long size) {
        if (readOffset < 0L || writeOffset < 0L || size < 0L || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid copy range: readOffset=" + readOffset
                + ", writeOffset=" + writeOffset + ", size=" + size);
        }
        BufferStorageSnapshot read = requireLegacyBufferSnapshot(readBufferId);
        BufferStorageSnapshot write = requireLegacyBufferSnapshot(writeBufferId);
        if (size == 0L) {
            return new BufferCopyPlan(read, write, readOffset, writeOffset, 0);
        }
        if (readOffset + size > read.logicalSizeBytes()) {
            throw new IllegalArgumentException("Read range exceeds source buffer size");
        }
        if (writeOffset + size > write.logicalSizeBytes()) {
            throw new IllegalArgumentException("Write range exceeds destination buffer size");
        }
        if (legacyBufferMappedViews.containsKey(readBufferId) || legacyBufferMappedViews.containsKey(writeBufferId)) {
            throw new IllegalStateException("Cannot copy while legacy source/destination buffer is mapped");
        }
        return new BufferCopyPlan(read, write, readOffset, writeOffset, (int) size);
    }

    int createVertexArray() {
        int id = nextVirtualVaoId.getAndIncrement();
        virtualVaos.add(id);
        virtualVaoStates.put(id, new VirtualVaoState());
        return id;
    }

    void bindVertexArray(int vao) {
        boundVirtualVao = vao;
    }

    void deleteVertexArray(int vertexArray) {
        virtualVaos.remove(vertexArray);
        virtualVaoStates.remove(vertexArray);
        if (boundVirtualVao == vertexArray) {
            boundVirtualVao = 0;
        }
    }

    int boundVertexArray() {
        return boundVirtualVao;
    }

    boolean containsVertexArray(int array) {
        return array != 0 && virtualVaos.contains(array);
    }

    void enableVertexAttribute(int index) {
        currentVirtualVaoState().enableAttribute(index);
    }

    void disableVertexAttribute(int index) {
        currentVirtualVaoState().disableAttribute(index);
    }

    void setVertexAttributePointer(int index, int size, int type, boolean normalized, boolean integer, int stride, long pointer) {
        currentVirtualVaoState().setAttributePointer(index, size, type, normalized, integer, stride, pointer, boundLegacyBufferId(VulkanicAPI.GL_ARRAY_BUFFER));
    }

    void setVertexAttributeFormat(int index, int size, int type, boolean normalized, boolean integer, int relativeOffset) {
        currentVirtualVaoState().setAttributeFormat(index, size, type, normalized, integer, relativeOffset);
    }

    void setVertexAttributeBinding(int index, int binding) {
        currentVirtualVaoState().setAttributeBinding(index, binding);
    }

    void setVertexBinding(int binding, int stride, long offset, int buffer) {
        currentVirtualVaoState().setBinding(binding, stride, offset, buffer);
    }

    void setVertexAttributeDivisor(int index, int divisor) {
        currentVirtualVaoState().setDivisor(index, divisor);
    }

    VulkanDrawExecutionCoordinator.DrawResourceSnapshot drawResourceSnapshot() {
        @Nullable Integer fallbackArrayBuffer = null;
        int arrayBuffer = boundLegacyBufferId(VulkanicAPI.GL_ARRAY_BUFFER);
        if (arrayBuffer > 0) {
            fallbackArrayBuffer = arrayBuffer;
        }
        VulkanDrawExecutionCoordinator.LegacyVaoSnapshot vao = currentVirtualVaoState().snapshot(fallbackArrayBuffer);
        @Nullable VulkanDrawExecutionCoordinator.IndexBufferSnapshot index = null;
        BufferStorageSnapshot element = optionalAllocatedBoundLegacyBufferSnapshot(VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER);
        if (element != null && element.buffer() != null) {
            index = new VulkanDrawExecutionCoordinator.IndexBufferSnapshot(
                element.id(),
                element.buffer().getVkBufferHandle(),
                element.logicalSizeBytes()
            );
        }
        return new VulkanDrawExecutionCoordinator.DrawResourceSnapshot(vao, index);
    }

    void clearAll() {
        legacyBuffers.clear();
        legacyBufferBindings.clear();
        legacyBufferMappedViews.clear();
        virtualVaos.clear();
        virtualVaoStates.clear();
        boundVirtualVao = 0;
    }

    int legacyBufferCountForTests() {
        return legacyBuffers.size();
    }

    int mappedBufferCountForTests() {
        return legacyBufferMappedViews.size();
    }

    int vertexArrayCountForTests() {
        return virtualVaos.size();
    }

    private LegacyBufferObject requireLegacyBuffer(int bufferId) {
        LegacyBufferObject legacy = legacyBuffers.get(bufferId);
        if (legacy == null) {
            throw new IllegalArgumentException("Unknown Vulkan legacy buffer handle: " + bufferId);
        }
        return legacy;
    }

    private VirtualVaoState currentVirtualVaoState() {
        int vao = boundVirtualVao;
        if (vao == 0) {
            return virtualVaoStates.computeIfAbsent(0, ignored -> new VirtualVaoState());
        }
        if (!virtualVaos.contains(vao)) {
            virtualVaos.add(vao);
        }
        return virtualVaoStates.computeIfAbsent(vao, ignored -> new VirtualVaoState());
    }

    private int invalidateVertexArrayBufferReferences(int bufferId) {
        int invalidated = 0;
        for (VirtualVaoState vao : virtualVaoStates.values()) {
            invalidated += vao.removeBufferReferences(bufferId);
        }
        return invalidated;
    }

    private static final class LegacyBufferObject {
        private final int id;
        @Nullable
        private volatile VulkanBuffer buffer;
        private volatile int logicalSizeBytes;
        private volatile int lastTarget;
        private volatile int explicitUsage;
        @Nullable
        private volatile String debugLabel;

        private LegacyBufferObject(int id) {
            this.id = id;
            this.lastTarget = VulkanicAPI.GL_ARRAY_BUFFER;
        }

        private BufferStorageSnapshot snapshot() {
            return new BufferStorageSnapshot(id, buffer, logicalSizeBytes, lastTarget, explicitUsage, debugLabel);
        }
    }

    private static final class VirtualVaoState {
        private final ConcurrentHashMap<Integer, LegacyVertexAttribute> attributes = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, LegacyVertexBinding> bindings = new ConcurrentHashMap<>();
        private final Set<Integer> enabledAttributes = ConcurrentHashMap.newKeySet();

        private void enableAttribute(int index) {
            if (index < 0) {
                throw new IllegalArgumentException("attribute index must be >= 0");
            }
            enabledAttributes.add(index);
        }

        private void disableAttribute(int index) {
            enabledAttributes.remove(index);
        }

        private void setAttributePointer(int index, int size, int type, boolean normalized, boolean integer, int stride, long pointer, int buffer) {
            int effectiveStride = stride > 0 ? stride : VulkanDrawExecutionCoordinator.attributeByteSize(size, type);
            LegacyVertexAttribute previous = attributes.get(index);
            int divisor = previous != null ? previous.divisor() : 0;
            attributes.put(index, new LegacyVertexAttribute(index, index, size, type, normalized, integer, Math.toIntExact(pointer), divisor));
            bindings.put(index, new LegacyVertexBinding(index, effectiveStride, 0, divisor, buffer));
        }

        private void setAttributeFormat(int index, int size, int type, boolean normalized, boolean integer, int relativeOffset) {
            LegacyVertexAttribute previous = attributes.get(index);
            int binding = previous != null ? previous.binding() : 0;
            int divisor = previous != null ? previous.divisor() : 0;
            attributes.put(index, new LegacyVertexAttribute(index, binding, size, type, normalized, integer, relativeOffset, divisor));
        }

        private void setAttributeBinding(int index, int binding) {
            if (binding < 0) {
                throw new IllegalArgumentException("attribute binding must be >= 0");
            }
            LegacyVertexAttribute previous = attributes.get(index);
            if (previous == null) {
                attributes.put(index, new LegacyVertexAttribute(index, binding, 4, VulkanicAPI.GL_FLOAT, false, false, 0, 0));
                return;
            }
            attributes.put(index, new LegacyVertexAttribute(
                previous.index(),
                binding,
                previous.size(),
                previous.type(),
                previous.normalized(),
                previous.integer(),
                previous.offset(),
                previous.divisor()
            ));
        }

        private void setBinding(int binding, int stride, long offset, int buffer) {
            if (binding < 0) {
                throw new IllegalArgumentException("binding index must be >= 0");
            }
            if (stride < 0) {
                throw new IllegalArgumentException("binding stride must be >= 0");
            }
            LegacyVertexBinding previous = bindings.get(binding);
            int divisor = previous != null ? previous.divisor() : 0;
            bindings.put(binding, new LegacyVertexBinding(binding, stride, offset, divisor, buffer));
        }

        private void setDivisor(int index, int divisor) {
            LegacyVertexAttribute previous = attributes.get(index);
            if (previous == null) {
                attributes.put(index, new LegacyVertexAttribute(index, 0, 4, VulkanicAPI.GL_FLOAT, false, false, 0, divisor));
                return;
            }
            attributes.put(index, new LegacyVertexAttribute(
                previous.index(),
                previous.binding(),
                previous.size(),
                previous.type(),
                previous.normalized(),
                previous.integer(),
                previous.offset(),
                divisor
            ));
            LegacyVertexBinding previousBinding = bindings.get(previous.binding());
            if (previousBinding != null) {
                bindings.put(previous.binding(), new LegacyVertexBinding(
                    previousBinding.binding(),
                    previousBinding.stride(),
                    previousBinding.offset(),
                    divisor,
                    previousBinding.buffer()
                ));
            }
        }

        private int removeBufferReferences(int bufferId) {
            int removed = 0;
            for (Map.Entry<Integer, LegacyVertexBinding> entry : new ArrayList<>(bindings.entrySet())) {
                if (entry.getValue().buffer() == bufferId) {
                    bindings.put(entry.getKey(), new LegacyVertexBinding(
                        entry.getValue().binding(),
                        entry.getValue().stride(),
                        entry.getValue().offset(),
                        entry.getValue().divisor(),
                        0
                    ));
                    removed++;
                }
            }
            return removed;
        }

        private VulkanDrawExecutionCoordinator.LegacyVaoSnapshot snapshot(@Nullable Integer fallbackArrayBuffer) {
            List<VulkanDrawExecutionCoordinator.LegacyVertexAttributeSnapshot> attributeSnapshots =
                enabledAttributes.stream()
                    .map(attributes::get)
                    .filter(java.util.Objects::nonNull)
                    .map(attribute -> new VulkanDrawExecutionCoordinator.LegacyVertexAttributeSnapshot(
                        attribute.index(),
                        attribute.binding(),
                        attribute.size(),
                        attribute.type(),
                        attribute.normalized(),
                        attribute.integer(),
                        attribute.offset(),
                        attribute.divisor()
                    ))
                    .sorted(Comparator.comparingInt(VulkanDrawExecutionCoordinator.LegacyVertexAttributeSnapshot::index))
                    .toList();

            List<VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot> bindingSnapshots =
                bindings.values().stream()
                    .map(binding -> new VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot(
                        binding.binding(),
                        binding.stride(),
                        binding.offset(),
                        binding.divisor(),
                        binding.buffer()
                    ))
                    .sorted(Comparator.comparingInt(VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot::binding))
                    .toList();

            List<VulkanDrawExecutionCoordinator.VertexBufferBindingPlan> vertexBuffers = new ArrayList<>();
            vertexBuffers.add(new VulkanDrawExecutionCoordinator.VertexBufferBindingPlan(
                VulkanBackend.LEGACY_DEFAULT_VERTEX_ATTRIBUTE_BINDING,
                0,
                0L,
                true
            ));
            List<VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot> activeBindings = bindingSnapshots.stream()
                .filter(binding -> binding.bufferId() > 0)
                .toList();
            if (activeBindings.isEmpty()) {
                if (fallbackArrayBuffer != null && fallbackArrayBuffer > 0) {
                    vertexBuffers.add(new VulkanDrawExecutionCoordinator.VertexBufferBindingPlan(0, fallbackArrayBuffer, 0L, false));
                }
            } else {
                for (VulkanDrawExecutionCoordinator.LegacyVertexBindingSnapshot binding : activeBindings) {
                    vertexBuffers.add(new VulkanDrawExecutionCoordinator.VertexBufferBindingPlan(
                        binding.binding(),
                        binding.bufferId(),
                        binding.offset(),
                        false
                    ));
                }
            }
            return new VulkanDrawExecutionCoordinator.LegacyVaoSnapshot(attributeSnapshots, bindingSnapshots, vertexBuffers);
        }
    }

    private record LegacyVertexAttribute(
        int index,
        int binding,
        int size,
        int type,
        boolean normalized,
        boolean integer,
        int offset,
        int divisor
    ) {
    }

    private record LegacyVertexBinding(int binding, int stride, long offset, int divisor, int buffer) {
    }

    record BufferStorageSnapshot(
        int id,
        @Nullable VulkanBuffer buffer,
        int logicalSizeBytes,
        int lastTarget,
        int explicitUsage,
        @Nullable String debugLabel
    ) {
    }

    record BufferStorageReplacementPlan(
        int bufferId,
        int target,
        int size,
        int explicitUsage,
        @Nullable String debugLabel,
        @Nullable VulkanBuffer previousStorage,
        @Nullable LegacyMappedBufferView mappedView
    ) {
    }

    record BufferDeletionSnapshot(
        int bufferId,
        @Nullable VulkanBuffer previousStorage,
        @Nullable LegacyMappedBufferView mappedView,
        List<Integer> unboundTargets,
        int invalidatedVertexReferences
    ) {
        BufferDeletionSnapshot {
            unboundTargets = List.copyOf(unboundTargets);
        }

        static BufferDeletionSnapshot empty(int bufferId) {
            return new BufferDeletionSnapshot(bufferId, null, null, List.of(), 0);
        }
    }

    record LegacyMappedBufferView(
        VulkanicBuffer.MappedView view,
        VulkanBuffer buffer,
        int offset,
        int length,
        boolean write
    ) {
    }

    record BufferMapRequest(int bufferId, int offset, int length, boolean read, boolean write) {
    }

    record BufferCopyPlan(
        BufferStorageSnapshot source,
        BufferStorageSnapshot destination,
        long sourceOffset,
        long destinationOffset,
        int size
    ) {
    }
}
