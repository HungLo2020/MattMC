package com.seibel.distanthorizons.core.util.threading;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.concurrent.ThreadFactory;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.logging.DhLogger;
import org.jetbrains.annotations.NotNull;

/**
 * Just a simple ThreadFactory to name ExecutorService
 * threads, which is helpful when debugging.
 */
public class DhThreadFactory implements ThreadFactory
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	public final String threadName;
	public final int priority;
	public final boolean isDaemon;
	private int threadCount = 0;
	private final LinkedList<WeakReference<Thread>> threads = new LinkedList<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	/** @param isDaemon should be set to true for static/cleanup threads that aren't manually shut down. */
	public DhThreadFactory(String newThreadName, int priority, boolean isDaemon)
	{
		if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY)
		{
			throw new IllegalArgumentException("Thread priority [" + priority + "] out of bounds. Priority should be between [" + Thread.MIN_PRIORITY + "-" + Thread.MAX_PRIORITY + "]!");
		}
		
		this.threadName = ThreadUtil.THREAD_NAME_PREFIX + newThreadName + " Thread";
		this.priority = priority;
		this.isDaemon = isDaemon;
	}
	
	@Override
	public Thread newThread(@NotNull Runnable runnable)
	{
		Thread thread = new Thread(runnable, this.threadName + "[" + (this.threadCount++) + "]");
		thread.setPriority(this.priority);
		thread.setDaemon(this.isDaemon);
		this.threads.add(new WeakReference<>(thread));
		return thread;
	}
	
	
	
	//===========//
	// debugging //
	//===========//
	
	private static String StackTraceToString(StackTraceElement[] stackTraceArray)
	{
		StringBuilder str = new StringBuilder();
		str.append(stackTraceArray[0]);
		str.append('\n');
		for (int i = 1; i < stackTraceArray.length; i++)
		{
			str.append("  at ");
			str.append(stackTraceArray[i]);
			str.append('\n');
		}
		return str.toString();
	}
	
	public void dumpAllThreadStacks()
	{
		for (WeakReference<Thread> threadRef : this.threads)
		{
			Thread thread = threadRef.get();
			if (thread != null)
			{
				StackTraceElement[] stacks = thread.getStackTrace();
				if (stacks.length != 0)
				{
					LOGGER.info("===========================================\n" +
							"Thread: " + thread.getName() + "\n" +
							StackTraceToString(stacks));
				}
			}
		}
		
		this.threads.removeIf((weakRef) -> weakRef.get() == null);
	}
	
}
