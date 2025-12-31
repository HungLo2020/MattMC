package net.minecraft.util;

import java.util.UUID;

/**
 * Utility for converting UUIDs to/from undashed string format.
 * Replaces com.mojang.util.UndashedUuid from authlib.
 */
public final class UndashedUuid {
	private UndashedUuid() {
	}

	/**
	 * Parse UUID from string, accepting both dashed and undashed formats.
	 */
	public static UUID fromStringLenient(String string) {
		if (string == null) {
			throw new IllegalArgumentException("UUID string cannot be null");
		}
		
		// If it already has dashes, use standard parsing
		if (string.contains("-")) {
			return UUID.fromString(string);
		}
		
		// Parse undashed format
		if (string.length() != 32) {
			throw new IllegalArgumentException("Invalid undashed UUID length: " + string.length());
		}
		
		// Insert dashes at proper positions: 8-4-4-4-12
		String dashed = string.substring(0, 8) + "-" +
			string.substring(8, 12) + "-" +
			string.substring(12, 16) + "-" +
			string.substring(16, 20) + "-" +
			string.substring(20, 32);
		
		return UUID.fromString(dashed);
	}

	/**
	 * Convert UUID to undashed string format.
	 */
	public static String toString(UUID uuid) {
		if (uuid == null) {
			throw new IllegalArgumentException("UUID cannot be null");
		}
		return uuid.toString().replace("-", "");
	}
}
