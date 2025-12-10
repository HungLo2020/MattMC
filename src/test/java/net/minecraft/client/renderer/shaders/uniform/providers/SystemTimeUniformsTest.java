package net.minecraft.client.renderer.shaders.uniform.providers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SystemTimeUniforms.
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class SystemTimeUniformsTest {

	@BeforeEach
	public void setUp() {
		// Reset timer and counter before each test
		SystemTimeUniforms.TIMER.reset();
		SystemTimeUniforms.COUNTER.reset();
	}

	@Test
	public void testFrameCounterIncrement() {
		SystemTimeUniforms.FrameCounter counter = SystemTimeUniforms.COUNTER;
		
		assertEquals(0, counter.getAsInt(), "Counter should start at 0");
		
		counter.beginFrame();
		assertEquals(1, counter.getAsInt(), "Counter should increment to 1");
		
		counter.beginFrame();
		assertEquals(2, counter.getAsInt(), "Counter should increment to 2");
	}

	@Test
	public void testFrameCounterWrapAround() {
		SystemTimeUniforms.FrameCounter counter = SystemTimeUniforms.COUNTER;
		
		// Simulate 720720 frames (wrap around point)
		for (int i = 0; i < 720720; i++) {
			counter.beginFrame();
		}
		
		assertEquals(0, counter.getAsInt(), "Counter should wrap to 0 at 720720");
	}

	@Test
	public void testFrameCounterReset() {
		SystemTimeUniforms.FrameCounter counter = SystemTimeUniforms.COUNTER;
		
		counter.beginFrame();
		counter.beginFrame();
		counter.beginFrame();
		assertEquals(3, counter.getAsInt());
		
		counter.reset();
		assertEquals(0, counter.getAsInt(), "Counter should reset to 0");
	}

	@Test
	public void testTimerInitialState() {
		SystemTimeUniforms.Timer timer = SystemTimeUniforms.TIMER;
		
		assertEquals(0.0f, timer.getFrameTimeCounter(), 0.001f, "frameTimeCounter should start at 0");
		assertEquals(0.0f, timer.getLastFrameTime(), 0.001f, "lastFrameTime should start at 0");
	}

	@Test
	public void testTimerFrameTime() {
		SystemTimeUniforms.Timer timer = SystemTimeUniforms.TIMER;
		
		long startTime = System.nanoTime();
		timer.beginFrame(startTime);
		
		// First frame should have 0 frame time (no previous frame)
		assertEquals(0.0f, timer.getLastFrameTime(), 0.001f);
		
		// Simulate 16ms frame time (60 FPS)
		long secondFrameTime = startTime + 16_000_000L; // 16ms in nanoseconds
		timer.beginFrame(secondFrameTime);
		
		// Should be approximately 0.016 seconds (16ms)
		assertTrue(timer.getLastFrameTime() >= 0.015f && timer.getLastFrameTime() <= 0.017f,
			"Frame time should be approximately 0.016s, got: " + timer.getLastFrameTime());
	}

	@Test
	public void testTimerFrameTimeCounter() {
		SystemTimeUniforms.Timer timer = SystemTimeUniforms.TIMER;
		
		long startTime = System.nanoTime();
		timer.beginFrame(startTime);
		
		assertEquals(0.0f, timer.getFrameTimeCounter(), 0.001f);
		
		// Simulate multiple frames
		for (int i = 1; i <= 10; i++) {
			timer.beginFrame(startTime + (i * 16_000_000L)); // 16ms per frame
		}
		
		// Should have accumulated approximately 10 * 0.016 = 0.16 seconds
		assertTrue(timer.getFrameTimeCounter() >= 0.15f && timer.getFrameTimeCounter() <= 0.17f,
			"Frame time counter should be approximately 0.16s, got: " + timer.getFrameTimeCounter());
	}

	@Test
	public void testTimerCounterReset() {
		SystemTimeUniforms.Timer timer = SystemTimeUniforms.TIMER;
		
		long startTime = System.nanoTime();
		timer.beginFrame(startTime);
		timer.beginFrame(startTime + 16_000_000L);
		
		assertTrue(timer.getFrameTimeCounter() > 0.0f);
		
		timer.reset();
		assertEquals(0.0f, timer.getFrameTimeCounter(), 0.001f);
		assertEquals(0.0f, timer.getLastFrameTime(), 0.001f);
	}

	@Test
	public void testTimerHourlyReset() {
		SystemTimeUniforms.Timer timer = SystemTimeUniforms.TIMER;
		
		long startTime = System.nanoTime();
		timer.beginFrame(startTime);
		
		// Simulate going past 3600 seconds (1 hour)
		// Force frameTimeCounter to be >= 3600
		for (int i = 0; i < 3601; i++) {
			timer.beginFrame(startTime + (i * 1_000_000_000L)); // 1 second per frame
		}
		
		// After hitting 3600 seconds, counter should reset
		assertTrue(timer.getFrameTimeCounter() < 3600.0f, 
			"Frame time counter should reset before hitting 3600s, got: " + timer.getFrameTimeCounter());
	}
}
