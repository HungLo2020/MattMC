package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SectionKeyOrderTest {
	private record Section(long key, String name) {
	}

	@Test
	void identicalSetsGetIdenticalOrderRegardlessOfInput() {
		var order = new SectionKeyOrder();
		var first = new ArrayList<>(List.of(new Section(30, "c"), new Section(-5, "a"), new Section(7, "b")));
		var second = new ArrayList<>(List.of(new Section(7, "b"), new Section(30, "c"), new Section(-5, "a")));
		order.sort(first, Section::key);
		order.sort(second, Section::key);
		assertEquals(first, second);
		assertEquals(List.of(-5L, 7L, 30L), first.stream().map(Section::key).toList());
	}

	@Test
	void extremeKeysSortSignedAndEqualKeysStayStable() {
		var order = new SectionKeyOrder();
		var list = new ArrayList<>(List.of(
			new Section(Long.MAX_VALUE, "max"), new Section(0, "zero-first"),
			new Section(Long.MIN_VALUE, "min"), new Section(0, "zero-second")));
		order.sort(list, Section::key);
		assertEquals(List.of("min", "zero-first", "zero-second", "max"),
			list.stream().map(Section::name).toList());
	}

	@Test
	void scratchGrowsAndReusesAcrossSizes() {
		var order = new SectionKeyOrder();
		var large = new ArrayList<Section>();
		for (int index = 5000; index > 0; index--) large.add(new Section(index * 31L, "s" + index));
		order.sort(large, Section::key);
		for (int index = 1; index < large.size(); index++) {
			assertEquals(true, large.get(index - 1).key() < large.get(index).key());
		}
		var small = new ArrayList<>(List.of(new Section(2, "b"), new Section(1, "a")));
		order.sort(small, Section::key);
		assertEquals(List.of("a", "b"), small.stream().map(Section::name).toList());
	}
}
