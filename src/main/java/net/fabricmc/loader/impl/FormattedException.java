package net.fabricmc.loader.impl;

@SuppressWarnings("serial")
public final class FormattedException extends RuntimeException {
	private final String mainText;

	public FormattedException(String mainText, String message) {
		super(message);

		this.mainText = mainText;
	}

	public FormattedException(String mainText, String format, Object... args) {
		super(String.format(format, args));

		this.mainText = mainText;
	}

	public FormattedException(String mainText, String message, Throwable cause) {
		super(message, cause);

		this.mainText = mainText;
	}

	public FormattedException(String mainText, Throwable cause) {
		super(cause);

		this.mainText = mainText;
	}

	public static FormattedException ofLocalized(String key, String message) {
		// Localization removed - using hardcoded English strings
		return new FormattedException(getEnglishText(key), message);
	}

	public static FormattedException ofLocalized(String key, String format, Object... args) {
		// Localization removed - using hardcoded English strings
		return new FormattedException(getEnglishText(key), format, args);
	}

	public static FormattedException ofLocalized(String key, String message, Throwable cause) {
		// Localization removed - using hardcoded English strings
		return new FormattedException(getEnglishText(key), message, cause);
	}

	public static FormattedException ofLocalized(String key, Throwable cause) {
		// Localization removed - using hardcoded English strings
		return new FormattedException(getEnglishText(key), cause);
	}

	private static String getEnglishText(String key) {
		// Hardcoded English translations for exception messages
		switch (key) {
			case "exception.incompatible": return "Incompatible mods found!";
			case "exception.parsingOverride": return "Error parsing dependency overrides!";
			case "exception.initializerFailure": return "A mod crashed on startup!";
			case "exception.minecraft.invokeFailure": return "Failed to start Minecraft!";
			case "exception.minecraft.generic": return "Minecraft has crashed!";
			default: return key; // fallback to key itself
		}
	}

	public String getMainText() {
		return mainText;
	}

	public String getDisplayedText() {
		return mainText;
	}
}
