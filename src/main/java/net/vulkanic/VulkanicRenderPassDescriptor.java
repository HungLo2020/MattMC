package net.vulkanic;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Backend-agnostic render pass metadata descriptor.
 *
 * <p>This models Vulkan-style attachment load/store intent while remaining usable by
 * existing OpenGL paths. It is a pre-Vulkan seam and does not require native Vulkan
 * execution to be present.
 */
public record VulkanicRenderPassDescriptor(
    Supplier<String> label,
    ColorAttachment colorAttachment,
    @Nullable DepthAttachment depthAttachment
) {

    public VulkanicRenderPassDescriptor {
        label = Objects.requireNonNull(label, "label must not be null");
        colorAttachment = Objects.requireNonNull(colorAttachment, "colorAttachment must not be null");
    }

    /**
     * Convenience constructor for color-only pass semantics using legacy optional clear.
     */
    public static VulkanicRenderPassDescriptor color(
        Supplier<String> label,
        VulkanicTextureView colorTarget,
        OptionalInt clearColor
    ) {
        Objects.requireNonNull(clearColor, "clearColor must not be null");
        ColorAttachment colorAttachment = new ColorAttachment(
            colorTarget,
            clearColor.isPresent() ? LoadOp.CLEAR : LoadOp.LOAD,
            StoreOp.STORE,
            clearColor
        );
        return new VulkanicRenderPassDescriptor(label, colorAttachment, null);
    }

    /**
     * Convenience constructor for color + depth pass semantics using legacy optional clears.
     */
    public static VulkanicRenderPassDescriptor colorAndDepth(
        Supplier<String> label,
        VulkanicTextureView colorTarget,
        OptionalInt clearColor,
        @Nullable VulkanicTextureView depthTarget,
        OptionalDouble clearDepth
    ) {
        Objects.requireNonNull(clearColor, "clearColor must not be null");
        Objects.requireNonNull(clearDepth, "clearDepth must not be null");

        ColorAttachment colorAttachment = new ColorAttachment(
            colorTarget,
            clearColor.isPresent() ? LoadOp.CLEAR : LoadOp.LOAD,
            StoreOp.STORE,
            clearColor
        );

        DepthAttachment depthAttachment = null;
        if (depthTarget != null) {
            depthAttachment = new DepthAttachment(
                depthTarget,
                clearDepth.isPresent() ? LoadOp.CLEAR : LoadOp.LOAD,
                StoreOp.STORE,
                clearDepth
            );
        }

        return new VulkanicRenderPassDescriptor(label, colorAttachment, depthAttachment);
    }

    public record ColorAttachment(
        VulkanicTextureView target,
        LoadOp loadOp,
        StoreOp storeOp,
        OptionalInt clearColor
    ) {
        public ColorAttachment {
            target = Objects.requireNonNull(target, "target must not be null");
            loadOp = Objects.requireNonNull(loadOp, "loadOp must not be null");
            storeOp = Objects.requireNonNull(storeOp, "storeOp must not be null");
            clearColor = Objects.requireNonNull(clearColor, "clearColor must not be null");

            if (loadOp == LoadOp.CLEAR && clearColor.isEmpty()) {
                throw new IllegalArgumentException("ColorAttachment loadOp=CLEAR requires clearColor");
            }

            if (loadOp != LoadOp.CLEAR && clearColor.isPresent()) {
                throw new IllegalArgumentException(
                    "ColorAttachment clearColor must be empty unless loadOp=CLEAR");
            }
        }
    }

    public record DepthAttachment(
        VulkanicTextureView target,
        LoadOp loadOp,
        StoreOp storeOp,
        OptionalDouble clearDepth
    ) {
        public DepthAttachment {
            target = Objects.requireNonNull(target, "target must not be null");
            loadOp = Objects.requireNonNull(loadOp, "loadOp must not be null");
            storeOp = Objects.requireNonNull(storeOp, "storeOp must not be null");
            clearDepth = Objects.requireNonNull(clearDepth, "clearDepth must not be null");

            if (loadOp == LoadOp.CLEAR && clearDepth.isEmpty()) {
                throw new IllegalArgumentException("DepthAttachment loadOp=CLEAR requires clearDepth");
            }

            if (loadOp != LoadOp.CLEAR && clearDepth.isPresent()) {
                throw new IllegalArgumentException(
                    "DepthAttachment clearDepth must be empty unless loadOp=CLEAR");
            }
        }
    }

    public enum LoadOp {
        LOAD,
        CLEAR,
        DONT_CARE
    }

    public enum StoreOp {
        STORE,
        DONT_CARE
    }
}
