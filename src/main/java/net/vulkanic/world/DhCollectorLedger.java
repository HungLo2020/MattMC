package net.vulkanic.world;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;

/** Downcalls into the Rust DH collector ledger ({@code render/dh_collector},
 * exported by {@code render/bridge/dh_collector.rs}). Callers hold the
 * collector lock; variable-length results are taken right after the call
 * that produced them. */
final class DhCollectorLedger {
	static final int CONFIG_RUST_WHOLE_FRAME = 1;
	static final int CONFIG_LEGACY_OBSERVATION = 2;
	static final int CONFIG_EXECUTION_SNAPSHOTS = 4;
	static final int CONFIG_TRACE_PUBLICATION = 8;

	static final int EFFECT_SET_CURRENT = 1;
	static final int EFFECT_DROP_CURRENT = 2;
	static final int EFFECT_PUBLISH_CURRENT = 3;
	static final int EFFECT_PUBLISH_UPDATE = 4;
	static final int EFFECT_DROP_PUBLISHED = 5;
	static final int EFFECT_CLEAR = 6;

	static final int QUERY_LIFECYCLE = 0;
	static final int QUERY_STALE_RECEIPTS = 1;
	static final int QUERY_EXECUTIONS = 2;
	static final int QUERY_ROUTE_FRAME = 3;
	static final int QUERY_LAST_EXECUTED_WORLD_FRAME = 4;
	static final int QUERY_UNPUBLISHED_VISIBLE = 5;
	static final int QUERY_PAYLOAD_STABLE = 6;
	static final int QUERY_PUBLISHED_GENERATION = 7;
	static final int QUERY_PUBLISHED_PAYLOAD_GENERATION = 8;
	static final int QUERY_TOUCH = 9;
	static final int QUERY_COLUMN_COUNT = 10;
	static final int QUERY_PENDING_SEGMENTS = 11;
	static final int QUERY_FRAME_ENABLED = 12;
	static final int QUERY_FRAME_FLAGS = 13;
	static final int QUERY_NEXT_GENERATION = 14;
	static final int QUERY_HAS_COLUMN = 15;
	static final int QUERY_HAS_PUBLISHED_COLUMN = 16;
	static final int QUERY_CURRENT_GENERATION = 17;

	static final int SEGMENTS_PENDING = 0;
	static final int SEGMENTS_LAST_CONSUMED = 1;

	static final int CONSUME_VISIBLE_FRAME = 0;
	static final int CONSUME_RENDER_FRAME = 1;
	static final int CONSUME_VISIBLE_SEGMENTS = 2;

	private static final Linker.Option CRITICAL = Linker.Option.critical(true);
	private static final MethodHandle TAKE_EFFECTS = bind("take_effects", true, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
	private static final MethodHandle TAKE_OUTPUT = bind("take_output", true, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
	private static final MethodHandle TAKE_TEXT = bind("take_text", true, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
	private static final MethodHandle ALLOCATE_GENERATION = bind("allocate_generation", false, ValueLayout.JAVA_LONG);
	private static final MethodHandle QUERY = bind("query", false, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG);
	private static final MethodHandle HAS_COLUMN_GENERATION = bind("has_column_generation", false, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
	private static final MethodHandle REQUEST_PUBLICATION = bind("request_publication", false, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG);
	private static final MethodHandle STAGE_SEGMENT = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_collector_stage_segment",
		FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT), CRITICAL);
	private static final MethodHandle RECORD_BUILT = bind("record_built", true, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final MethodHandle PAYLOAD = bind("payload", false, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT);
	private static final MethodHandle TAKE_BYTES = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_collector_take_bytes",
		FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT), CRITICAL);
	private static final MethodHandle PUBLISHED_PAYLOAD_KEYS = bind("published_payload_keys", false, ValueLayout.JAVA_INT);
	private static final MethodHandle RELEASE_OWNER = bind("release_owner", false, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
	private static final MethodHandle RECORD_BUILD_ATTEMPT = bindVoid("record_build_attempt", ValueLayout.JAVA_INT);
	private static final MethodHandle REMOVE_COLUMN = bind("remove_column", false, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
	private static final MethodHandle TRIM = bind("trim", false, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG);
	private static final MethodHandle BEGIN_FRAME = bindVoid("begin_frame", ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
	private static final MethodHandle SET_CLIP_DISTANCE = bindVoid("set_clip_distance", ValueLayout.JAVA_FLOAT);
	private static final MethodHandle VISIBLE_COLUMN = bind("visible_column", true, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final MethodHandle APPEND_VISIBLE_COLUMN = bind("append_visible_column", false, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
	private static final MethodHandle APPEND_SEGMENTS = bind("append_segments", true, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
	private static final MethodHandle SEGMENTS = bind("segments", false, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
	private static final MethodHandle CONSUME = bind("consume", true, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final MethodHandle SELECT_ROUTE = bind("select_route", false, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
	private static final MethodHandle REJECT_ROUTE = bindVoid("reject_route", ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
	private static final MethodHandle RECORD_EXECUTION = bind("record_execution", true, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final MethodHandle OBSERVE_RENDER_LIST = bindVoid("observe_render_list", ValueLayout.JAVA_INT);
	private static final MethodHandle RECORD_VISIBILITY = bindVoid("record_visibility", ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
	private static final MethodHandle PENDING_UPDATE = bind("pending_update", false, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT);
	private static final MethodHandle ACKNOWLEDGE = bind("acknowledge", true, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
	private static final MethodHandle RELEASE_IN_FLIGHT = bindVoid("release_in_flight", ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
	private static final MethodHandle CLEAR = bind("clear", false, ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final MethodHandle RESET_FOR_TEST = bind("reset_for_test", false, ValueLayout.JAVA_INT);
	private static final MethodHandle BEGIN_TEST_FRAME = bindVoid("begin_test_frame", ValueLayout.JAVA_INT);
	private static final MethodHandle COLUMN_KEYS = bind("column_keys", false, ValueLayout.JAVA_INT);
	private static final MethodHandle PAYLOAD_DIFFERENCE = bind("payload_difference", false, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG);
	private static final MethodHandle DIAGNOSTICS = bind("diagnostics", false, ValueLayout.JAVA_INT);

	private DhCollectorLedger() {
	}

	private static MethodHandle bind(String name, boolean critical, ValueLayout result, ValueLayout... arguments) {
		FunctionDescriptor descriptor = FunctionDescriptor.of(result, arguments);
		return critical
			? NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_collector_" + name, descriptor, CRITICAL)
			: NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_collector_" + name, descriptor);
	}

	private static MethodHandle bindVoid(String name, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_dh_collector_" + name, FunctionDescriptor.ofVoid(arguments));
	}

	private static IllegalStateException failure(String operation, Throwable error) {
		return new IllegalStateException("Rust DH collector " + operation + " failed", error);
	}

	/** Throws Java's original exception for a ledger error code. */
	static int check(int status) {
		if (status >= 0) return status;
		throw switch (status) {
			case -1 -> new IllegalStateException("Distant Horizons pending visible-column admission exceeds 16384");
			case -2 -> new IllegalStateException("Distant Horizons visible LOD segment capture exceeds 16384");
			case -3 -> new IllegalStateException("Cannot select a Rust DH route without enabled frame semantics");
			case -4 -> new IllegalStateException("Cannot select Rust DH material route with visible unsupported segments");
			case -5 -> new IllegalArgumentException("Distant Horizons semantic retention bounds must be positive");
			case -6 -> new ArithmeticException("long overflow");
			default -> new IllegalStateException("Rust DH collector ledger is unavailable (" + status + ")");
		};
	}

	private static MemorySegment utf8(Arena arena, String value) {
		return arena.allocateFrom(value == null ? "" : value);
	}

	static long allocateGeneration() {
		try {
			return (long)ALLOCATE_GENERATION.invokeExact();
		} catch (Throwable error) {
			throw failure("generation", error);
		}
	}

	static long query(int kind, long argument) {
		try {
			return (long)QUERY.invokeExact(kind, argument);
		} catch (Throwable error) {
			throw failure("query", error);
		}
	}

	static boolean hasColumnGeneration(long key, long generation) {
		try {
			return check((int)HAS_COLUMN_GENERATION.invokeExact(key, generation)) != 0;
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("column", error);
		}
	}

	static boolean requestPublication(long key) {
		try {
			return check((int)REQUEST_PUBLICATION.invokeExact(key)) != 0;
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("publication request", error);
		}
	}

	/** Copies one packed segment into the column {@link #recordBuilt} records
	 * next; {@code layer} is 0..3 (opaque, side, up, water). */
	static void stageSegment(int layer, int sourceIndex, byte[] packedVertices) {
		try {
			STAGE_SEGMENT.invokeExact(layer, sourceIndex, MemorySegment.ofArray(packedVertices), packedVertices.length);
		} catch (Throwable error) {
			throw failure("segment staging", error);
		}
	}

	/** Records the staged segments as the column's payload. Returns the effect
	 * count; writes [generation, owner token] to {@code result}. */
	static int recordBuilt(int config, long key, long generation, int originX, int originY, int originZ,
		long provenanceBytes, boolean sameProvenance, boolean retainOwner, long[] result) {
		try {
			return check((int)RECORD_BUILT.invokeExact(config, key, generation, originX, originY, originZ,
				provenanceBytes, sameProvenance ? 1 : 0, retainOwner ? 1 : 0, MemorySegment.ofArray(result)));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("record", error);
		}
	}

	static int releaseOwner(int config, long key, long generation, long token) {
		try {
			return check((int)RELEASE_OWNER.invokeExact(config, key, generation, token));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("lease release", error);
		}
	}

	static void recordBuildAttempt(int config) {
		try {
			RECORD_BUILD_ATTEMPT.invokeExact(config);
		} catch (Throwable error) {
			throw failure("build attempt", error);
		}
	}

	/** {@code generation} < 0 removes by key alone. Returns the effect count. */
	static int removeColumn(int config, long key, long generation) {
		try {
			return check((int)REMOVE_COLUMN.invokeExact(config, key, generation));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("removal", error);
		}
	}

	static int trim(int maximumColumns, long maximumBytes) {
		try {
			return check((int)TRIM.invokeExact(maximumColumns, maximumBytes));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("trim", error);
		}
	}

	static void beginFrame(boolean accepted, int flags, String reason, String matrixStatus, String matrixDetail) {
		try (Arena arena = Arena.ofConfined()) {
			BEGIN_FRAME.invokeExact(accepted ? 1 : 0, flags, utf8(arena, reason), utf8(arena, matrixStatus), utf8(arena, matrixDetail));
		} catch (Throwable error) {
			throw failure("frame", error);
		}
	}

	static void setClipDistance(float clipDistance) {
		try {
			SET_CLIP_DISTANCE.invokeExact(clipDistance);
		} catch (Throwable error) {
			throw failure("clip distance", error);
		}
	}

	/** Writes [generation, opaque, side, up, water]; returns true when admitted. */
	static boolean visibleColumn(long key, boolean append, long[] result) {
		try {
			return check((int)VISIBLE_COLUMN.invokeExact(key, append ? 1 : 0, MemorySegment.ofArray(result))) == 1;
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("visible column", error);
		}
	}

	static void appendVisibleColumn(long key, long generation, int opaque, int side, int up, int water) {
		try {
			check((int)APPEND_VISIBLE_COLUMN.invokeExact(key, generation, opaque, side, up, water));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("visible append", error);
		}
	}

	static void appendSegments(long key, long generation, int layer, int[] indexes) {
		try {
			check((int)APPEND_SEGMENTS.invokeExact(key, generation, layer, MemorySegment.ofArray(indexes), indexes.length));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("segment append", error);
		}
	}

	static long[] takeOutput(int length) {
		long[] values = new long[Math.max(0, length)];
		if (length <= 0) return values;
		try {
			int copied = (int)TAKE_OUTPUT.invokeExact(MemorySegment.ofArray(values), values.length);
			if (copied != values.length) throw new IllegalStateException("Rust DH collector output was truncated");
			return values;
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("output", error);
		}
	}

	static String[] takeText(int length) {
		if (length <= 0) return new String[] {""};
		byte[] bytes = new byte[length];
		try {
			int copied = (int)TAKE_TEXT.invokeExact(MemorySegment.ofArray(bytes), bytes.length);
			if (copied != bytes.length) throw new IllegalStateException("Rust DH collector text was truncated");
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("text", error);
		}
		return new String(bytes, StandardCharsets.UTF_8).split("\0", -1);
	}

	/** Five longs per segment: key, generation, layer, segment index, order. */
	static long[] segments(int kind) {
		try {
			return takeOutput((int)SEGMENTS.invokeExact(kind));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("segments", error);
		}
	}

	/** Writes [enabled, flags, lifecycle, route frame]; returns the consumed segments. */
	static long[] consume(int mode, long[] header) {
		try {
			return takeOutput((int)CONSUME.invokeExact(mode, MemorySegment.ofArray(header)));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("consume", error);
		}
	}

	static boolean selectRoute(boolean completeExactAtlas) {
		try {
			return check((int)SELECT_ROUTE.invokeExact(completeExactAtlas ? 1 : 0)) == 1;
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("route selection", error);
		}
	}

	static void rejectRoute(String reason, int opaque, int transparent, int water) {
		try (Arena arena = Arena.ofConfined()) {
			REJECT_ROUTE.invokeExact(utf8(arena, reason), opaque, transparent, water);
		} catch (Throwable error) {
			throw failure("route rejection", error);
		}
	}

	/** Returns the route frame, or -1 for a stale lifecycle whose receipt was dropped. */
	static long recordExecution(long lifecycle, long worldFrame, long submission, long captureFrame, int instances,
		int opaque, int transparent, int water) {
		long[] routeFrame = new long[1];
		try {
			int recorded = check((int)RECORD_EXECUTION.invokeExact(lifecycle, worldFrame, submission, captureFrame, instances,
				opaque, transparent, water, MemorySegment.ofArray(routeFrame)));
			return recorded == 1 ? routeFrame[0] : -1L;
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("execution", error);
		}
	}

	static void observeRenderList(int visibleColumns) {
		try {
			OBSERVE_RENDER_LIST.invokeExact(visibleColumns);
		} catch (Throwable error) {
			throw failure("render list", error);
		}
	}

	static void recordVisibility(int candidates, int unpublished, List<Long> keys) {
		long[] values = new long[keys.size()];
		for (int index = 0; index < values.length; index++) values[index] = keys.get(index);
		try (Arena arena = Arena.ofConfined()) {
			RECORD_VISIBILITY.invokeExact(candidates, unpublished, arena.allocateFrom(ValueLayout.JAVA_LONG, values), values.length);
		} catch (Throwable error) {
			throw failure("visibility", error);
		}
	}

	/** [generation, asset count, retirement count, (key, generation) per asset and retirement], or empty. */
	static long[] pendingUpdate(int config, boolean visibleCandidatesOnly) {
		try {
			return takeOutput(check((int)PENDING_UPDATE.invokeExact(config, visibleCandidatesOnly ? 1 : 0)));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("pending update", error);
		}
	}

	/** {@code assets} and {@code retirements}: (key, generation) each. */
	static int acknowledge(int config, long[] assets, long[] retirements) {
		try {
			return check((int)ACKNOWLEDGE.invokeExact(config, MemorySegment.ofArray(assets), assets.length / 2,
				MemorySegment.ofArray(retirements), retirements.length / 2));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("acknowledgement", error);
		}
	}

	static void releaseInFlight(long[] assets) {
		try (Arena arena = Arena.ofConfined()) {
			RELEASE_IN_FLIGHT.invokeExact(arena.allocateFrom(ValueLayout.JAVA_LONG, assets), assets.length / 2);
		} catch (Throwable error) {
			throw failure("in-flight release", error);
		}
	}

	static int clear(String reason) {
		try (Arena arena = Arena.ofConfined()) {
			return check((int)CLEAR.invokeExact(utf8(arena, reason)));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("clear", error);
		}
	}

	static int resetForTest() {
		try {
			return check((int)RESET_FOR_TEST.invokeExact());
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("reset", error);
		}
	}

	static void beginTestFrame(boolean enabled) {
		try {
			BEGIN_TEST_FRAME.invokeExact(enabled ? 1 : 0);
		} catch (Throwable error) {
			throw failure("test frame", error);
		}
	}

	/** Column keys in the ledger's LRU order. */
	static long[] columnKeys() {
		try {
			return takeOutput((int)COLUMN_KEYS.invokeExact());
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("column keys", error);
		}
	}

	static final int PAYLOAD_CURRENT = 0;
	static final int PAYLOAD_PUBLISHED = 1;

	/** A copy of a column payload: [generation, origin x, y, z, segments per
	 * layer ×4, (source index, byte length) per segment] and the concatenated
	 * packed vertices. */
	record PayloadCopy(long[] header, byte[] bytes) {}

	/** The current or retained published payload of a column, or null. */
	static PayloadCopy payload(long key, int which) {
		try {
			int length = (int)PAYLOAD.invokeExact(key, which);
			if (length <= 0) return null;
			long[] header = takeOutput(length);
			long byteCount = 0L;
			for (int at = 9; at < header.length; at += 2) byteCount += header[at];
			byte[] bytes = new byte[Math.toIntExact(byteCount)];
			TAKE_BYTES.invokeExact(MemorySegment.ofArray(bytes), bytes.length);
			return new PayloadCopy(header, bytes);
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("payload copy", error);
		}
	}

	/** Keys with a retained published payload, in insertion order. */
	static long[] publishedPayloadKeys() {
		try {
			return takeOutput((int)PUBLISHED_PAYLOAD_KEYS.invokeExact());
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("published payload keys", error);
		}
	}

	static String payloadDifference(long key) {
		try {
			int length = (int)PAYLOAD_DIFFERENCE.invokeExact(key);
			return length < 0 ? null : takeText(length)[0];
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("payload difference", error);
		}
	}

	/** The ledger's route diagnostics: numbers, and [decision, reason, matrix
	 * status, matrix detail, last payload difference, last reset reason]. */
	record Diagnostics(long[] numbers, String[] text) {}

	static Diagnostics diagnostics() {
		try {
			long[] numbers = takeOutput((int)DIAGNOSTICS.invokeExact());
			return new Diagnostics(numbers, takeText((int)TAKE_TEXT.invokeExact(MemorySegment.NULL, 0)));
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("diagnostics", error);
		}
	}

	/** Takes the last call's effects, three longs each: kind, key, detail. */
	static long[] takeEffects(int count) {
		long[] values = new long[Math.max(0, count) * 3];
		if (count <= 0) return values;
		try {
			int copied = (int)TAKE_EFFECTS.invokeExact(MemorySegment.ofArray(values), values.length);
			if (copied != count) throw new IllegalStateException("Rust DH collector effects were truncated");
			return values;
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("effects", error);
		}
	}
}
