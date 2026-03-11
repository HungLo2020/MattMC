package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrails for reducing OpenGL-flavored API usage in high-traffic render callsites.
 */
public class ApiNeutralityCallsiteTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");

    @Test
    public void testPrimitiveAndPolygonLegacyMappingsRemainAvailable() {
        assertTrue(VulkanicPrimitiveMode.fromLegacyGlConstant(VulkanicAPI.GL_TRIANGLES).isPresent(),
            "Primitive mode mapping should recognize GL_TRIANGLES");
        assertTrue(VulkanicPolygonFace.fromLegacyGlConstant(VulkanicAPI.GL_FRONT_AND_BACK).isPresent(),
            "Polygon-face mapping should recognize GL_FRONT_AND_BACK");
        assertTrue(VulkanicPolygonMode.fromLegacyGlConstant(VulkanicAPI.GL_FILL).isPresent(),
            "Polygon-mode mapping should recognize GL_FILL");
        assertTrue(VulkanicIndexType.fromLegacyGlConstant(VulkanicAPI.GL_UNSIGNED_INT).isPresent(),
            "Index-type mapping should recognize GL_UNSIGNED_INT");
        assertTrue(VulkanicVertexAttributeType.fromLegacyGlConstant(VulkanicAPI.GL_FLOAT).isPresent(),
            "Vertex attribute type mapping should recognize GL_FLOAT");
        assertTrue(VulkanicTextureParameterValue.fromLegacyGlConstant(VulkanicAPI.GL_CLAMP_TO_EDGE).isPresent(),
            "Texture parameter value mapping should recognize GL_CLAMP_TO_EDGE");
    }

    @Test
    public void testKeyRenderersAvoidRawPolygonModeConstants() throws IOException {
        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/renderer/DebugRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/TestRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(source.contains("setPolygonMode(ctx, VulkanicAPI.GL_FRONT_AND_BACK"),
                "Renderer should use typed polygon-mode overloads: " + relative);
        }
    }

    @Test
    public void testKeyRenderersAvoidRawDrawModeConstants() throws IOException {
        Pattern rawDrawModePattern = Pattern.compile("draw(?:Arrays|Elements|IndexedInstanced)\\s*\\([^\\n]*VulkanicAPI\\.GL_");

        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/renderer/ScreenQuad.java",
            "com/seibel/distanthorizons/core/render/renderer/TestRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/DebugRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java",
            "net/vulkanic/backends/opengl/OpenGLRenderPass.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawDrawModePattern.matcher(source).find(),
                "Renderer should use typed draw-mode overloads: " + relative);
        }
    }

    @Test
    public void testKeyRenderersAvoidRawVertexAttributeTypeConstants() throws IOException {
        Pattern rawVertexAttribPattern = Pattern.compile("setVertexAttrib(?:I)?Pointer\\s*\\([^\\n]*VulkanicAPI\\.GL_");

        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/renderer/generic/GenericObjectRenderer.java",
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawVertexAttribPattern.matcher(source).find(),
                "Renderer should use typed vertex-attribute types: " + relative);
        }
    }

    @Test
    public void testDhTextureSetupAvoidsRawTextureParameterValueConstants() throws IOException {
        Pattern rawTextureParamValuePattern = Pattern.compile("texParameteri\\s*\\([^\\n]*VulkanicTextureParameterName\\.[A-Z_]+,\\s*VulkanicAPI\\.GL_");

        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/glObject/texture/DhColorTexture.java",
            "com/seibel/distanthorizons/core/render/glObject/texture/DHDepthTexture.java",
            "com/seibel/distanthorizons/core/render/renderer/DhFadeRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/VanillaFadeRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/FogRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/SSAORenderer.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawTextureParamValuePattern.matcher(source).find(),
                "Renderer should use typed texture-parameter values: " + relative);
        }
    }

    @Test
    public void testDhRenderersUseTypedViewportAndClearColorQueryHelpers() throws IOException {
        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/glObject/GLState.java",
            "com/seibel/distanthorizons/core/render/renderer/LodRenderer.java",
            "com/seibel/distanthorizons/core/render/renderer/shaders/FogShader.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(source.contains("GL_VIEWPORT"),
                "Renderer state queries should avoid raw GL_VIEWPORT constants: " + relative);
            assertFalse(source.contains("GL_COLOR_CLEAR_VALUE"),
                "Renderer state queries should avoid raw GL_COLOR_CLEAR_VALUE constants: " + relative);
        }
    }
}
