package com.seibel.distanthorizons.core.render.glObject;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GLProxyRenderThreadTaskTest
{
	@Test
	public void lodUploadDrainRunsMinimumTaskCountEvenWhenTimeBudgetExpires()
	{
		GLProxy.resetDrainStatsForTesting();
		AtomicInteger executedTasks = new AtomicInteger();
		for (int i = 0; i < 5; i++)
		{
			GLProxy.queueRunningOnRenderThread(() ->
			{
				executedTasks.incrementAndGet();
				LockSupport.parkNanos(1_000_000L);
			});
		}
		
		int firstDrainCount = GLProxy.runRenderThreadTasksForTesting(1L, 3);
		assertEquals(3, firstDrainCount);
		assertEquals(3, executedTasks.get());
		
		int secondDrainCount = GLProxy.runRenderThreadTasksForTesting(Long.MAX_VALUE, 0);
		assertEquals(2, secondDrainCount);
		assertEquals(5, executedTasks.get());
	}

	@Test
	public void lodUploadDrainExitsBoundedlyWithLargeBacklog()
	{
		GLProxy.resetDrainStatsForTesting();
		System.setProperty("mattmc.dhUploadDrainDiagnostics", "true");
		try
		{
			AtomicInteger executedTasks = new AtomicInteger();
			for (int i = 0; i < 100; i++)
			{
				GLProxy.queueRunningOnRenderThread(() ->
				{
					executedTasks.incrementAndGet();
					LockSupport.parkNanos(1_000_000L);
				});
			}

			int firstDrainCount = GLProxy.runRenderThreadTasksForTesting(1L, 4);
			GLProxy.DrainStats stats = GLProxy.drainStatsForTesting();

			assertEquals(4, firstDrainCount);
			assertEquals(4, executedTasks.get());
			assertEquals(96, stats.queuedAfter());
			assertEquals(1, stats.frames());
			assertEquals(4, stats.tasksProcessed());
			assertEquals(1, stats.framesHittingTimeLimit());
			assertEquals(1, stats.framesUsingMinimumTaskFloor());
			assertEquals(100, stats.maxObservedBacklog());
		}
		finally
		{
			System.clearProperty("mattmc.dhUploadDrainDiagnostics");
			GLProxy.resetDrainStatsForTesting();
		}
	}

	@Test
	public void defaultOpenGlDrainPolicyDoesNotUseMinimumTaskFloor()
	{
		GLProxy.resetDrainStatsForTesting();
		AtomicInteger executedTasks = new AtomicInteger();
		for (int i = 0; i < 5; i++)
		{
			GLProxy.queueRunningOnRenderThread(() ->
			{
				executedTasks.incrementAndGet();
				LockSupport.parkNanos(1_000_000L);
			});
		}

		int drainCount = GLProxy.runRenderThreadTasksForTesting(1L, 0);
		assertEquals(1, drainCount);
		assertEquals(1, executedTasks.get());

		int remainingCount = GLProxy.runRenderThreadTasksForTesting(Long.MAX_VALUE, 0);
		assertEquals(4, remainingCount);
		assertEquals(5, executedTasks.get());
	}
}
