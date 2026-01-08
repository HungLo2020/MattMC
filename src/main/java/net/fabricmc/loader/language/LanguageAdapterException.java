package net.fabricmc.loader.language;

@SuppressWarnings("serial")
@Deprecated
public class LanguageAdapterException extends Exception {
	public LanguageAdapterException(String s) {
		super(s);
	}

	public LanguageAdapterException(String s, Throwable t) {
		super(s, t);
	}
}
