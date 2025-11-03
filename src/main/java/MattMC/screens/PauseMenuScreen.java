package MattMC.screens;

import MattMC.core.Game;
import MattMC.core.Window;
import MattMC.gfx.BlurEffect;
import MattMC.gfx.Framebuffer;
import MattMC.ui.UIButton;
import MattMC.world.World;
import MattMC.world.WorldSaveManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Pause menu overlay shown when player presses ESC in-game.
 * Similar to Minecraft's pause screen.
 */
public final class PauseMenuScreen implements Screen {
    private final Game game;
    private final Window window;
    private final DevplayScreen gameScreen;
    private final List<UIButton> buttons = new ArrayList<>();
    private final ByteBuffer fontBuffer = BufferUtils.createByteBuffer(16 * 4096);
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;
    
    private float titleScale = 2.5f;
    private float titleCX, titleCY;
    private int buttonWidth = 300, buttonHeight = 44, buttonGap = 12;
    private int buttonsStartY;
    
    // Blur effect for background
    private BlurEffect blurEffect;

    public PauseMenuScreen(Game game, DevplayScreen gameScreen) {
        this.game = game;
        this.window = game.window();
        this.gameScreen = gameScreen;

        glfwSetCursorPosCallback(window.handle(), (h, x, y) -> { mouseXWin = x; mouseYWin = y; });
        glfwSetMouseButtonCallback(window.handle(), (h, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) mouseDown = (action == GLFW_PRESS);
        });
        
        // Set up key callback for ESC to unpause
        glfwSetKeyCallback(window.handle(), (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                unpause();
            }
        });

        recomputeLayout();

        glfwSetFramebufferSizeCallback(window.handle(), (win, newW, newH) -> {
            glViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            recomputeLayout();
        });
        
        // Release mouse cursor
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    private void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.30f;

        int totalButtonsH = 3 * buttonHeight + 2 * buttonGap;
        buttonsStartY = (int)(h / 2f - totalButtonsH / 2f);

        int x = (w - buttonWidth) / 2;
        buttons.clear();

        buttons.add(new UIButton("Back to Game",  x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new UIButton("Save and Exit", x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new UIButton("Options",       x, buttonsStartY + 2 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }
    
    private void unpause() {
        // Recapture mouse for FPS controls
        glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        game.setScreen(gameScreen);
    }

    @Override
    public void tick() {
        // Convert window coords -> framebuffer coords for accurate hit-testing on HiDPI
        float mxFB, myFB;
        try (MemoryStack stack = stackPush()) {
            IntBuffer winW = stack.mallocInt(1), winH = stack.mallocInt(1);
            IntBuffer fbW  = stack.mallocInt(1), fbH  = stack.mallocInt(1);
            glfwGetWindowSize(window.handle(), winW, winH);
            glfwGetFramebufferSize(window.handle(), fbW, fbH);
            float sx = fbW.get(0) / Math.max(1f, winW.get(0));
            float sy = fbH.get(0) / Math.max(1f, winH.get(0));
            mxFB = (float) mouseXWin * sx;
            myFB = (float) mouseYWin * sy;
        }

        for (var b : buttons) b.setHover(b.contains(mxFB, myFB));

        if (mouseDown) {
            for (var b : buttons) {
                if (b.contains(mxFB, myFB)) {
                    onClick(b.label);
                    break;
                }
            }
            mouseDown = false;
        }
    }

    private void onClick(String label) {
        if ("Back to Game".equals(label)) {
            unpause();
            return;
        }
        if ("Save and Exit".equals(label)) {
            saveAndExit();
            return;
        }
        if ("Options".equals(label)) {
            // TODO: Show options screen
            System.out.println("Options not yet implemented in pause menu");
            return;
        }
    }
    
    private void saveAndExit() {
        try {
            // Save the world
            gameScreen.saveWorld();
            
            // Return to title screen
            glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            game.setScreen(new TitleScreen(game));
        } catch (Exception e) {
            System.err.println("Failed to save world: " + e.getMessage());
            e.printStackTrace();
            // Still exit even if save failed
            glfwSetInputMode(window.handle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            game.setScreen(new TitleScreen(game));
        }
    }

    @Override
    public void render(double alpha) {
        int w = window.width(), h = window.height();
        
        // First render the game screen behind this overlay
        gameScreen.render(alpha);
        
        // Apply blur if enabled
        if (MattMC.util.OptionsManager.isMenuScreenBlurEnabled()) {
            if (blurEffect == null) {
                blurEffect = new BlurEffect();
            }
            
            // Capture screen to texture
            int captureTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, captureTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            
            // Apply blur
            Framebuffer blurredResult = blurEffect.applyBlur(captureTexture, w, h);
            
            // Render blurred result as background
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, 1, 1, 0, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, blurredResult.getTextureId());
            glColor4f(1f, 1f, 1f, 1f);
            
            glBegin(GL_QUADS);
            glTexCoord2f(0, 1); glVertex2f(0, 0);
            glTexCoord2f(1, 1); glVertex2f(1, 0);
            glTexCoord2f(1, 0); glVertex2f(1, 1);
            glTexCoord2f(0, 0); glVertex2f(0, 1);
            glEnd();
            
            glDisable(GL_TEXTURE_2D);
            glDeleteTextures(captureTexture);
        }
        
        // Draw dark overlay
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        setColor(0x000000, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(w, 0);
        glVertex2f(w, h);
        glVertex2f(0, h);
        glEnd();
        
        // Draw UI
        for (var b : buttons) drawButton(b);
        drawTitle("Game Paused", titleCX, titleCY, titleScale, 0xFFFFFF);
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

        setColor(0x000000, 0.35f);
        fillRect(b.x + 2, b.y + 3, b.w, b.h);

        glBegin(GL_QUADS);
        setColor(edge, 1f);
        glVertex2f(b.x, b.y);
        glVertex2f(b.x + b.w, b.y);
        setColor(base, 1f);
        glVertex2f(b.x + b.w, b.y + b.h);
        glVertex2f(b.x, b.y + b.h);
        glEnd();

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
    public void onOpen() {}
    
    @Override
    public void onClose() {
        if (blurEffect != null) {
            blurEffect.close();
            blurEffect = null;
        }
    }
}
