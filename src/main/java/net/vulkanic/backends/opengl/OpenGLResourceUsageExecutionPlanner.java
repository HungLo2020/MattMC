package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicPassResourcePlanner;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.util.List;
import java.util.Objects;

/**
 * OpenGL lowering of the backend-neutral resource usage contract.
 *
 * <p>OpenGL has implicit layout ownership, so this planner only derives the
 * memory-barrier categories needed after shader/transfer writes become visible.
 * It consumes the same immutable GAL resource plan as Vulkan and never reaches
 * back into mutable compatibility state.</p>
 */
final class OpenGLResourceUsageExecutionPlanner {
    private OpenGLResourceUsageExecutionPlanner() {
    }

    static ExecutionPlan plan(VulkanicPassResourceModel.PassExecutionPlan resourcePlan) {
        Objects.requireNonNull(resourcePlan, "resourcePlan");
        VulkanicPassResourceModel.PassExecutionPlan canonical =
            VulkanicPassResourcePlanner.plan(resourcePlan.request());
        if (!canonical.orderedUses().equals(resourcePlan.orderedUses())
            || !canonical.finalResourceUsages().equals(resourcePlan.finalResourceUsages())) {
            throw new IllegalArgumentException(
                "GAL resource execution plan is not canonical for pass " + resourcePlan.request().label()
            );
        }

        int postWriteBarrierBits = 0;
        for (VulkanicPassResourceModel.ResourceUse use : resourcePlan.finalResourceUsages()) {
            if (!use.writes()) {
                continue;
            }
            postWriteBarrierBits |= barrierBitsForWrittenUse(use);
        }
        return new ExecutionPlan(
            resourcePlan.request().kind(),
            resourcePlan.request().label(),
            resourcePlan.orderedUses(),
            postWriteBarrierBits
        );
    }

    private static int barrierBitsForWrittenUse(VulkanicPassResourceModel.ResourceUse use) {
        return switch (use.kind()) {
            case COLOR_ATTACHMENT, DEPTH_ATTACHMENT ->
                GL42.GL_FRAMEBUFFER_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT;
            case SAMPLED_TEXTURE, STORAGE_TEXTURE ->
                GL42.GL_TEXTURE_FETCH_BARRIER_BIT | GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
            case STORAGE_BUFFER -> GL43.GL_SHADER_STORAGE_BARRIER_BIT;
            case UNIFORM_BUFFER -> GL42.GL_UNIFORM_BARRIER_BIT;
            case TEXEL_BUFFER -> GL42.GL_TEXTURE_FETCH_BARRIER_BIT;
            case VERTEX_BUFFER -> GL42.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT;
            case INDEX_BUFFER -> GL42.GL_ELEMENT_ARRAY_BARRIER_BIT;
            case INDIRECT_BUFFER -> GL42.GL_COMMAND_BARRIER_BIT;
            case TRANSFER_SOURCE, TRANSFER_DESTINATION, READBACK_SOURCE ->
                GL42.GL_PIXEL_BUFFER_BARRIER_BIT | GL42.GL_TEXTURE_UPDATE_BARRIER_BIT;
        };
    }

    record ExecutionPlan(
        VulkanicPassResourceModel.PassKind kind,
        String label,
        List<VulkanicPassResourceModel.ResourceUse> orderedUses,
        int postWriteBarrierBits
    ) {
        ExecutionPlan {
            kind = Objects.requireNonNull(kind, "kind");
            label = Objects.requireNonNull(label, "label");
            orderedUses = List.copyOf(Objects.requireNonNull(orderedUses, "orderedUses"));
        }
    }
}
