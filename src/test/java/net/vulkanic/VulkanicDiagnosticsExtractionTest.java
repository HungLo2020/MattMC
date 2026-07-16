package net.vulkanic;

import net.vulkanic.diagnostics.VulkanicDiagnostics;
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
