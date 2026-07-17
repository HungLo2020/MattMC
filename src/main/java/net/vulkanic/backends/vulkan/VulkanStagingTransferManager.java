package net.vulkanic.backends.vulkan;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Owns staging-transfer lifecycle state for Vulkan uploads and diagnostic
 * readbacks. Vulkan allocation, mapping, command recording, barriers, and
 * submission policy remain in {@link VulkanBackend.NativeSpine}.
 */
final class VulkanStagingTransferManager {
    enum TransferKind {
        UPLOAD,
        READBACK
    }

    enum TransferState {
        ALLOCATED,
        MAPPED,
        READY_FOR_TRANSFER,
        TRANSFER_ASSOCIATED,
        PENDING_RETIREMENT,
        RETIRED,
        RESULT_MAPPED,
        RESULT_CONSUMED,
        DESTROYED,
        FAILED
    }

    private final List<StagingBufferRecord> pendingGlobalRetirement =
        Collections.synchronizedList(new ArrayList<>());
    private final List<List<StagingBufferRecord>> pendingImmediateRetirement;
    private final Set<StagingBufferRecord> pendingRetirementSet =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<StagingBufferRecord> destroyedRecords =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ReadbackTransferRecord> liveReadbacks =
        Collections.newSetFromMap(new IdentityHashMap<>());

    VulkanStagingTransferManager(int immediateSubmitSlots) {
        if (immediateSubmitSlots <= 0) {
            throw new IllegalArgumentException("immediateSubmitSlots must be positive");
        }
        this.pendingImmediateRetirement = new ArrayList<>(immediateSubmitSlots);
        for (int slot = 0; slot < immediateSubmitSlots; slot++) {
            pendingImmediateRetirement.add(Collections.synchronizedList(new ArrayList<>()));
        }
    }

    StagingBufferRecord recordUploadAllocation(long bufferHandle, long memoryHandle, long sizeBytes) {
        if (bufferHandle == 0L || memoryHandle == 0L) {
            throw new IllegalArgumentException("staging upload allocation requires non-null buffer and memory handles");
        }
        if (sizeBytes <= 0L) {
            throw new IllegalArgumentException("staging upload allocation requires a positive size");
        }
        return new StagingBufferRecord(TransferKind.UPLOAD, bufferHandle, memoryHandle, sizeBytes);
    }

    void markMapped(StagingBufferRecord record) {
        Objects.requireNonNull(record, "record");
        record.transition(TransferState.ALLOCATED, TransferState.MAPPED);
        record.mapped = true;
    }

    void markUnmapped(StagingBufferRecord record) {
        Objects.requireNonNull(record, "record");
        if (!record.mapped) {
            return;
        }
        record.mapped = false;
        record.transition(TransferState.MAPPED, TransferState.READY_FOR_TRANSFER);
    }

    void associateTransferCommand(StagingBufferRecord record, long commandBufferHandle) {
        Objects.requireNonNull(record, "record");
        if (commandBufferHandle == 0L) {
            throw new IllegalArgumentException("staging transfer requires a command buffer handle");
        }
        record.commandBufferHandle = commandBufferHandle;
        if (record.state == TransferState.READY_FOR_TRANSFER || record.state == TransferState.ALLOCATED) {
            record.state = TransferState.TRANSFER_ASSOCIATED;
        }
    }

    void retireAfterTransfer(StagingBufferRecord record, int activeImmediateSlot) {
        Objects.requireNonNull(record, "record");
        if (record.state == TransferState.DESTROYED || record.state == TransferState.FAILED) {
            return;
        }
        synchronized (pendingRetirementSet) {
            if (!pendingRetirementSet.add(record)) {
                return;
            }
        }
        record.state = TransferState.PENDING_RETIREMENT;
        if (isValidImmediateSlot(activeImmediateSlot)) {
            pendingImmediateRetirement.get(activeImmediateSlot).add(record);
        } else {
            pendingGlobalRetirement.add(record);
        }
    }

    void cleanupFailedTransfer(
        StagingBufferRecord record,
        boolean commandMayReferenceRecord,
        int activeImmediateSlot,
        boolean deviceAvailable,
        Consumer<StagingBufferRecord> destroy
    ) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(destroy, "destroy");
        if (commandMayReferenceRecord) {
            retireAfterTransfer(record, activeImmediateSlot);
            return;
        }
        synchronized (destroyedRecords) {
            if (!destroyedRecords.add(record)) {
                return;
            }
        }
        record.state = TransferState.FAILED;
        if (deviceAvailable) {
            destroy.accept(record);
        }
    }

    void retireGlobal(Consumer<StagingBufferRecord> destroy) {
        drain(pendingGlobalRetirement, destroy);
    }

    void retireImmediateSlot(int slot, Consumer<StagingBufferRecord> destroy) {
        if (!isValidImmediateSlot(slot)) {
            return;
        }
        drain(pendingImmediateRetirement.get(slot), destroy);
    }

    void cleanupForShutdownOrDeviceLoss(boolean deviceAvailable, Consumer<StagingBufferRecord> destroy) {
        retireGlobal(deviceAvailable ? destroy : record -> markDestroyedWithoutVk(record, TransferState.DESTROYED));
        for (int slot = 0; slot < pendingImmediateRetirement.size(); slot++) {
            retireImmediateSlot(slot, deviceAvailable ? destroy : record -> markDestroyedWithoutVk(record, TransferState.DESTROYED));
        }
        synchronized (liveReadbacks) {
            liveReadbacks.clear();
        }
    }

    boolean canReuse(StagingBufferRecord record) {
        return record != null && record.state == TransferState.RETIRED;
    }

    ReadbackTransferRecord recordReadbackStaging(int bufferId, long byteCount) {
        if (bufferId <= 0) {
            throw new IllegalArgumentException("readback staging requires a positive legacy buffer id");
        }
        if (byteCount <= 0L) {
            throw new IllegalArgumentException("readback staging requires a positive byte count");
        }
        ReadbackTransferRecord record = new ReadbackTransferRecord(bufferId, byteCount);
        synchronized (liveReadbacks) {
            liveReadbacks.add(record);
        }
        return record;
    }

    void associateReadbackCommand(ReadbackTransferRecord record, long commandBufferHandle) {
        Objects.requireNonNull(record, "record");
        if (commandBufferHandle == 0L) {
            throw new IllegalArgumentException("readback transfer requires a command buffer handle");
        }
        record.commandBufferHandle = commandBufferHandle;
        record.state = TransferState.TRANSFER_ASSOCIATED;
    }

    ReadbackResult mapReadbackResult(ReadbackTransferRecord record, ByteBuffer mappedData, Runnable unmapAction) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(mappedData, "mappedData");
        Objects.requireNonNull(unmapAction, "unmapAction");
        if (record.state != TransferState.TRANSFER_ASSOCIATED && record.state != TransferState.READY_FOR_TRANSFER) {
            throw new IllegalStateException("readback result cannot be mapped from state " + record.state);
        }
        record.state = TransferState.RESULT_MAPPED;
        return new ReadbackResult(record, mappedData, unmapAction, this::consumeReadbackResult);
    }

    void discardReadback(ReadbackTransferRecord record) {
        if (record == null) {
            return;
        }
        if (record.state != TransferState.RESULT_CONSUMED) {
            record.state = TransferState.DESTROYED;
        }
        synchronized (liveReadbacks) {
            liveReadbacks.remove(record);
        }
    }

    int pendingRetirementCountForTests() {
        synchronized (pendingRetirementSet) {
            return pendingRetirementSet.size();
        }
    }

    int pendingRetirementCountForTests(int slot) {
        return isValidImmediateSlot(slot) ? pendingImmediateRetirement.get(slot).size() : 0;
    }

    int liveReadbackCountForTests() {
        synchronized (liveReadbacks) {
            return liveReadbacks.size();
        }
    }

    private void consumeReadbackResult(ReadbackTransferRecord record) {
        record.state = TransferState.RESULT_CONSUMED;
        synchronized (liveReadbacks) {
            liveReadbacks.remove(record);
        }
    }

    private void drain(List<StagingBufferRecord> records, Consumer<StagingBufferRecord> destroy) {
        Objects.requireNonNull(destroy, "destroy");
        List<StagingBufferRecord> toDestroy;
        synchronized (records) {
            if (records.isEmpty()) {
                return;
            }
            toDestroy = new ArrayList<>(records);
            records.clear();
        }

        Set<StagingBufferRecord> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        unique.addAll(toDestroy);
        for (StagingBufferRecord record : unique) {
            synchronized (pendingRetirementSet) {
                pendingRetirementSet.remove(record);
            }
            synchronized (destroyedRecords) {
                if (!destroyedRecords.add(record)) {
                    continue;
                }
            }
            destroy.accept(record);
            markDestroyedWithoutVk(record, TransferState.RETIRED);
        }
    }

    private void markDestroyedWithoutVk(StagingBufferRecord record, TransferState state) {
        record.state = state;
        record.mapped = false;
        synchronized (pendingRetirementSet) {
            pendingRetirementSet.remove(record);
        }
    }

    private boolean isValidImmediateSlot(int slot) {
        return slot >= 0 && slot < pendingImmediateRetirement.size();
    }

    static final class StagingBufferRecord {
        private final TransferKind kind;
        private final long bufferHandle;
        private final long memoryHandle;
        private final long sizeBytes;
        private long commandBufferHandle;
        private boolean mapped;
        private TransferState state = TransferState.ALLOCATED;

        private StagingBufferRecord(TransferKind kind, long bufferHandle, long memoryHandle, long sizeBytes) {
            this.kind = kind;
            this.bufferHandle = bufferHandle;
            this.memoryHandle = memoryHandle;
            this.sizeBytes = sizeBytes;
        }

        long bufferHandle() {
            return bufferHandle;
        }

        long memoryHandle() {
            return memoryHandle;
        }

        long sizeBytes() {
            return sizeBytes;
        }

        long commandBufferHandle() {
            return commandBufferHandle;
        }

        TransferKind kind() {
            return kind;
        }

        TransferState stateForTests() {
            return state;
        }

        private void transition(TransferState expected, TransferState next) {
            if (state != expected) {
                throw new IllegalStateException("staging transfer cannot move from " + state + " to " + next);
            }
            state = next;
        }
    }

    static final class ReadbackTransferRecord {
        private final int bufferId;
        private final long byteCount;
        private long commandBufferHandle;
        private TransferState state = TransferState.READY_FOR_TRANSFER;

        private ReadbackTransferRecord(int bufferId, long byteCount) {
            this.bufferId = bufferId;
            this.byteCount = byteCount;
        }

        int bufferId() {
            return bufferId;
        }

        long byteCount() {
            return byteCount;
        }

        long commandBufferHandle() {
            return commandBufferHandle;
        }

        TransferState stateForTests() {
            return state;
        }
    }

    static final class ReadbackResult implements AutoCloseable {
        private final ReadbackTransferRecord record;
        private final ByteBuffer data;
        private final Runnable unmapAction;
        private final Consumer<ReadbackTransferRecord> consumeAction;
        private boolean closed;

        private ReadbackResult(
            ReadbackTransferRecord record,
            ByteBuffer data,
            Runnable unmapAction,
            Consumer<ReadbackTransferRecord> consumeAction
        ) {
            this.record = record;
            this.data = data;
            this.unmapAction = unmapAction;
            this.consumeAction = consumeAction;
        }

        ByteBuffer data() {
            if (closed) {
                throw new IllegalStateException("readback result has already been consumed");
            }
            return data;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                unmapAction.run();
            } finally {
                consumeAction.accept(record);
            }
        }
    }
}
