package net.fabricmc.loader.api;

@SuppressWarnings({ "deprecation", "serial" }) //Extending the deprecated one for backwards compatibility
public class VersionParsingException extends net.fabricmc.loader.util.version.VersionParsingException {
	public VersionParsingException() {
		super();
	}

	public VersionParsingException(Throwable t) {
		super(t);
	}

	public VersionParsingException(String s) {
		super(s);
	}

	public VersionParsingException(String s, Throwable t) {
		super(s, t);
	}
}
