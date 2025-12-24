package net.minecraft.client.gui.components.tabs;

import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public interface Tab {
	Component getTabTitle();

	Component getTabExtraNarration();

	void visitChildren(Consumer<AbstractWidget> consumer);

	void doLayout(ScreenRectangle screenRectangle);
}
