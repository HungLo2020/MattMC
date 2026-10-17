package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativeDhColumnGroupTest {
	@Test
	void groupedColumnsShareOneArenaAndKeepListSemantics() {
		NativeDhColumnGroup group = new NativeDhColumnGroup();
		NativeDhColumnList first = NativeDhColumnList.empty(group);
		NativeDhColumnList second = NativeDhColumnList.empty(group);
		LongArrayList expectedFirst = new LongArrayList();
		LongArrayList expectedSecond = new LongArrayList();
		for (long value = 1; value <= 40; value++) {
			first.add(value * 3);
			expectedFirst.add(value * 3);
			second.add(0, -value);
			expectedSecond.add(0, -value);
		}
		first.set(5, 99L);
		expectedFirst.set(5, 99L);
		second.removeElements(2, 7);
		expectedSecond.removeElements(2, 7);
		assertEquals(expectedFirst, first);
		assertEquals(expectedSecond, second);
		assertEquals(expectedFirst.hashCode(), first.hashCode());
		// One automatic session for the whole group, not one per column.
		assertSame(first.stagePoints().scope(), second.stagePoints().scope());
		first.clear();
		assertEquals(0, first.size());
		assertEquals(expectedSecond, second);
	}

	@Test
	void ungroupedColumnsKeepTheirOwnArena() {
		NativeDhColumnList first = NativeDhColumnList.empty();
		NativeDhColumnList second = NativeDhColumnList.empty();
		first.add(1L);
		second.add(2L);
		assertNotSame(first.stagePoints().scope(), second.stagePoints().scope());
		assertEquals(LongArrayList.of(1L), first);
		assertEquals(LongArrayList.of(2L), second);
	}

	@Test
	void bulkResizePreservesValuesZerosIdentityAndFrozenNegativeSizeState() {
		var group = new NativeDhColumnGroup();
		var column = NativeDhColumnList.empty(group);
		column.add(11); column.add(12);
		column.size(4096);
		assertEquals(4096, column.size());
		assertEquals(11, column.getLong(0)); assertEquals(12, column.getLong(1));
		for (int i = 2; i < 4096; i++) assertEquals(0, column.getLong(i));
		column.size(1); assertEquals(LongArrayList.of(11), column);
		column.size(3); assertEquals(LongArrayList.of(11, 0, 0), column);
		int hash = column.hashCode(); column.size(3); assertEquals(hash, column.hashCode());
		com.seibel.distanthorizons.core.util.ListUtil.clearAndSetSize(column, 7);
		assertEquals(LongArrayList.wrap(new long[7]), column);
		column.size(-1);assertEquals(-1, column.size());
		assertThrows(ArrayIndexOutOfBoundsException.class, () -> column.size(2));
		assertEquals(-1, column.size());column.clear();assertEquals(0, column.size());
	}
}
