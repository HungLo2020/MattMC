package mattmc.client.gui.screens;

import mattmc.client.MattMC;
import mattmc.client.gui.components.Button;

/** Graphics options submenu screen. */
public final class GraphicsScreen extends AbstractMenuScreen {

    public GraphicsScreen(MattMC game) {
        super(game);
        recomputeLayout();
    }

    @Override
    protected void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.18f;

        int totalButtonsH = 8 * buttonHeight + 7 * buttonGap;
        buttonsStartY = (int)(h / 2f - totalButtonsH / 2f);

        int x = (w - buttonWidth) / 2;
        buttons.clear();

        // Graphics-related buttons
        buttons.add(new Button(getResolutionButtonLabel(), x, buttonsStartY + 0 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getFullscreenButtonLabel(), x, buttonsStartY + 1 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getMenuBlurButtonLabel(), x, buttonsStartY + 2 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getTitleBlurButtonLabel(), x, buttonsStartY + 3 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getMipmapButtonLabel(), x, buttonsStartY + 4 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getAnisotropicButtonLabel(), x, buttonsStartY + 5 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button(getAntiAliasingButtonLabel(), x, buttonsStartY + 6 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
        buttons.add(new Button("Back",     x, buttonsStartY + 7 * (buttonHeight + buttonGap), buttonWidth, buttonHeight));
    }
    
    private String getResolutionButtonLabel() {
        String currentRes = mattmc.client.settings.OptionsManager.getResolutionString();
        return "Resolution: " + currentRes;
    }
    
    private String getFullscreenButtonLabel() {
        return "Fullscreen: " + (mattmc.client.settings.OptionsManager.isFullscreenEnabled() ? "ON" : "OFF");
    }
    
    private String getMenuBlurButtonLabel() {
        return "Menu Blur: " + (mattmc.client.settings.OptionsManager.isMenuScreenBlurEnabled() ? "ON" : "OFF");
    }
    
    private String getTitleBlurButtonLabel() {
        return "Title Blur: " + (mattmc.client.settings.OptionsManager.isTitleScreenBlurEnabled() ? "ON" : "OFF");
    }
    
    private String getMipmapButtonLabel() {
        int level = mattmc.client.settings.OptionsManager.getMipmapLevel();
        if (level == 0) {
            return "Mipmaps: OFF";
        }
        return "Mipmaps: " + level;
    }
    
    private String getAnisotropicButtonLabel() {
        int level = mattmc.client.settings.OptionsManager.getAnisotropicFiltering();
        if (level == 0) {
            return "Anisotropic Filtering: OFF";
        }
        return "Anisotropic Filtering: " + level + "x";
    }
    
    private String getAntiAliasingButtonLabel() {
        int samples = mattmc.client.settings.OptionsManager.getAntiAliasingSamples();
        if (samples == 0) {
            return "Anti-Aliasing: OFF";
        }
        return "Anti-Aliasing: " + samples + "x (restart)";
    }

    @Override
    protected void onClick(String label) {
        if ("Back".equals(label)) {
            game.setScreen(new OptionsScreen(game));
            return;
        }
        if (label.startsWith("Resolution:")) {
            // Cycle through supported resolutions
            String current = mattmc.client.settings.OptionsManager.getResolutionString();
            String[] supportedResolutions = {"1280x720", "1600x900", "1920x1080"};
            int currentIndex = -1;
            
            // Find current resolution index
            for (int i = 0; i < supportedResolutions.length; i++) {
                if (supportedResolutions[i].equals(current)) {
                    currentIndex = i;
                    break;
                }
            }
            
            // Move to next resolution (wrap around)
            int nextIndex = (currentIndex == -1) ? 0 : (currentIndex + 1) % supportedResolutions.length;
            String nextRes = supportedResolutions[nextIndex];
            String[] parts = nextRes.split("x");
            
            // Validate the resolution format before parsing
            if (parts.length == 2) {
                try {
                    int newWidth = Integer.parseInt(parts[0]);
                    int newHeight = Integer.parseInt(parts[1]);
                    
                    // Update settings and apply new resolution
                    mattmc.client.settings.OptionsManager.setResolution(newWidth, newHeight);
                    window.setSize(newWidth, newHeight);
                } catch (NumberFormatException e) {
                    // Invalid resolution format in supportedResolutions - should not happen with valid config
                }
            }
            recomputeLayout();
            return;
        }
        if (label.startsWith("Fullscreen:")) {
            mattmc.client.settings.OptionsManager.toggleFullscreen();
            window.setFullscreen(mattmc.client.settings.OptionsManager.isFullscreenEnabled());
            recomputeLayout();
            return;
        }
        if (label.startsWith("Menu Blur:")) {
            mattmc.client.settings.OptionsManager.toggleMenuScreenBlur();
            recomputeLayout();
            return;
        }
        if (label.startsWith("Title Blur:")) {
            mattmc.client.settings.OptionsManager.toggleTitleScreenBlur();
            recomputeLayout();
            return;
        }
        if (label.startsWith("Mipmaps:")) {
            // Cycle through mipmap levels: off, 1, 2, 3, 4
            int current = mattmc.client.settings.OptionsManager.getMipmapLevel();
            int[] allowedValues = mattmc.client.settings.OptionsManager.ALLOWED_MIPMAP_LEVELS;
            
            // Find next value in the cycle
            int nextIndex = 0;
            for (int i = 0; i < allowedValues.length; i++) {
                if (allowedValues[i] == current) {
                    nextIndex = (i + 1) % allowedValues.length;
                    break;
                }
            }
            
            mattmc.client.settings.OptionsManager.setMipmapLevel(allowedValues[nextIndex]);
            recomputeLayout();
            return;
        }
        if (label.startsWith("Anisotropic Filtering:")) {
            // Cycle through anisotropic filtering levels: off, 2x, 4x, 8x, 16x
            int current = mattmc.client.settings.OptionsManager.getAnisotropicFiltering();
            int[] allowedValues = mattmc.client.settings.OptionsManager.ALLOWED_ANISOTROPIC_LEVELS;
            
            // Find next value in the cycle
            int nextIndex = 0;
            for (int i = 0; i < allowedValues.length; i++) {
                if (allowedValues[i] == current) {
                    nextIndex = (i + 1) % allowedValues.length;
                    break;
                }
            }
            
            mattmc.client.settings.OptionsManager.setAnisotropicFiltering(allowedValues[nextIndex]);
            recomputeLayout();
            return;
        }
        if (label.startsWith("Anti-Aliasing:")) {
            // Cycle through anti-aliasing levels: off, 2x, 4x, 8x, 16x
            int current = mattmc.client.settings.OptionsManager.getAntiAliasingSamples();
            int[] allowedValues = mattmc.client.settings.OptionsManager.ALLOWED_AA_SAMPLES;
            
            // Find next value in the cycle
            int nextIndex = 0;
            for (int i = 0; i < allowedValues.length; i++) {
                if (allowedValues[i] == current) {
                    nextIndex = (i + 1) % allowedValues.length;
                    break;
                }
            }
            
            mattmc.client.settings.OptionsManager.setAntiAliasingSamples(allowedValues[nextIndex]);
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
            backend.drawButton(b);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
        }
        drawTitle("Graphics", titleCX, titleCY, titleScale, 0xFFFFFF);
        drawTitle("Configure graphics settings", titleCX, titleCY + 48f, 1.0f, 0xB0C4DE);
    }
}
