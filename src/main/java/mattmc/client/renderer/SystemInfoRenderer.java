package mattmc.client.renderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders system information on the right side of the screen.
 * Shows Java version, memory usage, CPU info, display resolution, GPU info.
 */
public class SystemInfoRenderer {
    
    /**
     * Draw system information on the right side of the screen.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param windowHandle GLFW window handle for display resolution
     */
    public void render(int screenWidth, int screenHeight, long windowHandle) {
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        // Position in top-right corner
        float lineHeight = 20f;
        float scale = 1.5f;
        float y = 10f;
        
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
        
        // Draw each line right-aligned
        UIRenderHelper.drawTextRightAligned(javaVersion, screenWidth - 10f, y, scale, 0xFFFFFF);
        y += lineHeight;
        
        UIRenderHelper.drawTextRightAligned(memoryInfo, screenWidth - 10f, y, scale, 0xFFFFFF);
        y += lineHeight;
        
        UIRenderHelper.drawTextRightAligned(cpuInfo, screenWidth - 10f, y, scale, 0xFFFFFF);
        y += lineHeight;
        
        UIRenderHelper.drawTextRightAligned(displayRes, screenWidth - 10f, y, scale, 0xFFFFFF);
        y += lineHeight;
        
        UIRenderHelper.drawTextRightAligned(gpuName, screenWidth - 10f, y, scale, 0xFFFFFF);
        y += lineHeight;
        
        UIRenderHelper.drawTextRightAligned(gpuUsageInfo, screenWidth - 10f, y, scale, 0xFFFFFF);
        
        UIRenderHelper.restore2DProjection();
    }
}
