package mattmc.client;

import mattmc.client.settings.OptionsManager;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.renderer.backend.opengl.Window;
import mattmc.client.renderer.backend.opengl.CubeMap;
import mattmc.client.renderer.backend.opengl.PanoramaRenderer;
import mattmc.client.gui.screens.Screen;

public final class Minecraft {
    // ISSUE-017 fix: Tiered sleep strategy for better power efficiency and frame timing
    private static final double LONG_SLEEP_THRESHOLD = 0.010;  // 10ms - use long sleep
    private static final double SHORT_SLEEP_THRESHOLD = 0.001; // 1ms - use short sleep
    private static final double SLEEP_BUFFER = 0.002;          // 2ms - buffer to avoid oversleeping
    
    private final Window window;
    private Screen current;
    private boolean running = true;
    private PanoramaRenderer sharedPanorama;
    private int cachedFpsCap;

    public Minecraft(Window window) { 
        this.window = window;
        // Load shared panorama once
        CubeMap sky = CubeMap.load("/assets/textures/gui/panorama1_", ".png");
        this.sharedPanorama = new PanoramaRenderer(sky);
        // Cache the FPS cap
        this.cachedFpsCap = OptionsManager.getFpsCap();
    }
    
    /**
     * Get the window handle for use by other code.
     * Returns the backend-agnostic WindowHandle interface.
     */
    public WindowHandle window() { return window; }
    public PanoramaRenderer panorama() { return sharedPanorama; }
    
    /**
     * Update the cached FPS cap. Call this when the FPS setting changes.
     */
    public void updateFpsCap() {
        this.cachedFpsCap = OptionsManager.getFpsCap();
    }

    public void setScreen(Screen next) {
        if (current != null) current.onClose();
        current = next;
        if (current != null) current.onOpen();
    }

    public void quit() { running = false; }

    public void run() {
        final double TICK_RATE = 20.0;  // 20 ticks per second (Minecraft standard)
        final double tickTime = 1.0 / TICK_RATE;
        double lastTime = now();
        double tickAccumulator = 0.0;
        double lastRenderTime = lastTime;

        while (running && !window.shouldClose()) {
            double currentTime = now();
            double deltaTime = currentTime - lastTime;
            lastTime = currentTime;
            
            // Clamp delta time to prevent spiral of death
            if (deltaTime > 0.25) deltaTime = 0.25;
            
            tickAccumulator += deltaTime;

            // Fixed tick rate: run game logic at exactly 20 TPS
            while (tickAccumulator >= tickTime) {
                if (current != null) current.tick();
                tickAccumulator -= tickTime;
            }
            
            // Variable render rate: render as fast as possible up to FPS cap
            double targetFrameTime = 1.0 / cachedFpsCap;
            double timeSinceLastRender = currentTime - lastRenderTime;
            
            // Only render if enough time has passed for target FPS
            if (timeSinceLastRender >= targetFrameTime) {
                // Alpha represents how far between ticks we are (for interpolation)
                double alpha = tickAccumulator / tickTime;
                if (current != null) current.render(alpha);
                window.swap();
                lastRenderTime = currentTime;
            } else {
                // ISSUE-017 fix: Tiered sleep strategy for better CPU efficiency
                double remainingTime = targetFrameTime - timeSinceLastRender;
                
                if (remainingTime > LONG_SLEEP_THRESHOLD) {
                    // Long wait (>10ms): sleep for most of the time
                    try {
                        long sleepMs = (long)((remainingTime - SLEEP_BUFFER) * 1000);
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else if (remainingTime > SHORT_SLEEP_THRESHOLD) {
                    // Medium wait (1-10ms): short sleep
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else if (remainingTime > 0) {
                    // Very short wait (<1ms): just yield to avoid busy-wait
                    Thread.yield();
                }
            }
        }
    }

    private static double now() { return System.nanoTime() * 1e-9; }
}
