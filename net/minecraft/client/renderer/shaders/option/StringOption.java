package net.minecraft.client.renderer.shaders.option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a string shader option with allowed values.
 * Copied verbatim from IRIS 1.21.9.
 */
public class StringOption extends BaseOption {
	private final String defaultValue;
	private final List<String> allowedValues;

	private StringOption(OptionType type, String name, String defaultValue) {
		super(type, name, null);

		this.defaultValue = Objects.requireNonNull(defaultValue);
		this.allowedValues = Collections.singletonList(defaultValue);
	}

	private StringOption(OptionType type, String name, String comment, String defaultValue, List<String> allowedValues) {
		super(type, name, comment);

		this.defaultValue = Objects.requireNonNull(defaultValue);
		this.allowedValues = Collections.unmodifiableList(new ArrayList<>(allowedValues));
	}

	public static StringOption create(OptionType type, String name, String comment, String defaultValue) {
		if (comment == null) {
			return null;
		}

		int openingBracket = comment.indexOf('[');

		if (openingBracket == -1) {
			return null;
		}

		int closingBracket = comment.indexOf(']', openingBracket);

		if (closingBracket == -1) {
			return null;
		}

		String[] allowedValues = comment.substring(openingBracket + 1, closingBracket).split(" ");
		comment = comment.substring(0, openingBracket) + comment.substring(closingBracket + 1);
		boolean allowedValuesContainsDefaultValue = false;

		for (String value : allowedValues) {
			if (defaultValue.equals(value)) {
				allowedValuesContainsDefaultValue = true;
				break;
			}
		}

		List<String> builder = new ArrayList<>(Arrays.asList(allowedValues));

		if (!allowedValuesContainsDefaultValue) {
			builder.add(defaultValue);
		}

		return new StringOption(type, name, comment.trim(), defaultValue, builder);
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public List<String> getAllowedValues() {
		return allowedValues;
	}
}
