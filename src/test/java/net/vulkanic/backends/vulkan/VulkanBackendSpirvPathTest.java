package net.vulkanic.backends.vulkan;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanBackendSpirvPathTest {

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
            return "vulkan-test";
        }
    };

    @Test
    public void testCompileShaderBuildsSpirvModuleUsingInjectedCompiler() {
        AtomicReference<String> capturedSource = new AtomicReference<>();

        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) -> {
            capturedSource.set(source.toString());
            return new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x01, 0x02, 0x03, 0x04}, sourceName, "stub");
        });

        int shader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        uploadSource(backend, shader, "#version 450\nvoid main(){}");
        backend.compileShader(TEST_CONTEXT, shader);

        assertEquals(VulkanicAPI.GL_TRUE,
            backend.getShaderParameter(TEST_CONTEXT, shader, VulkanicAPI.GL_COMPILE_STATUS));
        assertEquals("#version 450\nvoid main(){}", capturedSource.get());

        Optional<VulkanicSpirvModule> module = backend.getCompiledSpirvModule(TEST_CONTEXT, shader);
        assertTrue(module.isPresent());
        assertEquals(VulkanicShaderStage.VERTEX, module.get().stage());
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, module.get().spirvBytes());
    }

    @Test
    public void testProgramLinkUsesCompiledSpirvShaders() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) ->
            new VulkanicSpirvModule(stage, entryPoint, new byte[]{0x0A, 0x0B}, sourceName, "stub")
        );

        int vertexShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        int fragmentShader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_FRAGMENT_SHADER);

        uploadSource(backend, vertexShader, "#version 450\nvoid main(){}");
        uploadSource(backend, fragmentShader, "#version 450\nvoid main(){}");

        backend.compileShader(TEST_CONTEXT, vertexShader);
        backend.compileShader(TEST_CONTEXT, fragmentShader);

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, vertexShader);
        backend.attachShader(TEST_CONTEXT, program, fragmentShader);
        backend.linkProgram(TEST_CONTEXT, program);

        assertEquals(VulkanicAPI.GL_TRUE,
            backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_LINK_STATUS));
        assertEquals("", backend.getProgramInfoLog(TEST_CONTEXT, program));
    }

    @Test
    public void testCompileFailureSurfacesThroughShaderAndProgramStatus() {
        VulkanBackend backend = new VulkanBackend((stage, source, sourceName, entryPoint) -> {
            throw new IllegalStateException("forced compile failure");
        });

        int shader = backend.createShader(TEST_CONTEXT, VulkanicAPI.GL_VERTEX_SHADER);
        uploadSource(backend, shader, "#version 450\nvoid main(){}");
        backend.compileShader(TEST_CONTEXT, shader);

        assertEquals(VulkanicAPI.GL_FALSE,
            backend.getShaderParameter(TEST_CONTEXT, shader, VulkanicAPI.GL_COMPILE_STATUS));
        assertTrue(backend.getShaderInfoLog(TEST_CONTEXT, shader).contains("forced compile failure"));
        assertFalse(backend.getCompiledSpirvModule(TEST_CONTEXT, shader).isPresent());

        int program = backend.createShaderProgram(TEST_CONTEXT);
        backend.attachShader(TEST_CONTEXT, program, shader);
        backend.linkProgram(TEST_CONTEXT, program);

        assertEquals(VulkanicAPI.GL_FALSE,
            backend.getProgramParameter(TEST_CONTEXT, program, VulkanicAPI.GL_LINK_STATUS));
        assertTrue(backend.getProgramInfoLog(TEST_CONTEXT, program).contains("failed compilation"));
    }

    private static void uploadSource(VulkanBackend backend, int shader, String source) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer sourceBuffer = stack.UTF8(source);
            PointerBuffer pointers = stack.mallocPointer(1);
            pointers.put(0, MemoryUtil.memAddress(sourceBuffer));
            backend.uploadShaderSource(TEST_CONTEXT, shader, pointers.address0(), 1, 0L);
        }
    }
}
