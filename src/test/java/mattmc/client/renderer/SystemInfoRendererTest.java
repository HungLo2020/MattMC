package mattmc.client.renderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SystemInfoRenderer with Stage 4 backend integration.
 */
public class SystemInfoRendererTest {
    
    private SystemInfoRenderer renderer;
    private MockRenderBackend backend;
    private CommandBuffer capturedCommands;
    
    @BeforeEach
    public void setUp() {
        renderer = new SystemInfoRenderer();
        capturedCommands = new CommandBuffer();
        backend = new MockRenderBackend() {
            @Override
            public void submit(DrawCommand cmd) {
                capturedCommands.add(cmd);
            }
        };
    }
    
    @Test
    public void testSystemInfoRendererCreation() {
        assertNotNull(renderer);
    }
    
    @Test
    public void testSystemInfoLogicBuildsSingleCommand() {
        // System info with 6 lines should produce 1 command
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        String[] systemInfo = {
            "Java: 17.0.1",
            "Memory: 512/1024 MB (50%)",
            "CPU: Intel i7 (8 cores, 25.0%)",
            "Display: 1920x1080",
            "GPU: NVIDIA GeForce GTX 1080",
            "GPU Usage: 45%, VRAM: 2.1/8.0 GB"
        };
        
        logic.buildSystemInfoCommands(1920, 1080, systemInfo, buffer);
        
        // Should create 1 command encoding all lines
        assertEquals(1, buffer.size());
    }
    
    @Test
    public void testSystemInfoCommandsUseUIPass() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        String[] systemInfo = {"Line 1", "Line 2", "Line 3"};
        logic.buildSystemInfoCommands(1920, 1080, systemInfo, buffer);
        
        // Command should use UI render pass
        for (DrawCommand cmd : buffer.getCommands()) {
            assertEquals(RenderPass.UI, cmd.pass);
        }
    }
    
    @Test
    public void testSystemInfoCommandUsesSystemInfoMarker() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        String[] systemInfo = {"Java: 17", "Memory: 512 MB"};
        logic.buildSystemInfoCommands(1920, 1080, systemInfo, buffer);
        
        // Command should use meshId -9 (system info marker)
        DrawCommand cmd = buffer.getCommands().get(0);
        assertEquals(-9, cmd.meshId);
    }
    
    @Test
    public void testSystemInfoHandlesEmptyArray() {
        UIRenderLogic logic = new UIRenderLogic();
        CommandBuffer buffer = new CommandBuffer();
        
        String[] systemInfo = {};
        logic.buildSystemInfoCommands(1920, 1080, systemInfo, buffer);
        
        // Should not create commands for empty array
        assertEquals(0, buffer.size());
    }
    
    /**
     * Mock backend that captures commands for testing.
     */
    private static abstract class MockRenderBackend implements RenderBackend {
        private boolean frameActive = false;
        
        @Override
        public void beginFrame() {
            frameActive = true;
        }
        
        @Override
        public void endFrame() {
            frameActive = false;
        }
    }
}
