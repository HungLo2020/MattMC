package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenGLBackendIdentityCacheTest {

    @Test
    public void testBackendIdentityGettersUseCachedValuesWithoutContext() throws Exception {
        OpenGLBackend backend = new OpenGLBackend();

        setIdentityField(backend, "backendVendorName", "NVIDIA Corporation");
        setIdentityField(backend, "backendRendererName", "NVIDIA GeForce RTX 3080 Ti/PCIe/SSE2");
        setIdentityField(backend, "backendVersionName", "3.3.0 NVIDIA 580.126.09");

        assertEquals("NVIDIA Corporation", backend.getBackendVendorName());
        assertEquals("NVIDIA GeForce RTX 3080 Ti/PCIe/SSE2", backend.getBackendRendererName());
        assertEquals("3.3.0 NVIDIA 580.126.09", backend.getBackendVersionName());
    }

    private static void setIdentityField(OpenGLBackend backend, String fieldName, String value) throws Exception {
        Field field = OpenGLBackend.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(backend, value);
    }
}
