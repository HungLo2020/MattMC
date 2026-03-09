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

    @Test
    public void testDhGlObjectLayerExposesContextAwareSeams() throws IOException {
        Path shaderProgram = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java");
        Path glState = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/GLState.java");
        Path framebuffer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/texture/DhFramebuffer.java");
        Path glBuffer = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/buffer/GLBuffer.java");
        Path colorTexture = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/texture/DhColorTexture.java");
        Path depthTexture = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/texture/DHDepthTexture.java");
        Path textureState = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/DhTextureState.java");
        Path glProxy = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/GLProxy.java");
        Path abstractVertexAttribute = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/vertexAttribute/AbstractVertexAttribute.java");
        Path vertexAttributePreGL43 = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/vertexAttribute/VertexAttributePreGL43.java");
        Path vertexAttributePostGL43 = SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/glObject/vertexAttribute/VertexAttributePostGL43.java");

        String shaderProgramSource = readSourceWithoutComments(shaderProgram);
        String glStateSource = readSourceWithoutComments(glState);
        String framebufferSource = readSourceWithoutComments(framebuffer);
        String glBufferSource = readSourceWithoutComments(glBuffer);
        String colorTextureSource = readSourceWithoutComments(colorTexture);
        String depthTextureSource = readSourceWithoutComments(depthTexture);
        String textureStateSource = readSourceWithoutComments(textureState);
        String glProxySource = readSourceWithoutComments(glProxy);
        String abstractVertexAttributeSource = readSourceWithoutComments(abstractVertexAttribute);
        String vertexAttributePreGL43Source = readSourceWithoutComments(vertexAttributePreGL43);
        String vertexAttributePostGL43Source = readSourceWithoutComments(vertexAttributePostGL43);

        assertNoImmediateContext(shaderProgram);
        assertNoImmediateContext(glState);
        assertNoImmediateContext(framebuffer);
        assertNoImmediateContext(glBuffer);
        assertNoImmediateContext(colorTexture);
        assertNoImmediateContext(depthTexture);
        assertNoImmediateContext(textureState);
        assertNoImmediateContext(glProxy);
        assertNoImmediateContext(abstractVertexAttribute);
        assertNoImmediateContext(vertexAttributePreGL43);
        assertNoImmediateContext(vertexAttributePostGL43);

        assertTrue(shaderProgramSource.contains("bind(CommandContext ctx)"),
            "ShaderProgram should expose explicit context-aware bind");
        assertTrue(shaderProgramSource.contains("setUniform(CommandContext ctx"),
            "ShaderProgram should expose explicit context-aware uniform updates");

        assertTrue(glStateSource.contains("saveState(CommandContext ctx)"),
            "GLState should support context-aware state capture");
        assertTrue(glStateSource.contains("restore(CommandContext ctx)"),
            "GLState should support context-aware state restore");

        assertTrue(framebufferSource.contains("bind(CommandContext ctx)"),
            "DhFramebuffer should expose context-aware bind operations");
        assertTrue(framebufferSource.contains("addDepthAttachment(CommandContext ctx"),
            "DhFramebuffer should expose context-aware attachment operations");

        assertTrue(glBufferSource.contains("bind(CommandContext ctx)"),
            "GLBuffer should expose context-aware bind/unbind operations");

        assertTrue(colorTextureSource.contains("resizeTexture(CommandContext ctx"),
            "DhColorTexture should route texture upload through explicit context seam");
        assertTrue(depthTextureSource.contains("resize(CommandContext ctx"),
            "DHDepthTexture should expose context-aware resize operation");
        assertTrue(depthTextureSource.contains("destroy(CommandContext ctx)"),
            "DHDepthTexture should expose context-aware destroy operation");
        assertTrue(textureStateSource.contains("bindTexture2D(CommandContext ctx"),
            "DhTextureState should expose context-aware texture binding helper");
        assertTrue(glProxySource.contains("VulkanicAPI.getCommandContext()"),
            "GLProxy should query renderer metadata through backend-neutral context");

        assertTrue(abstractVertexAttributeSource.contains("create(CommandContext ctx)"),
            "AbstractVertexAttribute should expose context-aware factory method");
        assertTrue(abstractVertexAttributeSource.contains("bind(CommandContext ctx)"),
            "AbstractVertexAttribute should expose context-aware bind operation");
        assertTrue(vertexAttributePreGL43Source.contains("VertexAttributePreGL43(CommandContext ctx)"),
            "VertexAttributePreGL43 should support context-aware construction");
        assertTrue(vertexAttributePostGL43Source.contains("VertexAttributePostGL43(CommandContext ctx)"),
            "VertexAttributePostGL43 should support context-aware construction");
    }

    @Test
    public void testDhPostProcessRenderersAvoidImmediateContext() throws IOException {
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/SSAORenderer.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/FogRenderer.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/DhFadeRenderer.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/VanillaFadeRenderer.java"));
    }

    @Test
    public void testDhSharedAndDebugRenderPathsAvoidImmediateContext() throws IOException {
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/ScreenQuad.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/DebugRenderer.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "com/seibel/distanthorizons/core/render/renderer/TestRenderer.java"));
    }

    @Test
    public void testIrisDhCompatProgramsAvoidImmediateContext() throws IOException {
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java"));
    }

    @Test
    public void testIrisDhCompatEventsAndInternalsAvoidImmediateContext() throws IOException {
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/LodRendererEvents.java"));
        assertNoImmediateContext(SRC_MAIN_JAVA.resolve(
            "net/irisshaders/iris/compat/dh/DHCompatInternal.java"));
    }
}
