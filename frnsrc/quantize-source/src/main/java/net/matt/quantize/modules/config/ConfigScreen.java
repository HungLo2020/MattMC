package net.matt.quantize.modules.config;

import net.matt.quantize.Quantize;
import net.matt.quantize.modules.dark.DClientProxy;
import net.matt.quantize.modules.dark.ShaderConfig;
import net.matt.quantize.utils.DropdownWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ConfigScreen extends Screen {

    private static final List<String> PANORAMAS = List.of("panorama0", "panorama1", "panorama2"); // Add your panorama names here
    private int currentPanoramaIndex = PANORAMAS.indexOf(QClientConfig.CLIENT.SELECTED_PANORAMA.get());
    private Button darkModeButton;
    private Button blurButton;

    public ConfigScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        currentPanoramaIndex = PANORAMAS.indexOf(QClientConfig.CLIENT.SELECTED_PANORAMA.get());
        // Add a back button in the top-right corner
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.back"),
                        btn -> this.minecraft.setScreen(null)) // Close the screen
                .pos(10, 10)  // Top-right corner
                .size(100, 20)
                .build());

        // Add a button to toggle dark mode
        darkModeButton = Button.builder(
                        Component.literal("Dark Mode: " + (DClientProxy.SELECTED_SHADER_VALUE != null)),
                        btn -> {
                            try {
                                if (DClientProxy.SELECTED_SHADER_VALUE == null) {
                                    DClientProxy.SELECTED_SHADER_VALUE = DClientProxy.SHADER_VALUES.get(1);
                                    DClientProxy.CONFIG.setSelectedShaderIndex(1);
                                    Quantize.LOGGER.info("Dark mode enabled");
                                } else {
                                    DClientProxy.SELECTED_SHADER_VALUE = null;
                                    DClientProxy.CONFIG.setSelectedShaderIndex(0);
                                    Quantize.LOGGER.info("Dark mode disabled");
                                }
                                ShaderConfig.load();
                                darkModeButton.setMessage(Component.literal("Dark Mode: " + (DClientProxy.SELECTED_SHADER_VALUE != null)));
                            } catch (Exception e) {
                                Quantize.LOGGER.error("Error toggling dark mode", e);
                            }
                        })
                .pos(10, 30)
                .size(100, 20)
                .build();
        this.addRenderableWidget(darkModeButton);

        // Map panorama IDs to descriptive names
        List<String> panoramaNames = List.of("1.20", "1.13", "b1.7.3");
        List<String> panoramaIds = List.of("panorama0", "panorama1", "panorama2");
        // Add a dropdown menu for panoramas below the dark mode button
        this.addRenderableWidget(new DropdownWidget(
                10, 50, 100, 20, panoramaNames, currentPanoramaIndex,
                (dropdown, selectedName) -> {
                    int selectedIndex = panoramaNames.indexOf(selectedName);
                    QClientConfig.CLIENT.SELECTED_PANORAMA.set(panoramaIds.get(selectedIndex));
                    currentPanoramaIndex = selectedIndex;
                    Quantize.LOGGER.info("Selected panorama: {}", panoramaIds.get(selectedIndex));
                }
        ) {
            @Override
            public Component getMessage() {
                return Component.literal("Panorama: " + panoramaNames.get(currentPanoramaIndex));
            }
        });

        // Add a button to toggle panorama blur
        blurButton = Button.builder(
                        Component.literal("Blur: " + QClientConfig.CLIENT.PANORAMA_BLUR.get()),
                        btn -> {
                            boolean currentValue = QClientConfig.CLIENT.PANORAMA_BLUR.get();
                            QClientConfig.CLIENT.PANORAMA_BLUR.set(!currentValue);
                            Quantize.LOGGER.info("Panorama Blur: {}", !currentValue ? "Enabled" : "Disabled");
                            blurButton.setMessage(Component.literal("Blur: " + QClientConfig.CLIENT.PANORAMA_BLUR.get()));
                        })
                .pos(10, 70)
                .size(100, 20)
                .build();
        this.addRenderableWidget(blurButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Render the background
        this.renderBackground(graphics);
        // Render the title
        graphics.drawCenteredString(this.font, this.title.getString(), this.width / 2, 15, 0xFFFFFF);
        // Render buttons and other elements
        super.render(graphics, mouseX, mouseY, delta);
    }
}