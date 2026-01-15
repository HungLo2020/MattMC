package net.distanthorizons.core.util;

import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.util.threading.DhThreadFactory;
import net.distanthorizons.core.util.threading.ThreadPoolUtil;
import net.distanthorizons.coreapi.ModInfo;
import net.distanthorizons.core.logging.DhLogger;

import java.util.concurrent.*;

/**
 * Handles thread pool creation.
 * 
 * @see ThreadPoolUtil
 * @see TimerUtil
 */
public class ThreadUtil
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	public static final String THREAD_NAME_PREFIX = ModInfo.THREAD_NAME_PREFIX;
	
	// TODO move all "Runtime.getRuntime().availableProcessors()" calls here
	
	
	
	//===============//
	// standard pool // 
	//===============//
	
	public static ThreadPoolExecutor makeThreadPool(int poolSize, String name, int priority, boolean isDaemon)
	{
		// this is what was being internally used by Executors.newFixedThreadPool
		// I'm just calling it explicitly here so we can reference the more feature-rich
		// ThreadPoolExecutor vs the more generic ExecutorService
		return new ThreadPoolExecutor(/*corePoolSize*/ poolSize, /*maxPoolSize*/ poolSize,
				0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>(),
				new DhThreadFactory(name, priority, isDaemon));
	}
	
	public static ThreadPoolExecutor makeThreadPool(int poolSize, Class<?> clazz, int priority) { return makeThreadPool(poolSize, clazz.getSimpleName(), priority, false); }
	public static ThreadPoolExecutor makeThreadPool(int poolSize, String name) { return makeThreadPool(poolSize, name, Thread.NORM_PRIORITY, false); }
	public static ThreadPoolExecutor makeThreadPool(int poolSize, Class<?> clazz) { return makeThreadPool(poolSize, clazz.getSimpleName(), Thread.NORM_PRIORITY, false); }
	
	
	
	//====================//
	// single thread pool //
	//====================//
	
	public static ThreadPoolExecutor makeSingleThreadPool(String name, int priority) { return makeThreadPool(1, name, priority, false); }
	public static ThreadPoolExecutor makeSingleThreadPool(Class<?> clazz, int priority) { return makeThreadPool(1, clazz.getSimpleName(), priority, false); }
	public static ThreadPoolExecutor makeSingleThreadPool(String name) { return makeThreadPool(1, name, Thread.NORM_PRIORITY, false); }
	public static ThreadPoolExecutor makeSingleThreadPool(Class<?> clazz) { return makeThreadPool(1, clazz.getSimpleName(), Thread.NORM_PRIORITY, false); }
	
	public static ThreadPoolExecutor makeSingleDaemonThreadPool(String name) { return makeThreadPool(1, name, Thread.NORM_PRIORITY, true); }
	
}
