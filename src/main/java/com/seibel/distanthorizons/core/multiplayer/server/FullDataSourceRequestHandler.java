package com.seibel.distanthorizons.core.multiplayer.server;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.level.AbstractDhServerLevel;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import com.seibel.distanthorizons.core.network.exceptions.RequestRejectedException;
import com.seibel.distanthorizons.core.network.exceptions.SectionRequiresSplittingException;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceResponseMessage;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.BeaconBeamDTO;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class FullDataSourceRequestHandler implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder()
			.fileLevelConfig(Config.Common.Logging.logNetworkEventToFile).build();
	private final AbstractDhServerLevel serverLevel;
	private final ThreadPoolExecutor tickerThread;
	private volatile boolean closed;
	private GeneratedFullDataSourceProvider fullDataSourceProvider() { return this.serverLevel.serverside.fullDataFileHandler; }
	private List<BeaconBeamDTO> getAllBeamsForPos(long pos) { return this.serverLevel.beaconBeamRepo.getAllBeamsForPos(pos); }
	private final ConcurrentMap<Long, DataSourceRequestGroup> requestGroupsByPos = new ConcurrentHashMap<>();
	private final ConcurrentMap<Long, DataSourceRequestGroup> requestGroupsByFutureId = new ConcurrentHashMap<>();
	private final ConcurrentMap<Long, DataSourceRequestGroup.RequestData> syncRequestsByFutureId = new ConcurrentHashMap<>();

	public FullDataSourceRequestHandler(AbstractDhServerLevel serverLevel)
	{
		this.serverLevel = serverLevel;
		String levelId = this.serverLevel.getServerLevelWrapper().getDhIdentifier();
		this.tickerThread = ThreadUtil.makeSingleDaemonThreadPool("DataSource Request Ticker ["+levelId+"]");
		this.tickerThread.execute(this::tickLoop);
	}

	public void queueLodSyncForRequestMessage(ServerPlayerState player, FullDataSourceRequestMessage message, ServerPlayerState.RateLimiterSet limits)
	{
		if (!player.sessionConfig.getSynchronizeOnLoad())
		{
			message.sendResponse(new RequestRejectedException("Operation is disabled in config."));
			return;
		}
		if (!limits.syncOnLoginRateLimiter.tryAcquire(message)) return;
		DataSourceRequestGroup.RequestData request = new DataSourceRequestGroup.RequestData(player, message, limits, true);
		this.syncRequestsByFutureId.put(request.futureId(), request);
		PriorityTaskPicker.Executor io = ThreadPoolUtil.getFileHandlerExecutor();
		PriorityTaskPicker.Executor compression = ThreadPoolUtil.getNetworkCompressionExecutor();
		if (this.closed || io == null || compression == null)
		{
			this.reject(request);
			return;
		}
		try
		{
			this.executeSyncTask(request, io, () -> {
				if (request.isComplete()) return;
				try
				{
					long clientTimestamp = message.clientTimestamp != null ? message.clientTimestamp : -1;
					Long serverTimestamp = this.fullDataSourceProvider().getTimestampForPos(message.sectionPos);
					if (serverTimestamp == null || serverTimestamp <= clientTimestamp)
					{
						this.finish(request, null, null);
						return;
					}
					FullDataSourceV2 source = this.fullDataSourceProvider().get(message.sectionPos);
					if (source == null)
					{
						this.finish(request, null, null);
						return;
					}
					// execute/discard are exclusive owners of the retrieved source.
					this.executeSyncTask(request, compression, () -> {
						try (source)
						{
							if (request.isComplete()) return;
							FullDataPayload payload = new FullDataPayload(source, this.getAllBeamsForPos(message.sectionPos));
							this.sendPayload(request, payload);
						}
						catch (Throwable error) { this.reject(request); }
					}, () -> {
						source.close();
						this.reject(request);
					});
				}
				catch (Throwable error) { this.reject(request); }
			}, () -> this.reject(request));
		}
		catch (RejectedExecutionException ignored) { this.reject(request); }
	}

	private void executeSyncTask(DataSourceRequestGroup.RequestData request, PriorityTaskPicker.Executor executor, Runnable command, Runnable onDiscard)
	{
		Runnable task = () -> { request.taskStarted(); command.run(); };
		request.trackPendingTask(() -> executor.remove(task));
		executor.execute(task, onDiscard);
		// Cancellation can happen between recording the task and admission.
		if (request.isComplete()) executor.remove(task);
	}

	public void queueWorldGenForRequestMessage(ServerPlayerState player, FullDataSourceRequestMessage message, ServerPlayerState.RateLimiterSet limits)
	{
		if (!player.sessionConfig.isDistantGenerationEnabled())
		{
			message.sendResponse(new RequestRejectedException("Operation is disabled in config."));
			return;
		}
		if (!limits.generationRequestRateLimiter.tryAcquire(message)) return;
		this.doQueueWorldGenForRequestMessage(new DataSourceRequestGroup.RequestData(player, message, limits));
	}

	private synchronized void doQueueWorldGenForRequestMessage(DataSourceRequestGroup.RequestData request)
	{
		if (this.closed) { this.reject(request); return; }
		while (true)
		{
			DataSourceRequestGroup group = this.requestGroupsByPos.get(request.sectionPos());
			boolean created = group == null;
			if (created) group = new DataSourceRequestGroup(request.sectionPos());
			if (!group.tryAddRequest(request))
			{
				this.requestGroupsByPos.remove(group.pos, group);
				continue;
			}
			// Publish both indexes before an already-complete read can finish the group.
			if (created) this.requestGroupsByPos.put(group.pos, group);
			this.requestGroupsByFutureId.put(request.futureId(), group);
			if (created) this.tryFulfillDataSourceRequestGroup(group, group.pos);
			return;
		}
	}

	public synchronized void cancelRequest(long requestId)
	{
		DataSourceRequestGroup.RequestData sync = this.syncRequestsByFutureId.get(requestId);
		if (sync != null) { this.reject(sync); return; }
		DataSourceRequestGroup group = this.requestGroupsByFutureId.remove(requestId);
		if (group == null) return;
		DataSourceRequestGroup.RequestData removed = group.tryRemoveRequest(requestId, requests -> {
			this.requestGroupsByPos.remove(group.pos, group);
			if (requests.isEmpty()) this.fullDataSourceProvider().removeRetrievalRequestIf(pos -> pos == group.pos);
			else for (DataSourceRequestGroup.RequestData request : requests) this.doQueueWorldGenForRequestMessage(request);
		});
		if (removed != null) this.reject(removed);
	}

	private void tryFulfillDataSourceRequestGroup(DataSourceRequestGroup group, long pos)
	{
		try
		{
			this.fullDataSourceProvider().getAsync(pos).whenComplete((source, error) -> {
				if (error != null || source == null)
				{
					if (source != null) source.close();
					this.rejectGroup(group, new RequestRejectedException("LOD retrieval did not complete."));
					return;
				}
				if (group.isClosed.get()) { source.close(); return; }
				try
				{
					if (this.fullDataSourceProvider().isFullyGenerated(source.columnGenerationSteps))
					{
						group.publishSource(source);
						return;
					}
				}
				catch (Throwable failure)
				{
					source.close();
					this.rejectGroup(group, new RequestRejectedException("LOD retrieval did not complete."));
					return;
				}
				source.close();
				if (group.isClosed.get()) return;
				if (DhSectionPos.getDetailLevel(pos) > (Config.Common.WorldGenerator.distantGeneratorMode.get() == EDhApiDistantGeneratorMode.INTERNAL_SERVER
						? DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL : this.fullDataSourceProvider().lowestDataDetailLevel()))
				{
					this.rejectGroup(group, new SectionRequiresSplittingException());
				}
				else if (group.isWorldGenTaskComplete()) this.tryFulfillDataSourceRequestGroup(group, pos);
				else this.fullDataSourceProvider().queuePositionForRetrieval(pos);
			}).exceptionally(error -> {
				this.rejectGroup(group, new RequestRejectedException("LOD retrieval did not complete."));
				return null;
			});
		}
		catch (Throwable error) { this.rejectGroup(group, new RequestRejectedException("LOD retrieval did not complete.")); }
	}

	public void onWorldGenTaskComplete(long pos)
	{
		DataSourceRequestGroup group = this.requestGroupsByPos.get(pos);
		if (group != null)
		{
			group.markWorldGenTaskComplete();
			this.tryFulfillDataSourceRequestGroup(group, pos);
		}
	}

	private void tickLoop()
	{
		try
		{
			while (!Thread.interrupted()) { Thread.sleep(20); this.tick(); }
		}
		catch (InterruptedException ignored) { }
	}

	private synchronized void tick()
	{
		for (Map.Entry<Long, DataSourceRequestGroup> entry : this.requestGroupsByPos.entrySet())
		{
			DataSourceRequestGroup group = entry.getValue();
			if (group.fullDataSource == null || !group.tryClose()) continue;
			this.requestGroupsByPos.remove(entry.getKey(), group);
			PriorityTaskPicker.Executor executor = ThreadPoolUtil.getNetworkCompressionExecutor();
			if (executor == null) { this.rejectGroup(group, new RequestRejectedException("LOD compression is unavailable.")); continue; }
			try
			{
				executor.execute(() -> {
					try (FullDataSourceV2 source = group.takeSource())
					{
						if (source == null) return;
						FullDataPayload payload = new FullDataPayload(source, this.getAllBeamsForPos(group.pos));
						for (DataSourceRequestGroup.RequestData request : group.requestMessages.values()) this.sendPayload(request, payload);
					}
					catch (Throwable error) { this.rejectGroup(group, new RequestRejectedException("LOD compression did not complete.")); }
				}, () -> this.rejectGroup(group, new RequestRejectedException("LOD compression was cancelled.")));
			}
			catch (RejectedExecutionException ignored) { this.rejectGroup(group, new RequestRejectedException("LOD compression is unavailable.")); }
		}
	}

	private void sendPayload(DataSourceRequestGroup.RequestData request, FullDataPayload payload)
	{
		if (request.isComplete()) return;
		if (request.serverPlayerState.networkSession.isClosed()) { this.reject(request); return; }
		try
		{
			request.serverPlayerState.fullDataPayloadSender.sendInChunks(payload, () -> this.finish(request, payload, null));
		}
		catch (Throwable error) { this.reject(request); }
	}

	private void reject(DataSourceRequestGroup.RequestData request)
	{
		this.finish(request, null, new RequestRejectedException("LOD request was cancelled or its executor is unavailable."));
	}

	private void finish(DataSourceRequestGroup.RequestData request, FullDataPayload payload, Exception failure)
	{
		if (!request.complete()) return;
		if (request.sync) this.syncRequestsByFutureId.remove(request.futureId(), request);
		else
		{
			DataSourceRequestGroup group = this.requestGroupsByFutureId.remove(request.futureId());
			if (group != null) group.requestMessages.remove(request.futureId(), request);
		}
		if (request.serverPlayerState.networkSession.isClosed()) return;
		try
		{
			if (failure == null) request.message.sendResponse(new FullDataSourceResponseMessage(payload));
			else request.message.sendResponse(failure);
		}
		catch (Throwable error) { LOGGER.warn("Unable to send terminal LOD response: " + error.getMessage()); }
	}

	private void rejectGroup(DataSourceRequestGroup group, Exception failure)
	{
		group.tryClose();
		this.requestGroupsByPos.remove(group.pos, group);
		group.closeSource();
		for (DataSourceRequestGroup.RequestData request : group.requestMessages.values()) this.finish(request, null, failure);
		group.requestMessages.clear();
	}

	@Override public synchronized void close()
	{
		this.closed = true;
		this.tickerThread.shutdownNow();
		for (DataSourceRequestGroup.RequestData request : this.syncRequestsByFutureId.values()) this.reject(request);
		HashSet<DataSourceRequestGroup> groups = new HashSet<>(this.requestGroupsByPos.values());
		groups.addAll(this.requestGroupsByFutureId.values());
		for (DataSourceRequestGroup group : groups) this.rejectGroup(group, new RequestRejectedException("LOD request handler closed."));
	}
}
