package net.minecraft.client.gui.screens.shader;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.shader.pack.ShaderPackMetadata;
import net.minecraft.client.renderer.shader.pack.ShaderPackRepository;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Screen for selecting shader packs in-game.
 * Based on Iris ShaderPackScreen implementation.
 */
@Environment(EnvType.CLIENT)
public class ShaderPackSelectionScreen extends Screen {
    private final Screen lastScreen;
    private ShaderPackList shaderList;
    
    public ShaderPackSelectionScreen(Screen lastScreen) {
        super(Component.literal("Shader Packs"));
        this.lastScreen = lastScreen;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Create shader pack list matching Iris dimensions
        this.shaderList = new ShaderPackList(this.minecraft, this.width, this.height, 32, this.height - 58, 0, this.width);
        this.addRenderableWidget(this.shaderList);
        
        // Populate with available shader packs
        ShaderPackRepository repository = this.minecraft.getShaderPackRepository();
        if (repository != null) {
            // Add "None" option for vanilla rendering
            this.shaderList.addEntry(new ShaderPackEntry(null, repository));
            
            // Add all discovered shader packs
            List<ShaderPackMetadata> packs = repository.getAvailablePacks();
            for (ShaderPackMetadata metadata : packs) {
                this.shaderList.addEntry(new ShaderPackEntry(metadata, repository));
            }
            
            // Select currently active pack
            String currentPack = this.minecraft.options.shaderPack;
            if (currentPack != null && !currentPack.isEmpty()) {
                for (ShaderPackEntry entry : this.shaderList.children()) {
                    if (entry.metadata != null && entry.metadata.name().equals(currentPack)) {
                        this.shaderList.setSelected(entry);
                        break;
                    }
                }
            } else {
                // Select "None" by default
                if (!this.shaderList.children().isEmpty()) {
                    this.shaderList.setSelected(this.shaderList.children().get(0));
                }
            }
        }
        
        // Add buttons in Iris style
        int centerX = this.width / 2;
        int bottomY = this.height - 27;
        
        // Apply button
        this.addRenderableWidget(Button.builder(
            Component.literal("Apply"), 
            button -> this.applyShaderPack()
        ).bounds(centerX - 154, bottomY, 100, 20).build());
        
        // Done button
        this.addRenderableWidget(Button.builder(
            CommonComponents.GUI_DONE,
            button -> this.onClose()
        ).bounds(centerX + 4, bottomY, 100, 20).build());
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw title centered
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        
        // Draw subtitle
        guiGraphics.drawCenteredString(this.font, Component.literal("Select a shader pack").withStyle(style -> style.withItalic(true)), this.width / 2, 21, 0xAAAAAA);
    }
    
    private void applyShaderPack() {
        ShaderPackEntry selected = this.shaderList.getSelected();
        if (selected != null) {
            String packName = selected.metadata != null ? selected.metadata.name() : "";
            this.minecraft.options.shaderPack = packName;
            this.minecraft.options.save();
            
            // Load the shader pack
            if (!packName.isEmpty()) {
                this.minecraft.loadShaderPack(packName);
            } else {
                // Disable shaders
                ShaderPackRepository repository = this.minecraft.getShaderPackRepository();
                if (repository != null) {
                    repository.setActivePack(null);
                }
            }
        }
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
    
    /**
     * List widget for shader packs - modeled after Iris ShaderPackSelectionList.
     */
    class ShaderPackList extends ObjectSelectionList<ShaderPackEntry> {
        public ShaderPackList(Minecraft minecraft, int width, int height, int top, int bottom, int left, int right) {
            super(minecraft, width, height, top, 20);
        }
        
        @Override
        public int getRowWidth() {
            return 300;
        }
        
        @Override
        protected int getScrollbarPosition() {
            return this.getRight() - 6;
        }
    }
    
    /**
     * Entry in the shader pack list - modeled after Iris ShaderPackEntry.
     */
    class ShaderPackEntry extends ObjectSelectionList.Entry<ShaderPackEntry> {
        private final ShaderPackMetadata metadata;
        private final ShaderPackRepository repository;
        
        public ShaderPackEntry(ShaderPackMetadata metadata, ShaderPackRepository repository) {
            this.metadata = metadata;
            this.repository = repository;
        }
        
        @Override
        public void render(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovering, float partialTick) {
            String name = metadata != null ? metadata.name() : "None (Vanilla)";
            
            // Draw the shader pack name at the entry's position
            guiGraphics.drawString(
                ShaderPackSelectionScreen.this.font, 
                name, 
                x + 5, 
                y + 5, 
                0xFFFFFF,
                false
            );
            
            // Show if this is the currently active pack
            String currentPack = ShaderPackSelectionScreen.this.minecraft.options.shaderPack;
            boolean isActive = (metadata == null && (currentPack == null || currentPack.isEmpty())) || 
                              (metadata != null && metadata.name().equals(currentPack));
            if (isActive) {
                guiGraphics.drawString(
                    ShaderPackSelectionScreen.this.font, 
                    "[Active]", 
                    x + 5, 
                    y + 13, 
                    0x55FF55,
                    false
                );
            }
        }
        
        @Override
        public Component getNarration() {
            return Component.literal(metadata != null ? metadata.name() : "None");
        }
    }
}
