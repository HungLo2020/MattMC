package net.vulkanic.bridge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RustGalFrameSchedulerTest {
	@Test
	void eachTokenReservesUniqueOrderedSequenceRangeForSemanticSubBatches() {
		RustGalFrameScheduler<String> scheduler = new RustGalFrameScheduler<>("test");
		RustGalFrameScheduler.Token first = scheduler.enqueue(1L, "text", 10, "first");
		RustGalFrameScheduler.Token second = scheduler.enqueue(1L, "text", 10, "second");

		assertEquals(RustGalFrameScheduler.SEQUENCE_STRIDE, second.sequence() - first.sequence());
		assertEquals(List.of("first", "second"), scheduler.takeAll(List.of(first, second), 1L));
	}
}
