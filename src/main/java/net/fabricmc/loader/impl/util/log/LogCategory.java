package net.fabricmc.loader.impl.util.log;

public final class LogCategory {
	public static final LogCategory DISCOVERY = create("Discovery");
	public static final LogCategory ENTRYPOINT = create("Entrypoint");
	public static final LogCategory GAME_PATCH = create("GamePatch");
	public static final LogCategory GAME_PROVIDER = create("GameProvider");
	public static final LogCategory GAME_REMAP = create("GameRemap");
	public static final LogCategory GENERAL = create();
	public static final LogCategory KNOT = create("Knot");
	public static final LogCategory LIB_CLASSIFICATION = create("LibClassify");
	public static final LogCategory LOG = create("Log");
	public static final LogCategory MAPPINGS = create("Mappings");
	public static final LogCategory METADATA = create("Metadata");
	public static final LogCategory MOD_REMAP = create("ModRemap");
	public static final LogCategory MIXIN = create("Mixin");
	public static final LogCategory RESOLUTION = create("Resolution");
	public static final LogCategory TEST = create("Test");

	public static final String SEPARATOR = "/";

	public final String context;
	public final String name;
	public Object data;

	public static LogCategory create(String... names) {
		return new LogCategory(Log.NAME, names);
	}

	/**
	 * Create a log category for external uses, no API guarantees!
	 */
	public static LogCategory createCustom(String context, String... names) {
		return new LogCategory(context, names);
	}

	private LogCategory(String context, String[] names) {
		this.context = context;
		this.name = String.join(SEPARATOR, names);
	}

	@Override
	public String toString() {
		return name.isEmpty() ? context : context+SEPARATOR+name;
	}
}
