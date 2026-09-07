package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;
import net.vulkanic.backends.opengl.OpenGLCommandContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real driver tests: run on a desktop, not mocked GL calls or source-string checks. */
@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class OpenGLTextureBindingCoherenceTest {
    private long window;
    private OpenGLBackend backend;
    private final CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
    private int lightmap;
    private int overlay;

    @BeforeEach void createContext() {
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        window = GLFW.glfwCreateWindow(16, 16, "Texture binding regression", 0, 0);
        assertNotEquals(0, window);
        GLFW.glfwMakeContextCurrent(window);
        assertTrue(GL.createCapabilities().GL_ARB_direct_state_access,
                "This regression requires the DSA path used on the affected workstation");
        backend = new OpenGLBackend();
        lightmap = GL11.glGenTextures();
        overlay = GL11.glGenTextures();
        backend.setActiveTextureUnit(ctx, GL13.GL_TEXTURE1);
        backend.bindTexture2D(ctx, overlay); // establish the DSA object's target
        backend.bindTexture2D(ctx, lightmap);
    }

    @AfterEach void destroyContext() {
        if (window != 0) {
            GL11.glDeleteTextures(lightmap);
            GL11.glDeleteTextures(overlay);
            GL.setCapabilities(null);
            GLFW.glfwDestroyWindow(window);
        }
        GLFW.glfwTerminate();
    }

    @Test void overlayDsaUnbindMustNotSuppressNextTerrainLightmapBind() {
        backend.bindTextureUnit(ctx, 1, overlay);
        backend.bindTextureUnit(ctx, 1, 0);
        assertEquals(0, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        backend.bindTexture2D(ctx, lightmap);
        assertEquals(lightmap, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test void genericTargetBindMustNotSuppressNextTerrainLightmapBind() {
        backend.bindTexture(ctx, GL11.GL_TEXTURE_2D, overlay);
        backend.bindTexture2D(ctx, lightmap);
        assertEquals(lightmap, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test void dsaMutationOfInactiveUnitMustInvalidateThatUnitNotActiveUnit() {
        backend.setActiveTextureUnit(ctx, GL13.GL_TEXTURE0);
        backend.bindTexture2D(ctx, overlay);
        backend.bindTextureUnit(ctx, 1, 0);
        assertEquals(GL13.GL_TEXTURE0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        assertEquals(overlay, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        backend.setActiveTextureUnit(ctx, GL13.GL_TEXTURE1);
        backend.bindTexture2D(ctx, lightmap);
        assertEquals(lightmap, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test void managedAllocationMustNotSuppressNextTerrainLightmapBind() {
        try (var texture = backend.createManagedTexture("binding regression", 0,
                VulkanicTextureFormat.RGBA8, 1, 1, 1, 1)) {
            assertEquals(0, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            backend.bindTexture2D(ctx, lightmap);
            assertEquals(lightmap, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        }
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test void non2dDsaBindingMustNotBeMistakenForA2dBinding() {
        int cube = GL11.glGenTextures();
        try {
            backend.bindTexture(ctx, GL13.GL_TEXTURE_CUBE_MAP, cube);
            backend.bindTextureUnit(ctx, 1, cube);
            backend.bindTexture2D(ctx, lightmap);
            assertEquals(lightmap, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            assertEquals(cube, GL11.glGetInteger(GL13.GL_TEXTURE_BINDING_CUBE_MAP));
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GL11.glDeleteTextures(cube);
        }
    }

    @Test void restoredLightmapActuallyProducesLitPixelsAfterOverlayTeardown() {
        var texel = org.lwjgl.BufferUtils.createByteBuffer(4);
        texel.put(new byte[]{(byte)225, (byte)225, (byte)225, (byte)255}).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texel);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        backend.bindTextureUnit(ctx, 1, overlay);
        backend.bindTextureUnit(ctx, 1, 0);
        backend.bindTexture2D(ctx, lightmap);
        int vertex = compile(GL20.GL_VERTEX_SHADER, """
                #version 330 core
                void main() {
                    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                }
                """);
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, """
                #version 330 core
                uniform sampler2D lightmap;
                out vec4 color;
                void main() { color = texelFetch(lightmap, ivec2(0), 0); }
                """);
        int program = GL20.glCreateProgram();
        int vao = GL30.glGenVertexArrays();
        try {
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(program, GL20.GL_LINK_STATUS), GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "lightmap"), 1);
            GL30.glBindVertexArray(vao);
            GL11.glViewport(0, 0, 16, 16);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            var pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(8, 8, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            for (int channel = 0; channel < 3; channel++) {
                assertEquals(225, Byte.toUnsignedInt(pixel.get(channel)), "lit color channel " + channel);
            }
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        } finally {
            GL20.glUseProgram(0);
            GL30.glDeleteVertexArrays(vao);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS), GL20.glGetShaderInfoLog(shader));
        return shader;
    }
}
