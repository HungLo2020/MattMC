package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DistantHorizonsCommandContextMigrationTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");

    private static String readSourceWithoutComments(Path file) throws IOException {
        assertTrue(Files.exists(file), file + " must exist");
        String source = Files.readString(file);
        return source
            .replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("(?m)//.*$", "");
    }

    private static void assertNoImmediateContext(Path file) throws IOException {
        String sourceWithoutComments = readSourceWithoutComments(file);

        assertFalse(sourceWithoutComments.contains("VulkanicAPI.getImmediateContext()"),
            file + " should not hard-wire immediate OpenGL context retrieval");
    }

    private static void assertBackendNeutralSingleContext(Path file) throws IOException {
        String sourceWithoutComments = readSourceWithoutComments(file);

        assertNoImmediateContext(file);
        assertTrue(sourceWithoutComments.contains("VulkanicAPI.getCommandContext()"),
            file + " should fetch backend-neutral command context");
    }

    private static void assertShaderUsesInheritedContext(Path file) throws IOException {
        String sourceWithoutComments = readSourceWithoutComments(file);

        assertNoImmediateContext(file);
        assertFalse(sourceWithoutComments.contains("VulkanicAPI.getCommandContext()"),
            file + " should inherit command context from AbstractShaderRenderer rather than fetching its own");
        assertTrue(sourceWithoutComments.contains("CommandContext ctx"),
            file + " should receive a command context parameter in shader hooks");
    }

    @Test
    public void testAbstractShaderRendererProvidesSharedBackendNeutralContext() throws IOException {
        Path abstractShaderRenderer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/AbstractShaderRenderer.java");
        String sourceWithoutComments = readSourceWithoutComments(abstractShaderRenderer);

        assertNoImmediateContext(abstractShaderRenderer);
        assertTrue(sourceWithoutComments.contains("VulkanicAPI.getCommandContext()"),
            "AbstractShaderRenderer should fetch backend-neutral context once per render call");
        assertTrue(sourceWithoutComments.contains("onApplyUniforms(ctx, partialTicks)"),
            "AbstractShaderRenderer should pass shared context into uniform hook");
        assertTrue(sourceWithoutComments.contains("onRender(ctx)"),
            "AbstractShaderRenderer should pass shared context into render hook");
    }

    @Test
    public void testDhShaderPathsUseInheritedBackendNeutralContext() throws IOException {
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/SSAOApplyShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/DhApplyShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/FogApplyShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/FadeApplyShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/VanillaFadeShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/FogShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/SSAOShader.java"));
        assertShaderUsesInheritedContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/shaders/DhFadeShader.java"));
    }

    @Test
    public void testDhCoreRenderersUseBackendNeutralContext() throws IOException {
        assertBackendNeutralSingleContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java"));
        assertBackendNeutralSingleContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java"));
        assertBackendNeutralSingleContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/generic/RenderableBoxGroup.java"));
    }
}
