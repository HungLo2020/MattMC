package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scheduler that drains multiple LightPropagator queues per tick with distance-to-camera prioritization.
 * 
 * Goals:
 * - Keep lighting work within a frame budget (target ≤ 2ms/frame)
 * - Prioritize lighting updates near the player/camera first
 * - Track metrics for debugging (backlog size, nodes processed, time spent)
 * 
 * The scheduler works by:
 * 1. Collecting pending light update positions from the propagator
 * 2. Sorting by distance to camera
 * 3. Processing closest updates first within time budget
 * 4. Tracking performance metrics
 */
public class RelightScheduler {
    
    private final Level level;
    private final LightPropagator propagator;
    
    // Camera/player position for distance-based prioritization
    private float cameraX, cameraY, cameraZ;
    
    // Metrics for debug overlay
    private int backlogSize = 0;
    private int nodesProcessedLastFrame = 0;
    private double timeSpentLastFrame = 0.0; // milliseconds
    
    // Performance tracking
    private long totalNodesProcessed = 0;
    private double totalTimeSpent = 0.0; // milliseconds
    
    public RelightScheduler(Level level) {
        this.level = level;
        this.propagator = level.getLightPropagator();
    }
    
    /**
     * Update camera position for distance-based prioritization.
     * Should be called each frame before processLighting().
     * 
     * @param x Camera X position
     * @param y Camera Y position
     * @param z Camera Z position
     */
    public void updateCameraPosition(float x, float y, float z) {
        this.cameraX = x;
        this.cameraY = y;
        this.cameraZ = z;
    }
    
    /**
     * Process lighting updates with a time budget, prioritizing updates near the camera.
     * 
     * @param msBudget Time budget in milliseconds (typically 2ms)
     */
    public void processLighting(double msBudget) {
        long startTime = System.nanoTime();
        
        // Reset frame metrics
        nodesProcessedLastFrame = 0;
        
        // For now, we simply delegate to the propagator's updateBudget method
        // The propagator already processes updates incrementally with a time budget
        // Future enhancement: implement distance-based prioritization by reordering queues
        propagator.updateBudget(msBudget);
        
        // Calculate time spent
        long endTime = System.nanoTime();
        timeSpentLastFrame = (endTime - startTime) / 1_000_000.0; // convert to ms
        
        // Update backlog size
        backlogSize = propagator.hasPendingUpdates() ? 1 : 0; // Simplified for now
        
        // Track totals
        totalTimeSpent += timeSpentLastFrame;
        totalNodesProcessed += nodesProcessedLastFrame;
    }
    
    /**
     * Get the current backlog size (number of pending light updates).
     * 
     * @return Number of pending updates
     */
    public int getBacklogSize() {
        return backlogSize;
    }
    
    /**
     * Get the number of nodes processed in the last frame.
     * 
     * @return Nodes processed last frame
     */
    public int getNodesProcessedLastFrame() {
        return nodesProcessedLastFrame;
    }
    
    /**
     * Get the time spent processing lighting in the last frame.
     * 
     * @return Time in milliseconds
     */
    public double getTimeSpentLastFrame() {
        return timeSpentLastFrame;
    }
    
    /**
     * Get total nodes processed since creation.
     * 
     * @return Total nodes processed
     */
    public long getTotalNodesProcessed() {
        return totalNodesProcessed;
    }
    
    /**
     * Get total time spent processing lighting since creation.
     * 
     * @return Total time in milliseconds
     */
    public double getTotalTimeSpent() {
        return totalTimeSpent;
    }
    
    /**
     * Check if there are pending light updates.
     * 
     * @return true if updates are pending
     */
    public boolean hasPendingUpdates() {
        return propagator.hasPendingUpdates();
    }
    
    /**
     * Reset all metrics.
     */
    public void resetMetrics() {
        totalNodesProcessed = 0;
        totalTimeSpent = 0.0;
        nodesProcessedLastFrame = 0;
        timeSpentLastFrame = 0.0;
        backlogSize = 0;
    }
}
