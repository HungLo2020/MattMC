package net.minecraft.client.renderer.shaders.gui.element.widget;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.shaders.gui.NavigationController;
import net.minecraft.client.renderer.shaders.gui.screen.ShaderPackScreen;
import net.minecraft.client.renderer.shaders.shaderpack.option.menu.OptionMenuElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract base class for all element widgets in the shader pack GUI
 * <p>
 * IRIS VERBATIM - Adapted from Iris 1.21.9 for MattMC
 */
public abstract class AbstractElementWidget<T extends OptionMenuElement> implements GuiEventListener, NarratableEntry {
public static final AbstractElementWidget<OptionMenuElement> EMPTY = new AbstractElementWidget<>(null) {
@Override
public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered) {
}

@Override
public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
return null;
}

@Override
public @NotNull ScreenRectangle getRectangle() {
return ScreenRectangle.empty();
}
};

protected final T element;
public ScreenRectangle bounds = ScreenRectangle.empty();
private boolean focused;

public AbstractElementWidget(T element) {
this.element = element;
}

public void init(ShaderPackScreen screen, NavigationController navigation) {
}

public abstract void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered);

public boolean mouseClicked(double mouseX, double mouseY, int button) {
return false;
}

public boolean mouseReleased(double mouseX, double mouseY, int button) {
return false;
}

public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
return false;
}

@Override
public boolean isFocused() {
return focused;
}

@Override
public void setFocused(boolean focused) {
this.focused = focused;
}

@Nullable
@Override
public ComponentPath nextFocusPath(FocusNavigationEvent event) {
return (!isFocused()) ? ComponentPath.leaf(this) : null;
}

@Override
public ScreenRectangle getRectangle() {
return bounds;
}

@Override
public NarrationPriority narrationPriority() {
return NarrationPriority.NONE;
}

@Override
public void updateNarration(NarrationElementOutput narrationElementOutput) {
}
}
