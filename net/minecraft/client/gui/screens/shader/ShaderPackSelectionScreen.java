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
 * Displays all available shader packs and allows the user to activate one.
 */
@Environment(EnvType.CLIENT)
public class ShaderPackSelectionScreen extends Screen {
    private final Screen lastScreen;
    private ShaderPackList shaderList;
    private Button selectButton;
    private Button doneButton;
    
    public ShaderPackSelectionScreen(Screen lastScreen) {
        super(Component.literal("Shader Packs"));
        this.lastScreen = lastScreen;
    }
    
    @Override
    protected void init() {
        // Create shader pack list
        this.shaderList = new ShaderPackList(this.minecraft, this.width, this.height, 32, 64);
        this.addWidget(this.shaderList);
        
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
        
        // Add "Select" button
        this.selectButton = this.addRenderableWidget(Button.builder(
            Component.literal("Select Shader Pack"), 
            button -> this.selectShaderPack()
        ).bounds(this.width / 2 - 154, this.height - 52, 150, 20).build());
        
        // Add "Done" button
        this.doneButton = this.addRenderableWidget(Button.builder(
            CommonComponents.GUI_DONE,
            button -> this.onClose()
        ).bounds(this.width / 2 + 4, this.height - 52, 150, 20).build());
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.shaderList.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 16777215);
        
        // Draw description of selected pack
        ShaderPackEntry selected = this.shaderList.getSelected();
        if (selected != null && selected.metadata != null) {
            String desc = selected.metadata.description();
            guiGraphics.drawCenteredString(this.font, desc, this.width / 2, this.height - 32, 8421504);
        } else if (selected != null && selected.metadata == null) {
            guiGraphics.drawCenteredString(this.font, "No shader pack (vanilla rendering)", this.width / 2, this.height - 32, 8421504);
        }
    }
    
    private void selectShaderPack() {
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
            
            this.onClose();
        }
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
    
    /**
     * List widget for shader packs.
     */
    class ShaderPackList extends ObjectSelectionList<ShaderPackEntry> {
        public ShaderPackList(Minecraft minecraft, int width, int height, int y, int bottom) {
            super(minecraft, width, height - bottom - y, y, 36);
        }
        
        public int addEntry(ShaderPackEntry entry) {
            return super.addEntry(entry);
        }
        
        @Override
        public int getRowWidth() {
            return 300;
        }
    }
    
    /**
     * Entry in the shader pack list.
     */
    class ShaderPackEntry extends ObjectSelectionList.Entry<ShaderPackEntry> {
        private final ShaderPackMetadata metadata;
        private final ShaderPackRepository repository;
        
        public ShaderPackEntry(ShaderPackMetadata metadata, ShaderPackRepository repository) {
            this.metadata = metadata;
            this.repository = repository;
        }
        
        @Override
        public void renderContent(GuiGraphics guiGraphics, int index, int top, boolean isHovered, float partialTick) {
            String name = metadata != null ? metadata.name() : "None (Vanilla)";
            int x = this.getContentX();
            int y = this.getContentY();
            guiGraphics.drawString(ShaderPackSelectionScreen.this.font, name, x, y + 2, 16777215);
            
            // Show if this is the currently active pack
            String currentPack = ShaderPackSelectionScreen.this.minecraft.options.shaderPack;
            boolean isActive = (metadata == null && currentPack.isEmpty()) || 
                              (metadata != null && metadata.name().equals(currentPack));
            if (isActive) {
                guiGraphics.drawString(ShaderPackSelectionScreen.this.font, "[Active]", x, y + 14, 0x55FF55);
            }
        }
        
        @Override
        public Component getNarration() {
            return Component.literal(metadata != null ? metadata.name() : "None");
        }
    }
}
