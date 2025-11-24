package mattmc.client.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SystemInfo utility class.
 */
public class SystemInfoTest {
    
    @Test
    public void testGetJavaVersion() {
        String version = SystemInfo.getJavaVersion();
        assertNotNull(version);
        assertFalse(version.isEmpty());
        // Should contain a number
        assertTrue(version.matches(".*\\d+.*"));
    }
    
    @Test
    public void testGetMemoryInfo() {
        long usedMem = SystemInfo.getUsedMemoryMB();
        long maxMem = SystemInfo.getMaxMemoryMB();
        int percent = SystemInfo.getMemoryUsagePercent();
        
        // Memory values should be positive
        assertTrue(usedMem >= 0);
        assertTrue(maxMem > 0);
        assertTrue(percent >= 0 && percent <= 100);
        
        // Used memory should not exceed max memory
        assertTrue(usedMem <= maxMem);
    }
    
    @Test
    public void testGetCPUInfo() {
        String cpuName = SystemInfo.getCPUName();
        int cores = SystemInfo.getCPUCores();
        double usage = SystemInfo.getCPUUsage();
        
        assertNotNull(cpuName);
        assertFalse(cpuName.isEmpty());
        
        // At least 1 CPU core should be available
        assertTrue(cores >= 1);
        
        // CPU usage can be -1 if not available, or 0-100
        assertTrue(usage == -1 || (usage >= 0 && usage <= 100));
    }
    

}
