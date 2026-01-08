package net.fabricmc.loader.api;

/**
 * An exception that occurs during a {@link LanguageAdapter}'s object creation.
 *
 * @see LanguageAdapter
 */
@SuppressWarnings("serial")
public class LanguageAdapterException extends Exception {
	/**
	 * Creates a new language adapter exception.
	 *
	 * @param s the message
	 */
	public LanguageAdapterException(String s) {
		super(s);
	}

	/**
	 * Creates a new language adapter exception.
	 *
	 * @param t the cause
	 */
	public LanguageAdapterException(Throwable t) {
		super(t);
	}

	/**
	 * Creates a new language adapter exception.
	 *
	 * @param s the message
	 * @param t the cause
	 */
	public LanguageAdapterException(String s, Throwable t) {
		super(s, t);
	}
}
