package net.vulkanic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Backend-neutral adapter from mutable GL-style Vulkanic compatibility state to
 * immutable explicit pass requests.
 *
 * <p>The adapter is a translation boundary only. It snapshots current
 * compatibility facts, validates resource usage through
 * {@link VulkanicPassResourcePlanner}, and returns immutable requests/plans.
 * It does not own native handles, allocate resources, decide Vulkan layouts, or
 * emit commands. A future OpenGL backend can consume the same requests by
 * mapping attachments, resource reads/writes, ordering, and commands back to GL
 * state changes instead of deriving them from live mutable global state.</p>
 */
public final class VulkanicLegacyCompatibilityAdapter {
    private VulkanicLegacyCompatibilityAdapter() {
    }

    public static VulkanicPassResourceModel.PassExecutionPlan planRenderPass(RenderPassSnapshot snapshot) {
        return VulkanicPassResourcePlanner.plan(renderPassRequest(snapshot));
    }

    public static VulkanicPassResourceModel.PassRequest renderPassRequest(RenderPassSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<VulkanicPassResourceModel.AttachmentUse> attachments = new ArrayList<>(snapshot.attachments().size());
        for (AttachmentSnapshot attachment : snapshot.attachments()) {
            attachments.add(attachment.toAttachmentUse());
        }
        return new VulkanicPassResourceModel.PassRequest(
            VulkanicPassResourceModel.PassKind.RENDER,
            "render-pass:" + snapshot.label(),
            attachments,
            snapshot.resourceUses(),
            snapshot.bindings(),
            snapshot.commands().isEmpty()
                ? List.of(new VulkanicPassResourceModel.Command("render-pass-body", OptionalInt.empty(), OptionalInt.empty()))
                : snapshot.commands(),
            snapshot.requiredOrdering().isEmpty()
                ? List.of("attachments-ready-before-pass", "final-usage-published-after-pass")
                : snapshot.requiredOrdering(),
            snapshot.abandoned(),
            snapshot.deviceLost()
        );
    }

    public static VulkanicPassResourceModel.PassExecutionPlan planDraw(DrawSnapshot snapshot) {
        return VulkanicPassResourcePlanner.plan(drawRequest(snapshot));
    }

    public static VulkanicPassResourceModel.PassRequest drawRequest(DrawSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<VulkanicPassResourceModel.ResourceUse> uses = new ArrayList<>(snapshot.descriptorResourceUses());
        int order = uses.size();
        for (VertexBufferSnapshot vertex : snapshot.vertexBuffers()) {
            if (vertex.defaultAttributeBuffer()) {
                continue;
            }
            long firstVertex = snapshot.command().kind() == DrawCommandKind.INDEXED
                ? Math.max(0, snapshot.command().baseVertex())
                : snapshot.command().firstVertex();
            long vertexCount = snapshot.command().kind() == DrawCommandKind.INDEXED
                ? Math.max(1, snapshot.command().indexCount())
                : Math.max(1, snapshot.command().vertexCount());
            long offset = Math.max(0L, vertex.offset() + firstVertex * Math.max(1, vertex.stride()));
            long size = Math.max(1L, vertexCount * Math.max(1, vertex.stride()));
            uses.add(bufferUse(
                "vertex-buffer-" + vertex.binding(),
                VulkanicPassResourceModel.ResourceKind.VERTEX_BUFFER,
                vertex.stableKey(),
                VulkanicPassResourceModel.Access.READ,
                offset,
                size,
                VulkanicResourceUsage.INFERRED,
                snapshot.semanticSource() + ":vertex-buffer:" + vertex.binding(),
                order++
            ));
        }

        if (snapshot.command().kind() == DrawCommandKind.INDEXED) {
            IndexBufferSnapshot index = snapshot.indexBuffer().orElseThrow(
                () -> new IllegalArgumentException("Indexed draw snapshot requires an index buffer")
            );
            long indexOffset = index.offset() + (long) snapshot.command().firstIndex() * index.bytesPerIndex();
            long indexSize = Math.max(1L, (long) snapshot.command().indexCount() * index.bytesPerIndex());
            uses.add(bufferUse(
                "index-buffer",
                VulkanicPassResourceModel.ResourceKind.INDEX_BUFFER,
                index.stableKey(),
                VulkanicPassResourceModel.Access.READ,
                indexOffset,
                indexSize,
                VulkanicResourceUsage.INFERRED,
                snapshot.semanticSource() + ":index-buffer",
                order
            ));
        }

        return new VulkanicPassResourceModel.PassRequest(
            VulkanicPassResourceModel.PassKind.RENDER,
            "draw:" + snapshot.semanticSource(),
            List.of(),
            uses,
            snapshot.bindings(),
            List.of(new VulkanicPassResourceModel.Command(
                snapshot.command().kind().name().toLowerCase(java.util.Locale.ROOT),
                snapshot.command().kind() == DrawCommandKind.NONE ? OptionalInt.empty() : OptionalInt.of(1),
                OptionalInt.empty()
            )),
            List.of("pipeline-descriptors-geometry-bound-before-draw"),
            snapshot.abandoned(),
            snapshot.deviceLost()
        );
    }

    public static VulkanicPassResourceModel.PassExecutionPlan planCompute(ComputeSnapshot snapshot) {
        return VulkanicPassResourcePlanner.plan(computeRequest(snapshot));
    }

    public static VulkanicPassResourceModel.PassRequest computeRequest(ComputeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<VulkanicPassResourceModel.ResourceUse> uses = new ArrayList<>(snapshot.resourceUses());
        snapshot.indirectBuffer().ifPresent(indirect -> uses.add(bufferUse(
            "compute-indirect",
            VulkanicPassResourceModel.ResourceKind.INDIRECT_BUFFER,
            indirect.stableKey(),
            VulkanicPassResourceModel.Access.READ,
            indirect.offset(),
            12,
            VulkanicResourceUsage.INFERRED,
            snapshot.semanticSource() + ":indirect-dispatch",
            uses.size()
        )));
        return new VulkanicPassResourceModel.PassRequest(
            VulkanicPassResourceModel.PassKind.COMPUTE,
            "compute:" + snapshot.semanticSource(),
            List.of(),
            uses,
            snapshot.bindings(),
            List.of(new VulkanicPassResourceModel.Command(
                snapshot.dispatchLabel(),
                OptionalInt.empty(),
                OptionalInt.of(1)
            )),
            List.of("pipeline-and-descriptors-bound-before-dispatch"),
            snapshot.abandoned(),
            snapshot.deviceLost()
        );
    }

    public static VulkanicPassResourceModel.PassExecutionPlan planTransfer(TransferSnapshot snapshot) {
        return VulkanicPassResourcePlanner.plan(transferRequest(snapshot));
    }

    public static VulkanicPassResourceModel.PassRequest transferRequest(TransferSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        VulkanicPassResourceModel.ResourceUse use = VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(snapshot.logicalName(), snapshot.resourceKind(), snapshot.stableKey()),
            snapshot.access(),
            snapshot.subresource(),
            snapshot.usage(),
            snapshot.role(),
            false,
            0
        );
        return new VulkanicPassResourceModel.PassRequest(
            snapshot.passKind(),
            snapshot.label(),
            List.of(),
            List.of(use),
            List.of(),
            List.of(new VulkanicPassResourceModel.Command(snapshot.commandLabel(), OptionalInt.empty(), OptionalInt.empty())),
            snapshot.requiredOrdering().isEmpty()
                ? List.of("transition-before-operation", "publish-usage-after-operation")
                : snapshot.requiredOrdering(),
            snapshot.abandoned(),
            snapshot.deviceLost()
        );
    }

    public static VulkanicPassResourceModel.ResourceUse sampledTextureUse(
        String logicalName,
        String stableKey,
        VulkanicPassResourceModel.Subresource subresource,
        String role,
        int order
    ) {
        return resourceUse(
            logicalName,
            VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
            stableKey,
            VulkanicPassResourceModel.Access.READ,
            subresource,
            VulkanicResourceUsage.SAMPLED_READ,
            role,
            false,
            order
        );
    }

    public static VulkanicPassResourceModel.ResourceUse storageTextureUse(
        String logicalName,
        String stableKey,
        VulkanicPassResourceModel.Subresource subresource,
        String role,
        int order
    ) {
        return resourceUse(
            logicalName,
            VulkanicPassResourceModel.ResourceKind.STORAGE_TEXTURE,
            stableKey,
            VulkanicPassResourceModel.Access.READ_WRITE,
            subresource,
            VulkanicResourceUsage.STORAGE_READ_WRITE,
            role,
            false,
            order
        );
    }

    public static VulkanicPassResourceModel.ResourceUse uniformBufferUse(
        String logicalName,
        String stableKey,
        long offset,
        long size,
        String role,
        int order
    ) {
        return bufferUse(
            logicalName,
            VulkanicPassResourceModel.ResourceKind.UNIFORM_BUFFER,
            stableKey,
            VulkanicPassResourceModel.Access.READ,
            offset,
            size,
            VulkanicResourceUsage.SAMPLED_READ,
            role,
            order
        );
    }

    public static VulkanicPassResourceModel.ResourceUse texelBufferUse(
        String logicalName,
        String stableKey,
        String role,
        int order
    ) {
        return bufferUse(
            logicalName,
            VulkanicPassResourceModel.ResourceKind.TEXEL_BUFFER,
            stableKey,
            VulkanicPassResourceModel.Access.READ,
            0,
            1,
            VulkanicResourceUsage.SAMPLED_READ,
            role,
            order
        );
    }

    public static VulkanicPassResourceModel.ResourceUse bufferUse(
        String logicalName,
        VulkanicPassResourceModel.ResourceKind kind,
        String stableKey,
        VulkanicPassResourceModel.Access access,
        long offset,
        long size,
        VulkanicResourceUsage usage,
        String role,
        int order
    ) {
        return resourceUse(
            logicalName,
            kind,
            stableKey,
            access,
            VulkanicPassResourceModel.Subresource.bufferRange(offset, size),
            usage,
            role,
            false,
            order
        );
    }

    public static VulkanicPassResourceModel.ResourceUse resourceUse(
        String logicalName,
        VulkanicPassResourceModel.ResourceKind kind,
        String stableKey,
        VulkanicPassResourceModel.Access access,
        VulkanicPassResourceModel.Subresource subresource,
        VulkanicResourceUsage usage,
        String role,
        boolean feedbackLoop,
        int order
    ) {
        return VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(logicalName, kind, stableKey),
            access,
            subresource,
            usage,
            role,
            feedbackLoop,
            order
        );
    }

    public record RenderPassSnapshot(
        String label,
        List<AttachmentSnapshot> attachments,
        List<VulkanicPassResourceModel.ResourceUse> resourceUses,
        List<VulkanicPassResourceModel.BindingSnapshot> bindings,
        List<VulkanicPassResourceModel.Command> commands,
        List<String> requiredOrdering,
        boolean abandoned,
        boolean deviceLost
    ) {
        public RenderPassSnapshot {
            label = requireNonBlank(label, "label");
            attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
            resourceUses = List.copyOf(Objects.requireNonNull(resourceUses, "resourceUses"));
            bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
            commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
            requiredOrdering = List.copyOf(Objects.requireNonNull(requiredOrdering, "requiredOrdering"));
        }

        public static RenderPassSnapshot of(String label, List<AttachmentSnapshot> attachments) {
            return new RenderPassSnapshot(label, attachments, List.of(), List.of(), List.of(), List.of(), false, false);
        }
    }

    public record AttachmentSnapshot(
        int attachmentIndex,
        String logicalName,
        VulkanicPassResourceModel.ResourceKind kind,
        String stableKey,
        VulkanicPassResourceModel.Subresource subresource,
        VulkanicRenderPassDescriptor.LoadOp loadOp,
        VulkanicRenderPassDescriptor.StoreOp storeOp,
        OptionalInt clearColor,
        OptionalDouble clearDepth,
        VulkanicResourceUsage initialUsage,
        VulkanicResourceUsage passUsage,
        VulkanicResourceUsage finalUsage,
        boolean feedbackLoop
    ) {
        public AttachmentSnapshot {
            if (attachmentIndex < 0) {
                throw new IllegalArgumentException("attachmentIndex must be >= 0");
            }
            logicalName = requireNonBlank(logicalName, "logicalName");
            kind = Objects.requireNonNull(kind, "kind");
            stableKey = requireNonBlank(stableKey, "stableKey");
            subresource = Objects.requireNonNull(subresource, "subresource");
            loadOp = Objects.requireNonNull(loadOp, "loadOp");
            storeOp = Objects.requireNonNull(storeOp, "storeOp");
            clearColor = Objects.requireNonNull(clearColor, "clearColor");
            clearDepth = Objects.requireNonNull(clearDepth, "clearDepth");
            initialUsage = Objects.requireNonNull(initialUsage, "initialUsage");
            passUsage = Objects.requireNonNull(passUsage, "passUsage");
            finalUsage = Objects.requireNonNull(finalUsage, "finalUsage");
        }

        VulkanicPassResourceModel.AttachmentUse toAttachmentUse() {
            return new VulkanicPassResourceModel.AttachmentUse(
                attachmentIndex,
                VulkanicPassResourceModel.ResourceIdentity.of(logicalName, kind, stableKey),
                subresource,
                loadOp,
                storeOp,
                clearColor,
                clearDepth,
                initialUsage,
                passUsage,
                finalUsage,
                feedbackLoop
            );
        }
    }

    public record DrawSnapshot(
        String semanticSource,
        List<VertexBufferSnapshot> vertexBuffers,
        Optional<IndexBufferSnapshot> indexBuffer,
        List<VulkanicPassResourceModel.ResourceUse> descriptorResourceUses,
        List<VulkanicPassResourceModel.BindingSnapshot> bindings,
        DrawCommandSnapshot command,
        boolean abandoned,
        boolean deviceLost
    ) {
        public DrawSnapshot {
            semanticSource = requireNonBlank(semanticSource, "semanticSource");
            vertexBuffers = List.copyOf(Objects.requireNonNull(vertexBuffers, "vertexBuffers"));
            indexBuffer = Objects.requireNonNull(indexBuffer, "indexBuffer");
            descriptorResourceUses = List.copyOf(Objects.requireNonNull(descriptorResourceUses, "descriptorResourceUses"));
            bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
            command = Objects.requireNonNull(command, "command");
        }
    }

    public enum DrawCommandKind {
        NONE,
        ARRAYS,
        INDEXED
    }

    public record DrawCommandSnapshot(
        DrawCommandKind kind,
        int firstVertex,
        int vertexCount,
        int firstIndex,
        int indexCount,
        int baseVertex,
        int instanceCount
    ) {
        public DrawCommandSnapshot {
            kind = Objects.requireNonNull(kind, "kind");
            if (instanceCount < 0) {
                throw new IllegalArgumentException("instanceCount must be >= 0");
            }
        }

        public static DrawCommandSnapshot arrays(int firstVertex, int vertexCount, int instanceCount) {
            return new DrawCommandSnapshot(DrawCommandKind.ARRAYS, firstVertex, vertexCount, 0, 0, 0, instanceCount);
        }

        public static DrawCommandSnapshot indexed(int firstIndex, int indexCount, int baseVertex, int instanceCount) {
            return new DrawCommandSnapshot(DrawCommandKind.INDEXED, 0, 0, firstIndex, indexCount, baseVertex, instanceCount);
        }
    }

    public record VertexBufferSnapshot(
        int binding,
        String stableKey,
        long offset,
        int stride,
        boolean defaultAttributeBuffer
    ) {
        public VertexBufferSnapshot {
            if (binding < 0 || offset < 0L || stride < 0) {
                throw new IllegalArgumentException("vertex binding, offset, and stride must be non-negative");
            }
            stableKey = requireNonBlank(stableKey, "stableKey");
        }
    }

    public record IndexBufferSnapshot(
        String stableKey,
        long offset,
        int bytesPerIndex
    ) {
        public IndexBufferSnapshot {
            stableKey = requireNonBlank(stableKey, "stableKey");
            if (offset < 0L || bytesPerIndex <= 0) {
                throw new IllegalArgumentException("index offset must be non-negative and index size must be positive");
            }
        }
    }

    public record ComputeSnapshot(
        String semanticSource,
        String dispatchLabel,
        List<VulkanicPassResourceModel.ResourceUse> resourceUses,
        List<VulkanicPassResourceModel.BindingSnapshot> bindings,
        Optional<IndirectBufferSnapshot> indirectBuffer,
        boolean abandoned,
        boolean deviceLost
    ) {
        public ComputeSnapshot {
            semanticSource = requireNonBlank(semanticSource, "semanticSource");
            dispatchLabel = requireNonBlank(dispatchLabel, "dispatchLabel");
            resourceUses = List.copyOf(Objects.requireNonNull(resourceUses, "resourceUses"));
            bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
            indirectBuffer = Objects.requireNonNull(indirectBuffer, "indirectBuffer");
        }
    }

    public record IndirectBufferSnapshot(String stableKey, long offset) {
        public IndirectBufferSnapshot {
            stableKey = requireNonBlank(stableKey, "stableKey");
            if (offset < 0L) {
                throw new IllegalArgumentException("indirect offset must be non-negative");
            }
        }
    }

    public record TransferSnapshot(
        VulkanicPassResourceModel.PassKind passKind,
        String label,
        String commandLabel,
        String logicalName,
        VulkanicPassResourceModel.ResourceKind resourceKind,
        String stableKey,
        VulkanicPassResourceModel.Access access,
        VulkanicPassResourceModel.Subresource subresource,
        VulkanicResourceUsage usage,
        String role,
        List<String> requiredOrdering,
        boolean abandoned,
        boolean deviceLost
    ) {
        public TransferSnapshot {
            passKind = Objects.requireNonNull(passKind, "passKind");
            if (passKind != VulkanicPassResourceModel.PassKind.TRANSFER
                && passKind != VulkanicPassResourceModel.PassKind.READBACK) {
                throw new IllegalArgumentException("transfer snapshots must be TRANSFER or READBACK");
            }
            label = requireNonBlank(label, "label");
            commandLabel = requireNonBlank(commandLabel, "commandLabel");
            logicalName = requireNonBlank(logicalName, "logicalName");
            resourceKind = Objects.requireNonNull(resourceKind, "resourceKind");
            stableKey = requireNonBlank(stableKey, "stableKey");
            access = Objects.requireNonNull(access, "access");
            subresource = Objects.requireNonNull(subresource, "subresource");
            usage = Objects.requireNonNull(usage, "usage");
            role = requireNonBlank(role, "role");
            requiredOrdering = List.copyOf(Objects.requireNonNull(requiredOrdering, "requiredOrdering"));
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
