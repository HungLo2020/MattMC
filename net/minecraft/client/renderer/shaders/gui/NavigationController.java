package net.minecraft.client.renderer.shaders.gui;

import net.minecraft.client.renderer.shaders.gui.element.ShaderPackOptionList;
import net.minecraft.client.renderer.shaders.shaderpack.option.menu.OptionMenuContainer;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * IRIS VERBATIM - Adapted from Iris 1.21.9
 */
public class NavigationController {
private final Deque<String> history = new ArrayDeque<>();
private ShaderPackOptionList optionList;
private String currentScreen = null;

public NavigationController(OptionMenuContainer container) {
}

public void back() {
if (!history.isEmpty()) {
history.removeLast();

if (!history.isEmpty()) {
currentScreen = history.getLast();
} else {
currentScreen = null;
}
} else {
currentScreen = null;
}

this.rebuild();
}

public void open(String screen) {
currentScreen = screen;
history.addLast(screen);

this.rebuild();
}

public void rebuild() {
if (optionList != null) {
optionList.rebuild();
}
}

public void refresh() {
if (optionList != null) {
optionList.refresh();
}
}

public boolean hasHistory() {
return !this.history.isEmpty();
}

public void setActiveOptionList(ShaderPackOptionList optionList) {
this.optionList = optionList;
}

public String getCurrentScreen() {
return currentScreen;
}
}
