package com.seibel.distanthorizons.core.render;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBufferBuilder;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.fullDatafile.V2.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.render.renderer.generic.BeaconRenderHandler;
import com.seibel.distanthorizons.core.sql.dto.BeaconBeamDTO;
import com.seibel.distanthorizons.core.sql.repo.BeaconBeamRepo;
import com.seibel.distanthorizons.core.util.WorldGenUtil;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jetbrains.annotations.Nullable;

import javax.annotation.WillNotClose;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A render section represents an area that could be rendered.
 * For more information see {@link LodQuadTree}.
 */
public class LodRenderSection implements IDebugRenderable, AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	
	
	public final long pos;
	
	private final IDhClientLevel level;
	private final IClientLevelWrapper levelWrapper;
	@WillNotClose
	private final FullDataSourceProviderV2 fullDataSourceProvider;
	private final LodQuadTree quadTree;
	private final AtomicInteger uploadTaskCountRef;
	
	/** 
	 * contains the list of beacons currently being rendered in this section 
	 * if this list is modified the {@link LodRenderSection#beaconRenderHandler} should be updated to match.
	 */
	private final List<BeaconBeamDTO> activeBeaconList = new ArrayList<>();
	@Nullable
	public final BeaconRenderHandler beaconRenderHandler;
	@Nullable
	public final BeaconBeamRepo beaconBeamRepo;
	
	
	private boolean renderingEnabled = false;
    private net.vulkanic.world.NativeDhVisibility nativeVisibility;
    private long nativeVisibilityToken;
    private long nativeVisibilityEpoch;
    synchronized void bindNativeVisibility(net.vulkanic.world.NativeDhVisibility owner,long token) {
        this.nativeVisibility=owner;this.nativeVisibilityToken=token;this.publishNativeVisibility();this.watchNativeContainerClose();
    }
    private void watchNativeContainerClose() {
        if(this.nativeVisibility==null || this.bufferContainer==null || this.bufferContainer.getClass()!=LodBufferContainer.class) return;
        var owner=this.nativeVisibility;long token=this.nativeVisibilityToken;long epoch=this.nativeVisibilityEpoch;long key=this.pos;
        this.bufferContainer.setRustVisibilityCloseListener(() -> owner.invalidate(key,token,epoch));
    }
    private void publishNativeVisibility() {
        if(this.nativeVisibility==null)return;
        if(this.bufferContainer!=null && this.bufferContainer.getClass()!=LodBufferContainer.class) {
            this.nativeVisibility.requireCompatibility();return;
        }
        long generation=this.closed || this.bufferContainer==null ? -1L : this.bufferContainer.rustSemanticWalkGeneration();
        this.nativeVisibility.state(this.pos,this.nativeVisibilityToken,this.renderingEnabled,generation,this.nativeVisibilityEpoch);
    }
	/** Set by {@link #close()}; guarded by {@code this}. A late build must not install after it. */
	private boolean closed = false;

	/** this reference is necessary so we can determine what VBO to render */
	public LodBufferContainer bufferContainer; 
	
	
	/** 
	 * Encapsulates everything between pulling data from the database (including neighbors)
	 * up to the point when geometry data is uploaded to the GPU.
	 */
	private volatile CompletableFuture<Void> getAndBuildRenderDataFuture = null;
	@Nullable
	public CompletableFuture<Void> getRenderDataBuildFuture() { return this.getAndBuildRenderDataFuture; } 
	
	/** 
	 * used alongside {@link LodRenderSection#getAndBuildRenderDataFuture} so we can remove
	 * unnecessary tasks from the executor.
	 */
	private Runnable getAndBuildRenderDataRunnable = null;
	
	/** 
	 * Represents just uploading the {@link LodQuadBuilder} to the GPU. <br>
	 * Separate from {@link LodRenderSection#getAndBuildRenderDataFuture} because they run on
	 * different threads (buffer uploading is on the MC render thread) and need to be canceled separately.
	 */
	private CompletableFuture<LodBufferContainer> bufferUploadFuture = null;

	/** 
	 * should be an empty array if no positions need to be generated
	 * 
	 * @deprecated see the comment where this variable is set
	 */
	@Nullable
	@Deprecated
	private Supplier<LongArrayList> missingGenerationPosFunc;
	private LongArrayList getMissingGenerationPos() { return this.missingGenerationPosFunc != null ? this.missingGenerationPosFunc.get() : null; }
	
	private boolean checkedIfFullDataSourceExists = false;
	private boolean fullDataSourceExists = false;
	
	
	
	//=============//
	// constructor //
	//=============//
	//region constructor
	
	public LodRenderSection(
			long pos, 
			LodQuadTree quadTree, 
			IDhClientLevel level, FullDataSourceProviderV2 fullDataSourceProvider,
			AtomicInteger uploadTaskCountRef)
	{
		this.pos = pos;
		this.quadTree = quadTree;
		this.level = level;
		this.levelWrapper = level.getClientLevelWrapper();
		this.fullDataSourceProvider = fullDataSourceProvider;
		this.uploadTaskCountRef = uploadTaskCountRef;
		
		this.beaconRenderHandler = this.quadTree.beaconRenderHandler;
		this.beaconBeamRepo = this.level.getBeaconBeamRepo();
			
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus);
	}
	
	//endregion constructor
	
	
	
	//======================================//
	// render data generation and uploading //
	//======================================//
	//region render data uploading
	
	/** @return true if the upload started, false if it wasn't able to for any reason */
	public synchronized boolean uploadRenderDataToGpuAsync()
	{
		if (this.closed || !net.vulkanic.world.DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild()
			|| this.getAndBuildRenderDataFuture != null)
		{
			return false;
		}
		PriorityTaskPicker.Executor executor = ThreadPoolUtil.getRenderLoadingExecutor();
		if (executor == null || executor.isShutdown())
		{
			return false;
		}
		if (this.uploadTaskCountRef.getAndIncrement() > executor.getPoolSize())
		{
			this.uploadTaskCountRef.decrementAndGet();
			return false;
		}

		CompletableFuture<Void> future = new CompletableFuture<>();
		Runnable command = () ->
		{
			if (future.isDone())
			{
				return;
			}
			try
			{
				this.getAndRefreshRenderingBeacons();
				this.getAndUploadRenderDataToGpuAsync(future).whenComplete((ignored, failure) ->
				{
					if (failure == null) future.complete(null);
					else future.completeExceptionally(failure);
				});
			}
			catch (Throwable failure)
			{
				future.completeExceptionally(failure);
			}
		};
		this.getAndBuildRenderDataFuture = future;
		this.getAndBuildRenderDataRunnable = command;
		future.whenComplete((ignored, failure) ->
		{
			this.uploadTaskCountRef.decrementAndGet();
			synchronized (this)
			{
				if (this.getAndBuildRenderDataFuture == future)
				{
					this.getAndBuildRenderDataFuture = null;
					this.getAndBuildRenderDataRunnable = null;
				}
			}
			// The captured executor owns this command even if ThreadPoolUtil was replaced.
			executor.remove(command);
		});
		try
		{
			net.vulkanic.world.DistantHorizonsSemanticCollector.recordSemanticBuildAttempt(this.pos);
			executor.execute(command, () -> future.cancel(false));
			return true;
		}
		catch (RejectedExecutionException ignored)
		{
			future.cancel(false);
			return false;
		}
		catch (RuntimeException | Error failure)
		{
			future.completeExceptionally(failure);
			throw failure;
		}
	}

	private CompletableFuture<Void> getAndUploadRenderDataToGpuAsync(CompletableFuture<Void> owner)
	{
		CompletableFuture<ColumnRenderSource> centerFuture = this.getRenderSourceForPosAsync(this.pos, null);
		owner.whenComplete((ignored, failure) -> { if (owner.isCancelled()) centerFuture.cancel(false); });
		return centerFuture.thenCompose(center ->
		{
			if (center == null) return CompletableFuture.completedFuture(null);
			if (owner.isDone())
			{
				center.close();
				return CompletableFuture.completedFuture(null);
			}
			@SuppressWarnings("unchecked")
			CompletableFuture<ColumnRenderSource>[] neighbors = new CompletableFuture[4];
			try
			{
				for (int index = 0; index < EDhDirection.CARDINAL_COMPASS.length; index++)
				{
					EDhDirection direction = EDhDirection.CARDINAL_COMPASS[index];
					neighbors[direction.compassIndex] = Config.Client.Advanced.Graphics.Experimental.onlyLoadCenterLods.get()
						? CompletableFuture.completedFuture(null) : this.getRenderSourceForPosAsync(this.pos, direction);
				}
				return this.buildRenderDataAsync(center, neighbors, owner);
			}
			catch (Throwable failure)
			{
				center.close();
				for (CompletableFuture<ColumnRenderSource> neighbor : neighbors)
				{
					if (neighbor != null) neighbor.thenAccept(source -> { if (source != null) source.close(); });
				}
				return CompletableFuture.failedFuture(failure);
			}
		});
	}

	/** Owns every successful load until the aggregate terminates, including exceptional allOf results. */
	CompletableFuture<Void> buildRenderDataAsync(ColumnRenderSource center,
		CompletableFuture<ColumnRenderSource>[] neighbors, CompletableFuture<Void> owner)
	{
		owner.whenComplete((ignored, failure) ->
		{
			if (owner.isCancelled())
			{
				for (CompletableFuture<ColumnRenderSource> neighbor : neighbors) neighbor.cancel(false);
			}
		});
		return CompletableFuture.allOf(neighbors).thenCompose(ignored ->
		{
			if (owner.isDone()) return CompletableFuture.completedFuture(null);
			boolean transparency = Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
			try (LodQuadBuilder builder = new LodQuadBuilder(transparency, this.level.getClientLevelWrapper()))
			{
				ColumnRenderSource[] adjacent = new ColumnRenderSource[EDhDirection.CARDINAL_COMPASS.length];
				boolean[] sameDetail = new boolean[EDhDirection.CARDINAL_COMPASS.length];
				for (EDhDirection direction : EDhDirection.CARDINAL_COMPASS)
				{
					adjacent[direction.compassIndex] = neighbors[direction.compassIndex].join();
					sameDetail[direction.compassIndex] = this.isAdjacentPosSameDetailLevel(direction);
				}
				ColumnRenderBufferBuilder.makeLodRenderData(builder, center, this.level, adjacent, sameDetail);
				// Packet construction is synchronous; the collector owns its copies.
				return this.uploadToGpuAsync(builder, owner);
			}
		}).whenComplete((ignored, failure) ->
		{
			try (center;
				ColumnRenderSource north = completedSource(neighbors[0]);
				ColumnRenderSource south = completedSource(neighbors[1]);
				ColumnRenderSource west = completedSource(neighbors[2]);
				ColumnRenderSource east = completedSource(neighbors[3]))
			{
				// Close all successfully acquired native owners, even when another load failed.
			}
		});
	}

	private static ColumnRenderSource completedSource(CompletableFuture<ColumnRenderSource> future)
	{
		return future.isCompletedExceptionally() ? null : future.getNow(null);
	}

	/** Each load has its own terminal future and closes a result that loses a cancellation race. */
	private CompletableFuture<ColumnRenderSource> getRenderSourceForPosAsync(long pos, @Nullable EDhDirection direction)
	{
		final long finalPos = direction == null ? pos : DhSectionPos.getAdjacentPos(pos, direction);
		PriorityTaskPicker.Executor executor = ThreadPoolUtil.getRenderLoadingExecutor();
		CompletableFuture<ColumnRenderSource> future = new CompletableFuture<>();
		if (executor == null)
		{
			future.cancel(false);
			return future;
		}
		Runnable command = () ->
		{
			if (future.isDone()) return;
			try (FullDataSourceV2 fullData = direction == null
				? this.fullDataSourceProvider.get(finalPos)
				: this.fullDataSourceProvider.getAdjForDirection(finalPos, direction.opposite()))
			{
				ColumnRenderSource source = FullDataToRenderDataTransformer.transformFullDataToRenderSource(fullData, this.levelWrapper);
				if (!future.complete(source) && source != null) source.close();
			}
			catch (Throwable failure)
			{
				future.completeExceptionally(failure);
				LOGGER.error("Unexpected issue creating render data for pos: [" + DhSectionPos.toString(finalPos) + "].", failure);
			}
		};
		future.whenComplete((source, failure) -> { if (future.isCancelled()) executor.remove(command); });
		try
		{
			executor.execute(command, () -> future.cancel(false));
		}
		catch (RejectedExecutionException ignored)
		{
			future.cancel(false);
		}
		return future;
	}
	private boolean isAdjacentPosSameDetailLevel(EDhDirection direction)
	{
		long adjPos = DhSectionPos.getAdjacentPos(this.pos, direction);
		byte detailLevel = this.quadTree.calculateExpectedDetailLevel(new DhBlockPos2D(MC.getPlayerBlockPos()), adjPos);
		detailLevel += DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
		return detailLevel == DhSectionPos.getDetailLevel(this.pos);
	}
	private CompletableFuture<Void> uploadToGpuAsync(LodQuadBuilder builder, CompletableFuture<Void> owner)
	{
		if (owner.isDone()) return CompletableFuture.completedFuture(null);
		CompletableFuture<LodBufferContainer> upload = ColumnRenderBufferBuilder.uploadBuffersAsync(this.level, this.pos, builder);
		synchronized (this)
		{
			if (this.getAndBuildRenderDataFuture == owner && !owner.isDone()) this.bufferUploadFuture = upload;
		}
		return upload.thenAccept(buffer -> this.installBuiltContainer(buffer, owner)).whenComplete((ignored, failure) ->
		{
			synchronized (this)
			{
				if (this.bufferUploadFuture == upload) this.bufferUploadFuture = null;
			}
		});
	}

	/**
	 * Swaps in a finished build. The build completes on a DH worker and can
	 * outlive {@link #close()}; a container arriving after close is closed here
	 * instead of being installed, since nothing would ever close it later.
	 */
	void installBuiltContainer(LodBufferContainer buffer)
	{
		this.installBuiltContainer(buffer, null);
	}

	private void installBuiltContainer(LodBufferContainer buffer, @Nullable CompletableFuture<Void> owner)
	{
		LodBufferContainer containerToClose;
		synchronized (this)
		{
			if (this.closed || (owner != null && (owner.isDone() || this.getAndBuildRenderDataFuture != owner)))
			{
				containerToClose = buffer;
			}
			else
			{
				// needed to clean up the old data
				containerToClose = this.bufferContainer;

				// upload complete
				// The Rust whole-frame route owns no Java VBO, but its immutable CPU
				// semantic publication still has the same DH section lifetime. Keep the
				// container so close/reload retires that publication exactly once.
				this.bufferContainer = buffer.renderDataReady() ? buffer : null;
                this.nativeVisibilityEpoch++;
                this.publishNativeVisibility();this.watchNativeContainerClose();
			}
		}

		if (containerToClose != null)
		{
			containerToClose.close();
		}
	}

	/** Detach before closing so a completed worker build cannot be overwritten. */
	void retireBuiltContainer()
	{
		LodBufferContainer retired;
		synchronized (this)
		{
			retired = this.bufferContainer;
			this.bufferContainer = null;
			this.nativeVisibilityEpoch++;
			this.publishNativeVisibility();
		}
		if (retired != null)
		{
			retired.close();
		}
	}
	
	//endregion render data uploading
	
	
	
	//====================//
	// enabling rendering //
	//====================//
	//region enabling rendering
	
	public boolean canRender()
	{
		if (net.vulkanic.world.DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild())
		{
			// Legacy DH treats a completed empty buffer as render-ready. Preserve
			// that quadtree contract without fabricating a zero-byte Vulkan asset.
			if (this.bufferContainer != null
				&& this.bufferContainer.rustSemanticBuildHasNoDrawableGeometry())
			{
				return true;
			}
			// The retained container owns lifecycle only. A CPU-built column is not
			// renderable until Rust acknowledges its immutable asset. Requesting the
			// bounded publication here keeps a covering parent enabled while child
			// assets arrive, matching DH's no-hole parent/child transition contract.
			return net.vulkanic.world.DistantHorizonsSemanticCollector.requestColumnPublication(this.pos);
		}
		return this.bufferContainer != null;
	}

	/** A completed CPU semantic result is waiting for the bounded Rust asset
	 * transaction. It must keep its covering parent visible, but it must not be
	 * mistaken for missing source data and rebuilt every quadtree update. */
	public boolean hasRustSemanticBuildResult()
	{
		return net.vulkanic.world.DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild()
			&& this.bufferContainer != null
			&& this.bufferContainer.renderDataReady()
			&& this.bufferContainer.rustSemanticBuildLifecycleCurrent();
	}
	
	public boolean getRenderingEnabled() { return this.renderingEnabled; }
	/**
	 * Separate from {@link LodRenderSection#onRenderingEnabled} and {@link LodRenderSection#onRenderingDisabled}
	 * since we need to trigger external changes in disabled -> enabled order
	 * so beacons are removed and then re-added.
	 * However, to prevent holes in the world when disabling sections we need to
	 * enable the new section(s) first before disabling the old one(s).
	 */
	public synchronized void setRenderingEnabled(boolean enabled) { this.renderingEnabled = enabled; this.publishNativeVisibility(); }
	
	/** @see LodRenderSection#setRenderingEnabled */
	public void onRenderingEnabled() { this.startRenderingBeacons(); }
	/** @see LodRenderSection#setRenderingEnabled */
	public void onRenderingDisabled() 
	{
		this.stopRenderingBeacons();
		
		if (Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus.get())
		{
			// show that this position has just been disabled
			DebugRenderer.makeParticle(
				new DebugRenderer.BoxParticle(
					new DebugRenderer.Box(this.pos, 128f, 156f, 0.09f, Color.CYAN.darker()),
					0.2, 32f
				)
			);
		}
	}
	
	public boolean gpuUploadInProgress() { return this.getAndBuildRenderDataFuture != null; }
	
	//endregion enabling rendering
	
	
	
	//=================================//
	// full data retrieval (world gen) //
	//=================================//
	//region full data retrieval
	
	public boolean isFullyGenerated()
	{
		LongArrayList missingGenerationPos = this.getMissingGenerationPos();
		return missingGenerationPos != null && missingGenerationPos.isEmpty();
	}
	/** Returns true if an LOD exists, regardless of what data is in it */
	public boolean getFullDataSourceExists() 
	{  
		if (!this.checkedIfFullDataSourceExists)
		{
			this.fullDataSourceExists = this.fullDataSourceProvider.repo.existsWithKey(this.pos);
			this.checkedIfFullDataSourceExists = true;
		}
		
		return this.fullDataSourceExists;
	}
	public void updateFullDataSourceExists() 
	{
		// we don't have any ability to remove LODs so we only
		// need to check if an LOD was previously missing
		if (!this.fullDataSourceExists)
		{
			this.checkedIfFullDataSourceExists = false;
			this.getFullDataSourceExists();
		}
	}
	
	public boolean missingPositionsCalculated() { return this.getMissingGenerationPos() != null; }
	public int ungeneratedPositionCount()
	{
		LongArrayList missingGenerationPos = this.getMissingGenerationPos();
		return missingGenerationPos != null ? missingGenerationPos.size() : 0;
	}
	public int ungeneratedChunkCount()
	{
		LongArrayList missingGenerationPos = this.getMissingGenerationPos();
		if (missingGenerationPos == null)
		{
			return 0;
		}
		
		int chunkCount = 0;
		// get the number of chunks each position contains
		for (int i = 0; i < missingGenerationPos.size(); i++)
		{
			int chunkWidth = DhSectionPos.getChunkWidth(missingGenerationPos.getLong(i));
			chunkCount += (chunkWidth * chunkWidth);
		}
		return chunkCount;
	}
	
	public void tryQueuingMissingLodRetrieval()
	{
		if (this.fullDataSourceProvider.canRetrieveMissingDataSources() 
			&& this.fullDataSourceProvider.canQueueRetrieval())
		{
			// calculate the missing positions if not already done
			if (this.missingGenerationPosFunc == null)
			{
				// TODO memoization is needed for multiplayer, otherwise
				//  new retrieval requests won't be submitted.
				// TODO why is that the case? Shouldn't the missing positions be un-changing?
				// TODO setting this value to low can cause world gen to slow down significantly
				//  due to a race condition where the world gen thinks it is finished, but the results
				//  haven't been saved to file yet, causing the gen to fire again
				this.missingGenerationPosFunc = Suppliers.memoizeWithExpiration(
						() -> this.fullDataSourceProvider.getPositionsToRetrieve(this.pos),
						10, TimeUnit.MINUTES);
			}
			
			LongArrayList missingGenerationPos = this.getMissingGenerationPos();
			if (missingGenerationPos != null)
			{
				// queue from last to first to prevent shifting the array unnecessarily
				for (int i = missingGenerationPos.size() - 1; i >= 0; i--)
				{
					if (!this.fullDataSourceProvider.canQueueRetrieval())
					{
						// the data source provider isn't accepting any more jobs
						break;
					}
					
					long pos = missingGenerationPos.removeLong(i);
					
					boolean posInRange = WorldGenUtil.isPosInWorldGenRange(
						pos,
						Config.Common.WorldGenerator.generationCenterChunkX.get(), Config.Common.WorldGenerator.generationCenterChunkZ.get(),
						Config.Common.WorldGenerator.generationMaxChunkRadius.get()
					);
					if (!posInRange)
					{
						continue;
					}
					
					
					boolean positionQueued = (this.fullDataSourceProvider.queuePositionForRetrieval(pos) != null);
					if (!positionQueued)
					{
						// shouldn't normally happen, but just in case
						missingGenerationPos.add(pos);
					}
				}
			}
		}
	}
	
	//endregion full data retrieval
	
	
	
	//=================//
	// beacon handling //
	//=================//
	//region beacon handling
	
	/** gets the active beacon list and stops/starts beacon rendering as necessary */
	private void getAndRefreshRenderingBeacons()
	{
		// do nothing if beacon rendering or repos are unavailable
		if (this.beaconBeamRepo == null 
			|| this.beaconRenderHandler == null)
		{
			return;
		}
		
		
		// Synchronized to prevent two threads for starting/stopping rendering at once
		// Shouldn't be necessary, but just in case.
		synchronized (this.activeBeaconList)
		{
			List<BeaconBeamDTO> activeBeacons = this.beaconBeamRepo.getAllBeamsForPos(this.pos);
			
			
			// stop rendering current beacons
			for (BeaconBeamDTO beam : this.activeBeaconList)
			{
				this.beaconRenderHandler.stopRenderingBeaconAtPos(beam.blockPos);
			}
			
			// swap old and new active beacon list
			this.activeBeaconList.clear();
			this.activeBeaconList.addAll(activeBeacons);
			
			// start rendering new beacon list
			for (BeaconBeamDTO beam : this.activeBeaconList)
			{
				this.beaconRenderHandler.startRenderingBeacon(beam);
			}
		}
	}
	
	private void stopRenderingBeacons()
	{
		// do nothing if beacon rendering is unavailable
		if (this.beaconRenderHandler == null)
		{
			return;
		}
		
		
		synchronized (this.activeBeaconList)
		{
			for (BeaconBeamDTO beam : this.activeBeaconList)
			{
				this.beaconRenderHandler.stopRenderingBeaconAtPos(beam.blockPos);
			}
		}
	}
	
	private void startRenderingBeacons()
	{
		// do nothing if beacon rendering is unavailable 
		if (this.beaconRenderHandler == null)
		{
			return;
		}
		
		
		synchronized (this.activeBeaconList)
		{
			for (BeaconBeamDTO beam : this.activeBeaconList)
			{
				this.beaconRenderHandler.startRenderingBeacon(beam);
			}
		}
	}
	
	//endregion beacon handling
	
	
	
	//==============//
	// base methods //
	//==============//
	//region base methods
	
	@Override
	public void debugRender(DebugRenderer debugRenderer)
	{
		Color color = Color.red;
		if (this.renderingEnabled)
		{
			color = Color.green;
		}
		else if (this.getAndBuildRenderDataFuture != null)
		{
			color = Color.yellow;
		}
		else if (this.canRender())
		{
			color = Color.cyan;
		}
		
		debugRenderer.renderBox(new DebugRenderer.Box(this.pos, 400, 8f, Objects.hashCode(this), 0.1f, color));
	}
	
	@Override
	public String toString()
	{
		return  "pos=[" + DhSectionPos.toString(this.pos) + "] " +
				"enabled=[" + this.renderingEnabled + "] " +
				"uploading=[" + this.gpuUploadInProgress() + "] ";
	}
	
	@Override
	public void close()
	{
		DebugRenderer.unregister(this, Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus);
		
		if (Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus.get())
		{
			// show a particle for the closed section
			DebugRenderer.makeParticle(
				new DebugRenderer.BoxParticle(
					new DebugRenderer.Box(this.pos, 128f, 156f, 0.09f, Color.RED.darker()),
					0.5, 32f
				)
			);
		}
		
		
		this.stopRenderingBeacons();

		LodBufferContainer container;
		synchronized (this)
		{
			this.closed = true;
            this.publishNativeVisibility();
			container = this.bufferContainer;
		}
		if (container != null)
		{
			container.close();
		}
		
		// The owner callback removes queued work from its original executor, clears
		// retained busy fields and returns the upload slot exactly once. Already
		// running loads still own and close their native results on completion.
		CompletableFuture<Void> buildFuture = this.getAndBuildRenderDataFuture;
		if (buildFuture != null) buildFuture.cancel(false);

		CompletableFuture<LodBufferContainer> uploadFuture = this.bufferUploadFuture;
		if (uploadFuture != null)
		{
			uploadFuture.cancel(true);
		}
		
		
		
		// remove any active world gen requests that may be for this position
		ThreadPoolExecutor executor = ThreadPoolUtil.getCleanupExecutor();
		if (executor == null)
		{
			// thread pools are already shut down, nothing left to cancel
			return;
		}
		// while this should generally be a fast operation
		// this is run on a separate thread to prevent lag on the render thread
		executor.execute(() -> this.fullDataSourceProvider.removeRetrievalRequestIf((genPos) -> DhSectionPos.contains(this.pos, genPos)));
		
	}
	
	//endregion base methods
	
	
	
}
