package net.minecraft.world.level.levelgen;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The freeze verifier's call counts are per placement: another thread's
 * OP_WARM calls, which advance the global aggregate, must not make a
 * placement's count (and so its history check) disagree. */
class NativeFreezeVerificationTest {
	@BeforeAll static void load() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test void otherThreadsCallsDoNotInflateAPlacementsCount() throws Exception {
		long globalBefore = NativeDecoration.WARM_CALLS.sum();
		var placement = NativeFreezeVerification.begin(List.of(), null, BlockPos.ZERO);
		// Another worker's freeze placement makes 7 OP_WARM calls while this one runs.
		var inside = new CountDownLatch(1);
		Thread other = new Thread(() -> {
			try {
				inside.await();
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
			}
			for (int i = 0; i < 7; i++) NativeDecoration.WARM_CALLS.increment();
		}, "other-freeze-placement");
		other.start();
		inside.countDown();
		other.join();
		long globalDelta = NativeDecoration.WARM_CALLS.sum() - globalBefore;
		assertEquals(7, globalDelta, "the other thread's calls land inside this placement's window");

		// The former verifier took the global delta as this placement's count: a false mismatch.
		var former = NativeFreezeVerification.begin(List.of(), null, BlockPos.ZERO);
		former.afterNative(globalDelta);
		assertFalse(former.afterJava(0), "a global delta reports the other thread's calls as this placement's");
		former.finish();

		// This placement made none of those calls: its own count matches Java's replay.
		placement.afterNative(0);
		assertTrue(placement.afterJava(0), "a per-call count has no false mismatch");
		placement.finish();
	}
}
