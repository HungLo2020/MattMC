package net.matt.quantize.gui.screen;

import net.matt.quantize.utils.SkinResourceHelper;
import net.matt.quantize.modules.config.QClientConfig;
import net.matt.quantize.modules.storage.QFolders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SkinSelectionScreen extends Screen {
    private final List<Button> skinButtons = new ArrayList<>();
    private List<String> skins = List.of();
    private String selected;

    // layout constants
    private static final int BUTTON_W = 200;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 6;
    private static final int LIST_TOP_MARGIN = 40;

    // preview constants
    private static final int PREVIEW_SIZE = 64;     // size of the 2D face preview square
    private static final int PREVIEW_PAD_X = 40;    // horizontal padding between list and preview

    // cache: skin name -> registered texture location
    private static final Map<String, ResourceLocation> SKIN_TEX_CACHE = new HashMap<>();

    public SkinSelectionScreen() {
        super(Component.literal("Skin Selection"));
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.skinButtons.clear();

        // list skin names from <gameDir>/skins (no "default" fallback)
        this.skins = SkinResourceHelper.listSkinBaseNames();

        // load current selection from client config if present
        String cfg = QClientConfig.CLIENT.SELECTED_SKIN.get();
        this.selected = (cfg != null && !cfg.isBlank() && skins.contains(cfg)) ? cfg : null;

        final int totalH = skins.size() * BUTTON_H + Math.max(0, skins.size() - 1) * BUTTON_GAP;
        final int startX = this.width / 2 - BUTTON_W / 2;
        final int startY = Math.max(LIST_TOP_MARGIN, this.height / 2 - totalH / 2);

        // buttons in a vertical stack
        for (int i = 0; i < skins.size(); i++) {
            final String name = skins.get(i);
            int x = startX;
            int y = startY + i * (BUTTON_H + BUTTON_GAP);

            Button btn = Button.builder(buttonLabel(name), b -> {
                this.selected = name;
                QClientConfig.CLIENT.SELECTED_SKIN.set(name);
                refreshButtonLabels();

                // optional chat feedback
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("Selected skin: " + name), false);
                }
            }).pos(x, y).size(BUTTON_W, BUTTON_H).build();

            this.skinButtons.add(this.addRenderableWidget(btn));
        }

        // Back button below the list
        int backW = 80;
        int backX = this.width / 2 - backW / 2;
        int backY = (skins.isEmpty() ? LIST_TOP_MARGIN : (startY + totalH)) + 16;
        this.addRenderableWidget(
                Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().setScreen(null))
                        .pos(backX, backY)
                        .size(backW, BUTTON_H)
                        .build()
        );

        refreshButtonLabels();
    }

    private Component buttonLabel(String name) {
        return name != null && name.equals(this.selected)
                ? Component.literal("✓ " + name)
                : Component.literal(name);
    }

    private void refreshButtonLabels() {
        for (int i = 0; i < skinButtons.size(); i++) {
            skinButtons.get(i).setMessage(buttonLabel(skins.get(i)));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        gfx.drawCenteredString(this.font, this.getTitle(), this.width / 2, 15, 0xFFFFFF);

        if (skins.isEmpty()) {
            gfx.drawCenteredString(this.font,
                    "No skins found in .minecraft/skins",
                    this.width / 2, LIST_TOP_MARGIN, 0xAAAAAA);
        }

        super.render(gfx, mouseX, mouseY, partialTick);

        // Always render a 2D front preview (face) on the right
        renderSkinPreview2D(gfx);
    }

    /**
     * Renders a 2D head (front) preview of the selected skin.
     * Uses classic 64x64 skin layout: base head (8,8)-(8x8) and hat layer (40,8)-(8x8).
     */
    private void renderSkinPreview2D(GuiGraphics gfx) {
        final int totalH = skins.size() * BUTTON_H + Math.max(0, skins.size() - 1) * BUTTON_GAP;
        final int listStartY = Math.max(LIST_TOP_MARGIN, this.height / 2 - totalH / 2);
        final int listCenterY = skins.isEmpty() ? (LIST_TOP_MARGIN + 30) : (listStartY + totalH / 2);

        final int listCenterX = this.width / 2;
        final int previewCenterX = listCenterX + (BUTTON_W / 2) + PREVIEW_PAD_X;
        final int previewCenterY = listCenterY;

        // which skin are we showing?
        String name = (this.selected != null) ? this.selected : QClientConfig.CLIENT.SELECTED_SKIN.get();
        if (name == null || name.isBlank()) return;

        ResourceLocation rl = getOrLoadSkinTexture(name);
        if (rl == null) return;

        // Draw base head
        int drawX = previewCenterX - PREVIEW_SIZE / 2;
        int drawY = previewCenterY - PREVIEW_SIZE / 2;
        int texW = 64, texH = 64;

        // Draw base head (8x8 at 8,8) scaled up
        gfx.blit(rl, drawX, drawY, PREVIEW_SIZE, PREVIEW_SIZE, 8, 8, 8, 8, 64, 64);
        // Draw hat/overlay (8x8 at 40,8) over it
        gfx.blit(rl, drawX, drawY, PREVIEW_SIZE, PREVIEW_SIZE, 40, 8, 8, 8, 64, 64);

        // Optional caption
        gfx.drawCenteredString(this.font, "Preview", previewCenterX, drawY + PREVIEW_SIZE + 6, 0xCCCCCC);
    }

    /**
     * Load (or reuse) a ResourceLocation for <gameDir>/skins/{name}.png by registering a DynamicTexture.
     */
    private ResourceLocation getOrLoadSkinTexture(String name) {
        return SKIN_TEX_CACHE.computeIfAbsent(name, n -> {
            Path file = QFolders.SKINS_DIR.resolve(n + ".png");
            if (!Files.isRegularFile(file)) return null;

            try (var in = Files.newInputStream(file)) {
                NativeImage img = NativeImage.read(in);
                DynamicTexture tex = new DynamicTexture(img); // MC will own the pixels
                ResourceLocation rl = new ResourceLocation("quantize", "skins_gui/" + n);
                Minecraft.getInstance().getTextureManager().register(rl, tex);
                return rl;
            } catch (IOException e) {
                // log once if you like
                return null;
            }
        });
    }
}
