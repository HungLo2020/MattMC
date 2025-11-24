package mattmc.client.util;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Utility class for gathering system information (CPU, memory, Java runtime).
 * This class contains NO OpenGL dependencies.
 * For GPU and display information, see {@link mattmc.client.renderer.backend.opengl.OpenGLSystemInfo}.
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
    

}
