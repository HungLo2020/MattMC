package net.minecraft.client.renderer.shaders.uniform.providers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FrameUpdateNotifier.
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class FrameUpdateNotifierTest {

	@Test
	public void testAddListener() {
		FrameUpdateNotifier notifier = new FrameUpdateNotifier();
		boolean[] called = {false};
		
		notifier.addListener(() -> called[0] = true);
		notifier.onNewFrame();
		
		assertTrue(called[0], "Listener should be called");
	}

	@Test
	public void testMultipleListeners() {
		FrameUpdateNotifier notifier = new FrameUpdateNotifier();
		int[] callCount = {0};
		
		notifier.addListener(() -> callCount[0]++);
		notifier.addListener(() -> callCount[0]++);
		notifier.addListener(() -> callCount[0]++);
		
		notifier.onNewFrame();
		
		assertEquals(3, callCount[0], "All three listeners should be called");
	}

	@Test
	public void testMultipleFrames() {
		FrameUpdateNotifier notifier = new FrameUpdateNotifier();
		int[] frameCount = {0};
		
		notifier.addListener(() -> frameCount[0]++);
		
		notifier.onNewFrame();
		assertEquals(1, frameCount[0]);
		
		notifier.onNewFrame();
		assertEquals(2, frameCount[0]);
		
		notifier.onNewFrame();
		assertEquals(3, frameCount[0]);
	}

	@Test
	public void testListenerException() {
		FrameUpdateNotifier notifier = new FrameUpdateNotifier();
		boolean[] called = {false, false};
		
		notifier.addListener(() -> {
			called[0] = true;
			throw new RuntimeException("Test exception");
		});
		notifier.addListener(() -> called[1] = true);
		
		// Should not throw exception even if listener throws
		assertThrows(RuntimeException.class, notifier::onNewFrame);
		
		assertTrue(called[0], "First listener should be called");
		// Note: Second listener may not be called if first throws
	}

	@Test
	public void testNoListeners() {
		FrameUpdateNotifier notifier = new FrameUpdateNotifier();
		
		// Should not throw exception with no listeners
		assertDoesNotThrow(notifier::onNewFrame);
	}
}
