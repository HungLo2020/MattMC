package mattmc.client.ui.screens;

import mattmc.client.gui.screens.Screen;
import mattmc.client.settings.OptionsManager;

import mattmc.client.Minecraft;
import mattmc.client.gui.components.Button;
import mattmc.client.ui.components.ButtonRenderer;

/** Game options submenu screen. */
public final class GameScreen extends AbstractMenuScreen {

    public GameScreen(Minecraft game) {
        super(game);
        recomputeLayout();
    }

    @Override
    protected void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.18f;

        int totalButtonsH = 4 * buttonHeight + 3 * buttonGap;
        buttonsStartY = (int)(h / 2f - totalButtonsH / 2f);

        int x = (w - buttonWidth) / 2;
        buttons.clear();

        // Game-related buttons
        buttons.add(new Button(getFpsCapButtonLabel(), x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getRenderDistanceButtonLabel(), x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getBlockNameDisplayButtonLabel(), x, buttonsStartY + 2 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Back",     x, buttonsStartY + 3 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }
    
    private String getFpsCapButtonLabel() {
        int fpsCap = mattmc.client.settings.OptionsManager.getFpsCap();
        return "FPS Cap: " + fpsCap + " (-/+)";
    }
    
    private String getRenderDistanceButtonLabel() {
        int renderDistance = mattmc.client.settings.OptionsManager.getRenderDistance();
        return "Render Distance: " + renderDistance + " chunks";
    }
    
    private String getBlockNameDisplayButtonLabel() {
        boolean enabled = mattmc.client.settings.OptionsManager.isShowBlockNameEnabled();
        return "Block Name Display: " + (enabled ? "ON" : "OFF");
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    protected void onClick(String label) {
        if ("Back".equals(label)) {
            game.setScreen(new OptionsScreen(game));
            return;
        }
        if (label.startsWith("FPS Cap:")) {
            // Cycle through common FPS values
            int current = mattmc.client.settings.OptionsManager.getFpsCap();
            int[] commonValues = {30, 60, 75, 120, 144, 165, 240, 360, 999};
            int nextIndex = 0;
            
            // Find the next value in the cycle
            for (int i = 0; i < commonValues.length; i++) {
                if (commonValues[i] > current) {
                    nextIndex = i;
                    break;
                }
            }
            
            // If we're at or past the last value, wrap to the first
            if (current >= commonValues[commonValues.length - 1]) {
                nextIndex = 0;
            }
            
            mattmc.client.settings.OptionsManager.setFpsCap(commonValues[nextIndex]);
            game.window().applyFpsCapSetting();
            game.updateFpsCap();
            recomputeLayout();
            return;
        }
        if (label.startsWith("Render Distance:")) {
            // Cycle through allowed render distance values
            int current = mattmc.client.settings.OptionsManager.getRenderDistance();
            int[] allowedValues = mattmc.client.settings.OptionsManager.ALLOWED_RENDER_DISTANCES;
            
            // Find the current or next higher allowed value
            int nextIndex = 0;
            boolean foundCurrent = false;
            
            for (int i = 0; i < allowedValues.length; i++) {
                if (allowedValues[i] == current) {
                    // Found exact match, use next value
                    nextIndex = (i + 1) % allowedValues.length;
                    foundCurrent = true;
                    break;
                } else if (allowedValues[i] > current) {
                    // Current value is between allowed values, jump to next higher
                    nextIndex = i;
                    foundCurrent = true;
                    break;
                }
            }
            
            // If current is higher than all allowed values, wrap to first
            if (!foundCurrent) {
                nextIndex = 0;
            }
            
            mattmc.client.settings.OptionsManager.setRenderDistance(allowedValues[nextIndex]);
            recomputeLayout();
            return;
        }
        if (label.startsWith("Block Name Display:")) {
            // Toggle block name display
            mattmc.client.settings.OptionsManager.toggleShowBlockName();
            recomputeLayout();
            return;
        }
    }

    @Override
    public void render(double alpha) {
        // Render panorama background with blur based on settings
        boolean blurred = mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled();
        game.panorama().render(window.width(), window.height(), blurred);

        setupOrtho();
        for (var b : buttons) {
            ButtonRenderer.drawButton(b);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
        }
        drawTitle("Game", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("Configure game settings", titleCX, titleCY + 48f, 1.0f, 0xB0C4DE);
    }
}
