package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldAssetRegistryTest {
	@Test
	void keysAddedSinceAreExactlyThoseAbsentAtTheMark() {
		var map = new WorldAssetRegistry<Long, String>(Long.class);
		map.put(1L, "a");
		map.put(2L, "b");
		var mark = map.mark();
		map.put(1L, "replaced"); // membership unchanged
		map.put(3L, "c");
		map.remove(2L);
		map.put(2L, "b again"); // present at the mark: not "added"
		map.put(4L, "d");
		map.remove(4L); // added then removed: still absent at the mark
		map.putAll(Map.of(5L, "e"));
		assertEquals(List.of(3L, 4L, 5L), map.keysAddedSince(mark));
	}

	@Test
	void resetExpiresEarlierMarksAndRejectsUnjournaledMutators() {
		var map = new WorldAssetRegistry<Long, String>(Long.class);
		var mark = map.mark();
		map.resetJournal();
		assertThrows(IllegalStateException.class, () -> map.keysAddedSince(mark));
		assertThrows(UnsupportedOperationException.class, () -> map.putIfAbsent(1L, "a"));
		assertThrows(UnsupportedOperationException.class, () -> map.computeIfAbsent(1L, key -> "a"));
		assertThrows(UnsupportedOperationException.class, () -> map.remove(1L, "a"));
	}

	@Test
	void numericIdentityAndNullPayloadDoNotConflateMembership() {
		var map = new WorldAssetRegistry<Long, String>(Long.class);
		map.put(Long.MIN_VALUE, "minimum"); map.put(0L, null); map.put(Long.MAX_VALUE, "maximum");
		assertEquals(List.of(Long.MIN_VALUE, 0L, Long.MAX_VALUE), List.copyOf(map.keySet()));
		org.junit.jupiter.api.Assertions.assertTrue(map.containsKey(0L));
		org.junit.jupiter.api.Assertions.assertFalse(map.containsKey(Integer.valueOf(0)));
		assertEquals(null, map.get(Integer.valueOf(0)));
		assertEquals("minimum", map.remove(Long.MIN_VALUE)); map.put(-1L, "reused");
		assertEquals(List.of(0L, Long.MAX_VALUE, -1L), List.copyOf(map.keySet()));
		var ints = new WorldAssetRegistry<Integer, String>(Integer.class);
		ints.put(Integer.MIN_VALUE, "int");
		assertEquals(List.of(Integer.MIN_VALUE), List.copyOf(ints.keySet()));
		assertEquals(null, ints.get(Long.valueOf(Integer.MIN_VALUE)));
	}

	@Test
	void iterationUsesLiveValuesAndRejectsStructuralChangesAfterGrowth() {
		var map = new WorldAssetRegistry<Long, String>(Long.class);
		map.put(1L, "old"); map.put(2L, "second");
		var iterator = map.values().iterator(); map.put(1L, "replacement");
		assertEquals("replacement", iterator.next());
		for (long i = 3; i < 8192; i++) map.put(i, "value");
		assertThrows(java.util.ConcurrentModificationException.class, iterator::next);
		assertEquals(8191, map.size()); assertEquals(8191, map.values().stream().count());
		var mark = map.mark(); map.clear(); map.put(2L, "restored"); map.put(-1L, "new");
		assertEquals(List.of(-1L), map.keysAddedSince(mark));
		assertEquals(List.of(2L, -1L), List.copyOf(map.keySet()));
	}

	@Test
	void journalBoundExpiresOldMarksAndResultsRemainIndependent() {
		var map = new WorldAssetRegistry<Long, String>(Long.class);
		var mark = map.mark(); map.put(1L, "a");
		var result = map.keysAddedSince(mark); map.put(2L, "b");
		assertEquals(List.of(1L, 2L), map.keysAddedSince(mark)); assertEquals(List.of(1L), result);
		assertThrows(IndexOutOfBoundsException.class, () -> map.keysAddedSince(new MembershipJournalMark(mark.epoch(), -1)));
		for (int i = 0; i < (1 << 17); i++) { map.put(3L, "c"); map.remove(3L); }
		assertThrows(IllegalStateException.class, () -> map.keysAddedSince(mark));
	}

	@Test
	void rigProofSeesPayloadChangesAndCannotSurviveSlotReuse() {
		var meshes = new WorldAssetRegistry<Long, String>(Long.class);
		var textures = new WorldAssetRegistry<Integer, String>(Integer.class);
		meshes.put(7L, "mesh"); textures.put(19, "texture");
		var proof = WorldAssetRegistry.Proof.capture(meshes, new long[]{7}, textures, new long[]{19});
		org.junit.jupiter.api.Assertions.assertNotNull(proof);
		meshes.put(88L, "unrelated"); textures.put(33, "other");
		org.junit.jupiter.api.Assertions.assertTrue(proof.valid());
		textures.put(19, "new payload"); org.junit.jupiter.api.Assertions.assertFalse(proof.valid());
		org.junit.jupiter.api.Assertions.assertTrue(proof.refreshAfterAdmission());
		org.junit.jupiter.api.Assertions.assertTrue(proof.valid());
		textures.put(19, "new payload"); org.junit.jupiter.api.Assertions.assertTrue(proof.valid());
		proof = WorldAssetRegistry.Proof.capture(meshes, new long[]{7}, textures, new long[]{19});
		meshes.remove(7L); meshes.put(7L, "mesh");
		org.junit.jupiter.api.Assertions.assertFalse(proof.valid());
		org.junit.jupiter.api.Assertions.assertNull(WorldAssetRegistry.Proof.capture(meshes, new long[]{9}, textures, new long[]{19}));
	}
}
