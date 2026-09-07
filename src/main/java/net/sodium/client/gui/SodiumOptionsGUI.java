package net.sodium.client.gui;

import net.sodium.client.SodiumClientMod;
import net.sodium.client.console.Console;
import net.sodium.client.console.message.MessageLevel;
import net.sodium.client.gui.options.*;
import net.sodium.client.gui.options.*;
import net.sodium.client.gui.options.control.Control;
import net.sodium.client.gui.options.control.ControlElement;
import net.sodium.client.gui.options.storage.OptionStorage;
import net.sodium.client.gui.prompt.ScreenPrompt;
import net.sodium.client.gui.prompt.ScreenPromptable;
import net.sodium.client.gui.screen.ConfigCorruptedScreen;
import net.sodium.client.gui.widgets.FlatButtonWidget;
import net.sodium.client.util.Dim2i;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

// TODO: Rename in Sodium 0.6
public class SodiumOptionsGUI extends Screen implements ScreenPromptable {
    private final List<OptionPage> pages = new ArrayList<>();

    private final List<ControlElement<?>> controls = new ArrayList<>();

    private final Screen prevScreen;

    private OptionPage currentPage;

    private FlatButtonWidget applyButton, closeButton, undoButton;

    private boolean hasPendingChanges;
    private ControlElement<?> hoveredElement;
    
    // Iris: From MixinSodiumOptionsGUI - shader packs page
    private OptionPage iris$shaderPacks;

    private SodiumOptionsGUI(Screen prevScreen) {
        super(Component.literal("Sodium Renderer Settings"));

        this.prevScreen = prevScreen;

        this.pages.add(SodiumGameOptionPages.general());
        this.pages.add(SodiumGameOptionPages.quality());
        this.pages.add(SodiumGameOptionPages.performance());
        this.pages.add(SodiumGameOptionPages.advanced());
        
        // Iris: From MixinSodiumOptionsGUI - add shader packs page
        if (SodiumGameOptionPages.irisRuntimeOptionsAvailable()) {
            Component shaderPacksTranslated = Component.translatable("options.iris.shaderPackSelection");
            iris$shaderPacks = new OptionPage(shaderPacksTranslated, com.google.common.collect.ImmutableList.of());
            pages.add(iris$shaderPacks);
        }
    }

    public static Screen createScreen(Screen currentScreen) {
        if (SodiumClientMod.options().isReadOnly()) {
            return new ConfigCorruptedScreen(currentScreen, SodiumOptionsGUI::new);
        } else {
            return new SodiumOptionsGUI(currentScreen);
        }
    }

    public void setPage(OptionPage page) {
        // Iris: From MixinSodiumOptionsGUI - intercept shader packs page and open ShaderPackScreen
        if (page == iris$shaderPacks) {
            minecraft.setScreen(new net.irisshaders.iris.gui.screen.ShaderPackScreen(this));
            return;
        }
        
        this.currentPage = page;

        this.rebuildGUI();
    }

    @Override
    protected void init() {
        super.init();

        this.rebuildGUI();
    }

    private void rebuildGUI() {
        this.controls.clear();

        this.clearWidgets();

        if (this.currentPage == null) {
            if (this.pages.isEmpty()) {
                throw new IllegalStateException("No pages are available?!");
            }

            // Just use the first page for now
            this.currentPage = this.pages.get(0);
        }

        this.rebuildGUIPages();
        this.rebuildGUIOptions();

        this.undoButton = new FlatButtonWidget(new Dim2i(this.width - 211, this.height - 30, 65, 20), Component.translatable("sodium.options.buttons.undo"), this::undoChanges);
        this.applyButton = new FlatButtonWidget(new Dim2i(this.width - 142, this.height - 30, 65, 20), Component.translatable("sodium.options.buttons.apply"), this::applyChanges);
        this.closeButton = new FlatButtonWidget(new Dim2i(this.width - 73, this.height - 30, 65, 20), Component.translatable("gui.done"), this::onClose);

        this.addRenderableWidget(this.undoButton);
        this.addRenderableWidget(this.applyButton);
        this.addRenderableWidget(this.closeButton);
    }

    private void rebuildGUIPages() {
        int x = 6;
        int y = 6;

        for (OptionPage page : this.pages) {
            int width = 12 + this.font.width(page.getName());

            FlatButtonWidget button = new FlatButtonWidget(new Dim2i(x, y, width, 18), page.getName(), () -> this.setPage(page));
            button.setSelected(this.currentPage == page);

            x += width + 6;

            this.addRenderableWidget(button);
        }
    }

    private void rebuildGUIOptions() {
        int x = 6;
        int y = 28;

        for (OptionGroup group : this.currentPage.getGroups()) {
            // Add each option's control element
            for (Option<?> option : group.getOptions()) {
                Control<?> control = option.getControl();
                ControlElement<?> element = control.createElement(new Dim2i(x, y, 240, 18));

                this.addRenderableWidget(element);

                this.controls.add(element);

                // Move down to the next option
                y += 18;
            }

            // Add padding beneath each option group
            y += 4;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.updateControls();

        super.render(graphics, mouseX, mouseY, delta);

        if (this.hoveredElement != null) {
            this.renderOptionTooltip(graphics, this.hoveredElement);
        }
    }

    private void updateControls() {
        ControlElement<?> hovered = this.getActiveControls()
                .filter(ControlElement::isHovered)
                .findFirst()
                .orElse(this.getActiveControls() // If there is no hovered element, use the focused element.
                        .filter(ControlElement::isFocused)
                        .findFirst()
                        .orElse(null));

        boolean hasChanges = this.getAllOptions()
                .anyMatch(Option::hasChanged);

        for (OptionPage page : this.pages) {
            for (Option<?> option : page.getOptions()) {
                if (option.hasChanged()) {
                    hasChanges = true;
                }
            }
        }

        this.applyButton.setEnabled(hasChanges);
        this.undoButton.setVisible(hasChanges);
        this.closeButton.setEnabled(!hasChanges);

        this.hasPendingChanges = hasChanges;
        this.hoveredElement = hovered;
    }

    private Stream<Option<?>> getAllOptions() {
        return this.pages.stream()
                .flatMap(s -> s.getOptions().stream());
    }

    private Stream<ControlElement<?>> getActiveControls() {
        return this.controls.stream();
    }

    private void renderOptionTooltip(GuiGraphics graphics, ControlElement<?> element) {
        Dim2i dim = element.getDimensions();

        int textPadding = 3;
        int boxPadding = 3;

        int boxY = dim.y();
        int boxX = dim.getLimitX() + boxPadding;

        int boxWidth = Math.min(200, this.width - boxX - boxPadding);

        Option<?> option = element.getOption();
        var splitWidth = boxWidth - (textPadding * 2);
        List<FormattedCharSequence> tooltip = new ArrayList<>(this.font.split(option.getTooltip(),splitWidth));

        OptionImpact impact = option.getImpact();

        if (impact != null) {
            var impactText = Component.translatable("sodium.options.performance_impact_string",
                    impact.getLocalizedName());
            tooltip.addAll(this.font.split(impactText.withStyle(ChatFormatting.GRAY), splitWidth));
        }

        int boxHeight = (tooltip.size() * 12) + boxPadding;
        int boxYLimit = boxY + boxHeight;
        int boxYCutoff = this.height - 40;

        // If the box is going to be cut off on the Y-axis, move it back up the difference
        if (boxYLimit > boxYCutoff) {
            boxY -= boxYLimit - boxYCutoff;
        }

        graphics.fillGradient(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xE0000000, 0xE0000000);

        for (int i = 0; i < tooltip.size(); i++) {
            graphics.drawString(this.font, tooltip.get(i), boxX + textPadding, boxY + textPadding + (i * 12), 0xFFFFFFFF);
        }
    }

    private void applyChanges() {
        final HashSet<OptionStorage<?>> dirtyStorages = new HashSet<>();
        final EnumSet<OptionFlag> flags = EnumSet.noneOf(OptionFlag.class);

        this.getAllOptions().forEach((option -> {
            if (!option.hasChanged()) {
                return;
            }

            option.applyChanges();

            flags.addAll(option.getFlags());
            dirtyStorages.add(option.getStorage());
        }));

        Minecraft client = Minecraft.getInstance();

        if (client.level != null) {
            if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD)) {
                client.levelRenderer.allChanged();
            } else if (flags.contains(OptionFlag.REQUIRES_RENDERER_UPDATE)) {
                client.levelRenderer.needsUpdate();
            }
        }

        if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
            client.updateMaxMipLevel(client.options.mipmapLevels().get());
            client.delayTextureReload();
        }

        if (flags.contains(OptionFlag.REQUIRES_VIDEOMODE_RELOAD)) {
            client.getWindow().changeFullscreenVideoMode();
        }

        if (flags.contains(OptionFlag.REQUIRES_GAME_RESTART)) {
            Console.instance().logMessage(MessageLevel.WARN,
                    "sodium.console.game_restart", true, 10.0);
        }

        for (OptionStorage<?> storage : dirtyStorages) {
            storage.save();
        }
    }

    private void undoChanges() {
        this.getAllOptions()
                .forEach(Option::reset);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean repeated) {
        boolean clicked = super.mouseClicked(event, repeated);

        if (!clicked) {
            this.setFocused(null);
            return true;
        }

        return clicked;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.hasPendingChanges;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.prevScreen);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return super.children();
    }

    @Override
    public void setPrompt(@Nullable ScreenPrompt prompt) {
        // Sodium prompts are not used by MattMC's video settings screen.
    }

    @Nullable
    @Override
    public ScreenPrompt getPrompt() {
        return null;
    }

    @Override
    public Dim2i getDimensions() {
        return new Dim2i(0, 0, this.width, this.height);
    }
}
