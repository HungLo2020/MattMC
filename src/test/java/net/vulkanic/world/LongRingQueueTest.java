package net.vulkanic.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LongRingQueueTest {
	@Test
	void matchesFastutilFifoAcrossWrapAndGrowth() {
		LongRingQueue queue = new LongRingQueue(4);
		LongArrayFIFOQueue reference = new LongArrayFIFOQueue();
		Random random = new Random(7);
		for (int step = 0; step < 20_000; step++) {
			if (reference.isEmpty() || random.nextInt(5) < 3) {
				long value = random.nextLong();
				queue.enqueue(value);
				reference.enqueue(value);
			} else {
				assertEquals(reference.dequeueLong(), queue.dequeueLong());
			}
			assertEquals(reference.size(), queue.size());
			if (step % 5_000 == 4_999) {
				queue.clear();
				reference.clear();
			}
		}
		while (!reference.isEmpty()) {
			assertEquals(reference.dequeueLong(), queue.dequeueLong());
		}
		assertTrue(queue.isEmpty());
		assertThrows(NoSuchElementException.class, queue::dequeueLong);
	}
}
