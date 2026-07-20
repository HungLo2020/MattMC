package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicPassResourcePlanner;
import net.vulkanic.VulkanicResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OpenGLResourceUsageExecutionPlannerTest {
    @Test
    void derivesOpenGlBarrierCategoriesFromSharedResourcePlan() {
        VulkanicPassResourceModel.ResourceUse colorWrite = use(
            "color0",
            VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT,
            VulkanicPassResourceModel.Access.WRITE,
            VulkanicResourceUsage.COLOR_ATTACHMENT_WRITE,
            "texture:color"
        );
        VulkanicPassResourceModel.ResourceUse storageWrite = use(
            "storage",
            VulkanicPassResourceModel.ResourceKind.STORAGE_BUFFER,
            VulkanicPassResourceModel.Access.READ_WRITE,
            VulkanicResourceUsage.STORAGE_READ_WRITE,
            "buffer:ssbo"
        );
        VulkanicPassResourceModel.PassExecutionPlan plan = VulkanicPassResourcePlanner.plan(
            new VulkanicPassResourceModel.PassRequest(
                VulkanicPassResourceModel.PassKind.RENDER,
                "composite",
                List.of(),
                List.of(colorWrite, storageWrite),
                List.of(),
                List.of(),
                List.of(),
                false,
                false
            )
        );

        OpenGLResourceUsageExecutionPlanner.ExecutionPlan execution =
            OpenGLResourceUsageExecutionPlanner.plan(plan);

        assertEquals(VulkanicPassResourceModel.PassKind.RENDER, execution.kind());
        assertEquals(2, execution.orderedUses().size());
        assertTrue((execution.postWriteBarrierBits() & GL42.GL_FRAMEBUFFER_BARRIER_BIT) != 0);
        assertTrue((execution.postWriteBarrierBits() & GL42.GL_TEXTURE_FETCH_BARRIER_BIT) != 0);
        assertTrue((execution.postWriteBarrierBits() & GL43.GL_SHADER_STORAGE_BARRIER_BIT) != 0);
    }

    @Test
    void rejectsNonCanonicalPlanBeforeOpenGlLowering() {
        VulkanicPassResourceModel.ResourceUse use = use(
            "copy-dst",
            VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
            VulkanicPassResourceModel.Access.WRITE,
            VulkanicResourceUsage.TRANSFER_DST,
            "texture:copy-dst"
        );
        VulkanicPassResourceModel.PassRequest request = new VulkanicPassResourceModel.PassRequest(
            VulkanicPassResourceModel.PassKind.TRANSFER,
            "copy",
            List.of(),
            List.of(use),
            List.of(),
            List.of(),
            List.of(),
            false,
            false
        );

        assertThrows(IllegalArgumentException.class, () -> OpenGLResourceUsageExecutionPlanner.plan(
            new VulkanicPassResourceModel.PassExecutionPlan(request, List.of(), List.of())
        ));
    }

    private static VulkanicPassResourceModel.ResourceUse use(
        String logicalName,
        VulkanicPassResourceModel.ResourceKind kind,
        VulkanicPassResourceModel.Access access,
        VulkanicResourceUsage usage,
        String stableKey
    ) {
        VulkanicPassResourceModel.Subresource subresource =
            kind == VulkanicPassResourceModel.ResourceKind.STORAGE_BUFFER
                ? VulkanicPassResourceModel.Subresource.bufferRange(0, 64)
                : VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1);
        return VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(logicalName, kind, stableKey),
            access,
            subresource,
            usage,
            logicalName,
            false,
            0
        );
    }
}
