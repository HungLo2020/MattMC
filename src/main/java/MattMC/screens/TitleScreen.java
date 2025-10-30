package MattMC.screens;

import MattMC.core.Game;
import MattMC.core.Window;
import MattMC.gfx.CubeMap;
import MattMC.ui.UIButton;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/** Title screen with a time-based, fluid cubemap panorama and game logic at fixed 20 TPS. */
public final class TitleScreen implements Screen {
    // Core
    private final Game game;
    private final Window window;

    // UI
    private final List<UIButton> buttons = new ArrayList<>();
    private final ByteBuffer fontBuffer = BufferUtils.createByteBuffer(16 * 4096);
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;

    // Panorama
    private CubeMap sky;
    private float yawDeg = 0f;   // rotated by real time (deg)
    private float pitchDeg = 5f; // slight upward tilt
    private float yawSpeedDegPerSec = 2.0f; // visual speed, time-based

    // Fixed 20 TPS logic clock
    private static final double TPS = 20.0;
    private static final double TICK_LEN = 1.0 / TPS;
    private double tickAcc = 0.0;

    // Real-time frame clock for visuals & accumulator
    private double lastFrameTimeSec = System.nanoTime() * 1e-9;

    // Layout
    private float titleScale = 3.0f;
    private float subtitleScale = 1.1f;
    private float titleYFrac = 1f / 3f;
    private int buttonWidth = 280, buttonHeight = 42, buttonGap = 12;
    private float titleCX, titleCY, subtitleCX, subtitleCY;
    private int buttonsStartY;

    public TitleScreen(Game game) {
        this.game = game;
        this.window = game.window();

        // Load six faces: panorama1_0.png ... panorama1_5.png
        sky = MattMC.gfx.CubeMap.load("/assets/textures/gui/panorama1_", ".png");

        glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { mouseXWin = x; mouseYWin = y; });
        glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) mouseDown = (action == GLFW_PRESS);
        });

        recomputeLayout();

        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            recomputeLayout();
        });
    }

    private void recomputeLayout() {
        int w = window.width(), h = window.height();

        titleCX = w / 2f;
        titleCY = h * titleYFrac;

        subtitleCX = w / 2f;
        subtitleCY = titleCY + 56f;

        int subtitleH = (int)(STBEasyFont.stb_easy_font_height("A") * subtitleScale);
        int minButtonsTop = (int)(subtitleCY + subtitleH + 24);

        int totalButtonsH = 3 * buttonHeight + 2 * buttonGap;
        int centeredTop = h / 2 - totalButtonsH / 2;

        buttonsStartY = Math.max(minButtonsTop, centeredTop);

        int x = (w - buttonWidth) / 2;
        buttons.clear();
        buttons.add(new UIButton("Singleplayer", x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new UIButton("Options",      x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new UIButton("Quit",         x, buttonsStartY + 2 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }

    @Override
    public void tick() {
        // Frame delta (seconds)
        double now = System.nanoTime() * 1e-9;
        double frameDt = now - lastFrameTimeSec;
        lastFrameTimeSec = now;
        if (frameDt < 0) frameDt = 0;
        if (frameDt > 0.25) frameDt = 0.25; // clamp huge pauses to keep things sane

        // Smooth, time-based panorama rotation (independent of tick/refresh)
        yawDeg += yawSpeedDegPerSec * (float)frameDt;
        if (yawDeg >= 360f) yawDeg -= 360f;
        if (yawDeg < 0f)    yawDeg += 360f;

        // Hover every frame for responsiveness (not tied to TPS)
        float mxFB, myFB;
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1),  fbH  = stack.mallocInt(1);
            glfwGetWindowSize(window.handle(), winW, winH);
            glfwGetFramebufferSize(window.handle(), fbW, fbH);
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            mxFB = (float)mouseXWin * sx;
            myFB = (float)mouseYWin * sy;
        }
        for (var b : buttons) b.setHover(b.contains(mxFB, myFB));

        // Fixed 20 TPS for game logic
        tickAcc += frameDt;
        while (tickAcc >= TICK_LEN) {
            doTick20(mxFB, myFB);
            tickAcc -= TICK_LEN;
        }
    }

    /** Logic that runs exactly at 20 TPS (clicks, timers, etc.). */
    private void doTick20(float mxFB, float myFB) {
        if (mouseDown) {
            for (var b : buttons) {
                if (b.contains(mxFB, myFB)) {
                    onClick(b.label);
                    break;
                }
            }
            mouseDown = false; // debounce
        }
    }

    private void onClick(String label) {
        switch (label) {
            case "Singleplayer" -> game.setScreen(new SingleplayerScreen(game));
            case "Options"      -> System.out.println("→ TODO: options screen");
            case "Quit"         -> {
                glfwSetWindowShouldClose(window.handle(), true);
                game.quit();
            }
        }
    }

    @Override
    public void render(double alpha) {
        // 1) draw rotating cubemap panorama (perspective)
        drawSkybox();

        // 2) switch to orthographic for UI and draw
        setupOrtho();
        for (var b : buttons) drawButton(b);
        drawTitle("MattMC", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("A blocky sandbox by Matt", subtitleCX, subtitleCY, subtitleScale, 0xB0C4DE);
    }

    private void drawSkybox() {
        int w = window.width(), h = window.height();
        float aspect = Math.max(1f, (float)w / Math.max(1, h));

        // Clear and set perspective
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        // Simple perspective via glFrustum
        float fov = 70f, zn = 0.1f, zf = 10f;
        float top = (float)(Math.tan(Math.toRadians(fov * 0.5)) * zn);
        float bottom = -top;
        float right = top * aspect;
        float left = -right;
        glFrustum(left, right, bottom, top, zn, zf);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Camera at origin, rotate the skybox
        glRotatef(pitchDeg, 1f, 0f, 0f);
        glRotatef(yawDeg,   0f, 1f, 0f);

        // Draw a cube of size 2 centered at origin with cubemap lookup
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_CUBE_MAP);
        glBindTexture(GL_TEXTURE_CUBE_MAP, sky.id);
        glColor4f(1f, 1f, 1f, 1f);

        glBegin(GL_QUADS);
        // +X (right)
        glTexCoord3f(+1, -1, -1); glVertex3f(+1, -1, -1);
        glTexCoord3f(+1, -1, +1); glVertex3f(+1, -1, +1);
        glTexCoord3f(+1, +1, +1); glVertex3f(+1, +1, +1);
        glTexCoord3f(+1, +1, -1); glVertex3f(+1, +1, -1);

        // -X (left)
        glTexCoord3f(-1, -1, +1); glVertex3f(-1, -1, +1);
        glTexCoord3f(-1, -1, -1); glVertex3f(-1, -1, -1);
        glTexCoord3f(-1, +1, -1); glVertex3f(-1, +1, -1);
        glTexCoord3f(-1, +1, +1); glVertex3f(-1, +1, +1);

        // +Y (top)
        glTexCoord3f(-1, +1, -1); glVertex3f(-1, +1, -1);
        glTexCoord3f(+1, +1, -1); glVertex3f(+1, +1, -1);
        glTexCoord3f(+1, +1, +1); glVertex3f(+1, +1, +1);
        glTexCoord3f(-1, +1, +1); glVertex3f(-1, +1, +1);

        // -Y (bottom)
        glTexCoord3f(-1, -1, +1); glVertex3f(-1, -1, +1);
        glTexCoord3f(+1, -1, +1); glVertex3f(+1, -1, +1);
        glTexCoord3f(+1, -1, -1); glVertex3f(+1, -1, -1);
        glTexCoord3f(-1, -1, -1); glVertex3f(-1, -1, -1);

        // +Z (front)
        glTexCoord3f(-1, -1, +1); glVertex3f(-1, -1, +1);
        glTexCoord3f(-1, +1, +1); glVertex3f(-1, +1, +1);
        glTexCoord3f(+1, +1, +1); glVertex3f(+1, +1, +1);
        glTexCoord3f(+1, -1, +1); glVertex3f(+1, -1, +1);

        // -Z (back)
        glTexCoord3f(+1, -1, -1); glVertex3f(+1, -1, -1);
        glTexCoord3f(+1, +1, -1); glVertex3f(+1, +1, -1);
        glTexCoord3f(-1, +1, -1); glVertex3f(-1, +1, -1);
        glTexCoord3f(-1, -1, -1); glVertex3f(-1, -1, -1);
        glEnd();

        glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
        glDisable(GL_TEXTURE_CUBE_MAP);
    }

    private void setupOrtho() {
        int w = window.width(), h = window.height();
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void drawButton(UIButton b) {
        int base = b.hover() ? 0x3A5FCD : 0x2E4A9B;
        int edge = b.hover() ? 0x6D89E3 : 0x20356B;

        // Shadow
        setColor(0x000000, 0.35f);
        fillRect(b.x + 2, b.y + 3, b.w, b.h);

        // Body gradient
        glBegin(GL_QUADS);
        setColor(edge, 1f);
        glVertex2f(b.x, b.y);
        glVertex2f(b.x + b.w, b.y);
        setColor(base, 1f);
        glVertex2f(b.x + b.w, b.y + b.h);
        glVertex2f(b.x, b.y + b.h);
        glEnd();

        // Border
        setColor(0x0B1220, 1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(b.x, b.y);
        glVertex2f(b.x + b.w, b.y);
        glVertex2f(b.x + b.w, b.y + b.h);
        glVertex2f(b.x, b.y + b.h);
        glEnd();

        drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
    }

    private void fillRect(int x, int y, int w, int h) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }

    private void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }

    private void drawTitle(String text, float cx, float cy, float scale, int rgb) {
        int tw = STBEasyFont.stb_easy_font_width(text);
        int th = STBEasyFont.stb_easy_font_height(text);
        float x = cx - (tw * scale) / 2f;
        float y = cy - (th * scale) / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawTextCentered(String text, float cx, float cy, float scale, int rgb) {
        int tw = STBEasyFont.stb_easy_font_width(text);
        int th = STBEasyFont.stb_easy_font_height(text);
        float x = cx - (tw * scale) / 2f;
        float y = cy - (th * scale) / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawText(String text, float x, float y, float scale, int rgb) {
        setColor(rgb, 1f);
        fontBuffer.clear();
        int quads = STBEasyFont.stb_easy_font_print(0, 0, text, null, fontBuffer);

        glPushMatrix();
        glTranslatef(x, y, 0f);
        glScalef(scale, scale, 1f);

        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(2, GL_FLOAT, 16, fontBuffer);
        glDrawArrays(GL_QUADS, 0, quads * 4);
        glDisableClientState(GL_VERTEX_ARRAY);

        glPopMatrix();
    }

    @Override
    public void onClose() {
        if (sky != null) { sky.close(); sky = null; }
    }
}
