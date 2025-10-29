package MattMC.core;

import MattMC.screens.Screen;

public final class Game {
    private final Window window;
    private Screen current;
    private boolean running = true;

    public Game(Window window) { this.window = window; }
    public Window window() { return window; }

    public void setScreen(Screen next) {
        if (current != null) current.onClose();
        current = next;
        if (current != null) current.onOpen();
    }

    public void quit() { running = false; }

    public void run() {
        final double tick = 1.0 / 60.0;
        double prev = now(), acc = 0.0;

        while (running && !window.shouldClose()) {
            double curr = now();
            double dt   = curr - prev; prev = curr;
            acc += dt;

            while (acc >= tick) {
                if (current != null) current.tick();
                acc -= tick;
            }
            if (current != null) current.render(acc / tick);
            window.swap();
        }
    }

    private static double now() { return System.nanoTime() * 1e-9; }
}
