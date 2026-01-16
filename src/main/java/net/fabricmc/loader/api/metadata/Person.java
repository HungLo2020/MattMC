package net.fabricmc.loader.api.metadata;

/**
 * Represents a person.
 */
public interface Person {
	/**
	 * Returns the display name of the person.
	 */
	String getName();

	/**
	 * Returns the contact information of the person.
	 */
	ContactInformation getContact();
}
