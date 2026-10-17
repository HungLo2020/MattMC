package net.minecraft.world.level.levelgen.structure.structures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NativeStructureStubsAccess;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** {@code OceanMonumentStructure.regeneratePiecesAfterLoad} through the native
 * monument planner against its original Java body: the regenerated pieces'
 * saved form and the random's final state, over seeds, chunk positions, saved
 * origins, the four saved orientations and none, and the empty container. */
class NativeMonumentRegenerationTest {
	static final StructurePieceSerializationContext CONTEXT = new StructurePieceSerializationContext(null, null, null);

	@BeforeAll static void load() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@AfterAll static void close() {
		NativeStructureStubsAccess.setMonumentRegeneration(true);
	}

	/** A saved start's first piece: a building at (minX, minZ) with {@code orientation}. */
	static PiecesContainer saved(int minX, int minZ, Direction direction, boolean nullOrientation) {
		var building = new OceanMonumentPieces.MonumentBuilding(new WorldgenRandom(new LegacyRandomSource(minX * 31L + minZ)), minX, minZ, direction);
		if (nullOrientation) building.setOrientation(null);
		return new PiecesContainer(List.of(building));
	}

	static PiecesContainer regenerate(ChunkPos chunk, long seed, PiecesContainer container, boolean nativeRoute) {
		NativeStructureStubsAccess.setMonumentRegeneration(nativeRoute);
		try {
			return OceanMonumentStructure.regeneratePiecesAfterLoad(chunk, seed, container);
		} finally {
			NativeStructureStubsAccess.setMonumentRegeneration(true);
		}
	}

	/** Java's own regeneration on a known random, for its final state. */
	static long javaFinalState(ChunkPos chunk, long seed, PiecesContainer container) throws ReflectiveOperationException {
		var source = new LegacyRandomSource(0);
		var random = new WorldgenRandom(source);
		random.setLargeFeatureSeed(seed, chunk.x, chunk.z);
		StructurePiece first = container.pieces().get(0);
		Direction drawn = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction direction = first.getOrientation() == null ? drawn : first.getOrientation();
		new OceanMonumentPieces.MonumentBuilding(random, first.getBoundingBox().minX(), first.getBoundingBox().minZ(), direction);
		// Read the actual owned source on Current; the original Frozen fixture has
		// only its private AtomicLong. Neither path advances or resets the source.
		try {
			return (long)LegacyRandomSource.class.getMethod("rawSeed").invoke(source);
		} catch (NoSuchMethodException originalFrozen) {
			var field = LegacyRandomSource.class.getDeclaredField("seed");
			field.setAccessible(true);
			return ((AtomicLong)field.get(source)).get();
		}
	}

	/** Every field of the building and its pieces, recursively: room definitions by
	 * their index (with openings, flags, scan index and connections by index). */
	static String describe(PiecesContainer container) throws ReflectiveOperationException {
		StringBuilder out = new StringBuilder();
		var seen = new java.util.IdentityHashMap<Object, Integer>();
		for (StructurePiece piece : container.pieces()) describe(piece, out, seen, 0);
		return out.toString();
	}

	private static void describe(Object value, StringBuilder out, java.util.IdentityHashMap<Object, Integer> seen, int depth) throws ReflectiveOperationException {
		if (value == null || value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof String) {
			out.append(value);
			return;
		}
		if (value instanceof net.minecraft.world.level.levelgen.structure.BoundingBox box) {
			out.append(box);
			return;
		}
		if (value instanceof OceanMonumentPieces.RoomDefinition room) {
			out.append("room#").append(room.index);
			if (seen.containsKey(room) || depth > 64) return;
			seen.put(room, seen.size());
			out.append("{open=").append(java.util.Arrays.toString(room.hasOpening)).append(" claimed=").append(room.claimed).append(" source=").append(room.isSource);
			var scan = OceanMonumentPieces.RoomDefinition.class.getDeclaredField("scanIndex");
			scan.setAccessible(true);
			out.append(" scan=").append(scan.getInt(room)).append(" links=[");
			for (var link : room.connections) {
				out.append(link == null ? "-" : "#" + link.index).append(',');
			}
			out.append("]}");
			for (var link : room.connections) if (link != null) describe(link, out, seen, depth + 1);
			return;
		}
		if (value instanceof java.util.List<?> list) {
			out.append('[');
			for (Object element : list) {
				describe(element, out, seen, depth + 1);
				out.append(';');
			}
			out.append(']');
			return;
		}
		if (value instanceof StructurePiece piece) {
			out.append(piece.getClass().getSimpleName()).append('(').append(piece.getBoundingBox()).append(' ').append(piece.getOrientation())
				.append(" depth=").append(piece.getGenDepth());
			for (Class<?> type = piece.getClass(); type != StructurePiece.class && type != Object.class; type = type.getSuperclass()) {
				for (var field : type.getDeclaredFields()) {
					if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
					field.setAccessible(true);
					out.append(' ').append(field.getName()).append('=');
					describe(field.get(piece), out, seen, depth + 1);
				}
			}
			out.append(')');
			return;
		}
		out.append(value.getClass().getSimpleName());
	}

	@Test void regenerationMatchesJava() throws Exception {
		long[] seeds = {0, 42, -7_340_013_412_337L, Long.MIN_VALUE};
		ChunkPos[] chunks = {new ChunkPos(0, 0), new ChunkPos(-1, 3), new ChunkPos(37, -91), new ChunkPos(-6250, 4321), new ChunkPos(1_874_999, -1_874_999)};
		List<Direction> orientations = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
		int cases = 0;
		for (long seed : seeds) {
			for (ChunkPos chunk : chunks) {
				// Saved origins: the createTopPiece origin and an offset one (the saved box, not the chunk, decides).
				int[][] origins = {{chunk.getMinBlockX() - 29, chunk.getMinBlockZ() - 29}, {chunk.getMinBlockX() + 3, chunk.getMinBlockZ() - 61}};
				for (int[] origin : origins) {
					for (int o = 0; o <= orientations.size(); o++) {
						boolean none = o == orientations.size();
						var container = saved(origin[0], origin[1], none ? Direction.NORTH : orientations.get(o), none);
						String context = "seed " + seed + " " + chunk + " origin " + origin[0] + "," + origin[1] + " orientation " + (none ? "null" : orientations.get(o));
						var java = regenerate(chunk, seed, container, false);
						long nativeBefore = NativeStructureStubsAccess.monumentsRegenerated();
						var candidate = regenerate(chunk, seed, container, true);
						assertEquals(nativeBefore + 1, NativeStructureStubsAccess.monumentsRegenerated(), () -> context + " must regenerate natively");
						assertEquals(java.save(CONTEXT), candidate.save(CONTEXT), () -> context + " saved pieces");
						assertEquals(describe(java), describe(candidate), () -> context + " building, rooms and child pieces");
						long[] state = new long[1];
						assertNotNull(NativeStructureStubsAccess.regenerate(seed, chunk, origin[0], origin[1], none ? null : orientations.get(o), state), context);
						assertEquals(javaFinalState(chunk, seed, container), state[0], () -> context + " final random state");
						cases++;
					}
				}
			}
		}
		System.out.println("MONUMENT_REGENERATION cases=" + cases);
	}

	/** The structural description is not vacuous: it reaches rooms and child pieces, and orientation changes it. */
	@Test void descriptionCoversRoomsAndPieces() throws Exception {
		var chunk = new ChunkPos(5, 9);
		var east = describe(regenerate(chunk, 7, saved(chunk.getMinBlockX() - 29, chunk.getMinBlockZ() - 29, Direction.EAST, false), true));
		var south = describe(regenerate(chunk, 7, saved(chunk.getMinBlockX() - 29, chunk.getMinBlockZ() - 29, Direction.SOUTH, false), true));
		assertTrue(east.contains("room#") && east.contains("childPieces=") && east.contains("OceanMonumentSimpleRoom"), east.substring(0, Math.min(400, east.length())));
		assertTrue(east.length() > 2000, "a full building: " + east.length());
		assertNotEquals(east, south, "orientation changes the regenerated building");
	}

	/** Saved origins outside the planner's safe envelope (|minX|, |minZ| <= 2^30)
	 * decline before the native planner and keep Java's own body, which wraps as
	 * Java does; the envelope's edge still plans natively. Each axis, both bounds,
	 * every orientation and none. */
	@Test void extremeSavedOriginsKeepJava() throws Exception {
		int edge = 1 << 30;
		int[] outside = {Integer.MAX_VALUE - 10, Integer.MIN_VALUE + 10, edge + 1, -edge - 1};
		List<Direction> orientations = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
		var chunk = new ChunkPos(-3, 8);
		int declined = 0;
		for (int value : outside) {
			for (int axis = 0; axis < 2; axis++) {
				int minX = axis == 0 ? value : 100, minZ = axis == 1 ? value : -100;
				for (int o = 0; o <= orientations.size(); o++) {
					boolean none = o == orientations.size();
					Direction direction = none ? Direction.NORTH : orientations.get(o);
					String context = "origin " + minX + "," + minZ + " orientation " + (none ? "null" : direction);
					long[] state = {12345};
					assertNull(NativeStructureStubsAccess.regenerate(7, chunk, minX, minZ, none ? null : direction, state), context + " must decline");
					assertEquals(12345, state[0], () -> context + ": a decline writes nothing");
					var container = saved(minX, minZ, direction, none);
					long before = NativeStructureStubsAccess.monumentsRegenerated();
					var java = regenerate(chunk, 7, container, false);
					var candidate = regenerate(chunk, 7, container, true);
					assertEquals(before, NativeStructureStubsAccess.monumentsRegenerated(), () -> context + " stays on Java's path");
					assertEquals(describe(java), describe(candidate), () -> context);
					declined++;
				}
			}
		}
		for (int value : new int[]{edge, -edge}) {
			var container = saved(value, -value, Direction.WEST, false);
			long before = NativeStructureStubsAccess.monumentsRegenerated();
			var candidate = regenerate(chunk, 7, container, true);
			assertEquals(before + 1, NativeStructureStubsAccess.monumentsRegenerated(), "the envelope edge " + value + " plans natively");
			assertEquals(describe(regenerate(chunk, 7, container, false)), describe(candidate), "edge " + value);
		}
		System.out.println("MONUMENT_EXTREME declined=" + declined);
	}

	@Test void emptyContainerIsReturnedUnchanged() {
		var empty = new PiecesContainer(List.of());
		long before = NativeStructureStubsAccess.monumentsRegenerated();
		assertSame(empty, OceanMonumentStructure.regeneratePiecesAfterLoad(new ChunkPos(3, 4), 42, empty));
		assertEquals(before, NativeStructureStubsAccess.monumentsRegenerated(), "nothing to regenerate");
	}

	/** A declined native regeneration (disabled here) leaves Java's own body: same pieces, nothing native. */
	@Test void declinedRegenerationKeepsJava() throws Exception {
		var chunk = new ChunkPos(11, -7);
		var container = saved(chunk.getMinBlockX() - 29, chunk.getMinBlockZ() - 29, Direction.EAST, false);
		long before = NativeStructureStubsAccess.monumentsRegenerated();
		var java = regenerate(chunk, 99, container, false);
		assertEquals(before, NativeStructureStubsAccess.monumentsRegenerated(), "the Java route ran");
		assertEquals(regenerate(chunk, 99, container, true).save(CONTEXT), java.save(CONTEXT));
		assertEquals(describe(regenerate(chunk, 99, container, true)), describe(java));
	}
}
