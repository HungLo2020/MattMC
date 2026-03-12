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
        assertTrue(VulkanicPrimitiveMode.fromLegacyGlConstant(VulkanicAPI.GL_PATCHES).isPresent(),
            "Primitive mode mapping should recognize GL_PATCHES for tessellation draw routing");
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
        assertTrue(VulkanicTextureParameterValue.fromLegacyGlConstant(VulkanicAPI.GL_LINEAR_MIPMAP_NEAREST).isPresent(),
            "Texture parameter value mapping should recognize GL_LINEAR_MIPMAP_NEAREST");
        assertTrue(VulkanicTextureParameterValue.fromLegacyGlConstant(VulkanicAPI.GL_NEAREST_MIPMAP_LINEAR).isPresent(),
            "Texture parameter value mapping should recognize GL_NEAREST_MIPMAP_LINEAR");
        assertTrue(VulkanicTextureSwizzleComponent.fromLegacyGlConstant(VulkanicAPI.GL_RED).isPresent(),
            "Texture swizzle component mapping should recognize GL_RED");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_SAMPLER_2D).isPresent(),
            "Uniform reflection type mapping should recognize GL_SAMPLER_2D");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_IMAGE_2D_ARRAY).isPresent(),
            "Uniform reflection type mapping should recognize GL_IMAGE_2D_ARRAY");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_SAMPLER_2D)
                .orElseThrow(() -> new IllegalStateException("Missing reflection mapping for GL_SAMPLER_2D"))
                .isSampler(),
            "GL_SAMPLER_2D reflection type should classify as sampler");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_IMAGE_2D_ARRAY)
                .orElseThrow(() -> new IllegalStateException("Missing reflection mapping for GL_IMAGE_2D_ARRAY"))
                .isImage(),
            "GL_IMAGE_2D_ARRAY reflection type should classify as image");
    }

    @Test
    public void testProgramUniformsTypeIntrospectionUsesTypedReflectionHelper() throws IOException {
        String relative = "net/irisshaders/iris/gl/program/ProgramUniforms.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertTrue(source.contains("VulkanicUniformReflectionType.fromLegacyGlConstant(type)"),
            "ProgramUniforms should route reflected type resolution through VulkanicUniformReflectionType helper: " + relative);
        assertTrue(source.contains("type.isSampler()"),
            "ProgramUniforms should classify sampler uniforms using VulkanicUniformReflectionType.isSampler(): " + relative);
        assertTrue(source.contains("type.isImage()"),
            "ProgramUniforms should classify image uniforms using VulkanicUniformReflectionType.isImage(): " + relative);

        Pattern rawTypeComparisonPattern = Pattern.compile("type\\s*==\\s*VulkanicAPI\\.GL_");
        assertFalse(rawTypeComparisonPattern.matcher(source).find(),
            "ProgramUniforms should avoid raw reflected type comparisons against VulkanicAPI.GL_* constants: " + relative);

        Pattern hardcodedSamplerSwitchPattern = Pattern.compile("case\\s+SAMPLER_1D|case\\s+SAMPLER_2D|case\\s+SAMPLER_3D");
        assertFalse(hardcodedSamplerSwitchPattern.matcher(source).find(),
            "ProgramUniforms sampler classification should avoid hardcoded sampler enum cases in favor of typed helpers: " + relative);
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
    public void testSelectedShaderCompilationAndLinkChecksUseStatusHelpers() throws IOException {
        List<String> programFiles = List.of(
            "net/blaze3d/opengl/GlProgram.java",
            "net/irisshaders/iris/gl/shader/ProgramCreator.java",
            "net/irisshaders/iris/pipeline/programs/ShaderMap.java",
            "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java",
            "net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java",
            "net/sodium/client/gl/shader/GlProgram.java",
            "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java"
        );

        for (String relative : programFiles) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertTrue(source.contains("isProgramLinkSuccessful("),
                "Program link checks should route through VulkanicAPI.isProgramLinkSuccessful helper: " + relative);
            assertFalse(source.contains("!= VulkanicAPI.GL_TRUE"),
                "Program link checks should avoid raw GL_TRUE comparisons: " + relative);
            assertFalse(source.contains("== VulkanicAPI.GL_FALSE"),
                "Program link checks should avoid raw GL_FALSE comparisons: " + relative);
            assertFalse(source.contains("!= 1"),
                "Program link checks should avoid raw numeric GL_TRUE comparisons: " + relative);
        }

        List<String> shaderFiles = List.of(
            "net/blaze3d/opengl/GlDevice.java",
            "net/irisshaders/iris/gl/shader/GlShader.java",
            "net/irisshaders/iris/pipeline/programs/ShaderCreator.java",
            "net/sodium/client/gl/shader/GlShader.java",
            "com/seibel/distanthorizons/core/render/glObject/shader/Shader.java"
        );

        for (String relative : shaderFiles) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertTrue(source.contains("isShaderCompileSuccessful("),
                "Shader compile checks should route through VulkanicAPI.isShaderCompileSuccessful helper: " + relative);
            assertFalse(source.contains("!= VulkanicAPI.GL_TRUE"),
                "Shader compile checks should avoid raw GL_TRUE comparisons: " + relative);
            assertFalse(source.contains("== VulkanicAPI.GL_FALSE"),
                "Shader compile checks should avoid raw GL_FALSE comparisons: " + relative);
            assertFalse(source.contains("!= 1"),
                "Shader compile checks should avoid raw numeric GL_TRUE comparisons: " + relative);
        }
    }

    @Test
    public void testGlProgramUsesTypedActiveUniformBlockParameterName() throws IOException {
        String relative = "net/blaze3d/opengl/GlProgram.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("getProgramParameter(VulkanicAPI.getCommandContext(), this.programId, 35382)"),
            "GlProgram should avoid raw numeric active-uniform-block pname queries: " + relative);
        assertTrue(source.contains("VulkanicProgramParameterName.ACTIVE_UNIFORM_BLOCKS"),
            "GlProgram should query active uniform blocks via VulkanicProgramParameterName.ACTIVE_UNIFORM_BLOCKS: " + relative);
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

    @Test
    public void testIrisBufferBlendToggleUsesTypedCapabilityEnum() throws IOException {
        String relative = "net/irisshaders/iris/gl/IrisRenderSystem.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("setIndexedEnabled(VulkanicAPI.getCommandContext(), VulkanicAPI.GL_BLEND"),
            "Iris indexed blend toggles should use VulkanicCapability.BLEND: " + relative);
    }

    @Test
    public void testGlCommandEncoderMipRangeUsesTypedTextureParameterNames() throws IOException {
        String relative = "net/blaze3d/opengl/GlCommandEncoder.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        Pattern rawMipParameterPattern = Pattern.compile("setTextureParameter\\s*\\([^\\n]*GL_TEXTURE_(?:BASE_LEVEL|MAX_LEVEL)");
        assertFalse(rawMipParameterPattern.matcher(source).find(),
            "GlCommandEncoder mip parameter setup should use typed texture-parameter names: " + relative);
    }

    @Test
    public void testIrisGlSamplerUsesTypedSamplerParameterEnums() throws IOException {
        String relative = "net/irisshaders/iris/gl/sampler/GlSampler.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        Pattern rawSamplerCallPattern = Pattern.compile("samplerParameteri\\s*\\([^\\n]*GL_");
        assertFalse(rawSamplerCallPattern.matcher(source).find(),
            "Iris GlSampler should use typed sampler parameter enums instead of raw GL constants: " + relative);
        assertFalse(source.contains("private static final int GL_TEXTURE_"),
            "Iris GlSampler should not define raw GL texture parameter constants: " + relative);
    }

    @Test
    public void testBlaze3dGlTextureFlushModeUsesTypedTextureParameterEnums() throws IOException {
        String relative = "net/blaze3d/opengl/GlTexture.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        Pattern rawNumericTextureParamPattern = Pattern.compile("iris\\$texParameterDSA\\s*\\([^\\n]*\\b(?:10240|10241|10242|10243|9728|9729|9984|9985|9986|9987)\\b");
        assertFalse(rawNumericTextureParamPattern.matcher(source).find(),
            "GlTexture flush-mode parameter path should use typed texture parameter names/values: " + relative);
    }

    @Test
    public void testIrisDepthCopyStrategyAvoidsExplicitTexture2dTargets() throws IOException {
        String relative = "net/irisshaders/iris/gl/texture/DepthCopyStrategy.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("VulkanicAPI.GL_TEXTURE_2D"),
            "DepthCopyStrategy should use typed/default-2D helper overloads instead of explicit GL texture target constants: " + relative);
    }

    @Test
    public void testIrisTextureWrapperDefaultsUseTypedTexture2dHelpers() throws IOException {
        String relative = "net/irisshaders/iris/gl/IrisRenderSystem.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("generateMipmaps(texture, VulkanicAPI.GL_TEXTURE_2D)"),
            "Iris wrapper defaults should route through typed 2D helper overloads for mipmaps: " + relative);
        assertFalse(source.contains("texImage2D(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for texImage2D: " + relative);
        assertFalse(source.contains("texParameteriv(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for texParameteriv: " + relative);
        assertFalse(source.contains("setTextureSwizzleRgba(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for swizzle setup: " + relative);
        assertFalse(source.contains("copyTexSubImage2D(destTexture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for copyTexSubImage2D: " + relative);
        assertFalse(source.contains("texParameteri(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for texParameteri: " + relative);
        assertFalse(source.contains("texParameterf(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for texParameterf: " + relative);
        assertFalse(source.contains("getTexParameteri(texture, VulkanicAPI.GL_TEXTURE_2D"),
            "Iris wrapper defaults should route through typed 2D helper overloads for getTexParameteri: " + relative);
    }

    @Test
    public void testIrisTextureBindingAndCreationDefaultsUseTypedTexture2dHelpers() throws IOException {
        String relative = "net/irisshaders/iris/gl/IrisRenderSystem.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("dsaState.bindTextureToUnit(VulkanicAPI.GL_TEXTURE_2D, unit, texture)"),
            "Iris bindTextureToUnit 2D default should route through typed texture-target helper overloads: " + relative);
        assertFalse(source.contains("return dsaState.createTexture(VulkanicAPI.GL_TEXTURE_2D);"),
            "Iris createTexture2D should route through typed texture-target helper overloads: " + relative);
        assertFalse(source.contains("if (glType == VulkanicAPI.GL_TEXTURE_2D)"),
            "Iris bindTextureForSetup should avoid raw GL_TEXTURE_2D comparisons when typed target mapping is available: " + relative);
        assertFalse(source.contains("if (target == VulkanicAPI.GL_TEXTURE_2D)"),
            "Iris DSA bindTextureToUnit paths should avoid raw GL_TEXTURE_2D comparisons when typed target mapping is available: " + relative);
    }

    @Test
    public void testBlaze3dTextureStatePathsAvoidLegacyTargetUnwrapping() throws IOException {
        String commandEncoderRelative = "net/blaze3d/opengl/GlCommandEncoder.java";
        String commandEncoderSource = Files.readString(SRC_MAIN_JAVA.resolve(commandEncoderRelative));

        assertFalse(commandEncoderSource.contains("texture.flushModeChanges(textureTarget.toLegacyGlTarget())"),
            "GlCommandEncoder should use typed texture-target overload for flushModeChanges: " + commandEncoderRelative);
        assertFalse(commandEncoderSource.contains("q = VulkanicAPI.GL_TEXTURE_2D;"),
            "GlCommandEncoder write-to-texture path should avoid explicit GL_TEXTURE_2D target locals when default-2D helpers exist: " + commandEncoderRelative);
        assertFalse(commandEncoderSource.contains("o = VulkanicAPI.GL_TEXTURE_2D;"),
            "GlCommandEncoder byte-buffer upload path should avoid explicit GL_TEXTURE_2D target locals when default-2D helpers exist: " + commandEncoderRelative);
        assertFalse(commandEncoderSource.contains("glPrimitiveMode == VulkanicAPI.GL_TRIANGLES"),
            "GlCommandEncoder tessellation override should avoid raw GL_TRIANGLES comparisons when typed primitive-mode mapping is available: " + commandEncoderRelative);
        assertTrue(commandEncoderSource.contains("VulkanicPrimitiveMode.PATCHES"),
            "GlCommandEncoder tessellation override should use typed VulkanicPrimitiveMode.PATCHES routing: " + commandEncoderRelative);

        String deviceRelative = "net/blaze3d/opengl/GlDevice.java";
        String deviceSource = Files.readString(SRC_MAIN_JAVA.resolve(deviceRelative));

        assertFalse(deviceSource.contains("setTextureMaxLevel(ctx, o, m - 1)"),
            "GlDevice texture setup should use typed texture-target overloads for mip configuration: " + deviceRelative);
        assertFalse(deviceSource.contains("disableTextureCompareMode(ctx, o)"),
            "GlDevice depth texture setup should use typed texture-target overloads for compare mode: " + deviceRelative);
    }

    @Test
    public void testIrisTextureTypeDrivenCallsitesPreferTypedTargetHelpers() throws IOException {
        String samplerBindingRelative = "net/irisshaders/iris/gl/sampler/SamplerBinding.java";
        String samplerBindingSource = Files.readString(SRC_MAIN_JAVA.resolve(samplerBindingRelative));

        assertFalse(samplerBindingSource.contains("bindTextureToUnit(textureType.getGlType(), textureUnit, textureId)"),
            "SamplerBinding should route texture binding through TextureType typed helper methods: " + samplerBindingRelative);

        String glTextureRelative = "net/irisshaders/iris/gl/texture/GlTexture.java";
        String glTextureSource = Files.readString(SRC_MAIN_JAVA.resolve(glTextureRelative));

        assertFalse(glTextureSource.contains("bindTextureForSetup(target.getGlType(), getGlId())"),
            "Iris GlTexture setup should route through TextureType typed helper methods: " + glTextureRelative);
        assertFalse(glTextureSource.contains("bindTextureToUnit(target.getGlType(), unit, getGlId())"),
            "Iris GlTexture bind should route through TextureType typed helper methods: " + glTextureRelative);

        String glImageRelative = "net/irisshaders/iris/gl/image/GlImage.java";
        String glImageSource = Files.readString(SRC_MAIN_JAVA.resolve(glImageRelative));

        assertFalse(glImageSource.contains("IrisRenderSystem.createTexture(target.getGlType())"),
            "Iris GlImage creation should route through TextureType typed helper methods: " + glImageRelative);
        assertFalse(glImageSource.contains("bindTextureForSetup(target.getGlType(), getGlId())"),
            "Iris GlImage setup should route through TextureType typed helper methods: " + glImageRelative);

        String textureTypeRelative = "net/irisshaders/iris/gl/texture/TextureType.java";
        String textureTypeSource = Files.readString(SRC_MAIN_JAVA.resolve(textureTypeRelative));

        assertFalse(textureTypeSource.contains("TEXTURE_RECTANGLE(VulkanicAPI.GL_TEXTURE_3D)"),
            "TextureType.TEXTURE_RECTANGLE should map to GL_TEXTURE_RECTANGLE instead of GL_TEXTURE_3D: " + textureTypeRelative);
    }
}
