package mattmc.client.renderer.backend.opengl.gui.screens;

import mattmc.client.gui.screens.AbstractMenuScreen;

import mattmc.client.gui.screens.Screen;
import mattmc.client.settings.OptionsManager;

import mattmc.client.Minecraft;
import mattmc.client.gui.components.Button;
import mattmc.client.renderer.backend.opengl.gui.components.ButtonRenderer;

/** Options menu screen. */
public final class OptionsScreen extends AbstractMenuScreen {

    public OptionsScreen(Minecraft game) {
        super(game);
        recomputeLayout();
    }

    @Override
    protected void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.18f;

        int totalButtonsH = 6 * buttonHeight + 5 * buttonGap;
        buttonsStartY = (int)(h / 2f - totalButtonsH / 2f);

        int x = (w - buttonWidth) / 2;
        buttons.clear();

        // Submenu buttons
        buttons.add(new Button("Keybinds", x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Graphics", x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Game",     x, buttonsStartY + 2 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Skins",    x, buttonsStartY + 3 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Sounds",   x, buttonsStartY + 4 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Back",     x, buttonsStartY + 5 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }

    @Override
    protected void onClick(String label) {
        if ("Back".equals(label)) {
            game.setScreen(new TitleScreen(game));
            return;
        }
        if ("Keybinds".equals(label)) {
            game.setScreen(new ControlsScreen(game));
            return;
        }
        if ("Graphics".equals(label)) {
            game.setScreen(new GraphicsScreen(game));
            return;
        }
        if ("Game".equals(label)) {
            game.setScreen(new GameScreen(game));
            return;
        }
        if ("Skins".equals(label)) {
            game.setScreen(new SkinsScreen(game));
            return;
        }
        if ("Sounds".equals(label)) {
            game.setScreen(new SoundsScreen(game));
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
        drawTitle("Options", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("Configure game settings", titleCX, titleCY + 48f, 1.0f, 0xB0C4DE);
    }
}
