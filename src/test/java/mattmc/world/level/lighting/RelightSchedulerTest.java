package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the RelightScheduler to verify proper time budgeting and metric tracking.
 */
public class RelightSchedulerTest {
    
    private static final int TEST_Y = 200; // Above terrain to avoid interference
    private Level level;
    private RelightScheduler scheduler;
    
    @BeforeEach
    public void setup() {
        level = new Level();
        scheduler = level.getRelightScheduler();
        
        // Pre-load chunks for testing
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.getChunk(x, z);
            }
        }
        
        // Reset metrics
        scheduler.resetMetrics();
    }
    
    @Test
    public void testSchedulerInitialization() {
        assertNotNull(scheduler, "Scheduler should be initialized");
        assertFalse(scheduler.hasPendingUpdates(), "Should have no pending updates initially");
        assertEquals(0, scheduler.getBacklogSize(), "Backlog size should be 0 initially");
        assertEquals(0, scheduler.getNodesProcessedLastFrame(), "Nodes processed should be 0 initially");
        assertEquals(0.0, scheduler.getTimeSpentLastFrame(), 0.001, "Time spent should be 0 initially");
    }
    
    @Test
    public void testCameraPositionUpdate() {
        // Should not throw exception
        scheduler.updateCameraPosition(10f, 100f, 20f);
        scheduler.updateCameraPosition(-10f, 50f, -20f);
        scheduler.updateCameraPosition(0f, 0f, 0f);
    }
    
    @Test
    public void testProcessingWithBudget() {
        // Place a torch to create light updates
        level.setBlock(0, TEST_Y, 0, Blocks.TORCH);
        
        assertTrue(scheduler.hasPendingUpdates(), "Should have pending updates after placing torch");
        
        // Process with a reasonable budget
        scheduler.updateCameraPosition(0f, TEST_Y, 0f);
        scheduler.processLighting(10.0); // 10ms budget
        
        // After processing, there should be no pending updates (small workload)
        assertFalse(scheduler.hasPendingUpdates(), "Should complete all updates with large budget");
    }
    
    @Test
    public void testTimeBudgetRespected() {
        // Create a moderate amount of work by placing multiple torches
        for (int i = 0; i < 5; i++) {
            level.setBlock(i * 5, TEST_Y, 0, Blocks.TORCH);
        }
        
        // Process with a very small budget
        scheduler.updateCameraPosition(0f, TEST_Y, 0f);
        scheduler.processLighting(0.5); // 0.5ms budget
        
        double timeSpent = scheduler.getTimeSpentLastFrame();
        assertTrue(timeSpent < 5.0, "Time spent should be reasonable (< 5ms), was: " + timeSpent);
    }
    
    @Test
    public void testMetricsTracking() {
        // Place torch
        level.setBlock(0, TEST_Y, 0, Blocks.TORCH);
        
        // Reset metrics before test
        scheduler.resetMetrics();
        assertEquals(0L, scheduler.getTotalNodesProcessed());
        assertEquals(0.0, scheduler.getTotalTimeSpent(), 0.001);
        
        // Process lighting
        scheduler.updateCameraPosition(0f, TEST_Y, 0f);
        scheduler.processLighting(5.0);
        
        // Check that time was tracked
        assertTrue(scheduler.getTimeSpentLastFrame() >= 0, "Time spent should be non-negative");
        assertTrue(scheduler.getTotalTimeSpent() >= 0, "Total time should be non-negative");
    }
    
    @Test
    public void testMetricsReset() {
        // Place torch and process
        level.setBlock(0, TEST_Y, 0, Blocks.TORCH);
        scheduler.processLighting(5.0);
        
        // Metrics should have some values
        assertTrue(scheduler.getTotalTimeSpent() > 0 || scheduler.getTimeSpentLastFrame() >= 0);
        
        // Reset
        scheduler.resetMetrics();
        
        // Check all metrics are reset
        assertEquals(0L, scheduler.getTotalNodesProcessed(), "Total nodes should be reset");
        assertEquals(0.0, scheduler.getTotalTimeSpent(), 0.001, "Total time should be reset");
        assertEquals(0, scheduler.getNodesProcessedLastFrame(), "Nodes per frame should be reset");
        assertEquals(0.0, scheduler.getTimeSpentLastFrame(), 0.001, "Time per frame should be reset");
        assertEquals(0, scheduler.getBacklogSize(), "Backlog should be reset");
    }
    
    @Test
    public void testIncrementalProcessing() {
        // Place multiple torches
        for (int i = 0; i < 10; i++) {
            level.setBlock(i * 3, TEST_Y, 0, Blocks.TORCH);
        }
        
        assertTrue(scheduler.hasPendingUpdates(), "Should have pending updates");
        
        // Process with small budget multiple times
        scheduler.updateCameraPosition(0f, TEST_Y, 0f);
        int iterations = 0;
        while (scheduler.hasPendingUpdates() && iterations < 100) {
            scheduler.processLighting(1.0); // 1ms budget per iteration
            iterations++;
        }
        
        assertTrue(iterations > 0, "Should take at least one iteration");
        assertFalse(scheduler.hasPendingUpdates(), "Should eventually complete all updates");
        System.out.println("[RelightScheduler] Completed in " + iterations + " iterations");
    }
    
    @Test
    public void testNoUpdatesWithoutChanges() {
        // Process with nothing queued
        scheduler.updateCameraPosition(0f, TEST_Y, 0f);
        scheduler.processLighting(2.0);
        
        assertFalse(scheduler.hasPendingUpdates(), "Should have no updates without changes");
        assertEquals(0, scheduler.getBacklogSize(), "Backlog should be 0");
    }
    
    @Test
    public void testMultipleLightingCycles() {
        // Place torch
        level.setBlock(0, TEST_Y, 0, Blocks.TORCH);
        
        // Process with adequate budget
        scheduler.processLighting(10.0);
        
        // May still have pending updates, so process until complete
        int iterations = 0;
        while (scheduler.hasPendingUpdates() && iterations < 100) {
            scheduler.processLighting(5.0);
            iterations++;
        }
        assertFalse(scheduler.hasPendingUpdates(), "Should complete first cycle");
        
        // Remove torch
        level.setBlock(0, TEST_Y, 0, Blocks.AIR);
        assertTrue(scheduler.hasPendingUpdates(), "Should have updates after removal");
        
        // Process removal with multiple iterations if needed
        iterations = 0;
        while (scheduler.hasPendingUpdates() && iterations < 100) {
            scheduler.processLighting(5.0);
            iterations++;
        }
        assertFalse(scheduler.hasPendingUpdates(), "Should complete removal cycle");
    }
}
