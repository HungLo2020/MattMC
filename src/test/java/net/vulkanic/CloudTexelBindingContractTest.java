package net.vulkanic;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Supplemental callsite guard; the deterministic cloud capture verifies driver state and pixels. */
class CloudTexelBindingContractTest {
    @Test void bothTexelBindingPathsSelectTheBackendUnitWithoutAnIrisCacheShortcut() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/blaze3d/opengl/GlCommandEncoder.java"));
        int descriptorStart = source.indexOf("private void prepareTexelBufferBindingsForVulkanDescriptors(");
        String descriptor = source.substring(descriptorStart, source.indexOf("\n\t}", descriptorStart));
        String legacy = source.substring(source.indexOf("case Uniform.Utb(int var41"),
                source.indexOf("case Uniform.Sampler(int glTextureView2"));
        assertTrue(descriptor.contains("VulkanicAPI.setActiveTextureUnitIndex(ctx, samplerIndex);"));
        assertTrue(legacy.contains("VulkanicAPI.setActiveTextureUnitIndex(ctx, var42);"));
        for (String path : new String[]{descriptor, legacy}) {
            assertFalse(path.contains("IrisRenderSystem.setActiveTextureUnitIndex"));
            assertTrue(path.indexOf("VulkanicAPI.setActiveTextureUnitIndex") < path.indexOf("VulkanicAPI.bindTextureBuffer("));
        }
    }
}
