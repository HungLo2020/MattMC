package net.minecraft.util.profiling.custom;

import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ProfilerCollectorWrapper to verify hierarchical data collection.
 */
public class ProfilerCollectorWrapperTest {

    @Test
    public void testBasicHierarchy() {
        ProfilerFiller delegate = InactiveProfiler.INSTANCE;
        ProfilerCollectorWrapper wrapper = new ProfilerCollectorWrapper(delegate);
        
        // Simulate some profiler calls
        wrapper.push("root");
        wrapper.push("tick");
        wrapper.push("entities");
        wrapper.pop(); // entities
        wrapper.pop(); // tick
        wrapper.pop(); // root
        
        Map<String, OperationRecord> ops = wrapper.getOperations();
        
        // Should have captured the hierarchical paths
        assertTrue(ops.containsKey("root"), "Should contain root");
        assertTrue(ops.containsKey("root.tick"), "Should contain root.tick");
        assertTrue(ops.containsKey("root.tick.entities"), "Should contain root.tick.entities");
    }
    
    @Test
    public void testMultipleOperations() {
        ProfilerFiller delegate = InactiveProfiler.INSTANCE;
        ProfilerCollectorWrapper wrapper = new ProfilerCollectorWrapper(delegate);
        
        // Simulate tick with multiple operations
        wrapper.push("root");
        wrapper.push("tick");
        wrapper.push("world");
        wrapper.pop();
        
        wrapper.push("entities");
        wrapper.pop();
        
        wrapper.push("blocks");
        wrapper.pop();
        
        wrapper.push("tileEntities");
        wrapper.pop();
        
        wrapper.pop(); // tick
        wrapper.pop(); // root
        
        Map<String, OperationRecord> ops = wrapper.getOperations();
        
        // Should have all paths
        assertEquals(6, ops.size(), "Should have 6 operations");
        assertTrue(ops.containsKey("root.tick.world"));
        assertTrue(ops.containsKey("root.tick.entities"));
        assertTrue(ops.containsKey("root.tick.blocks"));
        assertTrue(ops.containsKey("root.tick.tileEntities"));
    }
    
    @Test
    public void testPopPush() {
        ProfilerFiller delegate = InactiveProfiler.INSTANCE;
        ProfilerCollectorWrapper wrapper = new ProfilerCollectorWrapper(delegate);
        
        wrapper.push("root");
        wrapper.push("first");
        wrapper.popPush("second"); // Should pop first and push second
        wrapper.pop(); // second
        wrapper.pop(); // root
        
        Map<String, OperationRecord> ops = wrapper.getOperations();
        
        assertTrue(ops.containsKey("root.first"));
        assertTrue(ops.containsKey("root.second"));
    }
    
    @Test
    public void testClear() {
        ProfilerFiller delegate = InactiveProfiler.INSTANCE;
        ProfilerCollectorWrapper wrapper = new ProfilerCollectorWrapper(delegate);
        
        wrapper.push("root");
        wrapper.push("test");
        wrapper.pop();
        wrapper.pop();
        
        assertFalse(wrapper.getOperations().isEmpty());
        
        wrapper.clear();
        
        assertTrue(wrapper.getOperations().isEmpty(), "Should be empty after clear");
    }
}
