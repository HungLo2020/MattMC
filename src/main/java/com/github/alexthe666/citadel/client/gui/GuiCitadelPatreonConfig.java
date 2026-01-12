package com.github.alexthe666.citadel.client.gui;

import com.github.alexthe666.citadel.client.rewards.CitadelPatreonRenderer;
import com.github.alexthe666.citadel.server.entity.CitadelEntityData;
import com.github.alexthe666.citadel.server.message.PropertiesMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
// Citadel: ExtendedSlider doesn't exist in vanilla - using vanilla AbstractSliderButton
// PacketDistributor is NeoForge - will use Fabric networking later

public class GuiCitadelPatreonConfig extends OptionsSubScreen {

    private CitadelSlider distSlider;
    private CitadelSlider speedSlider;
    private CitadelSlider heightSlider;
    private Button changeButton;
    private float rotateDist;
    private float rotateSpeed;
    private float rotateHeight;
    private String followType;

    public GuiCitadelPatreonConfig(Screen parentScreenIn, Options gameSettingsIn) {
        super(parentScreenIn, gameSettingsIn, Component.translatable("citadel.gui.patreon_customization"));
        CompoundTag tag = CitadelEntityData.getOrCreateCitadelTag(Minecraft.getInstance().player);
        // Citadel: 1.21 API - CompoundTag.getFloat() and getString() now return Optional
        float distance = tag.contains("CitadelRotateDistance") ? tag.getFloat("CitadelRotateDistance").orElse(2F) : 2F;
        float speed = tag.contains("CitadelRotateSpeed") ? tag.getFloat("CitadelRotateSpeed").orElse(1F) : 1F;
        float height = tag.contains("CitadelRotateHeight") ? tag.getFloat("CitadelRotateHeight").orElse(1F) : 1F;
        rotateDist = roundTo(distance, 3);
        rotateSpeed = roundTo(speed, 3);
        rotateHeight = roundTo(height, 3);
        followType = tag.contains("CitadelFollowerType") ? tag.getString("CitadelFollowerType").orElse("citadel") : "citadel";
    }

    private void setSliderValue(int i, float sliderValue) {
        CompoundTag tag = CitadelEntityData.getOrCreateCitadelTag(Minecraft.getInstance().player);
        if (i == 0) {
            rotateDist = roundTo(sliderValue, 3);
            tag.putFloat("CitadelRotateDistance", rotateDist);
        } else if (i == 1) {
            rotateSpeed = roundTo(sliderValue, 3);
            tag.putFloat("CitadelRotateSpeed", rotateSpeed);
        } else {
            rotateHeight = roundTo(sliderValue, 3);
            tag.putFloat("CitadelRotateHeight", rotateHeight);
        }
        CitadelEntityData.setCitadelTag(Minecraft.getInstance().player, tag);
        // TODO: Wire to Fabric networking
        // PacketDistributor.sendToServer(new PropertiesMessage("CitadelPatreonConfig", tag, Minecraft.getInstance().player.getId()));
    }

    public static float roundTo(float value, int places) {
        return value;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 16777215);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    protected void init() {
        super.init();
        int i = this.width / 2;
        int j = this.height / 6;
        Button doneButton = Button.builder(CommonComponents.GUI_DONE, (p_213079_1_) -> this.minecraft.setScreen(this.lastScreen)).size(200, 20).pos(i - 100, j + 120).build();
        this.addRenderableWidget(doneButton);
        
        this.addRenderableWidget(distSlider = new CitadelSlider(i - 150 / 2 - 25, j + 30, 150, 20, Component.translatable("citadel.gui.orbit_dist").append(Component.translatable(": ")), 0.125F, 5F, rotateDist) {
            @Override
            protected void applyValue() {
                GuiCitadelPatreonConfig.this.setSliderValue(0, (float) this.getValue());
            }
        });

        Button reset1Button = Button.builder(Component.translatable("citadel.gui.reset"), (p_213079_1_) -> this.setSliderValue(0, 0.4F)).size(40, 20).pos(i - 150 / 2 + 135, j + 30).build();
        this.addRenderableWidget(reset1Button);

        this.addRenderableWidget(speedSlider = new CitadelSlider(i - 150 / 2 - 25, j + 60, 150, 20, Component.translatable("citadel.gui.orbit_speed").append(Component.translatable(": ")), 0.0F, 5F, rotateSpeed) {
            @Override
            protected void applyValue() {
                GuiCitadelPatreonConfig.this.setSliderValue(1, (float) this.getValue());
            }
        });

        Button reset2Button = Button.builder(Component.translatable("citadel.gui.reset"), (p_213079_1_) -> this.setSliderValue(1, 1F / 5F)).size(40, 20).pos(i - 150 / 2 + 135, j + 60).build();
        this.addRenderableWidget(reset2Button);

        this.addRenderableWidget(heightSlider = new CitadelSlider(i - 150 / 2 - 25, j + 90, 150, 20, Component.translatable("citadel.gui.orbit_height").append(Component.translatable(": ")), 0.0F, 2F, rotateHeight) {
            @Override
            protected void applyValue() {
                GuiCitadelPatreonConfig.this.setSliderValue(2, (float) this.getValue());
            }
        });

        Button reset3Button = Button.builder(Component.translatable("citadel.gui.reset"), (p_213079_1_) -> this.setSliderValue(2, 0.5F)).size(40, 20).pos(i - 150 / 2 + 135, j + 90).build();
        this.addRenderableWidget(reset3Button);

        changeButton = Button.builder(getTypeText(), (p_213079_1_) -> {
            this.followType = CitadelPatreonRenderer.getIdOfNext(followType);
            CompoundTag tag = CitadelEntityData.getOrCreateCitadelTag(Minecraft.getInstance().player);
            tag.putString("CitadelFollowerType", followType);
            CitadelEntityData.setCitadelTag(Minecraft.getInstance().player, tag);
            // TODO: Wire to Fabric networking
            // PacketDistributor.sendToServer(new PropertiesMessage("CitadelPatreonConfig", tag, Minecraft.getInstance().player.getId()));
            changeButton.setMessage(getTypeText());
        }).size(200, 20).pos(i - 100, j).build();
        this.addRenderableWidget(changeButton);
    }

    @Override
    protected void addOptions() {

    }

    private Component getTypeText() {
        return Component.translatable("citadel.gui.follower_type").append(Component.translatable("citadel.follower." + followType));
    }
    
    // Citadel: Simple slider implementation to replace NeoForge ExtendedSlider
    private static abstract class CitadelSlider extends AbstractSliderButton {
        private final Component prefix;
        private final float minValue;
        private final float maxValue;
        
        public CitadelSlider(int x, int y, int width, int height, Component prefix, float minValue, float maxValue, float currentValue) {
            super(x, y, width, height, prefix, (currentValue - minValue) / (maxValue - minValue));
            this.prefix = prefix;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.updateMessage();
        }
        
        public double getValue() {
            return minValue + (maxValue - minValue) * this.value;
        }
        
        @Override
        protected void updateMessage() {
            this.setMessage(prefix.copy().append(String.format("%.2f", getValue())));
        }
    }
}
