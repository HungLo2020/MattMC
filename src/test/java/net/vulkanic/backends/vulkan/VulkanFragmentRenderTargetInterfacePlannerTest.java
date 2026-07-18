package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanFragmentRenderTargetInterfacePlannerTest {
    private static final int GL_NONE = 0;
    private static final int GL_BACK = 0x0405;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_COLOR_ATTACHMENT1 = GL_COLOR_ATTACHMENT0 + 1;

    @Test
    void explicitFragmentOutputMapsToMatchingFloatAttachment() {
        VulkanFragmentRenderTargetInterfacePlanner.FragmentRenderTargetCompatibilityResult result =
            VulkanFragmentRenderTargetInterfacePlanner.plan(
                VulkanFragmentRenderTargetInterfacePlanner.fragmentOutputInterface(List.of(
                    fragmentModule("composite.frag", output(0, "fragColor", "vec4"))
                )),
                framebufferTarget(
                    List.of(colorAttachment(0, GL_COLOR_ATTACHMENT0, VK10.VK_FORMAT_R16G16B16A16_SFLOAT)),
                    new int[] {GL_COLOR_ATTACHMENT0},
                    false
                )
            );

        assertTrue(result.compatible());
        assertEquals(1, result.mappings().size());
        assertTrue(result.mappings().getFirst().attachment().isPresent());
        assertEquals(0, result.mappings().getFirst().attachment().orElseThrow().colorIndex());
        assertEquals(1, result.blendPlan().attachments().size());
    }

    @Test
    void legacyGlFragColorAndFragDataAreReflectedAsColorLocations() {
        List<VulkanShaderVariantPlanner.ReflectedFragmentOutput> fragColor =
            VulkanShaderVariantPlanner.collectFragmentOutputs(
                "#version 330\nvoid main(){ gl_FragColor = vec4(1.0); }"
            );
        List<VulkanShaderVariantPlanner.ReflectedFragmentOutput> fragData =
            VulkanShaderVariantPlanner.collectFragmentOutputs(
                "#version 330\nvoid main(){ gl_FragData[2] = vec4(1.0); gl_FragData[0] = vec4(0.0); }"
            );

        assertEquals(List.of(new VulkanShaderVariantPlanner.ReflectedFragmentOutput(0, "gl_FragColor", "vec4")),
            fragColor);
        assertEquals(
            List.of(
                new VulkanShaderVariantPlanner.ReflectedFragmentOutput(2, "gl_FragData[2]", "vec4"),
                new VulkanShaderVariantPlanner.ReflectedFragmentOutput(0, "gl_FragData[0]", "vec4")
            ),
            fragData
        );
    }

    @Test
    void sparseDrawBuffersRouteOutputLocationsWithoutConfusingNoneWithAttachmentZero() {
        VulkanFragmentRenderTargetInterfacePlanner.FragmentRenderTargetCompatibilityResult result =
            VulkanFragmentRenderTargetInterfacePlanner.plan(
                new VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputInterface(List.of(
                    declaration(0, "unused", "vec4"),
                    declaration(2, "target", "vec4")
                )),
                framebufferTarget(
                    List.of(colorAttachment(0, GL_COLOR_ATTACHMENT0, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                    new int[] {GL_NONE, GL_NONE, GL_COLOR_ATTACHMENT0},
                    false
                )
            );

        assertTrue(result.compatible());
        assertFalse(result.mappings().get(0).attachment().isPresent());
        assertEquals("draw-buffer-none-or-missing", result.mappings().get(0).note());
        assertTrue(result.mappings().get(1).attachment().isPresent());
        assertEquals(0, result.mappings().get(1).attachment().orElseThrow().colorIndex());
        assertEquals(
            VulkanFragmentRenderTargetInterfacePlanner.Severity.WARNING,
            result.diagnostics().getFirst().severity()
        );
    }

    @Test
    void defaultBackBufferRoutesToAttachmentZero() {
        VulkanFragmentRenderTargetInterfacePlanner.FragmentRenderTargetCompatibilityResult result =
            VulkanFragmentRenderTargetInterfacePlanner.plan(
                new VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputInterface(List.of(
                    declaration(0, "fragColor", "vec4")
                )),
                framebufferTarget(
                    List.of(colorAttachment(0, GL_BACK, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                    new int[] {GL_BACK},
                    false
                )
            );

        assertTrue(result.compatible());
        assertEquals(0, result.mappings().getFirst().attachment().orElseThrow().colorIndex());
    }

    @Test
    void integerOutputsRequireIntegerAttachments() {
        VulkanFragmentRenderTargetInterfacePlanner.FragmentRenderTargetCompatibilityResult compatible =
            VulkanFragmentRenderTargetInterfacePlanner.plan(
                new VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputInterface(List.of(
                    declaration(0, "entityId", "uvec4")
                )),
                framebufferTarget(
                    List.of(colorAttachment(0, GL_COLOR_ATTACHMENT0, VK10.VK_FORMAT_R32G32B32A32_UINT)),
                    new int[] {GL_COLOR_ATTACHMENT0},
                    false
                )
            );
        VulkanFragmentRenderTargetInterfacePlanner.FragmentRenderTargetCompatibilityResult incompatible =
            VulkanFragmentRenderTargetInterfacePlanner.plan(
                new VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputInterface(List.of(
                    declaration(0, "entityId", "uvec4")
                )),
                framebufferTarget(
                    List.of(colorAttachment(0, GL_COLOR_ATTACHMENT0, VK10.VK_FORMAT_R16G16B16A16_SFLOAT)),
                    new int[] {GL_COLOR_ATTACHMENT0},
                    false
                )
            );

        assertTrue(compatible.compatible());
        assertFalse(incompatible.compatible());
        assertEquals(
            VulkanFragmentRenderTargetInterfacePlanner.Severity.ERROR,
            incompatible.diagnostics().getFirst().severity()
        );
        assertEquals("ShaderOutputAttachmentTypeMismatch", incompatible.diagnostics().getFirst().code());
    }

    @Test
    void missingColorAttachmentIsRejectedWhenDrawBufferRoutesThere() {
        VulkanFragmentRenderTargetInterfacePlanner.FragmentRenderTargetCompatibilityResult result =
            VulkanFragmentRenderTargetInterfacePlanner.plan(
                new VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputInterface(List.of(
                    declaration(0, "fragColor", "vec4")
                )),
                framebufferTarget(
                    List.of(colorAttachment(0, GL_COLOR_ATTACHMENT0, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                    new int[] {GL_COLOR_ATTACHMENT1},
                    false
                )
            );

        assertFalse(result.compatible());
        assertEquals("MissingColorAttachment", result.diagnostics().getFirst().code());
    }

    @Test
    void depthOnlyTargetAcceptsShadersWithNoFragmentOutputs() {
        VulkanFragmentRenderTargetInterfacePlanner.FragmentRenderTargetCompatibilityResult result =
            VulkanFragmentRenderTargetInterfacePlanner.plan(
                new VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputInterface(List.of()),
                VulkanFragmentRenderTargetInterfacePlanner.renderTargetInterface(
                    "DEPTH_ONLY",
                    List.of(),
                    VK10.VK_FORMAT_D32_SFLOAT,
                    true,
                    false,
                    false,
                    VulkanFragmentRenderTargetInterfacePlanner.DrawBufferRoutingSnapshot.fromDrawBuffers(new int[] {})
                )
            );

        assertTrue(result.compatible());
        assertTrue(result.blendPlan().attachments().isEmpty());
        assertTrue(result.mappings().isEmpty());
    }

    @Test
    void loadStoreParticipatesInRenderTargetIdentity() {
        VulkanFragmentRenderTargetInterfacePlanner.RenderTargetInterface first =
            framebufferTarget(
                List.of(new VulkanFragmentRenderTargetInterfacePlanner.ColorAttachmentInterface(
                    0,
                    GL_COLOR_ATTACHMENT0,
                    VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    VulkanFragmentRenderTargetInterfacePlanner.NumericClass.FLOAT,
                    Optional.of(VulkanicRenderPassDescriptor.LoadOp.LOAD),
                    Optional.of(VulkanicRenderPassDescriptor.StoreOp.STORE),
                    false
                )),
                new int[] {GL_COLOR_ATTACHMENT0},
                false
            );
        VulkanFragmentRenderTargetInterfacePlanner.RenderTargetInterface second =
            framebufferTarget(
                List.of(new VulkanFragmentRenderTargetInterfacePlanner.ColorAttachmentInterface(
                    0,
                    GL_COLOR_ATTACHMENT0,
                    VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    VulkanFragmentRenderTargetInterfacePlanner.NumericClass.FLOAT,
                    Optional.of(VulkanicRenderPassDescriptor.LoadOp.CLEAR),
                    Optional.of(VulkanicRenderPassDescriptor.StoreOp.STORE),
                    false
                )),
                new int[] {GL_COLOR_ATTACHMENT0},
                false
            );

        assertFalse(first.cacheKey().equals(second.cacheKey()));
    }

    @Test
    void feedbackLoopParticipatesInRenderTargetIdentity() {
        VulkanFragmentRenderTargetInterfacePlanner.RenderTargetInterface first =
            framebufferTarget(
                List.of(colorAttachment(0, GL_COLOR_ATTACHMENT0, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                new int[] {GL_COLOR_ATTACHMENT0},
                false
            );
        VulkanFragmentRenderTargetInterfacePlanner.RenderTargetInterface second =
            framebufferTarget(
                List.of(new VulkanFragmentRenderTargetInterfacePlanner.ColorAttachmentInterface(
                    0,
                    GL_COLOR_ATTACHMENT0,
                    VK10.VK_FORMAT_R8G8B8A8_UNORM,
                    VulkanFragmentRenderTargetInterfacePlanner.NumericClass.FLOAT,
                    Optional.empty(),
                    Optional.empty(),
                    true
                )),
                new int[] {GL_COLOR_ATTACHMENT0},
                true
            );

        assertFalse(first.cacheKey().equals(second.cacheKey()));
        assertTrue(second.feedbackLoop());
    }

    @Test
    void equivalentInterfaceKeysIgnoreUniformOnlyModuleChanges() {
        VulkanicSpirvModule first = fragmentModule("legacy.frag", output(0, "fragColor", "vec4"));
        VulkanicSpirvModule second = new VulkanicSpirvModule(
            VulkanicShaderStage.FRAGMENT,
            "main",
            new byte[] {9, 9, 9},
            "legacy.frag",
            "test"
        ).withFragmentOutputs(List.of(output(0, "fragColor", "vec4")));

        assertEquals(
            VulkanFragmentRenderTargetInterfacePlanner.fragmentOutputInterface(List.of(first)).cacheKey(),
            VulkanFragmentRenderTargetInterfacePlanner.fragmentOutputInterface(List.of(second)).cacheKey()
        );
    }

    @Test
    void entityTranslucentExtraLegacyOutputsAreClassifiedAsUnconsumedWarnings() {
        VulkanFragmentRenderTargetInterfacePlanner.FragmentRenderTargetCompatibilityResult result =
            VulkanFragmentRenderTargetInterfacePlanner.plan(
                new VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputInterface(List.of(
                    declaration(0, "gl_FragData[0]", "vec4"),
                    declaration(1, "gl_FragData[1]", "vec4"),
                    declaration(2, "gl_FragData[2]", "vec4")
                )),
                framebufferTarget(
                    List.of(colorAttachment(0, GL_COLOR_ATTACHMENT0, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
                    new int[] {GL_COLOR_ATTACHMENT0},
                    false
                )
            );

        assertTrue(result.compatible());
        assertTrue(result.mappings().get(0).attachment().isPresent());
        assertFalse(result.mappings().get(1).attachment().isPresent());
        assertFalse(result.mappings().get(2).attachment().isPresent());
        assertEquals(2, result.diagnostics().stream()
            .filter(diagnostic -> diagnostic.code().equals("ShaderOutputNotConsumed"))
            .count());
    }

    private static VulkanFragmentRenderTargetInterfacePlanner.RenderTargetInterface framebufferTarget(
        List<VulkanFragmentRenderTargetInterfacePlanner.ColorAttachmentInterface> colorAttachments,
        int[] drawBuffers,
        boolean feedbackLoop
    ) {
        return VulkanFragmentRenderTargetInterfacePlanner.renderTargetInterface(
            "FRAMEBUFFER",
            colorAttachments,
            VK10.VK_FORMAT_UNDEFINED,
            false,
            false,
            feedbackLoop,
            VulkanFragmentRenderTargetInterfacePlanner.DrawBufferRoutingSnapshot.fromDrawBuffers(drawBuffers)
        );
    }

    private static VulkanFragmentRenderTargetInterfacePlanner.ColorAttachmentInterface colorAttachment(
        int colorIndex,
        int drawBuffer,
        int format
    ) {
        return new VulkanFragmentRenderTargetInterfacePlanner.ColorAttachmentInterface(
            colorIndex,
            drawBuffer,
            format,
            VulkanFragmentRenderTargetInterfacePlanner.numericClassForVkFormat(format),
            Optional.empty(),
            Optional.empty(),
            false
        );
    }

    private static VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputDeclaration declaration(
        int location,
        String name,
        String typeName
    ) {
        return new VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputDeclaration(
            location,
            name,
            typeName,
            typeName.startsWith("u")
                ? VulkanFragmentRenderTargetInterfacePlanner.NumericClass.UINT
                : typeName.startsWith("i")
                ? VulkanFragmentRenderTargetInterfacePlanner.NumericClass.INT
                : VulkanFragmentRenderTargetInterfacePlanner.NumericClass.FLOAT,
            name.startsWith("gl_FragData")
                ? VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputSourceKind.LEGACY_FRAG_DATA
                : VulkanFragmentRenderTargetInterfacePlanner.FragmentOutputSourceKind.EXPLICIT_OUT,
            "test.frag"
        );
    }

    private static VulkanicSpirvModule fragmentModule(
        String sourceName,
        VulkanicSpirvModule.FragmentOutput... outputs
    ) {
        return new VulkanicSpirvModule(
            VulkanicShaderStage.FRAGMENT,
            "main",
            new byte[] {1, 2, 3},
            sourceName,
            "test"
        ).withFragmentOutputs(List.of(outputs));
    }

    private static VulkanicSpirvModule.FragmentOutput output(int location, String name, String typeName) {
        return new VulkanicSpirvModule.FragmentOutput(location, name, typeName);
    }
}
