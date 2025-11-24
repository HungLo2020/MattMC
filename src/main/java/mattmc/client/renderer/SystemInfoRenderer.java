package mattmc.client.renderer;

import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.util.SystemInfo;
import mattmc.client.renderer.backend.opengl.OpenGLSystemInfo;

/**
 * Backend-agnostic system info renderer.
 * 
 * <p>This class is responsible for determining <em>what</em> to draw (system information)
 * and coordinating with the backend to actually render it. It contains NO OpenGL-specific
 * code and works purely through the {@link RenderBackend} abstraction.
 * 
 * <p><b>Architecture:</b> This is a "coordinator" class that:
 * <ul>
 *   <li>Uses {@link UIRenderLogic} to build draw commands (what to draw)</li>
 *   <li>Submits commands to the {@link RenderBackend} (how to draw)</li>
 *   <li>Delegates projection setup to the backend (backend-agnostic)</li>
 * </ul>
 * 
 * <p><b>Abstraction Layer:</b> This class lives outside the backend/ directory
 * and can be safely used by any code in the application. It depends only on the
 * backend interface, not on any specific implementation.
 */
public class SystemInfoRenderer {
    
    private final UIRenderLogic logic;
    private final CommandBuffer buffer;
    private RenderBackend backend;
    
    /**
     * Create a new system info renderer.
     */
    public SystemInfoRenderer() {
        this.logic = new UIRenderLogic();
        this.buffer = new CommandBuffer();
    }
    
    /**
     * Set the render backend to use for rendering.
     * 
     * @param backend the backend to use (must not be null)
     * @throws IllegalArgumentException if backend is null
     */
    public void setBackend(RenderBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend cannot be null");
        }
        this.backend = backend;
    }
    
    /**
     * Render system information on the right side of the screen.
     * 
     * <p>This method is completely backend-agnostic. It delegates projection setup,
     * builds draw commands, and submits them to the backend for rendering.
     * 
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param windowHandle GLFW window handle for display resolution
     * @throws IllegalStateException if backend has not been set
     */
    public void render(int screenWidth, int screenHeight, long windowHandle) {
        if (backend == null) {
            throw new IllegalStateException("Backend must be set before calling render()");
        }
        
        // Get system information
        String javaVersion = "Java: " + SystemInfo.getJavaVersion();
        long usedMemMB = SystemInfo.getUsedMemoryMB();
        long maxMemMB = SystemInfo.getMaxMemoryMB();
        int memPercent = SystemInfo.getMemoryUsagePercent();
        String memoryInfo = String.format("Memory: %d/%d MB (%d%%)", usedMemMB, maxMemMB, memPercent);
        
        String cpuName = SystemInfo.getCPUName();
        int cpuCores = SystemInfo.getCPUCores();
        double cpuUsage = SystemInfo.getCPUUsage();
        String cpuInfo;
        if (cpuUsage >= 0) {
            cpuInfo = String.format("CPU: %s (%d cores, %.1f%%)", cpuName, cpuCores, cpuUsage);
        } else {
            cpuInfo = String.format("CPU: %s (%d cores)", cpuName, cpuCores);
        }
        
        String displayRes = "Display: " + OpenGLSystemInfo.getDisplayResolution(windowHandle);
        String gpuName = "GPU: " + OpenGLSystemInfo.getGPUName();
        
        int gpuUsage = OpenGLSystemInfo.getGPUUsage();
        String vramUsage = OpenGLSystemInfo.getGPUVRAMUsage();
        String gpuUsageInfo;
        if (gpuUsage >= 0 && !vramUsage.equals("N/A")) {
            gpuUsageInfo = String.format("GPU Usage: %d%%, VRAM: %s", gpuUsage, vramUsage);
        } else if (gpuUsage >= 0) {
            gpuUsageInfo = String.format("GPU Usage: %d%%", gpuUsage);
        } else if (!vramUsage.equals("N/A")) {
            gpuUsageInfo = String.format("VRAM: %s", vramUsage);
        } else {
            gpuUsageInfo = "GPU Usage: N/A";
        }
        
        String[] systemInfo = {javaVersion, memoryInfo, cpuInfo, displayRes, gpuName, gpuUsageInfo};
        
        // Setup 2D projection for UI rendering (delegated to backend)
        backend.setup2DProjection(screenWidth, screenHeight);
        
        // Build draw commands using backend-agnostic logic
        buffer.clear();
        logic.buildSystemInfoCommands(screenWidth, screenHeight, systemInfo, buffer);
        
        // Submit commands to backend with frame management
        backend.beginFrame();
        for (DrawCommand cmd : buffer.getCommands()) {
            backend.submit(cmd);
        }
        backend.endFrame();
        
        // Restore projection (delegated to backend)
        backend.restore2DProjection();
    }
}
