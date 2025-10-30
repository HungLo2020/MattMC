package MattMC.screens;

import MattMC.core.Game;
import MattMC.core.Window;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/* Minimal dev play screen that draws a single centered square.
   ESC returns to the singleplayer menu. */
public final class DevplayScreen implements Screen {
    private final Game game;
    private final Window window;
    private float cx, cy;
    private int squareSize = 128;

    public DevplayScreen(Game game) {
        this.game = game;
        this.window = game.window();

        // update layout on resize
        glfwSetFramebufferSizeCallback(window.handle(), (win, w, h) -> {
            glViewport(0, 0, Math.max(w, 1), Math.max(h, 1));
            recomputeLayout();
        });

        // ESC to go back
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                game.setScreen(new SingleplayerScreen(game));
            }
        });

        recomputeLayout();
    }

    private void recomputeLayout() {
        int w = window.width(), h = window.height();
        cx = w / 2f;
        cy = h / 2f;
    }

    @Override
    public void tick() {
        // no game logic yet
    }

    @Override
    public void render(double alpha) {
        // Clear to dark background
        glClearColor(0.06f, 0.08f, 0.10f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        setupOrtho();
        drawCenteredSquare((int)cx, (int)cy, squareSize, 0xFFD166);
    }

    private void setupOrtho() {
        int w = window.width(), h = window.height();
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void drawCenteredSquare(int cx, int cy, int size, int rgb) {
        int half = size / 2;
        int x = cx - half;
        int y = cy - half;

        // filled quad
        setColor(rgb, 1f);
        glBegin(GL_QUADS);
        glVertex2f(x,     y);
        glVertex2f(x + size, y);
        glVertex2f(x + size, y + size);
        glVertex2f(x,     y + size);
        glEnd();

        // border
        setColor(0x0B1220, 1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x,     y);
        glVertex2f(x + size, y);
        glVertex2f(x + size, y + size);
        glVertex2f(x,     y + size);
        glEnd();
    }

    private void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }

    @Override public void onOpen() {}
    @Override public void onClose() {}
}