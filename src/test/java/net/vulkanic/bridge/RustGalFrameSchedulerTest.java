package net.vulkanic.bridge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RustGalFrameSchedulerTest {
	@Test
	void eachTokenReservesUniqueOrderedSequenceRangeForSemanticSubBatches() {
		RustGalFrameScheduler<String> scheduler = new RustGalFrameScheduler<>("test");
		RustGalFrameScheduler.Token first = scheduler.enqueue(1L, "text", 10, "first");
		RustGalFrameScheduler.Token second = scheduler.enqueue(1L, "text", 10, "second");

		assertEquals(RustGalFrameScheduler.SEQUENCE_STRIDE, second.sequence() - first.sequence());
		assertEquals(List.of("first", "second"), scheduler.takeAll(List.of(first, second), 1L));
	}

	@Test
	void consumesOneSemanticBatchWhenSeveralRenderStateElementsReferenceItsToken() {
		RustGalFrameScheduler<String> scheduler = new RustGalFrameScheduler<>("test");
		RustGalFrameScheduler.Token token = scheduler.enqueue(1L, "image", 10, "split image payload");

		assertEquals(List.of("split image payload"), scheduler.takeAll(List.of(token, token), 1L));
		assertEquals(0, scheduler.pendingCount());
	}

	@Test
	void rejectsConflictingAliasesForTheSameSemanticBatch() {
		RustGalFrameScheduler<String> scheduler = new RustGalFrameScheduler<>("test");
		RustGalFrameScheduler.Token token = scheduler.enqueue(1L, "image", 10, "payload");
		RustGalFrameScheduler.Token conflicting = new RustGalFrameScheduler.Token(
			token.batchId(), token.sequence(), token.generation(), "other-image", token.stratumOrder()
		);

		assertThrows(IllegalStateException.class, () -> scheduler.takeAll(List.of(token, conflicting), 1L));
		assertEquals(1, scheduler.pendingCount());
	}
}
