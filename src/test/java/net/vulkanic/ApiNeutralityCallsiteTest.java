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
    public void testHighTrafficRendererCallsitesAvoidConcreteBackendCastLeaks() throws IOException {
        String commandEncoderSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/systems/CommandEncoder.java"));
        String glCommandEncoderSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/opengl/GlCommandEncoder.java"));
        String gpuDeviceSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/systems/GpuDevice.java"));
        String sodiumRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/sodium/client/render/chunk/ShaderChunkRenderer.java"));
        String irisSource = Files.readString(SRC_MAIN_JAVA.resolve("net/irisshaders/iris/Iris.java"));
        String renderSystemSource = Files.readString(SRC_MAIN_JAVA.resolve("net/blaze3d/systems/RenderSystem.java"));
        String gpuWarnlistManagerSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/GpuWarnlistManager.java"));
        String gameRendererSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/renderer/GameRenderer.java"));
        String debugEntrySystemSpecsSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/gui/components/debug/DebugEntrySystemSpecs.java"));
        String minecraftSource = Files.readString(SRC_MAIN_JAVA.resolve("net/minecraft/client/Minecraft.java"));

        assertTrue(commandEncoderSource.contains("void applyPipelineState(RenderPipeline renderPipeline);"),
            "CommandEncoder should expose a backend-neutral pipeline-state seam");
        assertTrue(commandEncoderSource.contains("void invalidateCachedProgramBinding();"),
            "CommandEncoder should expose a backend-neutral cached-program invalidation seam");
        assertTrue(glCommandEncoderSource.contains("public void applyPipelineState(RenderPipeline renderPipeline)"),
            "GlCommandEncoder should implement backend-neutral pipeline-state application through the interface seam");
        assertTrue(glCommandEncoderSource.contains("public void invalidateCachedProgramBinding()"),
            "GlCommandEncoder should implement backend-neutral cached-program invalidation through the interface seam");
        assertFalse(glCommandEncoderSource.contains("public GlProgram lastProgram"),
            "GlCommandEncoder should not expose lastProgram as a public concrete-backend field");

        assertTrue(sodiumRendererSource.contains("CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();"),
            "ShaderChunkRenderer should acquire a backend-neutral CommandEncoder");
        assertTrue(sodiumRendererSource.contains("commandEncoder.applyPipelineState(pass.getPipeline());"),
            "ShaderChunkRenderer should apply pipeline state through CommandEncoder");
        assertTrue(sodiumRendererSource.contains("commandEncoder.invalidateCachedProgramBinding();"),
            "ShaderChunkRenderer should invalidate cached program binding through CommandEncoder");
        assertFalse(sodiumRendererSource.contains("GlCommandEncoder"),
            "ShaderChunkRenderer should avoid concrete GlCommandEncoder dependency");

        assertTrue(irisSource.contains("VulkanicAPI.getBackendEnabledExtensions()"),
            "Iris should use backend-owned extension querying seam");
        assertFalse(irisSource.contains("((GlDevice) RenderSystem.getDevice())"),
            "Iris should avoid concrete GlDevice casts when querying enabled extensions");
        assertTrue(gpuDeviceSource.contains("record GpuDeviceInfo("),
            "GpuDevice should expose a backend-neutral device-info seam");
        assertTrue(gpuDeviceSource.contains("default GpuDeviceInfo getDeviceInfo()"),
            "GpuDevice should expose backend-neutral device-info access");
        assertTrue(gpuDeviceSource.contains("default List<String> getOptionalFeatureNames()"),
            "GpuDevice should expose backend-neutral optional feature reporting");
        assertTrue(gpuDeviceSource.contains("default boolean shouldApplyOpenGlWarnlist()"),
            "GpuDevice should expose explicit OpenGL warnlist applicability instead of requiring string comparisons");
        assertTrue(gpuWarnlistManagerSource.contains("gpuDevice.getDeviceInfo()"),
            "GpuWarnlistManager should query backend-neutral device info through GpuDevice");
        assertTrue(gpuWarnlistManagerSource.contains("deviceInfo.appliesOpenGlWarnlist()"),
            "GpuWarnlistManager should use explicit warnlist applicability seam");
        assertFalse(gpuWarnlistManagerSource.contains("getBackendName().equals(\"OpenGL\")"),
            "GpuWarnlistManager should avoid backend-name string comparisons for OpenGL warnlist decisions");
        assertTrue(gameRendererSource.contains("getDeviceInfo()"),
            "GameRenderer should use backend-neutral device info for hardware logging");
        assertFalse(gameRendererSource.contains("Supports OpenGL"),
            "GameRenderer should avoid hard-coded OpenGL capability wording in shared hardware logging");
        assertTrue(debugEntrySystemSpecsSource.contains("getDeviceInfo()"),
            "DebugEntrySystemSpecs should use backend-neutral device info for display strings");
        assertFalse(debugEntrySystemSpecsSource.contains("gpuDevice.getVersion()"),
            "DebugEntrySystemSpecs should avoid raw version string composition when backend-neutral device info helpers exist");
        assertTrue(minecraftSource.contains("getBackendOptionalFeatureNames()"),
            "Minecraft should log backend-owned optional rendering features instead of device-wrapper extension metadata");
        assertFalse(minecraftSource.contains("Using optional rendering extensions:"),
            "Minecraft should avoid OpenGL extension wording in shared startup logging");
        assertTrue(renderSystemSource.contains("VulkanicAPI.createRendererDevice("),
            "RenderSystem should create renderer devices through the backend-neutral VulkanicAPI seam");
        assertFalse(renderSystemSource.contains("new GlDevice("),
            "RenderSystem should avoid hard-coded GlDevice construction in the shared startup path");
        assertFalse(renderSystemSource.contains("OpenGL Vendor:"),
            "RenderSystem should avoid direct OpenGL startup logging in the shared startup path");
    }

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
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_SAMPLER_CUBE).isPresent(),
            "Uniform reflection type mapping should recognize GL_SAMPLER_CUBE");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_UNSIGNED_INT_VEC3).isPresent(),
            "Uniform reflection type mapping should recognize GL_UNSIGNED_INT_VEC3");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_BOOL_VEC4).isPresent(),
            "Uniform reflection type mapping should recognize GL_BOOL_VEC4");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_IMAGE_2D_ARRAY).isPresent(),
            "Uniform reflection type mapping should recognize GL_IMAGE_2D_ARRAY");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_INT_IMAGE_2D_ARRAY).isPresent(),
            "Uniform reflection type mapping should recognize GL_INT_IMAGE_2D_ARRAY");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_SAMPLER_2D)
                .orElseThrow(() -> new IllegalStateException("Missing reflection mapping for GL_SAMPLER_2D"))
                .isSampler(),
            "GL_SAMPLER_2D reflection type should classify as sampler");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_SAMPLER_CUBE)
                .orElseThrow(() -> new IllegalStateException("Missing reflection mapping for GL_SAMPLER_CUBE"))
                .isSampler(),
            "GL_SAMPLER_CUBE reflection type should classify as sampler");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_IMAGE_2D_ARRAY)
                .orElseThrow(() -> new IllegalStateException("Missing reflection mapping for GL_IMAGE_2D_ARRAY"))
                .isImage(),
            "GL_IMAGE_2D_ARRAY reflection type should classify as image");
        assertTrue(VulkanicUniformReflectionType.fromLegacyGlConstant(VulkanicAPI.GL_INT_IMAGE_2D_ARRAY)
                .orElseThrow(() -> new IllegalStateException("Missing reflection mapping for GL_INT_IMAGE_2D_ARRAY"))
                .isImage(),
            "GL_INT_IMAGE_2D_ARRAY reflection type should classify as image");
    }

    @Test
    public void testProgramUniformsTypeIntrospectionUsesTypedReflectionHelper() throws IOException {
        String relative = "net/irisshaders/iris/gl/program/ProgramUniforms.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertTrue(source.contains("activeUniformInfo.reflectionType()"),
            "ProgramUniforms should resolve reflected type through ActiveUniformInfo.reflectionType typed metadata: " + relative);
        assertTrue(source.contains("activeUniformInfo.reflectionTypeName()"),
            "ProgramUniforms should use ActiveUniformInfo.reflectionTypeName for unsupported-type diagnostics: " + relative);
        assertTrue(source.contains("type.isSampler()"),
            "ProgramUniforms should classify sampler uniforms using VulkanicUniformReflectionType.isSampler(): " + relative);
        assertTrue(source.contains("type.isImage()"),
            "ProgramUniforms should classify image uniforms using VulkanicUniformReflectionType.isImage(): " + relative);
        assertTrue(source.contains("VulkanicAPI.getActiveUniforms("),
            "ProgramUniforms should enumerate reflected uniforms through VulkanicAPI.getActiveUniforms typed metadata seam: " + relative);
        assertFalse(source.contains("IrisRenderSystem.getActiveUniform("),
            "ProgramUniforms should avoid IrisRenderSystem active-uniform wrapper usage in typed reflection path: " + relative);
        assertFalse(source.contains("VulkanicProgramParameterName.ACTIVE_UNIFORMS"),
            "ProgramUniforms should avoid direct ACTIVE_UNIFORMS query plumbing when typed active-uniform enumeration helper is available: " + relative);
        assertFalse(source.contains("activeUniformInfo.legacyType()"),
            "ProgramUniforms should avoid direct legacy active-uniform type usage when typed reflection metadata is available: " + relative);

        Pattern rawTypeComparisonPattern = Pattern.compile("type\\s*==\\s*VulkanicAPI\\.GL_");
        assertFalse(rawTypeComparisonPattern.matcher(source).find(),
            "ProgramUniforms should avoid raw reflected type comparisons against VulkanicAPI.GL_* constants: " + relative);

        Pattern hardcodedSamplerSwitchPattern = Pattern.compile("case\\s+SAMPLER_1D|case\\s+SAMPLER_2D|case\\s+SAMPLER_3D");
        assertFalse(hardcodedSamplerSwitchPattern.matcher(source).find(),
            "ProgramUniforms sampler classification should avoid hardcoded sampler enum cases in favor of typed helpers: " + relative);
    }

    @Test
    public void testActiveUniformReflectionCallsitesPreferTypedMetadataHelpers() throws IOException {
        List<String> allowedReflectionSeamFiles = List.of(
            "net/vulkanic/VulkanicAPI.java",
            "net/irisshaders/iris/gl/IrisRenderSystem.java"
        );

        List<String> offenders = new java.util.ArrayList<>();

        try (var paths = Files.walk(SRC_MAIN_JAVA)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    Path relativePath = SRC_MAIN_JAVA.relativize(path);
                    String relative = relativePath.toString().replace('\\', '/');

                    if (allowedReflectionSeamFiles.contains(relative)) {
                        return;
                    }

                    try {
                        String source = Files.readString(path);
                        if (source.contains("VulkanicAPI.getActiveUniform(")
                            || source.contains("VulkanicAPI.retrieveActiveUniformBlockName(")
                            || source.contains("IrisRenderSystem.getActiveUniform(")) {
                            offenders.add(relative);
                        }
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
        }

        assertTrue(offenders.isEmpty(),
            "Active-uniform reflection callsites should use typed metadata helpers (getActiveUniforms/getActiveUniformBlocks); offenders: " + offenders);
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
        assertTrue(source.contains("VulkanicAPI.getActiveUniformBlocks("),
            "GlProgram should route active uniform block reflection metadata through VulkanicAPI.getActiveUniformBlocks seam: " + relative);
        assertFalse(source.contains("retrieveActiveUniformBlockName("),
            "GlProgram should avoid low-level per-block name queries when typed block metadata helper is available: " + relative);
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
    public void testSelectedIrisTextureIdCallsitesPreferTypedCoreApiHelper() throws IOException {
        List<String> files = List.of(
            "net/irisshaders/iris/pipeline/CustomTextureManager.java",
            "net/irisshaders/iris/targets/RenderTargets.java",
            "net/irisshaders/iris/pipeline/FinalPassRenderer.java",
            "net/irisshaders/iris/pipeline/IrisRenderingPipeline.java",
            "net/irisshaders/iris/pbr/TextureTracker.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertFalse(source.contains("VulkanicAPI.getTextureHandle("),
                "Selected Iris texture-id callsites should avoid direct VulkanicAPI.getTextureHandle usage: " + relative);
            assertTrue(source.contains("VulkanicCoreAPI.textureId("),
                "Selected Iris texture-id callsites should route through VulkanicCoreAPI.textureId helper: " + relative);
        }

        String coreApiRelative = "net/vulkanic/VulkanicCoreAPI.java";
        String coreApiSource = Files.readString(SRC_MAIN_JAVA.resolve(coreApiRelative));

        assertTrue(coreApiSource.contains("public static int textureId(GpuTextureView textureView)"),
            "VulkanicCoreAPI should expose textureId(GpuTextureView) for typed texture-view callsites: " + coreApiRelative);
        assertTrue(coreApiSource.contains("return textureId(textureView.texture());"),
            "VulkanicCoreAPI texture-view helper should delegate through typed texture helper: " + coreApiRelative);
    }

    @Test
    public void testGlCommandEncoderPrefersTypedTextureIdHelper() throws IOException {
        String relative = "net/blaze3d/opengl/GlCommandEncoder.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertFalse(source.contains("VulkanicAPI.getTextureHandle("),
            "GlCommandEncoder should avoid direct VulkanicAPI.getTextureHandle usage in frontend callsites: " + relative);
        assertTrue(source.contains("VulkanicCoreAPI.textureId("),
            "GlCommandEncoder should resolve texture IDs through VulkanicCoreAPI.textureId typed helper: " + relative);
    }

    @Test
    public void testSelectedContextHotspotsPreferLocalCommandContextHelper() throws IOException {
        List<String> files = List.of(
            "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java",
            "net/sodium/client/gl/shader/GlProgram.java",
            "com/seibel/distanthorizons/core/render/glObject/buffer/GLBuffer.java"
        );

        Pattern inlineContextPattern = Pattern.compile("VulkanicAPI\\.[A-Za-z0-9_]+\\s*\\(\\s*VulkanicAPI\\.getCommandContext\\s*\\(");

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertTrue(source.contains("private static CommandContext commandContext()"),
                "Context-heavy frontend wrappers should centralize context acquisition behind a helper: " + relative);
            assertTrue(source.contains("return VulkanicAPI.getCommandContext();"),
                "Context helper should delegate to VulkanicAPI.getCommandContext for command-context retrieval: " + relative);
            assertFalse(inlineContextPattern.matcher(source).find(),
                "Context-heavy frontend wrappers should avoid inline VulkanicAPI.getCommandContext() in VulkanicAPI calls: " + relative);
        }
    }

    @Test
    public void testGraphicsBackendExposesTypedSeamsForClearLogicAndUniformLocations() throws IOException {
        String relative = "net/vulkanic/GraphicsBackend.java";
        String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));

        assertTrue(source.contains("default void clearBuffers(CommandContext ctx, VulkanicClearBuffer... buffers)"),
            "GraphicsBackend should expose typed clear-buffer overloads at the backend boundary: " + relative);
        assertTrue(source.contains("default void setLogicOp(CommandContext ctx, VulkanicLogicOp opcode)"),
            "GraphicsBackend should expose typed logic-op overloads at the backend boundary: " + relative);
        assertTrue(source.contains("default VulkanicUniformLocation resolveUniformLocation("),
            "GraphicsBackend should expose a typed uniform-location resolver at the backend boundary: " + relative);
        assertTrue(source.contains("default VulkanicShaderHandle createShaderHandle(CommandContext ctx, VulkanicShaderStage shaderStage)"),
            "GraphicsBackend should expose typed shader-handle creation seams at the backend boundary: " + relative);
        assertTrue(source.contains("default VulkanicProgramHandle createShaderProgramHandle(CommandContext ctx)"),
            "GraphicsBackend should expose typed program-handle creation seams at the backend boundary: " + relative);
        assertTrue(source.contains("default void attachShader(CommandContext ctx, VulkanicProgramHandle program, VulkanicShaderHandle shader)"),
            "GraphicsBackend should expose typed shader/program attachment seams at the backend boundary: " + relative);
        assertTrue(source.contains("default void bindUniformBufferRange(CommandContext ctx, VulkanicBufferTarget target"),
            "GraphicsBackend should expose typed uniform-buffer range binding overloads at the backend boundary: " + relative);
        assertTrue(source.contains("default void texBuffer(CommandContext ctx, VulkanicTextureTarget target"),
            "GraphicsBackend should expose typed tex-buffer target overloads at the backend boundary: " + relative);
        assertTrue(source.contains("default void uploadTexture2D(")
                && source.contains("VulkanicTextureUploadFormat uploadFormat"),
            "GraphicsBackend should expose typed texture-upload format overloads at the backend boundary: " + relative);
    }

    @Test
    public void testCenterDepthSamplerUsesTypedTextureUploadFormatSeam() throws IOException {
        String centerDepthRelative = "net/irisshaders/iris/pathways/CenterDepthSampler.java";
        String centerDepthSource = Files.readString(SRC_MAIN_JAVA.resolve(centerDepthRelative));

        assertTrue(centerDepthSource.contains("VulkanicTextureUploadFormat.RED32_SFLOAT"),
            "CenterDepthSampler should express center-depth texture upload intent through typed Vulkanic upload formats: " + centerDepthRelative);
        assertFalse(centerDepthSource.contains("PixelType.FLOAT.getGlFormat()"),
            "CenterDepthSampler should avoid raw GL pixel-type constants for center-depth texture setup: " + centerDepthRelative);

        String irisRenderSystemRelative = "net/irisshaders/iris/gl/IrisRenderSystem.java";
        String irisRenderSystemSource = Files.readString(SRC_MAIN_JAVA.resolve(irisRenderSystemRelative));
        assertTrue(irisRenderSystemSource.contains("VulkanicTextureUploadFormat uploadFormat"),
            "IrisRenderSystem should expose typed texture-upload overloads for backend-neutral callsites: " + irisRenderSystemRelative);
        assertTrue(irisRenderSystemSource.contains("VulkanicAPI.uploadTexture2D(VulkanicAPI.getCommandContext(), target, level, uploadFormat"),
            "IrisRenderSystem typed texture-upload overloads should route through VulkanicAPI typed seams: " + irisRenderSystemRelative);
    }

    @Test
    public void testBackendOwnedDeviceIdentitySeamsAreUsedByShaderAndDebugCallsites() throws IOException {
        String standardMacrosRelative = "net/irisshaders/iris/gl/shader/StandardMacros.java";
        String standardMacrosSource = Files.readString(SRC_MAIN_JAVA.resolve(standardMacrosRelative));
        assertTrue(standardMacrosSource.contains("VulkanicAPI.getBackendVendorName()"),
            "StandardMacros should use backend-owned vendor identity seam instead of RenderSystem device metadata: " + standardMacrosRelative);
        assertTrue(standardMacrosSource.contains("VulkanicAPI.getBackendRendererName()"),
            "StandardMacros should use backend-owned renderer identity seam instead of RenderSystem device metadata: " + standardMacrosRelative);
        assertFalse(standardMacrosSource.contains("RenderSystem.getDevice().getVendor()"),
            "StandardMacros should avoid direct RenderSystem.getDevice().getVendor() callsites: " + standardMacrosRelative);
        assertFalse(standardMacrosSource.contains("RenderSystem.getDevice().getRenderer()"),
            "StandardMacros should avoid direct RenderSystem.getDevice().getRenderer() callsites: " + standardMacrosRelative);

        String irisRelative = "net/irisshaders/iris/Iris.java";
        String irisSource = Files.readString(SRC_MAIN_JAVA.resolve(irisRelative));
        assertTrue(irisSource.contains("VulkanicAPI.getBackendEnabledExtensions()"),
            "Iris debug callback setup should use backend-owned extension seam: " + irisRelative);
        assertFalse(irisSource.contains("RenderSystem.getDevice().getEnabledExtensions()"),
            "Iris debug callback setup should avoid direct RenderSystem device extension access: " + irisRelative);

        String minecraftRelative = "net/minecraft/client/Minecraft.java";
        String minecraftSource = Files.readString(SRC_MAIN_JAVA.resolve(minecraftRelative));
        assertTrue(minecraftSource.contains("VulkanicAPI.getBackendOptionalFeatureNames()"),
            "Minecraft startup/system-report diagnostics should use backend-owned optional-feature seam: " + minecraftRelative);
        assertFalse(minecraftSource.contains("VulkanicAPI.getDevice().getOptionalFeatureNames()"),
            "Minecraft startup/system-report diagnostics should avoid device-wrapper optional-feature calls: " + minecraftRelative);

        String compatDeviceRelative = "net/vulkanic/backends/vulkan/VulkanCompatibilityGpuDevice.java";
        String compatDeviceSource = Files.readString(SRC_MAIN_JAVA.resolve(compatDeviceRelative));
        assertTrue(compatDeviceSource.contains("this.backend.getBackendVendorName()"),
            "Vulkan compatibility device should source vendor metadata from backend-owned seam: " + compatDeviceRelative);
        assertTrue(compatDeviceSource.contains("this.backend.getBackendRendererName()"),
            "Vulkan compatibility device should source renderer metadata from backend-owned seam: " + compatDeviceRelative);
        assertTrue(compatDeviceSource.contains("this.backend.getBackendEnabledExtensions()"),
            "Vulkan compatibility device should source extension diagnostics from backend-owned seam: " + compatDeviceRelative);
        assertTrue(compatDeviceSource.contains("this.backend.getBackendOptionalFeatureNames()"),
            "Vulkan compatibility device should source optional feature diagnostics from backend-owned seam: " + compatDeviceRelative);
        assertFalse(compatDeviceSource.contains("this.compatibilityDevice.getVendor()"),
            "Vulkan compatibility device should avoid leaking compatibility-device vendor metadata: " + compatDeviceRelative);
        assertFalse(compatDeviceSource.contains("this.compatibilityDevice.getRenderer()"),
            "Vulkan compatibility device should avoid leaking compatibility-device renderer metadata: " + compatDeviceRelative);
    }

    @Test
    public void testSpirvAndPipelineLayoutPrepSeamsExist() throws IOException {
        String pipelineDescriptorRelative = "net/vulkanic/PipelineDescriptor.java";
        String pipelineDescriptorSource = Files.readString(SRC_MAIN_JAVA.resolve(pipelineDescriptorRelative));

        assertTrue(pipelineDescriptorSource.contains("fromPortableStateAndSpirvModules("),
            "PipelineDescriptor should expose portable-state + SPIR-V module factory seam for Vulkan pipeline bring-up: " + pipelineDescriptorRelative);
        assertTrue(pipelineDescriptorSource.contains("fromRenderPipelineAndSpirvModules("),
            "PipelineDescriptor should expose RenderPipeline + SPIR-V module factory seam for staged migration: " + pipelineDescriptorRelative);
        assertTrue(pipelineDescriptorSource.contains("record PushConstantRange("),
            "PipelineDescriptor should expose push-constant layout metadata seam for future Vulkan pipeline layouts: " + pipelineDescriptorRelative);
        assertTrue(pipelineDescriptorSource.contains("getPipelineCompilationKey()"),
            "PipelineDescriptor should expose deterministic compilation-key seam including SPIR-V/push-constant metadata: " + pipelineDescriptorRelative);
        assertTrue(pipelineDescriptorSource.contains("withResourceLayout("),
            "PipelineDescriptor should expose explicit reflected resource-layout override seam for Vulkan descriptor-layout prep: " + pipelineDescriptorRelative);
        assertTrue(pipelineDescriptorSource.contains("hasExplicitResourceLayout()"),
            "PipelineDescriptor should expose explicit-layout presence seam for migration-safe callsite behavior: " + pipelineDescriptorRelative);

        String vulkanicApiRelative = "net/vulkanic/VulkanicAPI.java";
        String vulkanicApiSource = Files.readString(SRC_MAIN_JAVA.resolve(vulkanicApiRelative));
        assertTrue(vulkanicApiSource.contains("public static PipelineHandle createPipeline(")
            && vulkanicApiSource.contains("PipelineDescriptor.PortableState portableState")
            && vulkanicApiSource.contains("java.util.List<VulkanicSpirvModule> spirvModules"),
            "VulkanicAPI should expose a SPIR-V-aware portable pipeline creation seam: " + vulkanicApiRelative);
        assertTrue(vulkanicApiSource.contains("createPipelineDescriptor(")
            && vulkanicApiSource.contains("PipelineDescriptor.PortableState portableState")
            && vulkanicApiSource.contains("java.util.List<VulkanicSpirvModule> spirvModules"),
            "VulkanicAPI should expose a SPIR-V-aware descriptor creation seam: " + vulkanicApiRelative);
        assertTrue(vulkanicApiSource.contains("deriveResourceLayoutFromProgramReflection("),
            "VulkanicAPI should expose reflection-derived pipeline resource-layout seams for Vulkan descriptor-layout preparation: " + vulkanicApiRelative);
        assertTrue(vulkanicApiSource.contains("withReflectedResourceLayout("),
            "VulkanicAPI should expose descriptor enrichment seam from linked-program reflection metadata: " + vulkanicApiRelative);
        assertTrue(vulkanicApiSource.contains("java.util.Set<VulkanicShaderStage> stages"),
            "VulkanicAPI reflection/layout prep seams should support explicit stage-visibility metadata for Vulkan descriptor layout synthesis: " + vulkanicApiRelative);

        String coreApiRelative = "net/vulkanic/VulkanicCoreAPI.java";
        String coreApiSource = Files.readString(SRC_MAIN_JAVA.resolve(coreApiRelative));
        assertTrue(coreApiSource.contains("public static PipelineHandle createPipeline(")
            && coreApiSource.contains("PipelineDescriptor.PortableState portableState")
            && coreApiSource.contains("java.util.List<VulkanicSpirvModule> spirvModules"),
            "VulkanicCoreAPI should mirror SPIR-V-aware portable pipeline creation seam for typed frontend callsites: " + coreApiRelative);
        assertTrue(coreApiSource.contains("public static PipelineDescriptor createPipelineDescriptor(")
            && coreApiSource.contains("PipelineDescriptor.PortableState portableState")
            && coreApiSource.contains("java.util.List<VulkanicSpirvModule> spirvModules"),
            "VulkanicCoreAPI should mirror SPIR-V-aware descriptor creation seam for typed frontend callsites: " + coreApiRelative);
        assertTrue(coreApiSource.contains("deriveResourceLayoutFromProgramReflection("),
            "VulkanicCoreAPI should mirror reflection-derived resource-layout seams for typed frontend callsites: " + coreApiRelative);
        assertTrue(coreApiSource.contains("withReflectedResourceLayout("),
            "VulkanicCoreAPI should mirror descriptor enrichment from reflection metadata: " + coreApiRelative);
        assertTrue(coreApiSource.contains("java.util.Set<VulkanicShaderStage> stages"),
            "VulkanicCoreAPI should mirror explicit stage-visibility reflection/layout seams for typed frontend callsites: " + coreApiRelative);
    }

    @Test
    public void testSelectedShaderLifecycleHotspotsUseTypedHandleSeams() throws IOException {
        String sodiumShaderRelative = "net/sodium/client/gl/shader/GlShader.java";
        String sodiumShaderSource = Files.readString(SRC_MAIN_JAVA.resolve(sodiumShaderRelative));
        assertTrue(sodiumShaderSource.contains("VulkanicAPI.createShaderHandle(ctx, type.stage)"),
            "Sodium GlShader should create shaders via typed handle seam: " + sodiumShaderRelative);
        assertTrue(sodiumShaderSource.contains("ShaderWorkarounds.safeShaderSource(handle, parsedShader.src())"),
            "Sodium GlShader should upload shader source via typed handle seam: " + sodiumShaderRelative);
        assertTrue(sodiumShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), VulkanicShaderHandle.of(this.handle()))"),
            "Sodium GlShader should delete shaders via typed handle seam: " + sodiumShaderRelative);

        String irisShaderRelative = "net/irisshaders/iris/gl/shader/GlShader.java";
        String irisShaderSource = Files.readString(SRC_MAIN_JAVA.resolve(irisShaderRelative));
        assertTrue(irisShaderSource.contains("VulkanicAPI.createShaderHandle(ctx, type.stage)"),
            "Iris GlShader should create shaders via typed handle seam: " + irisShaderRelative);
        assertTrue(irisShaderSource.contains("ShaderWorkarounds.safeShaderSource(handle, src)"),
            "Iris GlShader should upload shader source via typed handle seam: " + irisShaderRelative);
        assertTrue(irisShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), VulkanicShaderHandle.of(this.getGlId()))"),
            "Iris GlShader should delete shaders via typed handle seam: " + irisShaderRelative);

        String sodiumProgramRelative = "net/sodium/client/gl/shader/GlProgram.java";
        String sodiumProgramSource = Files.readString(SRC_MAIN_JAVA.resolve(sodiumProgramRelative));
        assertTrue(sodiumProgramSource.contains("VulkanicAPI.createShaderProgramHandle(commandContext())"),
            "Sodium GlProgram should create programs via typed handle seam: " + sodiumProgramRelative);
        assertTrue(sodiumProgramSource.contains("VulkanicAPI.attachShader(commandContext(), this.program, VulkanicShaderHandle.of(shader.handle()))"),
            "Sodium GlProgram should attach shaders via typed handle seam: " + sodiumProgramRelative);

        String programCreatorRelative = "net/irisshaders/iris/gl/shader/ProgramCreator.java";
        String programCreatorSource = Files.readString(SRC_MAIN_JAVA.resolve(programCreatorRelative));
        assertTrue(programCreatorSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "Iris ProgramCreator should create programs via typed handle seam: " + programCreatorRelative);
        assertTrue(programCreatorSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(shader.getHandle()))"),
            "Iris ProgramCreator should attach shaders via typed handle seam: " + programCreatorRelative);

        String shaderCreatorRelative = "net/irisshaders/iris/pipeline/programs/ShaderCreator.java";
        String shaderCreatorSource = Files.readString(SRC_MAIN_JAVA.resolve(shaderCreatorRelative));
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "Iris ShaderCreator should create programs via typed handle seam: " + shaderCreatorRelative);
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(s))"),
            "Iris ShaderCreator should attach shaders via typed handle seam: " + shaderCreatorRelative);
        assertTrue(shaderCreatorSource.contains("VulkanicAPI.createShaderHandle(ctx, shaderType.stage)"),
            "Iris ShaderCreator should create shaders via typed handle seam: " + shaderCreatorRelative);

        String dhGenericRelative = "net/irisshaders/iris/compat/dh/IrisGenericRenderProgram.java";
        String dhGenericSource = Files.readString(SRC_MAIN_JAVA.resolve(dhGenericRelative));
        assertTrue(dhGenericSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "IrisGenericRenderProgram should create programs via typed handle seam: " + dhGenericRelative);
        assertTrue(dhGenericSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), VulkanicProgramHandle.of(id))"),
            "IrisGenericRenderProgram should delete programs via typed handle seam: " + dhGenericRelative);

        String dhLodRelative = "net/irisshaders/iris/compat/dh/IrisLodRenderProgram.java";
        String dhLodSource = Files.readString(SRC_MAIN_JAVA.resolve(dhLodRelative));
        assertTrue(dhLodSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "IrisLodRenderProgram should create programs via typed handle seam: " + dhLodRelative);
        assertTrue(dhLodSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), VulkanicProgramHandle.of(id))"),
            "IrisLodRenderProgram should delete programs via typed handle seam: " + dhLodRelative);

        String blazeGlProgramRelative = "net/blaze3d/opengl/GlProgram.java";
        String blazeGlProgramSource = Files.readString(SRC_MAIN_JAVA.resolve(blazeGlProgramRelative));
        assertTrue(blazeGlProgramSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "Blaze3D GlProgram should create programs via typed handle seam: " + blazeGlProgramRelative);
        assertTrue(blazeGlProgramSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(glShaderModule.getShaderId()))"),
            "Blaze3D GlProgram should attach shaders via typed handle seam: " + blazeGlProgramRelative);
        assertTrue(blazeGlProgramSource.contains("VulkanicAPI.deleteProgram(VulkanicAPI.getCommandContext(), VulkanicProgramHandle.of(this.programId))"),
            "Blaze3D GlProgram should delete programs via typed handle seam: " + blazeGlProgramRelative);

        String blazeShaderModuleRelative = "net/blaze3d/opengl/GlShaderModule.java";
        String blazeShaderModuleSource = Files.readString(SRC_MAIN_JAVA.resolve(blazeShaderModuleRelative));
        assertTrue(blazeShaderModuleSource.contains("net.vulkanic.VulkanicShaderHandle.of(this.shaderId)"),
            "Blaze3D GlShaderModule should delete shaders via typed handle seam: " + blazeShaderModuleRelative);

        String blazeDeviceRelative = "net/blaze3d/opengl/GlDevice.java";
        String blazeDeviceSource = Files.readString(SRC_MAIN_JAVA.resolve(blazeDeviceRelative));
        assertTrue(blazeDeviceSource.contains("net.vulkanic.VulkanicAPI.createShaderHandle(ctx, toVulkanicShaderStage(shaderCompilationKey.type))"),
            "Blaze3D GlDevice should create shaders via typed handle seam in shader compilation path: " + blazeDeviceRelative);
        assertTrue(blazeDeviceSource.contains("net.irisshaders.iris.gl.shader.ShaderWorkarounds.safeShaderSource(shader, string2)"),
            "Blaze3D GlDevice should upload shader source via typed handle seam in shader compilation path: " + blazeDeviceRelative);
        assertTrue(blazeDeviceSource.contains("net.vulkanic.VulkanicAPI.createShaderProgramHandle(ctx)"),
            "Blaze3D GlDevice should create programs via typed handle seam in AMD workaround path: " + blazeDeviceRelative);

        String dhShaderRelative = "com/seibel/distanthorizons/core/render/glObject/shader/Shader.java";
        String dhShaderSource = Files.readString(SRC_MAIN_JAVA.resolve(dhShaderRelative));
        assertTrue(dhShaderSource.contains("VulkanicAPI.createShaderHandle(ctx, stage)")
                || dhShaderSource.contains("VulkanicAPI.createShaderHandle(ctx, type)"),
            "Distant Horizons Shader should create shaders via typed handle seam when stage mapping is available: " + dhShaderRelative);
        assertFalse(dhShaderSource.contains("VulkanicAPI.createShader(ctx, type)"),
            "Distant Horizons Shader should avoid raw createShader(int) callsites now that typed-handle creation is available: " + dhShaderRelative);
        assertTrue(dhShaderSource.contains("VulkanicAPI.deleteShader(ctx, VulkanicShaderHandle.of(this.id))"),
            "Distant Horizons Shader should delete shaders via typed handle seam: " + dhShaderRelative);

        String dhShaderProgramRelative = "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java";
        String dhShaderProgramSource = Files.readString(SRC_MAIN_JAVA.resolve(dhShaderProgramRelative));
        assertTrue(dhShaderProgramSource.contains("VulkanicAPI.createShaderProgramHandle(ctx)"),
            "Distant Horizons ShaderProgram should create programs via typed handle seam: " + dhShaderProgramRelative);
        assertTrue(dhShaderProgramSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(vertShader.id))"),
            "Distant Horizons ShaderProgram should attach vertex shaders via typed handle seam: " + dhShaderProgramRelative);
        assertTrue(dhShaderProgramSource.contains("VulkanicAPI.attachShader(ctx, program, VulkanicShaderHandle.of(fragShader.id))"),
            "Distant Horizons ShaderProgram should attach fragment shaders via typed handle seam: " + dhShaderProgramRelative);
        assertTrue(dhShaderProgramSource.contains("VulkanicAPI.deleteProgram(ctx, VulkanicProgramHandle.of(this.id))"),
            "Distant Horizons ShaderProgram should delete programs via typed handle seam: " + dhShaderProgramRelative);

        String partialShaderRelative = "net/irisshaders/iris/pipeline/programs/PartialShader.java";
        String partialShaderSource = Files.readString(SRC_MAIN_JAVA.resolve(partialShaderRelative));
        assertTrue(partialShaderSource.contains("VulkanicAPI.deleteShader(VulkanicAPI.getCommandContext(), VulkanicShaderHandle.of(s))"),
            "Iris PartialShader should delete detached shaders via typed handle seam: " + partialShaderRelative);
    }

    @Test
    public void testSelectedHotspotsUseTypedUniformLocationAndLogicOpSeams() throws IOException {
        String shaderProgramRelative = "com/seibel/distanthorizons/core/render/glObject/shader/ShaderProgram.java";
        String shaderProgramSource = Files.readString(SRC_MAIN_JAVA.resolve(shaderProgramRelative));
        assertTrue(shaderProgramSource.contains("VulkanicAPI.resolveUniformLocation("),
            "ShaderProgram should resolve uniforms via typed location helper instead of raw location lookups: " + shaderProgramRelative);

        String glProgramRelative = "net/sodium/client/gl/shader/GlProgram.java";
        String glProgramSource = Files.readString(SRC_MAIN_JAVA.resolve(glProgramRelative));
        assertTrue(glProgramSource.contains("VulkanicAPI.resolveUniformLocation("),
            "Sodium GlProgram should resolve uniforms via typed location helper instead of raw location lookups: " + glProgramRelative);

        String encoderRelative = "net/blaze3d/opengl/GlCommandEncoder.java";
        String encoderSource = Files.readString(SRC_MAIN_JAVA.resolve(encoderRelative));
        assertTrue(encoderSource.contains("VulkanicAPI.setLogicOp(ctx, VulkanicLogicOp.OR_REVERSE)"),
            "GlCommandEncoder should use typed VulkanicLogicOp routing for OR_REVERSE logic-op setup: " + encoderRelative);
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

    @Test
    public void testShaderSourceWorkaroundsUseCharSequenceUploadSeam() throws IOException {
        List<String> files = List.of(
            "net/sodium/client/gl/shader/ShaderWorkarounds.java",
            "net/irisshaders/iris/gl/shader/ShaderWorkarounds.java",
            "com/seibel/distanthorizons/core/render/glObject/shader/Shader.java"
        );

        for (String relative : files) {
            String source = Files.readString(SRC_MAIN_JAVA.resolve(relative));
            assertTrue(source.contains("uploadShaderSource"),
                "Shader source helper should route through VulkanicAPI.uploadShaderSource seam: " + relative);
            assertFalse(source.contains("pointers.address0()"),
                "Shader source helper should avoid pointer-address plumbing at callsites: " + relative);
            assertFalse(source.contains("MemoryUtil.memUTF8"),
                "Shader source helper should avoid direct UTF8 native conversion at callsites: " + relative);
        }
    }
}
