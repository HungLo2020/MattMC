package net.vulkanic;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Backend-neutral render-target attachment contract for MRT-style passes.
 *
 * <p>This is the transitional descriptor used by shader paths that still own
 * legacy texture names but should not force Vulkan to infer pass structure from
 * mutable framebuffer binding state.</p>
 */
public record VulkanicRenderTargetDescriptor(
    Supplier<String> label,
    List<ColorAttachment> colorAttachments,
    @Nullable DepthAttachment depthAttachment,
    int width,
    int height
) {
    public VulkanicRenderTargetDescriptor(
        Supplier<String> label,
        List<ColorAttachment> colorAttachments,
        @Nullable DepthAttachment depthAttachment
    ) {
        this(label, colorAttachments, depthAttachment, -1, -1);
    }

    public VulkanicRenderTargetDescriptor {
        label = Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(colorAttachments, "colorAttachments must not be null");
        if ((width <= 0) != (height <= 0)) {
            throw new IllegalArgumentException("Render target descriptor explicit extent must include positive width and height");
        }
        if (colorAttachments.isEmpty() && depthAttachment == null && (width <= 0 || height <= 0)) {
            throw new IllegalArgumentException("Render target descriptor requires at least one attachment or an explicit extent");
        }
        colorAttachments = List.copyOf(colorAttachments);
    }

    public boolean hasDepthAttachment() {
        return depthAttachment != null;
    }

    public boolean hasExplicitExtent() {
        return width > 0 && height > 0;
    }

    public String debugSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append("extent=");
        if (hasExplicitExtent()) {
            builder.append(width).append('x').append(height);
        } else {
            builder.append("implicit");
        }
        builder.append(" colors=").append(colorAttachments.size());

        for (int i = 0; i < colorAttachments.size(); i++) {
            ColorAttachment attachment = colorAttachments.get(i);
            builder.append(" c").append(i)
                .append("{tex=").append(attachment.textureId())
                .append(",load=").append(attachment.loadOp())
                .append(",store=").append(attachment.storeOp());
            attachment.clearColor().ifPresent(clearColor -> builder.append(",clear=").append(clearColor));
            builder.append('}');
        }

        if (depthAttachment != null) {
            builder.append(" depth{tex=").append(depthAttachment.textureId())
                .append(",load=").append(depthAttachment.loadOp())
                .append(",store=").append(depthAttachment.storeOp());
            depthAttachment.clearDepth().ifPresent(clearDepth -> builder.append(",clear=").append(clearDepth));
            builder.append('}');
        } else {
            builder.append(" depth=none");
        }

        return builder.toString();
    }

    public record ColorAttachment(
        int textureId,
        VulkanicRenderPassDescriptor.LoadOp loadOp,
        VulkanicRenderPassDescriptor.StoreOp storeOp,
        OptionalInt clearColor
    ) {
        public ColorAttachment {
            if (textureId <= 0) {
                throw new IllegalArgumentException("Color attachment texture id must be positive");
            }
            loadOp = Objects.requireNonNull(loadOp, "loadOp must not be null");
            storeOp = Objects.requireNonNull(storeOp, "storeOp must not be null");
            clearColor = Objects.requireNonNull(clearColor, "clearColor must not be null");
            if (loadOp == VulkanicRenderPassDescriptor.LoadOp.CLEAR && clearColor.isEmpty()) {
                throw new IllegalArgumentException("Color attachment loadOp=CLEAR requires clearColor");
            }
            if (loadOp != VulkanicRenderPassDescriptor.LoadOp.CLEAR && clearColor.isPresent()) {
                throw new IllegalArgumentException("Color attachment clearColor requires loadOp=CLEAR");
            }
        }
    }

    public record DepthAttachment(
        int textureId,
        VulkanicRenderPassDescriptor.LoadOp loadOp,
        VulkanicRenderPassDescriptor.StoreOp storeOp,
        OptionalDouble clearDepth
    ) {
        public DepthAttachment {
            if (textureId <= 0) {
                throw new IllegalArgumentException("Depth attachment texture id must be positive");
            }
            loadOp = Objects.requireNonNull(loadOp, "loadOp must not be null");
            storeOp = Objects.requireNonNull(storeOp, "storeOp must not be null");
            clearDepth = Objects.requireNonNull(clearDepth, "clearDepth must not be null");
            if (loadOp == VulkanicRenderPassDescriptor.LoadOp.CLEAR && clearDepth.isEmpty()) {
                throw new IllegalArgumentException("Depth attachment loadOp=CLEAR requires clearDepth");
            }
            if (loadOp != VulkanicRenderPassDescriptor.LoadOp.CLEAR && clearDepth.isPresent()) {
                throw new IllegalArgumentException("Depth attachment clearDepth requires loadOp=CLEAR");
            }
        }
    }
}
