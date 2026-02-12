package net.fabricmc.loader.impl.gui;

import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

/** 
 * Simplified GUI entry for integrated mod approach.
 * No GUI needed - integrated mod always works, no external mod errors to display.
 */
public final class FabricGuiEntry {
	/**
	 * Simplified error display - just logs and exits.
	 * GUI removed since integrated mod setup has no external mod loading failures.
	 */
	public static void displayError(String mainText, Throwable exception, boolean exitAfter) {
		// Log the error
		if (exception != null) {
			Log.error(LogCategory.GENERAL, mainText, exception);
		} else {
			Log.error(LogCategory.GENERAL, mainText);
		}
		
		// Exit if requested
		if (exitAfter) {
			System.exit(1);
		}
	}
}
