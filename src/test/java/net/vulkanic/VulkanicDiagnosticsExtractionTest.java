package net.vulkanic;

import net.vulkanic.diagnostics.VulkanicDiagnostics;
import net.vulkanic.diagnostics.RenderTargetContentDiagnostics;
import net.vulkanic.diagnostics.RenderTargetContentDiagnostics.DiagnosticTextureContentHash;
import net.vulkanic.diagnostics.RenderTargetContentDiagnostics.PendingScopedCompositeColortex0SamplerReadback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanicDiagnosticsExtractionTest {
    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path DIAGNOSTICS_PATH = PROJECT_ROOT.resolve("src/main/java/net/vulkanic/diagnostics");

    @AfterEach
    void resetDiagnostics() {
        VulkanicDiagnostics.resetMutableStateForTests();
        RenderTargetContentDiagnostics.resetMutableStateForTests();
    }

    @Test
    void diagnosticsAreDisabledByDefault() {
        assertTrue(VulkanicDiagnostics.defaultDiagnosticsDisabledForTests(),
            "diagnostic flags should be inert unless explicitly enabled by system property");
        assertFalse(VulkanicAPI.isStandaloneUniformTracingEnabled());
        assertFalse(VulkanicAPI.isShaderInputParityTracingEnabled());
    }

    @Test
    void diagnosticLimitsRemainBounded() {
        assertEquals(0L, VulkanicDiagnostics.geometryShadowBytesForTests());
        assertFalse(VulkanicDiagnostics.reservePerBufferGeometryShadowForTests(
            VulkanicDiagnostics.DIAGNOSTIC_GEOMETRY_SHADOW_MAX_BUFFER_BYTES + 1));
        assertEquals(0L, VulkanicDiagnostics.geometryShadowBytesForTests());

        assertTrue(VulkanicDiagnostics.reservePerBufferGeometryShadowForTests(16));
        assertEquals(16L, VulkanicDiagnostics.geometryShadowBytesForTests());
        VulkanicDiagnostics.releaseGeometryShadowBytes(16);
        assertEquals(0L, VulkanicDiagnostics.geometryShadowBytesForTests());
    }

    @Test
    void renderTargetContentDiagnosticsAreDisabledByDefault() {
        assertFalse(RenderTargetContentDiagnostics.reserveContentReadback("pose-boundary", "disabled-key"));
        assertEquals("content-hashes-disabled",
            RenderTargetContentDiagnostics.contentReadbackUnavailableReason(null, "pose-boundary", "disabled-key"));
        assertEquals(0, RenderTargetContentDiagnostics.pendingReadbackCountForTests());
        assertEquals(0, RenderTargetContentDiagnostics.producerCountForTests());
    }

    @Test
    void renderTargetReadbackBudgetDeduplicatesAndStaysBounded() {
        assertTrue(RenderTargetContentDiagnostics.reserveContentReadbackForTests("pose-boundary", "same-resource", 2));
        assertFalse(RenderTargetContentDiagnostics.reserveContentReadbackForTests("pose-boundary", "same-resource", 2));
        assertEquals(1, VulkanicDiagnostics.RENDER_TARGET_CONTENT_READBACK_COUNT.get());
        assertEquals("content-readback-duplicate-skipped",
            RenderTargetContentDiagnostics.contentReadbackUnavailableReasonForTests(null, "pose-boundary", "same-resource", 2));

        assertFalse(RenderTargetContentDiagnostics.reserveContentReadbackForTests("pose-boundary", "over-budget", 1));
        assertEquals("content-readback-budget-exhausted",
            RenderTargetContentDiagnostics.contentReadbackUnavailableReasonForTests(null, "pose-boundary", "over-budget", 1));
    }

    @Test
    void renderTargetPendingReadbackLifecycleDrainsOnce() {
        PendingScopedCompositeColortex0SamplerReadback request = new PendingScopedCompositeColortex0SamplerReadback(
            "vulkan",
            "composite",
            "iris:composite",
            "vertex",
            "fragment",
            "pipeline-key",
            "stable-key",
            "colortex0",
            0,
            null,
            42,
            "legacy:42",
            "colortex0",
            "main",
            0,
            42,
            "target",
            "usage",
            "draw",
            "vertex-input",
            "pipeline-state",
            "0,0,1280,720",
            "0,0,1280,720",
            "initial",
            "pose=initial frame=1",
            new RenderTargetContentDiagnostics.PendingReadbackAction() {
                @Override
                public DiagnosticTextureContentHash read() {
                    return DiagnosticTextureContentHash.unavailable("colortex0", null, null, "test");
                }

                @Override
                public String lifecycleInfo() {
                    return "test-lifecycle";
                }
            }
        );

        RenderTargetContentDiagnostics.recordPendingScopedCompositeSamplerReadback("pending", request);
        RenderTargetContentDiagnostics.recordPendingScopedCompositeSamplerReadback("pending", request);

        assertEquals(1, RenderTargetContentDiagnostics.pendingReadbackCountForTests());
        assertEquals(1, RenderTargetContentDiagnostics.drainPendingScopedCompositeSamplerReadbacks().size());
        assertEquals(0, RenderTargetContentDiagnostics.pendingReadbackCountForTests());
        assertEquals(0, RenderTargetContentDiagnostics.drainPendingScopedCompositeSamplerReadbacks().size());
    }

    @Test
    void vulkanicApiDiagnosticShimsRemainEquivalent() {
        assertEquals(
            VulkanicDiagnostics.GENERATED_STANDALONE_UNIFORM_BLOCK_NAME,
            VulkanicAPI.generatedStandaloneUniformBlockName()
        );
        assertEquals(
            VulkanicDiagnostics.TRACE_STANDALONE_UNIFORMS,
            VulkanicAPI.isStandaloneUniformTracingEnabled()
        );
        assertEquals(
            VulkanicDiagnostics.TRACE_SHADER_INPUT_PARITY,
            VulkanicAPI.isShaderInputParityTracingEnabled()
        );
        assertEquals(
            VulkanicDiagnostics.currentSemanticDrawContextFields(),
            VulkanicAPI.currentShaderInputParitySemanticDrawContextFields()
        );
    }

    @Test
    void diagnosticsPackageDoesNotOwnBackendImplementationReferences() throws IOException {
        try (Stream<Path> paths = Files.walk(DIAGNOSTICS_PATH)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .toList();

            for (Path javaFile : javaFiles) {
                String source = Files.readString(javaFile);
                assertFalse(source.contains("net.vulkanic.backends."),
                    "diagnostics should not own backend implementation references: " + javaFile);
                assertFalse(source.contains("org.lwjgl.vulkan."),
                    "diagnostics should not own Vulkan resources: " + javaFile);
            }
        }
    }
}
