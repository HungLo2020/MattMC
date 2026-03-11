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
        assertTrue(VulkanicTextureParameterValue.fromLegacyGlConstant(VulkanicAPI.GL_COMPARE_REF_TO_TEXTURE).isPresent(),
            "Texture parameter value mapping should recognize GL_COMPARE_REF_TO_TEXTURE");
        assertTrue(VulkanicTextureSwizzleComponent.fromLegacyGlConstant(VulkanicAPI.GL_RED).isPresent(),
            "Texture swizzle component mapping should recognize GL_RED");
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

    @Test
    public void testIrisTextureParameterCallsitesAvoidRawPnameAndValueConstants() throws IOException {
        Pattern rawIrisTextureParamPattern = Pattern.compile("IrisRenderSystem\\.texParameteri\\s*\\([^\\n]*VulkanicAPI\\.GL_TEXTURE_[A-Z_]+[^\\n]*VulkanicAPI\\.GL_");

        List<String> files = List.of(
            "net/irisshaders/iris/gl/IrisRenderSystem.java",
            "net/irisshaders/iris/shadows/ShadowCompositeRenderer.java",
            "net/irisshaders/iris/shadows/ShadowRenderer.java",
            "net/irisshaders/iris/pipeline/FinalPassRenderer.java",
            "net/irisshaders/iris/pipeline/CompositeRenderer.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawIrisTextureParamPattern.matcher(source).find(),
                "Iris texture setup should use typed texture parameter APIs: " + relative);
        }
    }

    @Test
    public void testIrisPbrUtilitiesUseTypedViewportAndClearColorQueryHelpers() throws IOException {
        String relative = "net/irisshaders/iris/pbr/util/TextureManipulationUtil.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("GL_VIEWPORT"),
            "Iris PBR utilities should avoid raw GL_VIEWPORT constants: " + relative);
        assertFalse(source.contains("GL_COLOR_CLEAR_VALUE"),
            "Iris PBR utilities should avoid raw GL_COLOR_CLEAR_VALUE constants: " + relative);
    }

    @Test
    public void testIrisSwizzleSetupAvoidsRawSwizzlePnameConstants() throws IOException {
        List<String> files = List.of(
            "net/irisshaders/iris/gl/IrisRenderSystem.java",
            "net/irisshaders/iris/shadows/ShadowRenderer.java",
            "net/irisshaders/iris/pipeline/programs/ExtendedShader.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(source.contains("GL_TEXTURE_SWIZZLE_RGBA"),
                "Iris swizzle setup should use typed swizzle helpers: " + relative);
        }
    }

    @Test
    public void testIrisComputeSynchronizationAvoidsRawMemoryBarrierBitmasks() throws IOException {
        Pattern rawIrisBarrierPattern = Pattern.compile("IrisRenderSystem\\.memoryBarrier\\s*\\([^\\n]*VulkanicAPI\\.GL_");

        List<String> files = List.of(
            "net/irisshaders/iris/gl/program/Program.java",
            "net/irisshaders/iris/gl/program/ComputeProgram.java",
            "net/irisshaders/iris/pathways/colorspace/ColorSpaceComputeConverter.java",
            "net/irisshaders/iris/shadows/ShadowCompositeRenderer.java",
            "net/irisshaders/iris/pipeline/CompositeRenderer.java",
            "net/irisshaders/iris/pipeline/FinalPassRenderer.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawIrisBarrierPattern.matcher(source).find(),
                "Iris compute synchronization should use typed resource-barrier helpers: " + relative);
        }
    }

    @Test
    public void testSelectedShaderStatusQueriesAvoidRawProgramAndShaderPnames() throws IOException {
        Pattern rawStatusQueryPattern = Pattern.compile("(?:getProgramParameter|getShaderParameter|getProgramiv)\\s*\\([^\\n]*(?:VulkanicAPI\\.GL_|35714|35713)");

        List<String> files = List.of(
            "net/irisshaders/iris/gl/program/ComputeProgram.java",
            "net/irisshaders/iris/gl/program/ProgramUniforms.java",
            "net/irisshaders/iris/gl/shader/GlShader.java",
            "net/irisshaders/iris/gl/shader/ProgramCreator.java",
            "net/irisshaders/iris/pipeline/programs/ShaderCreator.java",
            "net/irisshaders/iris/pipeline/programs/ShaderMap.java",
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java",
            "net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java",
            "com/seibel/distanthorizons/core/render/glObject/shader/Shader.java",
            "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java",
            "net/sodium/client/gl/shader/GlShader.java",
            "net/sodium/client/gl/shader/GlProgram.java",
            "net/blaze3d/opengl/GlProgram.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawStatusQueryPattern.matcher(source).find(),
                "Shader/program status queries should use typed parameter names: " + relative);
        }
    }

    @Test
    public void testSelectedSsboBufferOperationsAvoidRawShaderStorageTargetConstants() throws IOException {
        Pattern rawSsboTargetPattern = Pattern.compile("(?:IrisRenderSystem|VulkanicAPI)\\.(?:bindBufferBase|bufferStorage|clearBufferSubData|bufferSubData)\\s*\\([^\\n]*VulkanicAPI\\.GL_SHADER_STORAGE_BUFFER");

        List<String> files = List.of(
            "net/irisshaders/iris/pipeline/IrisRenderingPipeline.java",
            "net/irisshaders/iris/gl/buffer/ShaderStorageBuffer.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(rawSsboTargetPattern.matcher(source).find(),
                "SSBO operations should use typed buffer-target APIs: " + relative);
        }
    }

    @Test
    public void testSelectedIrisCallsitesAvoidExplicitTexture2dTargetArguments() throws IOException {
        List<String> files = List.of(
            "net/irisshaders/iris/pipeline/programs/ExtendedShader.java",
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java",
            "net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java",
            "net/irisshaders/iris/pbr/TextureTracker.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(source.contains("TextureType.TEXTURE_2D.getGlType()"),
                "Iris callsite should use typed/default-2D overloads instead of explicit GL target arguments: " + relative);
        }
    }
}
