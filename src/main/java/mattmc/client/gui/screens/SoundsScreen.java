package mattmc.client.gui.screens;

import mattmc.client.MattMC;
import mattmc.client.gui.components.Button;
import mattmc.client.gui.components.SliderButton;
import mattmc.client.settings.OptionsManager;
import mattmc.client.sounds.SoundSource;
import mattmc.client.util.CoordinateUtils;

import java.util.ArrayList;
import java.util.List;

/** Sounds options submenu screen with volume sliders. */
public final class SoundsScreen extends AbstractMenuScreen {

    private final List<SliderButton> sliders = new ArrayList<>();
    
    // Sound sources to display sliders for (most relevant ones for user)
    private static final SoundSource[] DISPLAYED_SOURCES = {
        SoundSource.MASTER,
        SoundSource.MUSIC,
        SoundSource.BLOCKS,
        SoundSource.WEATHER,
        SoundSource.HOSTILE,
        SoundSource.NEUTRAL,
        SoundSource.PLAYERS,
        SoundSource.AMBIENT
    };

    public SoundsScreen(MattMC game) {
        super(game);
        recomputeLayout();
    }

    @Override
    protected void recomputeLayout() {
        int w = window.width(), h = window.height();
        titleCX = w / 2f;
        titleCY = h * 0.10f;

        // Layout: 2 columns of sliders
        int sliderWidth = 280;
        int sliderHeight = 36;
        int sliderGap = 10;
        int columnGap = 40;
        
        int numSliders = DISPLAYED_SOURCES.length;
        int numColumns = 2;
        int numRows = (numSliders + numColumns - 1) / numColumns;
        
        int totalWidth = sliderWidth * numColumns + columnGap;
        int totalHeight = numRows * sliderHeight + (numRows - 1) * sliderGap;
        
        int startX = (w - totalWidth) / 2;
        int startY = (int)(h * 0.22f);
        
        sliders.clear();
        
        for (int i = 0; i < numSliders; i++) {
            SoundSource source = DISPLAYED_SOURCES[i];
            int col = i % numColumns;
            int row = i / numColumns;
            
            int x = startX + col * (sliderWidth + columnGap);
            int y = startY + row * (sliderHeight + sliderGap);
            
            float currentVolume = OptionsManager.getSoundSourceVolume(source);
            SliderButton slider = new SliderButton(
                source.getDisplayName(), x, y, sliderWidth, sliderHeight, currentVolume
            );
            
            // Set up callback to update volume in real-time
            final SoundSource soundSource = source;
            slider.setOnValueChange((s, newValue) -> {
                // Update volume without saving (we'll save when leaving the screen)
                OptionsManager.setSoundSourceVolumeNoSave(soundSource, newValue);
            });
            
            sliders.add(slider);
        }

        // Back button at bottom
        int buttonY = startY + totalHeight + 30;
        int buttonX = (w - buttonWidth) / 2;
        
        buttons.clear();
        buttons.add(new Button("Done", buttonX, buttonY, buttonWidth, buttonHeight));
    }

    @Override
    protected void onClick(String label) {
        if ("Done".equals(label)) {
            // Save all volume settings
            OptionsManager.saveOptions();
            game.setScreen(new OptionsScreen(game));
            return;
        }
    }
    
    @Override
    public void tick() {
        // Convert window coords -> framebuffer coords for accurate hit-testing on HiDPI
        CoordinateUtils.Point2D fbCoords = CoordinateUtils.windowToFramebuffer(
            window.handle(), mouseXWin, mouseYWin
        );
        float mxFB = fbCoords.x;
        float myFB = fbCoords.y;

        // Update button hover states
        for (var b : buttons) b.setHover(b.contains(mxFB, myFB));
        
        // Update slider hover and dragging states
        for (SliderButton slider : sliders) {
            slider.setHover(slider.contains(mxFB, myFB));
            if (slider.isDragging()) {
                slider.updateFromMouse(mxFB);
            }
        }

        if (mouseDown) {
            // Check buttons first
            for (var b : buttons) {
                if (b.contains(mxFB, myFB)) {
                    onClick(b.label);
                    mouseDown = false;
                    return;
                }
            }
            
            // Check sliders
            for (SliderButton slider : sliders) {
                if (slider.onMousePressed(mxFB, myFB)) {
                    mouseDown = false;
                    return;
                }
            }
            
            mouseDown = false;
        }
    }
    
    @Override
    public void onOpen() {
        super.onOpen();
        
        // Add mouse release callback for sliders
        backend.setMouseButtonCallback(window.handle(), (button, action, mods) -> {
            if (button == 0) { // Left mouse button
                if (action == 1) { // Press
                    mouseDown = true;
                } else if (action == 0) { // Release
                    // Release all sliders
                    for (SliderButton slider : sliders) {
                        slider.onMouseReleased();
                    }
                }
            }
        });
    }

    @Override
    public void render(double alpha) {
        // Render panorama background with blur based on settings
        boolean blurred = OptionsManager.isMenuScreenBlurEnabled();
        game.panorama().render(window.width(), window.height(), blurred);

        setupOrtho();
        
        // Draw sliders
        for (SliderButton slider : sliders) {
            backend.drawSlider(slider);
            // Draw the slider text (label + value)
            drawTextCentered(slider.getDisplayText(), 
                slider.getX() + slider.getWidth() / 2f, 
                slider.getY() + slider.getHeight() / 2f, 
                1.0f, 0xFFFFFF);
        }
        
        // Draw buttons
        for (var b : buttons) {
            backend.drawButton(b);
            drawTextCentered(b.label, b.x + b.w / 2f, b.y + b.h / 2f, 1.2f, 0xFFFFFF);
        }
        
        drawTitle("Sound Options", titleCX, titleCY, titleScale, 0xFFFFFF);
    }
}
