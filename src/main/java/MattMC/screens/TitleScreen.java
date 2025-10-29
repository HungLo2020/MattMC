package MattMC.screens;

import MattMC.core.Game;
import MattMC.core.Window;
import MattMC.gfx.Texture;
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
import static org.lwjgl.system.MemoryStack.stackPush;

public final class TitleScreen implements Screen {
    private final Game game;
    private final Window window;
    private final List<UIButton> buttons = new ArrayList<>();
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;

    private final ByteBuffer fontBuffer = BufferUtils.createByteBuffer(16 * 4096);
    private Texture bg;

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

        bg = Texture.load("/assets/textures/gui/panorama1_0.png");

        glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { mouseXWin = x; mouseYWin = y; });
        glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) mouseDown = (action == GLFW_PRESS);
        });

        recomputeLayout();

        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW,1), Math.max(newH,1));
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, newW, newH, 0, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
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
        int centeredTop = h/2 - totalButtonsH/2;

        buttonsStartY = Math.max(minButtonsTop, centeredTop);

        int x = (w - buttonWidth) / 2;
        buttons.clear();
        buttons.add(new UIButton("Singleplayer", x, buttonsStartY + 0*(buttonHeight+buttonGap), buttonWidth, buttonHeight));
        buttons.add(new UIButton("Options",      x, buttonsStartY + 1*(buttonHeight+buttonGap), buttonWidth, buttonHeight));
        buttons.add(new UIButton("Quit",         x, buttonsStartY + 2*(buttonHeight+buttonGap), buttonWidth, buttonHeight));
    }

    @Override
    public void tick() {
        // window -> framebuffer coords (HiDPI safe)
        float mxFB, myFB;
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1),  fbH  = stack.mallocInt(1);
            glfwGetWindowSize(window.handle(), winW, winH);
            glfwGetFramebufferSize(window.handle(), fbW, fbH);
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            mxFB = (float) mouseXWin * sx;
            myFB = (float) mouseYWin * sy;
        }

        for (var b : buttons) {
            boolean hov = b.contains(mxFB, myFB);
            b.setHover(hov);
            if (hov && mouseDown) {
                onClick(b.label);
                mouseDown = false;
            }
        }
    }

    private void onClick(String label) {
        switch (label) {
            case "Singleplayer" -> System.out.println("→ TODO: world select / create world");
            case "Options"      -> System.out.println("→ TODO: options screen");
            case "Quit"         -> {
                glfwSetWindowShouldClose(window.handle(), true);
                game.quit();
            }
        }
    }

    @Override
    public void render(double alpha) {
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        // Background image (cover & center)
        drawBackgroundCover(bg, window.width(), window.height());

        // Buttons first, text last (z-order)
        for (var b : buttons) drawButton(b);

        drawTitle("MattMC", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("A blocky sandbox by Matt", subtitleCX, subtitleCY, subtitleScale, 0xB0C4DE);
    }

    @Override
    public void onClose() {
        if (bg != null) { bg.close(); bg = null; }
    }

    // ------------------- Drawing helpers -------------------

    private void drawBackgroundCover(Texture tex, int screenW, int screenH) {
        float imgW = tex.width, imgH = tex.height;
        float scale = Math.max(screenW / imgW, screenH / imgH); // cover
        float drawW = imgW * scale;
        float drawH = imgH * scale;
        float x = (screenW - drawW) / 2f;
        float y = (screenH - drawH) / 2f;

        glEnable(GL_TEXTURE_2D);
        tex.bind();
        glColor4f(1f, 1f, 1f, 1f);
        glBegin(GL_QUADS);
        // v=1.0 at top, v=0.0 at bottom
        glTexCoord2f(0f, 1f); glVertex2f(x,       y);
        glTexCoord2f(1f, 1f); glVertex2f(x+drawW, y);
        glTexCoord2f(1f, 0f); glVertex2f(x+drawW, y+drawH);
        glTexCoord2f(0f, 0f); glVertex2f(x,       y+drawH);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_TEXTURE_2D);
    }


    private void drawButton(UIButton b) {
        int base = b.hover() ? 0x3A5FCD : 0x2E4A9B;
        int edge = b.hover() ? 0x6D89E3 : 0x20356B;

        // Shadow
        setColor(0x000000, 0.35f);
        fillRect(b.x+2, b.y+3, b.w, b.h);

        // Body gradient
        glBegin(GL_QUADS);
        setColor(edge, 1f);
        glVertex2f(b.x, b.y);
        glVertex2f(b.x+b.w, b.y);
        setColor(base, 1f);
        glVertex2f(b.x+b.w, b.y+b.h);
        glVertex2f(b.x, b.y+b.h);
        glEnd();

        // Border
        setColor(0x0B1220, 1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(b.x, b.y);
        glVertex2f(b.x+b.w, b.y);
        glVertex2f(b.x+b.w, b.y+b.h);
        glVertex2f(b.x, b.y+b.h);
        glEnd();

        drawTextCentered(b.label, b.x + b.w/2f, b.y + b.h/2f, 1.2f, 0xFFFFFF);
    }

    private void fillRect(int x, int y, int w, int h) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x+w, y);
        glVertex2f(x+w, y+h);
        glVertex2f(x, y+h);
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
}
