package net.minecraft.client.renderer.shaders.helpers;

import java.util.function.BooleanSupplier;

/**
 * Represents an optional boolean value that can be DEFAULT, FALSE, or TRUE.
 * Matches IRIS's OptionalBoolean enum exactly.
 * 
 * Reference: frnsrc/Iris-1.21.9/.../helpers/OptionalBoolean.java
 */
public enum OptionalBoolean {
	DEFAULT,
	FALSE,
	TRUE;

	public boolean orElse(boolean defaultValue) {
		if (this == DEFAULT) {
			return defaultValue;
		}

		return this == TRUE;
	}

	public boolean orElseGet(BooleanSupplier defaultValue) {
		if (this == DEFAULT) {
			return defaultValue.getAsBoolean();
		}

		return this == TRUE;
	}
}
