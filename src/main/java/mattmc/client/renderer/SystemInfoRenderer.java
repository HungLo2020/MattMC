package mattmc.client.renderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders system information on the right side of the screen.
 * Shows Java version, memory usage, CPU info, display resolution, GPU info.
 * 
 * <p>Stage 4 refactor: Now supports backend rendering via UIRenderLogic + RenderBackend.
 */
public class SystemInfoRenderer {
    
    private RenderBackend backend;
    private final UIRenderLogic logic = new UIRenderLogic();
    
    /**
     * Set the render backend to use for rendering.
     * When set, system info will be rendered via the backend.
     * 
     * @param backend the render backend
     */
    public void setBackend(RenderBackend backend) {
        this.backend = backend;
    }
    
    /**
     * Draw system information on the right side of the screen.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param windowHandle GLFW window handle for display resolution
     */
    public void render(int screenWidth, int screenHeight, long windowHandle) {
        // Get system information
        String javaVersion = "Java: " + mattmc.client.util.SystemInfo.getJavaVersion();
        long usedMemMB = mattmc.client.util.SystemInfo.getUsedMemoryMB();
        long maxMemMB = mattmc.client.util.SystemInfo.getMaxMemoryMB();
        int memPercent = mattmc.client.util.SystemInfo.getMemoryUsagePercent();
        String memoryInfo = String.format("Memory: %d/%d MB (%d%%)", usedMemMB, maxMemMB, memPercent);
        
        String cpuName = mattmc.client.util.SystemInfo.getCPUName();
        int cpuCores = mattmc.client.util.SystemInfo.getCPUCores();
        double cpuUsage = mattmc.client.util.SystemInfo.getCPUUsage();
        String cpuInfo;
        if (cpuUsage >= 0) {
            cpuInfo = String.format("CPU: %s (%d cores, %.1f%%)", cpuName, cpuCores, cpuUsage);
        } else {
            cpuInfo = String.format("CPU: %s (%d cores)", cpuName, cpuCores);
        }
        
        String displayRes = "Display: " + mattmc.client.util.SystemInfo.getDisplayResolution(windowHandle);
        String gpuName = "GPU: " + mattmc.client.util.SystemInfo.getGPUName();
        
        int gpuUsage = mattmc.client.util.SystemInfo.getGPUUsage();
        String vramUsage = mattmc.client.util.SystemInfo.getGPUVRAMUsage();
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
        
        if (backend != null) {
            // Use backend rendering
            CommandBuffer buffer = new CommandBuffer();
            logic.buildSystemInfoCommands(screenWidth, screenHeight, systemInfo, buffer);
            
            // Submit to backend with frame management
            backend.beginFrame();
            for (DrawCommand cmd : buffer.getCommands()) {
                backend.submit(cmd);
            }
            backend.endFrame();
        } else {
            // Legacy rendering (fallback)
            renderLegacy(screenWidth, screenHeight, systemInfo);
        }
    }
    
    /**
     * Legacy rendering method (fallback when no backend is set).
     */
    private void renderLegacy(int screenWidth, int screenHeight, String[] systemInfo) {
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        float lineHeight = 20f;
        float scale = 1.5f;
        float y = 10f;
        
        // Draw each line right-aligned
        for (String line : systemInfo) {
            UIRenderHelper.drawTextRightAligned(line, screenWidth - 10f, y, scale, 0xFFFFFF);
            y += lineHeight;
        }
        
        UIRenderHelper.restore2DProjection();
    }
    
    /**
     * Render a single system info line (used by backend).
     * Package-private for backend access.
     */
    static void renderSystemInfoLine(String text, int x, int y, float scale, int color) {
        UIRenderHelper.drawTextRightAligned(text, x, y, scale, color);
    }
}
