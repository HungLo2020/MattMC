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
}
