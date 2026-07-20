package net.vulkanic;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Backend-neutral resource contract for one unit of render, compute, transfer,
 * or readback work.
 *
 * <p>This is intentionally semantic. It describes what the work reads and
 * writes without exposing Vulkan layouts, stage masks, access masks, descriptor
 * handles, or OpenGL binding slots. Compatibility frontends may still translate
 * legacy mutable state into this model until the public Vulkanic API grows an
 * explicit pass interface.</p>
 */
public final class VulkanicPassResourceModel {
    public static final long UNKNOWN_GENERATION = -1L;

    private VulkanicPassResourceModel() {
    }

    public enum PassKind {
        RENDER,
        COMPUTE,
        TRANSFER,
        READBACK
    }

    public enum ResourceKind {
        COLOR_ATTACHMENT,
        DEPTH_ATTACHMENT,
        SAMPLED_TEXTURE,
        STORAGE_TEXTURE,
        UNIFORM_BUFFER,
        STORAGE_BUFFER,
        TEXEL_BUFFER,
        VERTEX_BUFFER,
        INDEX_BUFFER,
        INDIRECT_BUFFER,
        TRANSFER_SOURCE,
        TRANSFER_DESTINATION,
        READBACK_SOURCE
    }

    public enum Access {
        READ,
        WRITE,
        READ_WRITE
    }

    public enum Aspect {
        BUFFER,
        COLOR,
        DEPTH,
        STENCIL
    }

    public enum BindingKind {
        BUFFER_RANGE,
        SAMPLED_TEXTURE,
        STORAGE_IMAGE,
        TEXEL_BUFFER,
        ATTACHMENT
    }

    public enum TargetClass {
        UNKNOWN,
        BUFFER,
        TEXTURE_1D,
        TEXTURE_2D,
        TEXTURE_3D,
        TEXTURE_CUBE,
        TEXTURE_2D_ARRAY
    }

    public enum FormatClass {
        UNKNOWN,
        BUFFER,
        COLOR,
        DEPTH,
        STENCIL,
        DEPTH_STENCIL
    }

    public record ResourceIdentity(
        String logicalName,
        ResourceKind kind,
        String stableKey
    ) {
        public ResourceIdentity {
            logicalName = requireNonBlank(logicalName, "logicalName");
            kind = Objects.requireNonNull(kind, "kind");
            stableKey = requireNonBlank(stableKey, "stableKey");
        }

        public static ResourceIdentity of(String logicalName, ResourceKind kind, String stableKey) {
            return new ResourceIdentity(logicalName, kind, stableKey);
        }
    }

    public record Subresource(
        Set<Aspect> aspects,
        int baseMipLevel,
        int levelCount,
        int baseLayer,
        int layerCount
    ) {
        public Subresource {
            Objects.requireNonNull(aspects, "aspects");
            if (aspects.isEmpty()) {
                throw new IllegalArgumentException("aspects must not be empty");
            }
            aspects = Set.copyOf(EnumSet.copyOf(aspects));
            if (baseMipLevel < 0 || levelCount <= 0 || baseLayer < 0 || layerCount <= 0) {
                throw new IllegalArgumentException(
                    "subresource requires non-negative base mip/layer and positive counts"
                );
            }
        }

        public static Subresource color(int baseMipLevel, int levelCount, int baseLayer, int layerCount) {
            return new Subresource(EnumSet.of(Aspect.COLOR), baseMipLevel, levelCount, baseLayer, layerCount);
        }

        public static Subresource bufferRange(long offset, long size) {
            if (offset < 0 || size <= 0 || offset > Integer.MAX_VALUE || size > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("buffer ranges must be positive and fit the current diagnostic model");
            }
            return new Subresource(EnumSet.of(Aspect.BUFFER), (int) offset, (int) size, 0, 1);
        }

        public static Subresource depth(int baseMipLevel, int levelCount, int baseLayer, int layerCount) {
            return new Subresource(EnumSet.of(Aspect.DEPTH), baseMipLevel, levelCount, baseLayer, layerCount);
        }

        public static Subresource depthStencil(int baseMipLevel, int levelCount, int baseLayer, int layerCount) {
            return new Subresource(EnumSet.of(Aspect.DEPTH, Aspect.STENCIL), baseMipLevel, levelCount, baseLayer, layerCount);
        }

        public boolean overlaps(Subresource other) {
            Objects.requireNonNull(other, "other");
            if (aspects.stream().noneMatch(other.aspects::contains)) {
                return false;
            }
            return rangesOverlap(baseMipLevel, levelCount, other.baseMipLevel, other.levelCount)
                && rangesOverlap(baseLayer, layerCount, other.baseLayer, other.layerCount);
        }

        private static boolean rangesOverlap(int baseA, int countA, int baseB, int countB) {
            return baseA < baseB + countB && baseB < baseA + countA;
        }
    }

    public record ResourceUse(
        ResourceIdentity resource,
        ResourceKind kind,
        Access access,
        Subresource subresource,
        VulkanicResourceUsage usage,
        String role,
        boolean feedbackLoop,
        int order
    ) {
        public ResourceUse {
            resource = Objects.requireNonNull(resource, "resource");
            kind = Objects.requireNonNull(kind, "kind");
            access = Objects.requireNonNull(access, "access");
            subresource = Objects.requireNonNull(subresource, "subresource");
            usage = Objects.requireNonNull(usage, "usage");
            role = requireNonBlank(role, "role");
            if (resource.kind() != kind) {
                throw new IllegalArgumentException(
                    "resource identity kind " + resource.kind() + " does not match use kind " + kind
                );
            }
            if (order < 0) {
                throw new IllegalArgumentException("order must be >= 0");
            }
        }

        public static ResourceUse of(
            ResourceIdentity resource,
            Access access,
            Subresource subresource,
            VulkanicResourceUsage usage,
            String role,
            boolean feedbackLoop,
            int order
        ) {
            return new ResourceUse(resource, resource.kind(), access, subresource, usage, role, feedbackLoop, order);
        }

        public boolean writes() {
            return access == Access.WRITE || access == Access.READ_WRITE;
        }

        public boolean reads() {
            return access == Access.READ || access == Access.READ_WRITE;
        }
    }

    public record CanonicalResourceReference(
        ResourceIdentity resource,
        BindingKind bindingKind,
        Access access,
        Subresource subresource,
        VulkanicResourceUsage usage,
        TargetClass targetClass,
        FormatClass formatClass,
        long generation,
        OptionalInt legacyId,
        OptionalInt legacyTarget,
        OptionalInt bindingUnit,
        OptionalInt samplerObject,
        OptionalInt imageAccess,
        OptionalInt imageFormat,
        boolean layered
    ) {
        public CanonicalResourceReference {
            resource = Objects.requireNonNull(resource, "resource");
            bindingKind = Objects.requireNonNull(bindingKind, "bindingKind");
            access = Objects.requireNonNull(access, "access");
            subresource = Objects.requireNonNull(subresource, "subresource");
            usage = Objects.requireNonNull(usage, "usage");
            targetClass = Objects.requireNonNull(targetClass, "targetClass");
            formatClass = Objects.requireNonNull(formatClass, "formatClass");
            legacyId = Objects.requireNonNull(legacyId, "legacyId");
            legacyTarget = Objects.requireNonNull(legacyTarget, "legacyTarget");
            bindingUnit = Objects.requireNonNull(bindingUnit, "bindingUnit");
            samplerObject = Objects.requireNonNull(samplerObject, "samplerObject");
            imageAccess = Objects.requireNonNull(imageAccess, "imageAccess");
            imageFormat = Objects.requireNonNull(imageFormat, "imageFormat");
            if (generation < UNKNOWN_GENERATION) {
                throw new IllegalArgumentException("generation must be >= UNKNOWN_GENERATION");
            }
        }

        public ResourceUse asResourceUse(String role, boolean feedbackLoop, int order) {
            return ResourceUse.of(resource, access, subresource, usage, role, feedbackLoop, order);
        }

        public static CanonicalResourceReference fromUse(ResourceUse use) {
            Objects.requireNonNull(use, "use");
            return new CanonicalResourceReference(
                use.resource(),
                bindingKindFor(use.kind()),
                use.access(),
                use.subresource(),
                use.usage(),
                targetClassFor(use.kind()),
                formatClassFor(use.kind(), use.subresource()),
                UNKNOWN_GENERATION,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                false
            );
        }

        public static CanonicalResourceReference sampledTexture(
            String logicalName,
            String stableKey,
            int legacyTextureId,
            OptionalInt legacyTarget,
            TargetClass targetClass,
            Subresource subresource,
            OptionalInt textureUnit,
            OptionalInt samplerObject
        ) {
            return new CanonicalResourceReference(
                ResourceIdentity.of(logicalName, ResourceKind.SAMPLED_TEXTURE, stableKey),
                BindingKind.SAMPLED_TEXTURE,
                Access.READ,
                subresource,
                VulkanicResourceUsage.SAMPLED_READ,
                targetClass,
                FormatClass.COLOR,
                UNKNOWN_GENERATION,
                OptionalInt.of(legacyTextureId),
                legacyTarget,
                textureUnit,
                samplerObject,
                OptionalInt.empty(),
                OptionalInt.empty(),
                false
            );
        }

        public static CanonicalResourceReference storageImage(
            String logicalName,
            String stableKey,
            int legacyTextureId,
            int level,
            boolean layered,
            int layer,
            Access access,
            VulkanicResourceUsage usage,
            OptionalInt imageUnit,
            OptionalInt imageAccess,
            OptionalInt imageFormat
        ) {
            return new CanonicalResourceReference(
                ResourceIdentity.of(logicalName, ResourceKind.STORAGE_TEXTURE, stableKey),
                BindingKind.STORAGE_IMAGE,
                access,
                layered
                    ? Subresource.color(level, 1, 0, Math.max(1, layer + 1))
                    : Subresource.color(level, 1, Math.max(0, layer), 1),
                usage,
                TargetClass.UNKNOWN,
                FormatClass.COLOR,
                UNKNOWN_GENERATION,
                OptionalInt.of(legacyTextureId),
                OptionalInt.empty(),
                imageUnit,
                OptionalInt.empty(),
                imageAccess,
                imageFormat,
                layered
            );
        }

        public static CanonicalResourceReference bufferRange(
            String logicalName,
            ResourceKind kind,
            String stableKey,
            long offset,
            long size,
            Access access,
            VulkanicResourceUsage usage,
            OptionalInt bindingUnit
        ) {
            return new CanonicalResourceReference(
                ResourceIdentity.of(logicalName, kind, stableKey),
                BindingKind.BUFFER_RANGE,
                access,
                Subresource.bufferRange(offset, size),
                usage,
                TargetClass.BUFFER,
                FormatClass.BUFFER,
                UNKNOWN_GENERATION,
                OptionalInt.empty(),
                OptionalInt.empty(),
                bindingUnit,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                false
            );
        }

        private static BindingKind bindingKindFor(ResourceKind kind) {
            return switch (kind) {
                case SAMPLED_TEXTURE -> BindingKind.SAMPLED_TEXTURE;
                case STORAGE_TEXTURE -> BindingKind.STORAGE_IMAGE;
                case TEXEL_BUFFER -> BindingKind.TEXEL_BUFFER;
                case COLOR_ATTACHMENT, DEPTH_ATTACHMENT -> BindingKind.ATTACHMENT;
                default -> BindingKind.BUFFER_RANGE;
            };
        }

        private static TargetClass targetClassFor(ResourceKind kind) {
            return switch (kind) {
                case SAMPLED_TEXTURE, STORAGE_TEXTURE, COLOR_ATTACHMENT, DEPTH_ATTACHMENT -> TargetClass.UNKNOWN;
                case TEXEL_BUFFER -> TargetClass.BUFFER;
                default -> TargetClass.BUFFER;
            };
        }

        private static FormatClass formatClassFor(ResourceKind kind, Subresource subresource) {
            if (kind == ResourceKind.TEXEL_BUFFER
                || kind == ResourceKind.UNIFORM_BUFFER
                || kind == ResourceKind.STORAGE_BUFFER
                || kind == ResourceKind.VERTEX_BUFFER
                || kind == ResourceKind.INDEX_BUFFER
                || kind == ResourceKind.INDIRECT_BUFFER) {
                return FormatClass.BUFFER;
            }
            if (subresource.aspects().contains(Aspect.DEPTH) && subresource.aspects().contains(Aspect.STENCIL)) {
                return FormatClass.DEPTH_STENCIL;
            }
            if (subresource.aspects().contains(Aspect.DEPTH)) {
                return FormatClass.DEPTH;
            }
            if (subresource.aspects().contains(Aspect.STENCIL)) {
                return FormatClass.STENCIL;
            }
            return FormatClass.COLOR;
        }
    }

    public record AttachmentUse(
        int attachmentIndex,
        ResourceIdentity resource,
        Subresource subresource,
        VulkanicRenderPassDescriptor.LoadOp loadOp,
        VulkanicRenderPassDescriptor.StoreOp storeOp,
        OptionalInt clearColor,
        OptionalDouble clearDepth,
        VulkanicResourceUsage initialUsage,
        VulkanicResourceUsage passUsage,
        VulkanicResourceUsage finalUsage,
        boolean feedbackLoop
    ) {
        public AttachmentUse {
            if (attachmentIndex < 0) {
                throw new IllegalArgumentException("attachmentIndex must be >= 0");
            }
            resource = Objects.requireNonNull(resource, "resource");
            subresource = Objects.requireNonNull(subresource, "subresource");
            loadOp = Objects.requireNonNull(loadOp, "loadOp");
            storeOp = Objects.requireNonNull(storeOp, "storeOp");
            clearColor = Objects.requireNonNull(clearColor, "clearColor");
            clearDepth = Objects.requireNonNull(clearDepth, "clearDepth");
            initialUsage = Objects.requireNonNull(initialUsage, "initialUsage");
            passUsage = Objects.requireNonNull(passUsage, "passUsage");
            finalUsage = Objects.requireNonNull(finalUsage, "finalUsage");
            if (resource.kind() != ResourceKind.COLOR_ATTACHMENT && resource.kind() != ResourceKind.DEPTH_ATTACHMENT) {
                throw new IllegalArgumentException("attachment resource must be color or depth attachment");
            }
            if (clearColor.isPresent() && clearDepth.isPresent()) {
                throw new IllegalArgumentException("attachment cannot have both color and depth clear values");
            }
        }

        public ResourceUse passResourceUse(int order) {
            Access access = feedbackLoop ? Access.READ_WRITE : Access.WRITE;
            return ResourceUse.of(resource, access, subresource, passUsage, "attachment[" + attachmentIndex + "]", feedbackLoop, order);
        }
    }

    public record BindingSnapshot(
        String name,
        ResourceUse resourceUse,
        OptionalInt set,
        OptionalInt binding,
        Optional<CanonicalResourceReference> resourceReference
    ) {
        public BindingSnapshot {
            name = requireNonBlank(name, "name");
            resourceUse = Objects.requireNonNull(resourceUse, "resourceUse");
            set = Objects.requireNonNull(set, "set");
            binding = Objects.requireNonNull(binding, "binding");
            resourceReference = Objects.requireNonNull(resourceReference, "resourceReference");
            if (resourceReference.isPresent()) {
                CanonicalResourceReference reference = resourceReference.get();
                if (!reference.resource().stableKey().equals(resourceUse.resource().stableKey())) {
                    throw new IllegalArgumentException(
                        "binding resource reference stable key "
                            + reference.resource().stableKey()
                            + " does not match resource use "
                            + resourceUse.resource().stableKey()
                    );
                }
            }
        }

        public BindingSnapshot(
            String name,
            ResourceUse resourceUse,
            OptionalInt set,
            OptionalInt binding
        ) {
            this(name, resourceUse, set, binding, Optional.empty());
        }
    }

    public record Command(
        String label,
        OptionalInt drawCount,
        OptionalInt dispatchGroupCount
    ) {
        public Command {
            label = requireNonBlank(label, "label");
            drawCount = Objects.requireNonNull(drawCount, "drawCount");
            dispatchGroupCount = Objects.requireNonNull(dispatchGroupCount, "dispatchGroupCount");
        }
    }

    public record PassRequest(
        PassKind kind,
        String label,
        List<AttachmentUse> attachments,
        List<ResourceUse> resources,
        List<BindingSnapshot> bindings,
        List<Command> commands,
        List<String> requiredOrdering,
        boolean abandoned,
        boolean deviceLost
    ) {
        public PassRequest {
            kind = Objects.requireNonNull(kind, "kind");
            label = requireNonBlank(label, "label");
            attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
            resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
            bindings = List.copyOf(Objects.requireNonNull(bindings, "bindings"));
            commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
            requiredOrdering = List.copyOf(Objects.requireNonNull(requiredOrdering, "requiredOrdering"));
        }
    }

    public record PassExecutionPlan(
        PassRequest request,
        List<ResourceUse> orderedUses,
        List<ResourceUse> finalResourceUsages
    ) {
        public PassExecutionPlan {
            request = Objects.requireNonNull(request, "request");
            orderedUses = List.copyOf(Objects.requireNonNull(orderedUses, "orderedUses"));
            finalResourceUsages = List.copyOf(Objects.requireNonNull(finalResourceUsages, "finalResourceUsages"));
        }

        public Optional<ResourceUse> firstUse(String stableKey) {
            Objects.requireNonNull(stableKey, "stableKey");
            return orderedUses.stream()
                .filter(use -> use.resource().stableKey().equals(stableKey))
                .findFirst();
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
