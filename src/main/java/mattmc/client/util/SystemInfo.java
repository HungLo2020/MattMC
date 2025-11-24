package mattmc.client.util;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Utility class for gathering system information for the debug screen.
 */
public class SystemInfo {
    
    private static final Runtime runtime = Runtime.getRuntime();
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    
    /**
     * Get the Java version.
     * @return Java version string
     */
    public static String getJavaVersion() {
        return System.getProperty("java.version");
    }
    
    /**
     * Get the current memory usage in MB.
     * @return Current memory usage in MB
     */
    public static long getUsedMemoryMB() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getUsed() / (1024 * 1024);
    }
    
    /**
     * Get the maximum allowed memory in MB.
     * @return Maximum memory in MB
     */
    public static long getMaxMemoryMB() {
        return runtime.maxMemory() / (1024 * 1024);
    }
    
    /**
     * Get the memory usage percentage.
     * @return Memory usage percentage (0-100)
     */
    public static int getMemoryUsagePercent() {
        long used = getUsedMemoryMB();
        long max = getMaxMemoryMB();
        if (max == 0) return 0;
        return (int) ((used * 100) / max);
    }
    
    /**
     * Get the CPU name.
     * @return CPU name or "Unknown"
     */
    public static String getCPUName() {
        // Try to get CPU name from system properties
        // This is platform-specific and may not always work
        String arch = System.getProperty("os.arch");
        String name = System.getProperty("sun.cpu.isalist", "");
        if (name.isEmpty()) {
            name = arch;
        }
        return name.isEmpty() ? "Unknown" : name;
    }
    
    /**
     * Get the number of available CPU cores.
     * @return Number of CPU cores
     */
    public static int getCPUCores() {
        return runtime.availableProcessors();
    }
    
    /**
     * Get the CPU usage by the JVM process.
     * This is an approximation and may not be 100% accurate.
     * @return CPU usage percentage (0-100)
     */
    public static double getCPUUsage() {
        // Note: Getting accurate CPU usage requires platform-specific code
        // For now, we'll use the system load average as a proxy
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = 
                (com.sun.management.OperatingSystemMXBean) osBean;
            return sunOsBean.getProcessCpuLoad() * 100.0;
        }
        return -1; // Not available
    }
    
    /**
     * Get the display resolution.
     * @param windowHandle GLFW window handle
     * @return Display resolution string (e.g., "1920x1080")
     */
    public static String getDisplayResolution(long windowHandle) {
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowSize(windowHandle, width, height);
        return width[0] + "x" + height[0];
    }
    
    /**
     * Get the graphics card name.
     * @return Graphics card name
     */
    public static String getGPUName() {
        try {
            // Check if we're in a headless environment
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                return "Unknown (Headless)";
            }
            String renderer = GL11.glGetString(GL11.GL_RENDERER);
            return renderer != null ? renderer : "Unknown";
        } catch (Exception | Error e) {
            return "Unknown";
        }
    }
    
    /**
     * Get GPU usage percentage.
     * Note: This is not reliably available through standard Java/LWJGL APIs.
     * @return GPU usage percentage or -1 if not available
     */
    public static int getGPUUsage() {
        // GPU usage is not available through standard Java/LWJGL APIs
        // This would require platform-specific native code or third-party libraries
        return -1;
    }
    
    /**
     * Get GPU VRAM usage.
     * Note: This is not reliably available through standard Java/LWJGL APIs.
     * @return GPU VRAM usage string or "N/A" if not available
     */
    public static String getGPUVRAMUsage() {
        // VRAM usage is not available through standard Java/LWJGL APIs
        // This would require platform-specific native code or extensions
        return "N/A";
    }
}
