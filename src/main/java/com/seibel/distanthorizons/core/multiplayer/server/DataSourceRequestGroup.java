package com.seibel.distanthorizons.core.multiplayer.server;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;

import javax.annotation.CheckForNull;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

class DataSourceRequestGroup
{
	public final long pos;
	
	/**
	 * If this variable is true, we definitely know that generation is complete and there's no need for checking column gen steps
	 */
	private volatile boolean worldGenTaskComplete = false;
	
	void markWorldGenTaskComplete()
	{
		this.worldGenTaskComplete = true;
	}
	
	boolean isWorldGenTaskComplete()
	{
		return this.worldGenTaskComplete;
	}
	
	@CheckForNull
	public volatile FullDataSourceV2 fullDataSource = null;
	
	public final ConcurrentMap<Long, RequestData> requestMessages = new ConcurrentHashMap<>();
	public final Semaphore pendingAdditionSemaphore = new Semaphore(Short.MAX_VALUE, true);
	public final AtomicBoolean isClosed = new AtomicBoolean();
	
	
	DataSourceRequestGroup(long pos)
	{
		this.pos = pos;
	}
	
	/** The group owns the source until the compression worker takes it. */
	public synchronized void publishSource(FullDataSourceV2 source)
	{
		if (this.isClosed.get() || this.fullDataSource != null)
		{
			source.close();
		}
		else
		{
			this.fullDataSource = source;
		}
	}

	public synchronized FullDataSourceV2 takeSource()
	{
		FullDataSourceV2 source = this.fullDataSource;
		this.fullDataSource = null;
		return source;
	}

	public void closeSource()
	{
		FullDataSourceV2 source = this.takeSource();
		if (source != null) source.close();
	}

	public boolean tryClose()
	{
		if (!this.isClosed.compareAndSet(false, true))
		{
			return false;
		}
		
		this.pendingAdditionSemaphore.acquireUninterruptibly(Short.MAX_VALUE);
		return true;
	}
	
	public boolean tryAddRequest(RequestData requestData)
	{
		if (this.isClosed.get() || !this.pendingAdditionSemaphore.tryAcquire())
		{
			return false;
		}
		
		try
		{
			if (this.isClosed.get()) return false;
			this.requestMessages.put(requestData.futureId(), requestData);
		}
		finally
		{
			this.pendingAdditionSemaphore.release();
		}
		
		return true;
	}
	
	
	public RequestData tryRemoveRequest(long requestId, IHangingRequestTransferConsumer hangingRequestTransferConsumer)
	{
		RequestData removed = this.requestMessages.remove(requestId);
		
		if (this.requestMessages.isEmpty())
		{
			boolean closedHere = this.tryClose();
			this.closeSource();
			if (closedHere) hangingRequestTransferConsumer.accept(this.requestMessages.values());
		}
		
		return removed;
	}
	
	
	static class RequestData
	{
		public final ServerPlayerState serverPlayerState;
		public final ServerPlayerState.RateLimiterSet rateLimiterSet;
		
		private final AtomicBoolean completed = new AtomicBoolean();
		private Runnable removePendingTask;
		public synchronized void trackPendingTask(Runnable removeTask) { this.removePendingTask = removeTask; }
		public synchronized void taskStarted() { this.removePendingTask = null; }
		public final boolean sync;
		public final FullDataSourceRequestMessage message;
		public boolean isComplete() { return this.completed.get(); }
		public boolean complete()
		{
			if (!this.completed.compareAndSet(false, true)) return false;
			if (this.sync) this.rateLimiterSet.syncOnLoginRateLimiter.release();
			else this.rateLimiterSet.generationRequestRateLimiter.release();
			Runnable removeTask;
			synchronized (this) { removeTask = this.removePendingTask; this.removePendingTask = null; }
			if (removeTask != null) removeTask.run();
			return true;
		}
		public long futureId() { return this.message.futureId; }
		public long sectionPos() { return this.message.sectionPos; }
		
		RequestData(ServerPlayerState serverPlayerState, FullDataSourceRequestMessage message, ServerPlayerState.RateLimiterSet rateLimiterSet)
		{ this(serverPlayerState, message, rateLimiterSet, false); }

		RequestData(ServerPlayerState serverPlayerState, FullDataSourceRequestMessage message, ServerPlayerState.RateLimiterSet rateLimiterSet, boolean sync)
		{
			this.sync = sync;
			this.serverPlayerState = serverPlayerState;
			this.rateLimiterSet = rateLimiterSet;
			this.message = message;
		}
		
	}
	
	/**
	 * While closing this group, some requests may slip through and end up lost. <br>
	 * This is a workaround that allows the caller to transfer these requests to a new group.
	 */
	@FunctionalInterface
	interface IHangingRequestTransferConsumer extends Consumer<Collection<RequestData>> { }
	
}
