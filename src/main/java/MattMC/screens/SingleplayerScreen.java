package MattMC.screens;

import MattMC.core.Game;
import MattMC.core.Window;
import MattMC.ui.UIButton;
import MattMC.ui.TextRenderer;
import MattMC.world.World;
import MattMC.world.WorldSaveManager;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/* Simple singleplayer menu with buttons to create/load worlds. */
public final class SingleplayerScreen implements Screen {
    private final Game game;
    private final Window window;
    private final List<UIButton> buttons = new ArrayList<>();
    private final List<String> worldList = new ArrayList<>();
    private final List<UIButton> worldButtons = new ArrayList<>();
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;
    private int selectedWorldIndex = -1;

    private float titleScale = 2.5f;
    private float titleCX, titleCY;
    private int buttonWidth = 300, buttonHeight = 44, buttonGap = 12;
    private int worldButtonHeight = 36;
    private int buttonsStartY;

    public SingleplayerScreen(Game game) {
        this.game = game;
        this.window = game.window();

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
        titleCY = h * 0.12f;

        // Load world list
        worldList.clear();
        worldList.addAll(WorldSaveManager.listWorlds());
        
        // World list area (top half)
        int worldListY = (int)(h * 0.25f);
        int worldListHeight = (int)(h * 0.4f);
        int maxVisibleWorlds = Math.max(1, worldListHeight / (worldButtonHeight + 6));
        
        worldButtons.clear();
        int x = (w - buttonWidth) / 2;
        for (int i = 0; i < Math.min(worldList.size(), maxVisibleWorlds); i++) {
            String worldName = worldList.get(i);
            int y = worldListY + i * (worldButtonHeight + 6);
            worldButtons.add(new UIButton(worldName, x, y, buttonWidth, worldButtonHeight));
        }
        
        // Bottom buttons
        buttonsStartY = worldListY + worldListHeight + 20;
        buttons.clear();

        // Centered singleplayer buttons
        buttons.add(new UIButton("Play Selected", x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new UIButton("Create New World", x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new UIButton("Back",         x, buttonsStartY + 2 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }

    @Override
    public void tick() {
        // Update panorama animation
        game.panorama().update();
        
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
        for (var b : worldButtons) b.setHover(b.contains(mxFB, myFB));

        if (mouseDown) {
            // Check world button clicks
            for (int i = 0; i < worldButtons.size(); i++) {
                if (worldButtons.get(i).contains(mxFB, myFB)) {
                    selectedWorldIndex = i;
                    mouseDown = false;
                    return;
                }
            }
            
            // Check main button clicks
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
        if ("Back".equals(label)) {
            game.setScreen(new TitleScreen(game));
            return;
        }
        if ("Create New World".equals(label)) {
            System.out.println("→ Create World clicked");
            game.setScreen(new CreateWorldScreen(game));
            return;
        }
        if ("Play Selected".equals(label)) {
            if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
                loadWorld(worldList.get(selectedWorldIndex));
            } else {
                System.out.println("No world selected");
            }
            return;
        }
    }
    
    private void loadWorld(String worldName) {
        try {
            System.out.println("→ Loading world: " + worldName);
            WorldSaveManager.WorldLoadResult result = WorldSaveManager.loadWorld(worldName);
            
            game.setScreen(new DevplayScreen(game, worldName, result.world,
                result.metadata.playerX, result.metadata.playerY, result.metadata.playerZ,
                result.metadata.playerYaw, result.metadata.playerPitch));
        } catch (Exception e) {
            System.err.println("Failed to load world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void render(double alpha) {
        // Render panorama background with blur based on settings
        boolean blurred = MattMC.util.OptionsManager.isMenuScreenBlurEnabled();
        game.panorama().render(window.width(), window.height(), blurred);

        setupOrtho();
        
        // Draw world list
        for (int i = 0; i < worldButtons.size(); i++) {
            drawWorldButton(worldButtons.get(i), i == selectedWorldIndex);
        }
        
        // Draw main buttons
        for (var b : buttons) drawButton(b);
        
        drawTitle("Singleplayer", titleCX, titleCY, titleScale, 0xFFFFFF);
        
        // Show message if no worlds
        if (worldList.isEmpty()) {
            drawTitle("No saved worlds", titleCX, titleCY + 48f, 1.2f, 0xB0C4DE);
            drawTitle("Create a new world to get started", titleCX, titleCY + 72f, 1.0f, 0x808080);
        } else {
            drawTitle("Select a world", titleCX, titleCY + 48f, 1.0f, 0xB0C4DE);
        }
    }

    private void setupOrtho() {
        int w = window.width(), h = window.height();
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, w, h, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void drawWorldButton(UIButton b, boolean selected) {
        int base = selected ? 0x4A7FED : (b.hover() ? 0x3A5FCD : 0x2E4A9B);
        int edge = selected ? 0x7DA5F3 : (b.hover() ? 0x6D89E3 : 0x20356B);

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

        drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.0f, 0xFFFFFF);
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
        float tw = TextRenderer.getTextWidth(text, scale);
        float th = TextRenderer.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawTextCentered(String text, float cx, float cy, float scale, int rgb) {
        float tw = TextRenderer.getTextWidth(text, scale);
        float th = TextRenderer.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawText(String text, float x, float y, float scale, int rgb) {
        setColor(rgb, 1f);
        TextRenderer.drawText(text, x, y, scale);
    }

    @Override
    public void onOpen() {}
    @Override
    public void onClose() {
        // Panorama is now shared and managed by Game
    }
}