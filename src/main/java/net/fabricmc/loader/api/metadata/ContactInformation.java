package net.fabricmc.loader.api.metadata;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a contact information.
 */
public interface ContactInformation {
	/**
	 * An empty contact information.
	 */
	ContactInformation EMPTY = new ContactInformation() {
		@Override
		public Optional<String> get(String key) {
			return Optional.empty();
		}

		@Override
		public Map<String, String> asMap() {
			return Collections.emptyMap();
		}
	};

	/**
	 * Gets a certain type of contact information.
	 *
	 * @param key the type of contact information
	 * @return an optional contact information
	 */
	Optional<String> get(String key);

	/**
	 * Gets all contact information provided as a map from contact type to information.
	 */
	Map<String, String> asMap();
}
