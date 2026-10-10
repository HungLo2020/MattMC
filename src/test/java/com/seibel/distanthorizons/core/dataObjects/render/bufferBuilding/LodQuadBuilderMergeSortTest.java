package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LodQuadBuilderMergeSortTest {
	@Test
	void primitiveSortMatchesStableComparatorSortForEveryDirection() {
		Random random = new Random(1234);
		for (EDhDirection direction : EDhDirection.values()) {
			for (BufferMergeDirectionEnum merge : BufferMergeDirectionEnum.values()) {
				for (int size : new int[] {0, 1, 2, 17, 500, 4000}) {
					// A small coordinate range forces equal keys, so stability is checked;
					// negative values exercise the sign-extended key bits.
					int range = size > 100 ? 40 : 6;
					assertSameOrder(randomQuads(random, direction, size, range), merge);
				}
			}
		}
	}

	@Test
	void listsBeyondThePrimitiveLimitKeepTheComparatorSort() {
		assertSameOrder(randomQuads(new Random(99), EDhDirection.UP, (1 << 16) + 5, 300), BufferMergeDirectionEnum.EastWest);
	}

	@Test
	void keySignMatchesCompare() {
		Random random = new Random(7);
		for (EDhDirection direction : EDhDirection.values()) {
			ArrayList<BufferQuad> quads = randomQuads(random, direction, 300, 1 << 15);
			for (BufferMergeDirectionEnum merge : BufferMergeDirectionEnum.values()) {
				for (int index = 1; index < quads.size(); index++) {
					BufferQuad a = quads.get(index - 1), b = quads.get(index);
					assertEquals(Integer.signum(a.compare(b, merge)),
						Long.signum(Long.compare(a.mergeSortKey(merge), b.mergeSortKey(merge))));
					assertEquals(0L, a.mergeSortKey(merge) & 0xFFFFL);
				}
			}
		}
	}

	private static void assertSameOrder(ArrayList<BufferQuad> quads, BufferMergeDirectionEnum merge) {
		ArrayList<BufferQuad> expected = new ArrayList<>(quads);
		expected.sort((a, b) -> a.compare(b, merge));
		LodQuadBuilder.sortForMerge(quads, merge);
		assertEquals(expected.size(), quads.size());
		for (int index = 0; index < expected.size(); index++) {
			assertSame(expected.get(index), quads.get(index), "index " + index);
		}
	}

	private static ArrayList<BufferQuad> randomQuads(Random random, EDhDirection direction, int size, int range) {
		ArrayList<BufferQuad> quads = new ArrayList<>(size);
		for (int index = 0; index < size; index++) {
			quads.add(new BufferQuad(
				(short) (random.nextInt(range * 2) - range), (short) (random.nextInt(range * 2) - range),
				(short) (random.nextInt(range * 2) - range), (short) 1, (short) 1,
				index, (byte) 0, (byte) 15, (byte) 0, direction, 0));
		}
		return quads;
	}
}
