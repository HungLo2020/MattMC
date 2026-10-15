package net.minecraft.network.chat.contents;

import java.util.Optional;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.ObjectInfo;

public record ObjectContents(ObjectInfo contents) implements ComponentContents {
	private static final String PLACEHOLDER = Character.toString('￼');
	@Override
	public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
		return contentConsumer.accept(this.contents.description());
	}

	@Override
	public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
		return styledContentConsumer.accept(style.withFont(this.contents.fontDescription()), PLACEHOLDER);
	}
}
