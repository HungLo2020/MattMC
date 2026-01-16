package net.fabricmc.loader.api;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.util.version.VersionParser;

/**
 * Represents a version of a mod.
 *
 * @see ModMetadata#getVersion()
 */
public interface Version extends Comparable<Version> {
	/**
	 * Returns the user-friendly representation of this version.
	 */
	String getFriendlyString();

	/**
	 * Parses a version from a string notation.
	 *
	 * @param string the string notation of the version
	 * @return the parsed version
	 * @throws VersionParsingException if a problem arises during version parsing
	 */
	static Version parse(String string) throws VersionParsingException {
		return VersionParser.parse(string, false);
	}
}
