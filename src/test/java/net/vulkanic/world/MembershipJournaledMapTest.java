package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MembershipJournaledMapTest {
	@Test
	void keysAddedSinceAreExactlyThoseAbsentAtTheMark() {
		var map = new MembershipJournaledMap<Long, String>();
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
		var map = new MembershipJournaledMap<Long, String>();
		var mark = map.mark();
		map.resetJournal();
		assertThrows(IllegalStateException.class, () -> map.keysAddedSince(mark));
		assertThrows(UnsupportedOperationException.class, () -> map.putIfAbsent(1L, "a"));
		assertThrows(UnsupportedOperationException.class, () -> map.computeIfAbsent(1L, key -> "a"));
		assertThrows(UnsupportedOperationException.class, () -> map.remove(1L, "a"));
	}
}
