package net.fabricmc.loader.util;

/**
 * @deprecated Internal API, do not use
 */
@SuppressWarnings("serial")
@Deprecated
public class UrlConversionException extends Exception {
	public UrlConversionException() {
		super();
	}

	public UrlConversionException(String s) {
		super(s);
	}

	public UrlConversionException(Throwable t) {
		super(t);
	}

	public UrlConversionException(String s, Throwable t) {
		super(s, t);
	}
}
