package net.fabricmc.loader.api;

/**
 * Represents an exception that arises when obtaining entrypoints.
 *
 * @see FabricLoader#getEntrypointContainers(String, Class)
 */
@SuppressWarnings("serial")
public class EntrypointException extends RuntimeException {
	private final String key;

	/**
	 * @deprecated For internal use only, to be removed!
	 */
	@Deprecated
	public EntrypointException(String key, Throwable cause) {
		super("Exception while loading entries for entrypoint '" + key + "'!", cause);
		this.key = key;
	}

	/**
	 * @deprecated For internal use only, use regular exceptions!
	 */
	@Deprecated
	public EntrypointException(String key, String causingMod, Throwable cause) {
		super("Exception while loading entries for entrypoint '" + key + "' provided by '" + causingMod + "'", cause);
		this.key = key;
	}

	/**
	 * @deprecated For internal use only, to be removed!
	 */
	@Deprecated
	public EntrypointException(String s) {
		super(s);
		this.key = "";
	}

	/**
	 * @deprecated For internal use only, to be removed!
	 */
	@Deprecated
	public EntrypointException(Throwable t) {
		super(t);
		this.key = "";
	}

	/**
	 * Returns the key of entrypoint in which the exception arose.
	 *
	 * @return the key
	 */
	public String getKey() {
		return key;
	}
}
