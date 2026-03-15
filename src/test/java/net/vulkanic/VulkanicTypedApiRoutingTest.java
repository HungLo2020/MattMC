package net.vulkanic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import net.blaze3d.pipeline.CompiledRenderPipeline;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanicTypedApiRoutingTest {

    private final RecordingInvocationHandler invocationHandler = new RecordingInvocationHandler();

    private static final CommandContext TEST_CONTEXT = new CommandContext() {
        @Override
        public boolean isImmediate() {
            return true;
        }

        @Override
        public long getHandle() {
            return 0L;
        }

        @Override
        public String getDebugName() {
            return "test";
        }
    };

    @BeforeEach
    public void setUp() throws Exception {
        resetBackendState();
        invocationHandler.configureProgramReflection(java.util.List.of(), java.util.List.of());
        invocationHandler.setBackendType(GraphicsBackendType.VULKAN);

        GraphicsBackend proxyBackend = (GraphicsBackend) Proxy.newProxyInstance(
            GraphicsBackend.class.getClassLoader(),
            new Class<?>[]{GraphicsBackend.class},
            invocationHandler
        );

        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        backendField.set(null, proxyBackend);
    }

    @AfterEach
    public void tearDown() throws Exception {
        resetBackendState();
    }

    @Test
    public void testCoreBindTextureUsesTypedBackendMethod() {
        VulkanicCoreAPI.bindTexture(TEST_CONTEXT, VulkanicTextureTarget.TEXTURE_2D, 19);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("bindTexture", invocation.method.getName());
        assertEquals(VulkanicTextureTarget.class, invocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicTextureTarget.TEXTURE_2D, invocation.args[1]);
    }

    @Test
    public void testLegacyBindTextureConvertsKnownTargetToTypedMethod() {
        VulkanicLegacyGLCompat.bindTexture(TEST_CONTEXT, VulkanicAPI.GL_TEXTURE_2D, 3);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("bindTexture", invocation.method.getName());
        assertEquals(VulkanicTextureTarget.class, invocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicTextureTarget.TEXTURE_2D, invocation.args[1]);
    }

    @Test
    public void testLegacyBindTextureFallsBackToRawMethodForUnknownTarget() {
        int unknownTarget = 0x7FFF_0001;
        VulkanicLegacyGLCompat.bindTexture(TEST_CONTEXT, unknownTarget, 3);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("bindTexture", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(unknownTarget, invocation.args[1]);
    }

    @Test
    public void testUploadTexture2DRoutingUsesTypedMethodForKnownTuple() {
        VulkanicAPI.uploadTexture2D(
            TEST_CONTEXT,
            VulkanicAPI.GL_TEXTURE_2D,
            0,
            VulkanicAPI.GL_R32F,
            1,
            1,
            0,
            VulkanicAPI.GL_RED,
            VulkanicAPI.GL_FLOAT,
            null
        );

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("uploadTexture2D", invocation.method.getName());
        assertEquals(VulkanicTextureTarget.class, invocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicTextureUploadFormat.class, invocation.method.getParameterTypes()[3]);
        assertEquals(VulkanicTextureTarget.TEXTURE_2D, invocation.args[1]);
        assertEquals(VulkanicTextureUploadFormat.RED32_SFLOAT, invocation.args[3]);
    }

    @Test
    public void testUploadTexture2DRoutingFallsBackForUnknownTuple() {
        int unknownInternalFormat = 0x7FFF_2010;
        VulkanicAPI.uploadTexture2D(
            TEST_CONTEXT,
            VulkanicAPI.GL_TEXTURE_2D,
            0,
            unknownInternalFormat,
            1,
            1,
            0,
            VulkanicAPI.GL_RED,
            VulkanicAPI.GL_FLOAT,
            null
        );

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("uploadTexture2D", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(int.class, invocation.method.getParameterTypes()[3]);
        assertEquals(unknownInternalFormat, invocation.args[3]);
    }

    @Test
    public void testUploadTexture2DRoutingPreservesRawTupleOnOpenGLBackend() {
        invocationHandler.setBackendType(GraphicsBackendType.OPENGL);

        VulkanicAPI.uploadTexture2D(
            TEST_CONTEXT,
            VulkanicAPI.GL_TEXTURE_2D,
            0,
            VulkanicAPI.GL_R32F,
            1,
            1,
            0,
            VulkanicAPI.GL_RED,
            VulkanicAPI.GL_FLOAT,
            null
        );

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("uploadTexture2D", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(int.class, invocation.method.getParameterTypes()[3]);
        assertEquals(VulkanicAPI.GL_R32F, invocation.args[3]);
    }

    @Test
    public void testBackendIdentitySeamsRouteThroughBackend() {
        assertEquals("NVIDIA", VulkanicAPI.getBackendVendorName());
        RecordedInvocation vendorInvocation = invocationHandler.lastInvocation;
        assertNotNull(vendorInvocation);
        assertEquals("getBackendVendorName", vendorInvocation.method.getName());

        assertEquals("GeForce RTX", VulkanicAPI.getBackendRendererName());
        RecordedInvocation rendererInvocation = invocationHandler.lastInvocation;
        assertNotNull(rendererInvocation);
        assertEquals("getBackendRendererName", rendererInvocation.method.getName());

        assertEquals("Vulkan 1.3", VulkanicAPI.getBackendVersionName());
        RecordedInvocation versionInvocation = invocationHandler.lastInvocation;
        assertNotNull(versionInvocation);
        assertEquals("getBackendVersionName", versionInvocation.method.getName());
    }

    @Test
    public void testBackendFeatureSeamsRouteThroughBackend() {
        java.util.List<String> extensions = VulkanicAPI.getBackendEnabledExtensions();
        RecordedInvocation extensionInvocation = invocationHandler.lastInvocation;
        assertNotNull(extensionInvocation);
        assertEquals("getBackendEnabledExtensions", extensionInvocation.method.getName());
        assertEquals(java.util.List.of("GL_KHR_debug"), extensions);

        java.util.List<String> optionalFeatures = VulkanicAPI.getBackendOptionalFeatureNames();
        RecordedInvocation featureInvocation = invocationHandler.lastInvocation;
        assertNotNull(featureInvocation);
        assertEquals("getBackendOptionalFeatureNames", featureInvocation.method.getName());
        assertEquals(java.util.List.of("native-vulkan-runtime"), optionalFeatures);
    }

    @Test
    public void testPipelinePrecompileAndCacheClearRouteThroughBackendSeams() {
        CompiledRenderPipeline compiled = VulkanicAPI.precompileRenderPipeline(null, null);
        assertNotNull(compiled);
        assertTrue(compiled.isValid());

        RecordedInvocation precompileInvocation = invocationHandler.lastInvocation;
        assertNotNull(precompileInvocation);
        assertEquals("precompileRenderPipeline", precompileInvocation.method.getName());

        VulkanicAPI.clearBackendPipelineCache();
        RecordedInvocation clearInvocation = invocationHandler.lastInvocation;
        assertNotNull(clearInvocation);
        assertEquals("clearPrecompiledPipelineCache", clearInvocation.method.getName());
    }

    @Test
    public void testPresentTextureToScreenRoutesThroughBackendSeam() {
        VulkanicAPI.presentTextureToScreen(TEST_CONTEXT, null);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("presentTextureToScreen", invocation.method.getName());
        assertEquals(net.blaze3d.textures.GpuTextureView.class, invocation.method.getParameterTypes()[1]);
    }

    @Test
    public void testCoreAndLegacyGetIntegerUseTypedAndFallbackPaths() {
        int typedResult = VulkanicCoreAPI.getInteger(TEST_CONTEXT, VulkanicIntegerQuery.MAX_TEXTURE_SIZE);
        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("getInteger", typedInvocation.method.getName());
        assertEquals(VulkanicIntegerQuery.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(111, typedResult);

        int fallbackPName = 0x7FFF_0002;
        int rawResult = VulkanicLegacyGLCompat.getInteger(TEST_CONTEXT, fallbackPName);
        RecordedInvocation rawInvocation = invocationHandler.lastInvocation;
        assertNotNull(rawInvocation);
        assertEquals("getInteger", rawInvocation.method.getName());
        assertEquals(int.class, rawInvocation.method.getParameterTypes()[1]);
        assertEquals(222, rawResult);
    }

    @Test
    public void testLegacyTexParameteriConvertsKnownParametersToTypedMethod() {
        VulkanicLegacyGLCompat.texParameteri(
            TEST_CONTEXT,
            VulkanicAPI.GL_TEXTURE_2D,
            VulkanicAPI.GL_TEXTURE_MIN_FILTER,
            VulkanicAPI.GL_LINEAR
        );

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("texParameteri", invocation.method.getName());
        assertEquals(VulkanicTextureTarget.class, invocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicTextureParameterName.class, invocation.method.getParameterTypes()[2]);
        assertEquals(VulkanicTextureTarget.TEXTURE_2D, invocation.args[1]);
        assertEquals(VulkanicTextureParameterName.MIN_FILTER, invocation.args[2]);
    }

    @Test
    public void testClearBuffersTypedRoutingUsesClearBufferEnumBits() {
        VulkanicCoreAPI.clearBuffers(TEST_CONTEXT, VulkanicClearBuffer.COLOR, VulkanicClearBuffer.DEPTH);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("clearBuffers", invocation.method.getName());
        assertEquals(VulkanicClearBuffer[].class, invocation.method.getParameterTypes()[1]);
    }

    @Test
    public void testLogicOpRoutingUsesTypedMethodForKnownConstant() {
        VulkanicAPI.setLogicOp(TEST_CONTEXT, VulkanicAPI.GL_OR_REVERSE);

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setLogicOp", typedInvocation.method.getName());
        assertEquals(VulkanicLogicOp.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicLogicOp.OR_REVERSE, typedInvocation.args[1]);
    }

    @Test
    public void testLogicOpRoutingFallsBackForUnknownConstant() {
        int unknownLogicOp = 0x7FFF_1010;
        VulkanicAPI.setLogicOp(TEST_CONTEXT, unknownLogicOp);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setLogicOp", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(unknownLogicOp, invocation.args[1]);
    }

    @Test
    public void testUniformAndBufferTypedRoutingUsesWrapperTypes() {
        VulkanicCoreAPI.setUniform1i(TEST_CONTEXT, VulkanicUniformLocation.of(12), 77);

        RecordedInvocation uniformInvocation = invocationHandler.lastInvocation;
        assertNotNull(uniformInvocation);
        assertEquals("setUniform1i", uniformInvocation.method.getName());
        assertEquals(VulkanicUniformLocation.class, uniformInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicUniformLocation.of(12), uniformInvocation.args[1]);

        VulkanicCoreAPI.bindUniformBufferRange(TEST_CONTEXT, VulkanicBufferTarget.UNIFORM, 3, 22, 64L, 128L);

        RecordedInvocation bindInvocation = invocationHandler.lastInvocation;
        assertNotNull(bindInvocation);
        assertEquals("bindUniformBufferRange", bindInvocation.method.getName());
        assertEquals(VulkanicBufferTarget.class, bindInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicBufferTarget.UNIFORM, bindInvocation.args[1]);

        VulkanicCoreAPI.texBuffer(TEST_CONTEXT, VulkanicTextureTarget.TEXTURE_BUFFER, VulkanicAPI.GL_RGBA8, 99);

        RecordedInvocation texBufferInvocation = invocationHandler.lastInvocation;
        assertNotNull(texBufferInvocation);
        assertEquals("texBuffer", texBufferInvocation.method.getName());
        assertEquals(VulkanicTextureTarget.class, texBufferInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicTextureTarget.TEXTURE_BUFFER, texBufferInvocation.args[1]);
    }

    @Test
    public void testSpirvCompileRoutingUsesTypedShaderStage() {
        VulkanicCoreAPI.compileSpirvModule(
            TEST_CONTEXT,
            VulkanicShaderStage.VERTEX,
            "#version 450\nvoid main(){}",
            "test.vert"
        );

        RecordedInvocation compileInvocation = invocationHandler.lastInvocation;
        assertNotNull(compileInvocation);
        assertEquals("compileSpirvModule", compileInvocation.method.getName());
        assertEquals(VulkanicShaderStage.class, compileInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicShaderStage.VERTEX, compileInvocation.args[1]);

        VulkanicAPI.getCompiledSpirvModule(TEST_CONTEXT, 99);

        RecordedInvocation queryInvocation = invocationHandler.lastInvocation;
        assertNotNull(queryInvocation);
        assertEquals("getCompiledSpirvModule", queryInvocation.method.getName());
        assertEquals(int.class, queryInvocation.method.getParameterTypes()[1]);
        assertEquals(99, queryInvocation.args[1]);
    }

    @Test
    public void testShaderSourceUploadRoutesViaCharSequenceSeam() {
        VulkanicAPI.uploadShaderSource(TEST_CONTEXT, 41, "#version 450\nvoid main(){}");

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("uploadShaderSource", invocation.method.getName());
        assertEquals(CharSequence.class, invocation.method.getParameterTypes()[2]);
        assertEquals(41, invocation.args[1]);
        assertEquals("#version 450\nvoid main(){}", invocation.args[2]);
    }

    @Test
    public void testTypedShaderAndProgramHandleLifecycleRouting() {
        VulkanicCoreAPI.createShaderHandle(TEST_CONTEXT, VulkanicShaderStage.VERTEX);

        RecordedInvocation createShaderInvocation = invocationHandler.lastInvocation;
        assertNotNull(createShaderInvocation);
        assertEquals("createShaderHandle", createShaderInvocation.method.getName());
        assertEquals(VulkanicShaderStage.class, createShaderInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicShaderStage.VERTEX, createShaderInvocation.args[1]);

        VulkanicCoreAPI.createShaderProgramHandle(TEST_CONTEXT);

        RecordedInvocation createProgramInvocation = invocationHandler.lastInvocation;
        assertNotNull(createProgramInvocation);
        assertEquals("createShaderProgramHandle", createProgramInvocation.method.getName());

        VulkanicShaderHandle shader = VulkanicShaderHandle.of(17);
        VulkanicProgramHandle program = VulkanicProgramHandle.of(23);

        VulkanicCoreAPI.compileShader(TEST_CONTEXT, shader);

        RecordedInvocation compileInvocation = invocationHandler.lastInvocation;
        assertNotNull(compileInvocation);
        assertEquals("compileShader", compileInvocation.method.getName());
        assertEquals(VulkanicShaderHandle.class, compileInvocation.method.getParameterTypes()[1]);
        assertEquals(shader, compileInvocation.args[1]);

        VulkanicCoreAPI.attachShader(TEST_CONTEXT, program, shader);

        RecordedInvocation attachInvocation = invocationHandler.lastInvocation;
        assertNotNull(attachInvocation);
        assertEquals("attachShader", attachInvocation.method.getName());
        assertEquals(VulkanicProgramHandle.class, attachInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicShaderHandle.class, attachInvocation.method.getParameterTypes()[2]);
        assertEquals(program, attachInvocation.args[1]);
        assertEquals(shader, attachInvocation.args[2]);
    }

    @Test
    public void testReflectionDerivedResourceLayoutRoutingAndNormalization() {
        invocationHandler.configureProgramReflection(
            java.util.List.of("Globals"),
            java.util.List.of(
                new RecordingInvocationHandler.ReflectedUniform("Sampler0[0]", 1, VulkanicAPI.GL_SAMPLER_2D),
                new RecordingInvocationHandler.ReflectedUniform("IgnoredScalar", 1, VulkanicAPI.GL_FLOAT)
            )
        );

        PipelineDescriptor.ResourceLayout layout = VulkanicCoreAPI.deriveResourceLayoutFromProgramReflection(
            TEST_CONTEXT,
            VulkanicProgramHandle.of(88),
            128,
            java.util.Set.of(VulkanicShaderStage.FRAGMENT)
        );

        assertEquals(2, layout.bindings().size());
        assertEquals("Globals", layout.bindings().get(0).name());
        assertEquals(PipelineDescriptor.ResourceType.UNIFORM_BUFFER, layout.bindings().get(0).type());
        assertEquals(java.util.Set.of(VulkanicShaderStage.FRAGMENT), layout.bindings().get(0).stages());
        assertEquals("Sampler0", layout.bindings().get(1).name());
        assertEquals(PipelineDescriptor.ResourceType.SAMPLER, layout.bindings().get(1).type());
        assertEquals(java.util.Set.of(VulkanicShaderStage.FRAGMENT), layout.bindings().get(1).stages());

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("getActiveUniform", invocation.method.getName());
    }

    @Test
    public void testCapabilityRoutingUsesTypedMethodForKnownCapability() {
        VulkanicCoreAPI.setCapabilityEnabled(TEST_CONTEXT, VulkanicCapability.SCISSOR_TEST, false);

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setCapabilityEnabled", typedInvocation.method.getName());
        assertEquals(VulkanicCapability.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicCapability.SCISSOR_TEST, typedInvocation.args[1]);

        VulkanicLegacyGLCompat.setCapabilityEnabled(TEST_CONTEXT, VulkanicAPI.GL_BLEND, true);

        RecordedInvocation convertedInvocation = invocationHandler.lastInvocation;
        assertNotNull(convertedInvocation);
        assertEquals("setCapabilityEnabled", convertedInvocation.method.getName());
        assertEquals(VulkanicCapability.class, convertedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicCapability.BLEND, convertedInvocation.args[1]);
    }

    @Test
    public void testCapabilityRoutingFallsBackForUnknownCapability() {
        int unknownCapability = 0x7FFF_1001;
        VulkanicLegacyGLCompat.setCapabilityEnabled(TEST_CONTEXT, unknownCapability, true);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setCapabilityEnabled", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(unknownCapability, invocation.args[1]);
    }

    @Test
    public void testDepthFuncRoutingUsesTypedMethodForKnownConstant() {
        VulkanicCoreAPI.setDepthFunc(TEST_CONTEXT, VulkanicDepthCompareOp.LESS);

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setDepthFunc", typedInvocation.method.getName());
        assertEquals(VulkanicDepthCompareOp.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicDepthCompareOp.LESS, typedInvocation.args[1]);

        VulkanicLegacyGLCompat.setDepthFunc(TEST_CONTEXT, VulkanicAPI.GL_LEQUAL);

        RecordedInvocation convertedInvocation = invocationHandler.lastInvocation;
        assertNotNull(convertedInvocation);
        assertEquals("setDepthFunc", convertedInvocation.method.getName());
        assertEquals(VulkanicDepthCompareOp.class, convertedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicDepthCompareOp.LEQUAL, convertedInvocation.args[1]);
    }

    @Test
    public void testDepthFuncRoutingFallsBackForUnknownConstant() {
        int unknownDepthFunc = 0x7FFF_1002;
        VulkanicLegacyGLCompat.setDepthFunc(TEST_CONTEXT, unknownDepthFunc);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setDepthFunc", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(unknownDepthFunc, invocation.args[1]);
    }

    @Test
    public void testCullModeRoutingUsesTypedMethodForKnownConstant() {
        VulkanicCoreAPI.setCullFaceMode(TEST_CONTEXT, VulkanicCullFaceMode.BACK);

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setCullFaceMode", typedInvocation.method.getName());
        assertEquals(VulkanicCullFaceMode.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicCullFaceMode.BACK, typedInvocation.args[1]);

        VulkanicLegacyGLCompat.setCullFaceMode(TEST_CONTEXT, VulkanicAPI.GL_FRONT);

        RecordedInvocation convertedInvocation = invocationHandler.lastInvocation;
        assertNotNull(convertedInvocation);
        assertEquals("setCullFaceMode", convertedInvocation.method.getName());
        assertEquals(VulkanicCullFaceMode.class, convertedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicCullFaceMode.FRONT, convertedInvocation.args[1]);
    }

    @Test
    public void testBlendFunctionRoutingUsesTypedMethodForKnownConstants() {
        VulkanicCoreAPI.setBlendFunction(
            TEST_CONTEXT,
            VulkanicBlendFactor.SRC_ALPHA,
            VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA,
            VulkanicBlendFactor.ONE,
            VulkanicBlendFactor.ZERO
        );

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setBlendFunction", typedInvocation.method.getName());
        assertEquals(VulkanicBlendFactor.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicBlendFactor.SRC_ALPHA, typedInvocation.args[1]);
        assertEquals(VulkanicBlendFactor.ONE_MINUS_SRC_ALPHA, typedInvocation.args[2]);
        assertEquals(VulkanicBlendFactor.ONE, typedInvocation.args[3]);
        assertEquals(VulkanicBlendFactor.ZERO, typedInvocation.args[4]);

        VulkanicLegacyGLCompat.setBlendFunction(
            TEST_CONTEXT,
            VulkanicAPI.GL_SRC_ALPHA,
            VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA,
            VulkanicAPI.GL_ONE,
            VulkanicAPI.GL_ZERO
        );

        RecordedInvocation convertedInvocation = invocationHandler.lastInvocation;
        assertNotNull(convertedInvocation);
        assertEquals("setBlendFunction", convertedInvocation.method.getName());
        assertEquals(VulkanicBlendFactor.class, convertedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicBlendFactor.SRC_ALPHA, convertedInvocation.args[1]);
    }

    @Test
    public void testBlendFunctionRoutingFallsBackForUnknownConstant() {
        int unknownBlendFactor = 0x7FFF_1003;
        VulkanicLegacyGLCompat.setBlendFunction(
            TEST_CONTEXT,
            unknownBlendFactor,
            VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA,
            VulkanicAPI.GL_ONE,
            VulkanicAPI.GL_ZERO
        );

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setBlendFunction", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(unknownBlendFactor, invocation.args[1]);
    }

    @Test
    public void testBlendEquationRoutingUsesTypedMethodForKnownConstant() {
        VulkanicCoreAPI.setBlendEquation(TEST_CONTEXT, VulkanicBlendEquation.ADD);

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setBlendEquation", typedInvocation.method.getName());
        assertEquals(VulkanicBlendEquation.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicBlendEquation.ADD, typedInvocation.args[1]);

        VulkanicLegacyGLCompat.setBlendEquation(TEST_CONTEXT, VulkanicAPI.GL_FUNC_ADD);

        RecordedInvocation convertedInvocation = invocationHandler.lastInvocation;
        assertNotNull(convertedInvocation);
        assertEquals("setBlendEquation", convertedInvocation.method.getName());
        assertEquals(VulkanicBlendEquation.class, convertedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicBlendEquation.ADD, convertedInvocation.args[1]);
    }

    @Test
    public void testBlendEquationRoutingFallsBackForUnknownConstant() {
        int unknownEquation = 0x7FFF_1004;
        VulkanicLegacyGLCompat.setBlendEquation(TEST_CONTEXT, unknownEquation);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setBlendEquation", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(unknownEquation, invocation.args[1]);
    }

    @Test
    public void testStencilFuncRoutingUsesTypedMethodForKnownConstant() {
        VulkanicCoreAPI.setStencilFunc(TEST_CONTEXT, VulkanicStencilCompareOp.LEQUAL, 5, 0xFF);

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setStencilFunc", typedInvocation.method.getName());
        assertEquals(VulkanicStencilCompareOp.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicStencilCompareOp.LEQUAL, typedInvocation.args[1]);

        VulkanicLegacyGLCompat.setStencilFunc(TEST_CONTEXT, VulkanicAPI.GL_ALWAYS, 2, 0x0F);

        RecordedInvocation convertedInvocation = invocationHandler.lastInvocation;
        assertNotNull(convertedInvocation);
        assertEquals("setStencilFunc", convertedInvocation.method.getName());
        assertEquals(VulkanicStencilCompareOp.class, convertedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicStencilCompareOp.ALWAYS, convertedInvocation.args[1]);
    }

    @Test
    public void testStencilFuncRoutingFallsBackForUnknownConstant() {
        int unknownStencilFunc = 0x7FFF_1005;
        VulkanicLegacyGLCompat.setStencilFunc(TEST_CONTEXT, unknownStencilFunc, 1, 0xFF);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setStencilFunc", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(unknownStencilFunc, invocation.args[1]);
    }

    @Test
    public void testStencilOpRoutingUsesTypedMethodForKnownConstants() {
        VulkanicCoreAPI.setStencilOp(
            TEST_CONTEXT,
            VulkanicStencilOperation.KEEP,
            VulkanicStencilOperation.REPLACE,
            VulkanicStencilOperation.INCREMENT_CLAMP
        );

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setStencilOp", typedInvocation.method.getName());
        assertEquals(VulkanicStencilOperation.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicStencilOperation.KEEP, typedInvocation.args[1]);

        VulkanicLegacyGLCompat.setStencilOp(
            TEST_CONTEXT,
            VulkanicAPI.GL_KEEP,
            VulkanicAPI.GL_REPLACE,
            VulkanicAPI.GL_INCR
        );

        RecordedInvocation convertedInvocation = invocationHandler.lastInvocation;
        assertNotNull(convertedInvocation);
        assertEquals("setStencilOp", convertedInvocation.method.getName());
        assertEquals(VulkanicStencilOperation.class, convertedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicStencilOperation.KEEP, convertedInvocation.args[1]);
        assertEquals(VulkanicStencilOperation.REPLACE, convertedInvocation.args[2]);
        assertEquals(VulkanicStencilOperation.INCREMENT_CLAMP, convertedInvocation.args[3]);
    }

    @Test
    public void testStencilOpRoutingFallsBackForUnknownConstant() {
        int unknownStencilOp = 0x7FFF_1006;
        VulkanicLegacyGLCompat.setStencilOp(TEST_CONTEXT, unknownStencilOp, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_REPLACE);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setStencilOp", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(unknownStencilOp, invocation.args[1]);
    }

    @Test
    public void testStencilWriteMaskRoutingUsesRawMaskMethod() {
        VulkanicCoreAPI.setStencilWriteMask(TEST_CONTEXT, 0x0F);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setStencilWriteMask", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(0x0F, invocation.args[1]);

        VulkanicLegacyGLCompat.setStencilWriteMask(TEST_CONTEXT, 0xF0);

        RecordedInvocation legacyInvocation = invocationHandler.lastInvocation;
        assertNotNull(legacyInvocation);
        assertEquals("setStencilWriteMask", legacyInvocation.method.getName());
        assertEquals(int.class, legacyInvocation.method.getParameterTypes()[1]);
        assertEquals(0xF0, legacyInvocation.args[1]);
    }

    @Test
    public void testStencilFuncSeparateRoutingUsesTypedMethodForKnownConstants() {
        VulkanicCoreAPI.setStencilFuncSeparate(TEST_CONTEXT, VulkanicStencilFace.FRONT, VulkanicStencilCompareOp.ALWAYS, 3, 0xFF);

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setStencilFuncSeparate", typedInvocation.method.getName());
        assertEquals(VulkanicStencilFace.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicStencilCompareOp.class, typedInvocation.method.getParameterTypes()[2]);
        assertEquals(VulkanicStencilFace.FRONT, typedInvocation.args[1]);
        assertEquals(VulkanicStencilCompareOp.ALWAYS, typedInvocation.args[2]);

        VulkanicLegacyGLCompat.setStencilFuncSeparate(TEST_CONTEXT, VulkanicAPI.GL_BACK, VulkanicAPI.GL_LEQUAL, 1, 0x0F);

        RecordedInvocation convertedInvocation = invocationHandler.lastInvocation;
        assertNotNull(convertedInvocation);
        assertEquals("setStencilFuncSeparate", convertedInvocation.method.getName());
        assertEquals(VulkanicStencilFace.class, convertedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicStencilCompareOp.class, convertedInvocation.method.getParameterTypes()[2]);
        assertEquals(VulkanicStencilFace.BACK, convertedInvocation.args[1]);
        assertEquals(VulkanicStencilCompareOp.LEQUAL, convertedInvocation.args[2]);
    }

    @Test
    public void testStencilFuncSeparateRoutingFallsBackForUnknownFace() {
        int unknownFace = 0x7FFF_1007;
        VulkanicLegacyGLCompat.setStencilFuncSeparate(TEST_CONTEXT, unknownFace, VulkanicAPI.GL_ALWAYS, 0, 0xFF);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setStencilFuncSeparate", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(unknownFace, invocation.args[1]);
    }

    @Test
    public void testStencilOpSeparateRoutingUsesTypedMethodForKnownConstants() {
        VulkanicCoreAPI.setStencilOpSeparate(
            TEST_CONTEXT,
            VulkanicStencilFace.BACK,
            VulkanicStencilOperation.KEEP,
            VulkanicStencilOperation.INCREMENT_WRAP,
            VulkanicStencilOperation.DECREMENT_WRAP
        );

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setStencilOpSeparate", typedInvocation.method.getName());
        assertEquals(VulkanicStencilFace.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicStencilOperation.class, typedInvocation.method.getParameterTypes()[2]);
        assertEquals(VulkanicStencilFace.BACK, typedInvocation.args[1]);
        assertEquals(VulkanicStencilOperation.INCREMENT_WRAP, typedInvocation.args[3]);

        VulkanicLegacyGLCompat.setStencilOpSeparate(
            TEST_CONTEXT,
            VulkanicAPI.GL_FRONT,
            VulkanicAPI.GL_REPLACE,
            VulkanicAPI.GL_INCR,
            VulkanicAPI.GL_DECR
        );

        RecordedInvocation convertedInvocation = invocationHandler.lastInvocation;
        assertNotNull(convertedInvocation);
        assertEquals("setStencilOpSeparate", convertedInvocation.method.getName());
        assertEquals(VulkanicStencilFace.class, convertedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicStencilOperation.class, convertedInvocation.method.getParameterTypes()[2]);
        assertEquals(VulkanicStencilFace.FRONT, convertedInvocation.args[1]);
        assertEquals(VulkanicStencilOperation.REPLACE, convertedInvocation.args[2]);
        assertEquals(VulkanicStencilOperation.INCREMENT_CLAMP, convertedInvocation.args[3]);
        assertEquals(VulkanicStencilOperation.DECREMENT_CLAMP, convertedInvocation.args[4]);
    }

    @Test
    public void testStencilOpSeparateRoutingFallsBackForUnknownOperation() {
        int unknownStencilOp = 0x7FFF_1008;
        VulkanicLegacyGLCompat.setStencilOpSeparate(TEST_CONTEXT, VulkanicAPI.GL_BACK, unknownStencilOp, VulkanicAPI.GL_KEEP, VulkanicAPI.GL_REPLACE);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setStencilOpSeparate", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[2]);
        assertEquals(unknownStencilOp, invocation.args[2]);
    }

    @Test
    public void testStencilWriteMaskSeparateRoutingUsesTypedFaceForKnownConstant() {
        VulkanicCoreAPI.setStencilWriteMaskSeparate(TEST_CONTEXT, VulkanicStencilFace.FRONT_AND_BACK, 0xAA);

        RecordedInvocation typedInvocation = invocationHandler.lastInvocation;
        assertNotNull(typedInvocation);
        assertEquals("setStencilWriteMaskSeparate", typedInvocation.method.getName());
        assertEquals(VulkanicStencilFace.class, typedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicStencilFace.FRONT_AND_BACK, typedInvocation.args[1]);
        assertEquals(0xAA, typedInvocation.args[2]);

        VulkanicLegacyGLCompat.setStencilWriteMaskSeparate(TEST_CONTEXT, VulkanicAPI.GL_FRONT, 0x55);

        RecordedInvocation convertedInvocation = invocationHandler.lastInvocation;
        assertNotNull(convertedInvocation);
        assertEquals("setStencilWriteMaskSeparate", convertedInvocation.method.getName());
        assertEquals(VulkanicStencilFace.class, convertedInvocation.method.getParameterTypes()[1]);
        assertEquals(VulkanicStencilFace.FRONT, convertedInvocation.args[1]);
        assertEquals(0x55, convertedInvocation.args[2]);
    }

    @Test
    public void testStencilWriteMaskSeparateRoutingFallsBackForUnknownFace() {
        int unknownFace = 0x7FFF_1009;
        VulkanicLegacyGLCompat.setStencilWriteMaskSeparate(TEST_CONTEXT, unknownFace, 0x7F);

        RecordedInvocation invocation = invocationHandler.lastInvocation;
        assertNotNull(invocation);
        assertEquals("setStencilWriteMaskSeparate", invocation.method.getName());
        assertEquals(int.class, invocation.method.getParameterTypes()[1]);
        assertEquals(unknownFace, invocation.args[1]);
    }

    private static void resetBackendState() throws Exception {
        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        backendField.set(null, null);

        Field rawVulkanBackendField = VulkanicAPI.class.getDeclaredField("rawVulkanBackend");
        rawVulkanBackendField.setAccessible(true);
        rawVulkanBackendField.set(null, null);
    }

    private static final class RecordedInvocation {
        private final Method method;
        private final Object[] args;

        private RecordedInvocation(Method method, Object[] args) {
            this.method = method;
            this.args = args;
        }
    }

    private static final class RecordingInvocationHandler implements InvocationHandler {
        private RecordedInvocation lastInvocation;
        private java.util.List<String> reflectedUniformBlocks = java.util.List.of();
        private java.util.List<ReflectedUniform> reflectedUniforms = java.util.List.of();
        private GraphicsBackendType backendType = GraphicsBackendType.VULKAN;

        private void setBackendType(GraphicsBackendType backendType) {
            this.backendType = backendType;
        }

        private void configureProgramReflection(
            java.util.List<String> uniformBlocks,
            java.util.List<ReflectedUniform> uniforms
        ) {
            this.reflectedUniformBlocks = java.util.List.copyOf(uniformBlocks);
            this.reflectedUniforms = java.util.List.copyOf(uniforms);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            this.lastInvocation = new RecordedInvocation(method, args == null ? new Object[0] : args.clone());

            if (method.getName().equals("getProgramParameter")) {
                int pname = (int) args[2];
                if (pname == VulkanicProgramParameterName.ACTIVE_UNIFORM_BLOCKS.toLegacyGlPName()) {
                    return reflectedUniformBlocks.size();
                }
                if (pname == VulkanicProgramParameterName.ACTIVE_UNIFORMS.toLegacyGlPName()) {
                    return reflectedUniforms.size();
                }
                return 0;
            }

            if (method.getName().equals("retrieveActiveUniformBlockName")) {
                int uniformBlockIndex = (int) args[2];
                return reflectedUniformBlocks.get(uniformBlockIndex);
            }

            if (method.getName().equals("getActiveUniform")) {
                int uniformIndex = (int) args[2];
                ReflectedUniform reflectedUniform = reflectedUniforms.get(uniformIndex);
                ((java.nio.IntBuffer) args[4]).put(0, reflectedUniform.arraySize());
                ((java.nio.IntBuffer) args[5]).put(0, reflectedUniform.legacyType());
                return reflectedUniform.name();
            }

            if (method.getName().equals("getInteger")) {
                Class<?> queryType = method.getParameterTypes()[1];
                if (queryType == VulkanicIntegerQuery.class) {
                    return 111;
                }
                return 222;
            }

            if (method.getName().equals("getBackendType")) {
                return backendType;
            }

            if (method.getName().equals("getBackendVendorName")) {
                return "NVIDIA";
            }

            if (method.getName().equals("getBackendRendererName")) {
                return "GeForce RTX";
            }

            if (method.getName().equals("getBackendVersionName")) {
                return "Vulkan 1.3";
            }

            if (method.getName().equals("getBackendEnabledExtensions")) {
                return java.util.List.of("GL_KHR_debug");
            }

            if (method.getName().equals("getBackendOptionalFeatureNames")) {
                return java.util.List.of("native-vulkan-runtime");
            }

            if (method.getName().equals("precompileRenderPipeline")) {
                return (CompiledRenderPipeline) () -> true;
            }

            return defaultValue(method.getReturnType());
        }

        private Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0.0f;
            }
            if (returnType == double.class) {
                return 0.0d;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }

        private record ReflectedUniform(String name, int arraySize, int legacyType) {
        }
    }
}