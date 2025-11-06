package mattmc.client;

import mattmc.client.settings.OptionsManager;
import mattmc.client.renderer.CubeMap;
import mattmc.client.renderer.PanoramaRenderer;
import mattmc.client.gui.screens.Screen;

public final class Minecraft {
    private static final double MIN_SLEEP_TIME = 0.002;  // 2ms - minimum time worth sleeping
    private static final double SLEEP_BUFFER = 0.001;    // 1ms - buffer to avoid oversleeping
    
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
    public Window window() { return window; }
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
        final double tick = 1.0 / 60.0;
        double prev = now(), acc = 0.0;
        double lastFrameTime = prev;

        while (running && !window.shouldClose()) {
            double curr = now();
            double dt   = curr - prev; prev = curr;
            acc += dt;

            while (acc >= tick) {
                if (current != null) current.tick();
                acc -= tick;
            }
            
            // Apply FPS cap using cached value
            double targetFrameTime = 1.0 / cachedFpsCap;
            double timeSinceLastFrame = curr - lastFrameTime;
            
            // Only render if enough time has passed for target FPS
            if (timeSinceLastFrame >= targetFrameTime) {
                if (current != null) current.render(acc / tick);
                window.swap();
                lastFrameTime = curr;
            } else {
                // Calculate precise sleep time to avoid busy-waiting
                double remainingTime = targetFrameTime - timeSinceLastFrame;
                if (remainingTime > MIN_SLEEP_TIME) {
                    try {
                        // Sleep for most of the remaining time, leaving a buffer
                        long sleepMs = (long)((remainingTime - SLEEP_BUFFER) * 1000);
                        if (sleepMs > 0) {
                            Thread.sleep(sleepMs);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private static double now() { return System.nanoTime() * 1e-9; }
}
