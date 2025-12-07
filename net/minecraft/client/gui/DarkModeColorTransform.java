package net.minecraft.client.gui;

import net.minecraft.util.ARGB;

public class DarkModeColorTransform {
	// Dark mode parameters (tunable for best look)
	private static final float BRIGHTNESS_REDUCTION = 0.25f;  // Reduce brightness to 25%
	private static final int BACKGROUND_DARKEN = 0x80;       // Darken backgrounds significantly
	
	/**
	 * Transform a color for dark mode while preserving alpha channel
	 */
	public static int transformColor(int color, boolean isDarkMode) {
		if (!isDarkMode) {
			return color;
		}
		
		int alpha = ARGB.alpha(color);
		int red = ARGB.red(color);
		int green = ARGB.green(color);
		int blue = ARGB.blue(color);
		
		// Calculate luminance to determine if color is light or dark
		float luminance = (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f;
		
		if (luminance > 0.5f) {
			// Light colors: darken significantly
			red = (int)(red * BRIGHTNESS_REDUCTION);
			green = (int)(green * BRIGHTNESS_REDUCTION);
			blue = (int)(blue * BRIGHTNESS_REDUCTION);
		} else {
			// Dark colors: lighten slightly for visibility
			red = Math.min(255, red + 30);
			green = Math.min(255, green + 30);
			blue = Math.min(255, blue + 30);
		}
		
		return ARGB.color(alpha, red, green, blue);
	}
	
	/**
	 * Transform background colors (more aggressive darkening)
	 */
	public static int transformBackgroundColor(int color, boolean isDarkMode) {
		if (!isDarkMode) {
			return color;
		}
		
		// Make backgrounds much darker
		int alpha = ARGB.alpha(color);
		int red = Math.max(0, ARGB.red(color) - BACKGROUND_DARKEN);
		int green = Math.max(0, ARGB.green(color) - BACKGROUND_DARKEN);
		int blue = Math.max(0, ARGB.blue(color) - BACKGROUND_DARKEN);
		
		return ARGB.color(alpha, red, green, blue);
	}
	
	/**
	 * Special handling for text colors (ensure readability)
	 */
	public static int transformTextColor(int color, boolean isDarkMode) {
		if (!isDarkMode) {
			return color;
		}
		
		int alpha = ARGB.alpha(color);
		int red = ARGB.red(color);
		int green = ARGB.green(color);
		int blue = ARGB.blue(color);
		
		// Calculate luminance
		float luminance = (0.299f * red + 0.587f * green + 0.114f * blue) / 255.0f;
		
		// Text should be light in dark mode
		if (luminance < 0.5f) {
			// Dark text: make it lighter
			red = Math.min(255, 255 - red + 50);
			green = Math.min(255, 255 - green + 50);
			blue = Math.min(255, 255 - blue + 50);
		}
		// Light text stays light
		
		return ARGB.color(alpha, red, green, blue);
	}
}
