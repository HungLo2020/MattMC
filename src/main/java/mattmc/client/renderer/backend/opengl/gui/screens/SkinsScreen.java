package mattmc.client.renderer.backend.opengl.gui.screens;

import mattmc.client.gui.screens.AbstractMenuScreen;

import mattmc.client.gui.screens.Screen;
import mattmc.client.settings.OptionsManager;

import mattmc.client.Minecraft;
import mattmc.client.gui.components.Button;
import mattmc.client.renderer.backend.opengl.gui.components.ButtonRenderer;

/** Skins options submenu screen. */
public final class SkinsScreen extends AbstractMenuScreen {

    public SkinsScreen(Minecraft game) {
        super(game);
        recomputeLayout();
    }

    @Override
    protected void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.18f;

        int totalButtonsH = 1 * buttonHeight;
        buttonsStartY = (int)(h / 2f - totalButtonsH / 2f);

        int x = (w - buttonWidth) / 2;
        buttons.clear();

        // Just a back button for now
        buttons.add(new Button("Back", x, buttonsStartY, buttonWidth, buttonHeight));
    }

    @Override
    protected void onClick(String label) {
        if ("Back".equals(label)) {
            game.setScreen(new OptionsScreen(game));
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
        drawTitle("Skins", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("Skin customization (coming soon)", titleCX, titleCY + 48f, 1.0f, 0xB0C4DE);
    }
}
