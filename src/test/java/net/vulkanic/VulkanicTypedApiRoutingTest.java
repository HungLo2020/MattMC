package net.vulkanic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            this.lastInvocation = new RecordedInvocation(method, args == null ? new Object[0] : args.clone());

            if (method.getName().equals("getInteger")) {
                Class<?> queryType = method.getParameterTypes()[1];
                if (queryType == VulkanicIntegerQuery.class) {
                    return 111;
                }
                return 222;
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
    }
}