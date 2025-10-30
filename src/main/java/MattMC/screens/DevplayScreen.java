package MattMC.screens;

import MattMC.core.Game;
import MattMC.core.Window;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/* Devplay: draws a rotating cube using 12 triangles (2 per face). ESC returns to Singleplayer. */
public final class DevplayScreen implements Screen {
    private final Game game;
    private final Window window;

    private float angleDeg = 0f;
    private double lastFrameTimeSec = now();
    private int cubeSize = 2; // world units

    public DevplayScreen(Game game) {
        this.game = game;
        this.window = game.window();

        // Resize -> update viewport
        glfwSetFramebufferSizeCallback(window.handle(), (win, w, h) -> {
            glViewport(0, 0, Math.max(w, 1), Math.max(h, 1));
        });

        // ESC to go back
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                game.setScreen(new SingleplayerScreen(game));
            }
        });
    }

    @Override
    public void tick() {
        // simple time-based rotation
        double now = now();
        double dt = now - lastFrameTimeSec;
        lastFrameTimeSec = now;
        if (dt < 0) dt = 0;
        if (dt > 0.5) dt = 0.5;
        angleDeg += 45f * dt; // 45 deg per second
        if (angleDeg >= 360f) angleDeg -= 360f;
    }

    @Override
    public void render(double alpha) {
        int w = window.width(), h = window.height();

        // Clear color + depth
        glClearColor(0.06f, 0.08f, 0.10f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Simple perspective (same style as TitleScreen)
        float aspect = Math.max(1f, (float) w / Math.max(1, h));
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float fov = 60f, zn = 0.1f, zf = 100f;
        float top = (float) (Math.tan(Math.toRadians(fov * 0.5)) * zn);
        float bottom = -top;
        float right = top * aspect;
        float left = -right;
        glFrustum(left, right, bottom, top, zn, zf);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Position camera and rotate cube
        glTranslatef(0f, 0f, -6f);           // move back so cube is visible
        glRotatef(25f, 1f, 0f, 0f);         // tilt a bit for visibility
        glRotatef(angleDeg, 0f, 1f, 0f);    // spin around Y

        glEnable(GL_DEPTH_TEST);
        drawCube(cubeSize);
        glDisable(GL_DEPTH_TEST);
    }

    private void drawCube(float size) {
        float h = size / 2f;

        // 6 faces, 2 triangles each = 12 triangles
        // Colors chosen per face for clarity
        // Front (+Z)
        setColor(0xFFD166, 1f);
        glBegin(GL_TRIANGLES);
        glVertex3f(-h, -h, +h); glVertex3f(+h, -h, +h); glVertex3f(+h, +h, +h);
        glVertex3f(-h, -h, +h); glVertex3f(+h, +h, +h); glVertex3f(-h, +h, +h);
        glEnd();

        // Back (-Z)
        setColor(0x06D6A0, 1f);
        glBegin(GL_TRIANGLES);
        glVertex3f(+h, -h, -h); glVertex3f(-h, -h, -h); glVertex3f(-h, +h, -h);
        glVertex3f(+h, -h, -h); glVertex3f(-h, +h, -h); glVertex3f(+h, +h, -h);
        glEnd();

        // Left (-X)
        setColor(0x118AB2, 1f);
        glBegin(GL_TRIANGLES);
        glVertex3f(-h, -h, -h); glVertex3f(-h, -h, +h); glVertex3f(-h, +h, +h);
        glVertex3f(-h, -h, -h); glVertex3f(-h, +h, +h); glVertex3f(-h, +h, -h);
        glEnd();

        // Right (+X)
        setColor(0x073B4C, 1f);
        glBegin(GL_TRIANGLES);
        glVertex3f(+h, -h, +h); glVertex3f(+h, -h, -h); glVertex3f(+h, +h, -h);
        glVertex3f(+h, -h, +h); glVertex3f(+h, +h, -h); glVertex3f(+h, +h, +h);
        glEnd();

        // Top (+Y)
        setColor(0xFFD700, 1f);
        glBegin(GL_TRIANGLES);
        glVertex3f(-h, +h, +h); glVertex3f(+h, +h, +h); glVertex3f(+h, +h, -h);
        glVertex3f(-h, +h, +h); glVertex3f(+h, +h, -h); glVertex3f(-h, +h, -h);
        glEnd();

        // Bottom (-Y)
        setColor(0xE63946, 1f);
        glBegin(GL_TRIANGLES);
        glVertex3f(-h, -h, -h); glVertex3f(+h, -h, -h); glVertex3f(+h, -h, +h);
        glVertex3f(-h, -h, -h); glVertex3f(+h, -h, +h); glVertex3f(-h, -h, +h);
        glEnd();

        // Optional thin border lines for silhouette
        setColor(0x0B1220, 1f);
        glBegin(GL_LINES);
        // 12 edges of a cube
        glVertex3f(-h, -h, -h); glVertex3f(+h, -h, -h);
        glVertex3f(+h, -h, -h); glVertex3f(+h, -h, +h);
        glVertex3f(+h, -h, +h); glVertex3f(-h, -h, +h);
        glVertex3f(-h, -h, +h); glVertex3f(-h, -h, -h);

        glVertex3f(-h, +h, -h); glVertex3f(+h, +h, -h);
        glVertex3f(+h, +h, -h); glVertex3f(+h, +h, +h);
        glVertex3f(+h, +h, +h); glVertex3f(-h, +h, +h);
        glVertex3f(-h, +h, +h); glVertex3f(-h, +h, -h);

        glVertex3f(-h, -h, -h); glVertex3f(-h, +h, -h);
        glVertex3f(+h, -h, -h); glVertex3f(+h, +h, -h);
        glVertex3f(+h, -h, +h); glVertex3f(+h, +h, +h);
        glVertex3f(-h, -h, +h); glVertex3f(-h, +h, +h);
        glEnd();
    }

    private void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }

    private static double now() { return System.nanoTime() * 1e-9; }

    @Override public void onOpen() {}
    @Override public void onClose() {}
}