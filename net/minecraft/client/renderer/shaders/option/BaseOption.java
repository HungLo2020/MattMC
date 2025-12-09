package net.minecraft.client.renderer.shaders.option;

import java.util.Optional;

/**
 * Base class for shader options.
 * Copied verbatim from IRIS 1.21.9.
 */
public abstract class BaseOption {
	private final OptionType type;
	private final String name;
	private final String comment;

	BaseOption(OptionType type, String name, String comment) {
		this.type = type;
		this.name = name;

		if (comment == null || comment.isEmpty()) {
			this.comment = null;
		} else {
			this.comment = comment;
		}
	}

	public OptionType getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public Optional<String> getComment() {
		return Optional.ofNullable(comment);
	}
}
