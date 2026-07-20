package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;
import net.vulkanic.backends.vulkan.VulkanBackend;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanicGalContractFreezeTest {

    private static final Set<String> RAW_EXECUTION_METHODS = Set.of(
        "drawArrays",
        "drawElements",
        "drawIndexedInstancedBaseVertex",
        "drawIndexedBaseVertex",
        "drawIndexedInstanced",
        "drawArraysInstanced",
        "multiDrawElementsBaseVertex",
        "dispatchCompute",
        "dispatchComputeIndirect",
        "clearBuffers",
        "uploadTexture1D",
        "uploadTexture2D",
        "uploadTexture2DSubImage",
        "uploadTexture3D",
        "copyBufferSubData",
        "copyNamedBufferSubDataDSA",
        "copyImageSubData",
        "copyTexImage2D",
        "copyTexSubImage2D",
        "copyTextureSubImage2D",
        "blitFramebuffer",
        "blitNamedFramebuffer",
        "blitNamedFramebufferDSA",
        "generateTextureMipmap",
        "generateTextureMipmapDSA",
        "readPixels"
    );

    @Test
    public void concreteBackendsExposeNoRawSemanticExecutionSurface() {
        assertNoPublicRawExecutionMethods(OpenGLBackend.class);
        assertNoPublicRawExecutionMethods(VulkanBackend.class);
    }

    @Test
    public void contractSchemaAndFingerprintAreDeterministicAndComplete() {
        String schema = VulkanicGalExecutionRequest.contractSchema();
        String first = VulkanicGalExecutionRequest.contractSchemaFingerprint();
        String second = VulkanicGalExecutionRequest.contractSchemaFingerprint();

        assertTrue(schema.startsWith("vulkanic-gal-contract " + VulkanicGalExecutionRequest.CONTRACT_VERSION));
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertTrue(first.equals(second));

        for (VulkanicGalExecutionRequest.ExecutionStatus status : VulkanicGalExecutionRequest.ExecutionStatus.values()) {
            assertTrue(schema.contains(status.name()), "schema should contain execution status " + status);
        }
        for (VulkanicGalExecutionRequest.DrawCommandKind kind : VulkanicGalExecutionRequest.DrawCommandKind.values()) {
            assertTrue(schema.contains(kind.name()), "schema should contain draw kind " + kind);
        }
        for (VulkanicGalExecutionRequest.RenderPassBeginKind kind : VulkanicGalExecutionRequest.RenderPassBeginKind.values()) {
            assertTrue(schema.contains(kind.name()), "schema should contain render-pass begin kind " + kind);
        }
        for (VulkanicGalExecutionRequest.TransferKind kind : VulkanicGalExecutionRequest.TransferKind.values()) {
            assertTrue(schema.contains(kind.name()), "schema should contain transfer kind " + kind);
        }
        for (Class<?> permitted : VulkanicGalExecutionRequest.TransferOperation.class.getPermittedSubclasses()) {
            assertTrue(schema.contains(permitted.getSimpleName()),
                "schema should contain transfer operation variant " + permitted.getSimpleName());
        }
    }

    @Test
    public void rustContractDocumentCoversEveryJavaRequestAndResultVariant() throws Exception {
        String document = VulkanicGalExecutionRequest.contractSchema();

        assertTrue(document.contains("GalRequest"));
        assertTrue(document.contains("ExecutionResult"));
        assertTrue(document.contains("GAL_CONTRACT_VERSION"));
        for (Class<?> permitted : VulkanicGalExecutionRequest.ExecutionResult.class.getPermittedSubclasses()) {
            assertTrue(document.contains(permitted.getSimpleName()),
                "document should mention result variant " + permitted.getSimpleName());
        }
        for (Class<?> permitted : VulkanicGalExecutionRequest.TransferOperation.class.getPermittedSubclasses()) {
            assertTrue(document.contains(permitted.getSimpleName()),
                "document should mention transfer operation " + permitted.getSimpleName());
        }
    }

    @Test
    public void executableRequestsDoNotCarryLegacyMetadataOrNativeHandles() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanic/VulkanicGalExecutionRequest.java"));

        assertFalse(source.contains("LegacyCompatibilityMetadata"));
        assertFalse(source.contains("long nativeHandle"));
        assertFalse(source.contains("Vk"));
        assertFalse(source.contains("GLenum"));
    }

    private static void assertNoPublicRawExecutionMethods(Class<?> backendType) {
        String publicRawNames = Arrays.stream(backendType.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::getName)
            .filter(RAW_EXECUTION_METHODS::contains)
            .distinct()
            .sorted()
            .collect(Collectors.joining(", "));

        assertTrue(publicRawNames.isEmpty(),
            backendType.getSimpleName() + " still exposes raw execution methods: " + publicRawNames);
    }
}
