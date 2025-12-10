package net.minecraft.client.renderer.shaders.gui.element.widget;

import net.minecraft.client.renderer.shaders.shaderpack.option.menu.OptionMenuElement;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Abstract base class for widgets that can display comments
 * <p>
 * IRIS VERBATIM - Copied from Iris 1.21.9
 */
public abstract class CommentedElementWidget<T extends OptionMenuElement> extends AbstractElementWidget<T> {
	public CommentedElementWidget(T element) {
		super(element);
	}

	public abstract Optional<Component> getCommentTitle();

	public abstract Optional<Component> getCommentBody();
}
