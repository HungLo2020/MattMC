package net.minecraft.network.chat.contents;

import java.util.Optional;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

public interface PlainTextContents extends ComponentContents {
	PlainTextContents EMPTY = new PlainTextContents() {
		public String toString() {
			return "empty";
		}

		@Override
		public String text() {
			return "";
		}
	};

	static PlainTextContents create(String string) {
		return (PlainTextContents)(string.isEmpty() ? EMPTY : new PlainTextContents.LiteralContents(string));
	}

	String text();

	public record LiteralContents(String text) implements PlainTextContents {
		@Override
		public <T> Optional<T> visit(FormattedText.ContentConsumer<T> contentConsumer) {
			return contentConsumer.accept(this.text);
		}

		@Override
		public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> styledContentConsumer, Style style) {
			return styledContentConsumer.accept(style, this.text);
		}

		public String toString() {
			return "literal{" + this.text + "}";
		}
	}
}
