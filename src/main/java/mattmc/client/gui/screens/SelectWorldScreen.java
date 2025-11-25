package mattmc.client.gui.screens;

import mattmc.client.Minecraft;
import mattmc.client.gui.components.Button;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.settings.OptionsManager;
import mattmc.client.util.CoordinateUtils;
import mattmc.world.level.storage.LevelStorageSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple singleplayer menu with buttons to create/load worlds. */
public final class SelectWorldScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(SelectWorldScreen.class);

    private final Minecraft game;
    private final WindowHandle window;
    private final RenderBackend backend;
    private final List<Button> buttons = new ArrayList<>();
    private final List<String> worldList = new ArrayList<>();
    private final List<Button> worldButtons = new ArrayList<>();
    private double mouseXWin, mouseYWin;
    private boolean mouseDown;
    private int selectedWorldIndex = -1;
    private boolean deleteConfirmMode = false;

    private float titleScale = 2.5f;
    private float titleCX, titleCY;
    private int buttonWidth = 300, buttonHeight = 44, buttonGap = 12;
    private int worldButtonHeight = 36;
    private int buttonsStartY;

    public SelectWorldScreen(Minecraft game) {
        this.game = game;
        this.window = game.window();
        this.backend = game.getRenderBackend();

        recomputeLayout();
    }
    
    @Override
    public void onOpen() {
        // Set up callbacks using backend-agnostic interface
        backend.setCursorPosCallback(window.handle(), (x, y) -> { 
            mouseXWin = x; 
            mouseYWin = y; 
        });
        backend.setMouseButtonCallback(window.handle(), (button, action, mods) -> {
            if (button == RenderBackend.MOUSE_BUTTON_LEFT) {
                mouseDown = (action == RenderBackend.ACTION_PRESS);
            }
        });
        backend.setFramebufferSizeCallback(window.handle(), (newW, newH) -> {
            backend.setViewport(0, 0, Math.max(newW, 1), Math.max(newH, 1));
            recomputeLayout();
        });
    }

    private void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.12f;

        // Load world list
        worldList.clear();
        worldList.addAll(LevelStorageSource.listWorlds());
        
        // Level list area (top half)
        int worldListY = (int)(h * 0.25f);
        int worldListHeight = (int)(h * 0.4f);
        int maxVisibleWorlds = Math.max(1, worldListHeight / (worldButtonHeight + 6));
        
        worldButtons.clear();
        int x = (w - buttonWidth) / 2;
        for (int i = 0; i < Math.min(worldList.size(), maxVisibleWorlds); i++) {
            String worldName = worldList.get(i);
            int y = worldListY + i * (worldButtonHeight + 6);
            worldButtons.add(new Button(worldName, x, y, buttonWidth, worldButtonHeight));
        }
        
        // Bottom buttons
        buttonsStartY = worldListY + worldListHeight + 20;
        buttons.clear();

        // Centered singleplayer buttons
        buttons.add(new Button("Play Selected", x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Create New World", x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        String deleteLabel = deleteConfirmMode ? "Confirm Delete?" : "Delete World";
        buttons.add(new Button(deleteLabel, x, buttonsStartY + 2 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Back",         x, buttonsStartY + 3 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }

    @Override
    public void tick() {
        // Panorama rotation is now updated during rendering to prevent jitter
        
        // Convert window coords -> framebuffer coords for accurate hit-testing on HiDPI
        CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
            window.handle(), mouseXWin, mouseYWin
        );
        float mxFB = fbCoords.x;
        float myFB = fbCoords.y;

        for (var b : buttons) b.setHover(b.contains(mxFB, myFB));
        for (var b : worldButtons) b.setHover(b.contains(mxFB, myFB));

        if (mouseDown) {
            // Check world button clicks
            for (int i = 0; i < worldButtons.size(); i++) {
                if (worldButtons.get(i).contains(mxFB, myFB)) {
                    if (selectedWorldIndex != i) {
                        deleteConfirmMode = false;  // Reset confirmation when selecting a different world
                    }
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
            logger.info("→ Create World clicked");
            game.setScreen(new CreateWorldScreen(game));
            return;
        }
        if ("Play Selected".equals(label)) {
            deleteConfirmMode = false;  // Reset confirmation mode
            if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
                loadWorld(worldList.get(selectedWorldIndex));
            } else {
                logger.info("No world selected");
            }
            return;
        }
        if ("Delete World".equals(label)) {
            if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
                // First click: enter confirmation mode
                deleteConfirmMode = true;
                recomputeLayout();
            } else {
                logger.info("No world selected");
            }
            return;
        }
        if ("Confirm Delete?".equals(label)) {
            if (selectedWorldIndex >= 0 && selectedWorldIndex < worldList.size()) {
                // Second click: actually delete the world
                deleteWorld(worldList.get(selectedWorldIndex));
            }
            return;
        }
    }
    
    private void loadWorld(String worldName) {
        try {
            logger.info("→ Loading world: {}", worldName);
            LevelStorageSource.WorldLoadResult result = LevelStorageSource.loadWorld(worldName);
            
            game.setScreen(new DevplayScreen(game, worldName, result.world,
                result.metadata.playerX, result.metadata.playerY, result.metadata.playerZ,
                result.metadata.playerYaw, result.metadata.playerPitch, result.metadata.playerInventory));
        } catch (IOException e) {
            logger.error("Failed to load world: {}", worldName, e);
        }
    }
    
    private void deleteWorld(String worldName) {
        try {
            logger.info("→ Deleting world: {}", worldName);
            LevelStorageSource.deleteWorld(worldName);
            
            // Reset selection, confirmation mode, and refresh the world list
            selectedWorldIndex = -1;
            deleteConfirmMode = false;
            recomputeLayout();
        } catch (IOException e) {
            logger.error("Failed to delete world: {}", worldName, e);
        }
    }

    @Override
    public void render(double alpha) {
        // Render panorama background with blur based on settings
        boolean blurred = OptionsManager.isMenuScreenBlurEnabled();
        game.panorama().render(window.width(), window.height(), blurred);

        backend.setup2DProjection(window.width(), window.height());
        
        // Draw world list
        for (int i = 0; i < worldButtons.size(); i++) {
            Button b = worldButtons.get(i);
            boolean selected = i == selectedWorldIndex;
            backend.drawButton(b, selected);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.0f, 0xFFFFFF);
        }
        
        // Draw main buttons
        for (var b : buttons) {
            backend.drawButton(b);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
        }
        
        drawTitle("Singleplayer", titleCX, titleCY, titleScale, 0xFFFFFF);
        
        // Show message if no worlds
        if (worldList.isEmpty()) {
            drawTitle("No saved worlds", titleCX, titleCY + 48f, 1.2f, 0xB0C4DE);
            drawTitle("Create a new world to get started", titleCX, titleCY + 72f, 1.0f, 0x808080);
        } else {
            drawTitle("Select a world", titleCX, titleCY + 48f, 1.0f, 0xB0C4DE);
        }
    }

    private void drawTitle(String text, float cx, float cy, float scale, int rgb) {
        float tw = backend.getTextWidth(text, scale);
        float th = backend.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawTextCentered(String text, float cx, float cy, float scale, int rgb) {
        float tw = backend.getTextWidth(text, scale);
        float th = backend.getTextHeight(text, scale);
        float x = cx - tw / 2f;
        float y = cy - th / 2f;
        drawText(text, x, y, scale, rgb);
    }

    private void drawText(String text, float x, float y, float scale, int rgb) {
        backend.setColor(rgb, 1f);
        backend.drawText(text, x, y, scale);
    }

    @Override
    public void onClose() {
        // Panorama is now shared and managed by Minecraft
    }
}
