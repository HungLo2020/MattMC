package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.StatsMap;
import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Java representation of one or more OpenGL buffers for rendering.
 *
 * @see ColumnRenderBufferBuilder
 */
public class LodBufferContainer implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	/** number of bytes a single quad takes */
	public static final int QUADS_BYTE_SIZE = LodUtil.LOD_VERTEX_FORMAT.getByteSize() * 4;
	/** how big a single VBO can be in bytes */
	public static final int MAX_VBO_BYTE_SIZE = 10 * 1024 * 1024; // 10 MB
	public static final int MAX_QUADS_PER_BUFFER = MAX_VBO_BYTE_SIZE / QUADS_BYTE_SIZE;
	public static final int FULL_SIZED_BUFFER = MAX_QUADS_PER_BUFFER * QUADS_BYTE_SIZE;
	
	
	/** the position closest to minimum X/Z infinity and the level's lowest Y */
	public final DhBlockPos minCornerBlockPos;
	public final long pos;
	
	public boolean buffersUploaded = false;
	/** True after this container published an immutable CPU semantic asset for
	 * the selected Rust whole-frame route.  This is a lifecycle state, not a
	 * Java GPU upload: retaining the container lets the owning render section
	 * retire that asset from {@link #close()} at the normal DH boundary. */
	private boolean rustSemanticBuffersPublished = false;
	private long rustSemanticColumnGeneration = 0L;
	private net.vulkanic.world.DistantHorizonsSemanticCollector.SemanticColumnLease rustSemanticColumnLease;
	
	
	private CompletableFuture<LodBufferContainer> uploadFuture = null;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public LodBufferContainer(long pos, DhBlockPos minCornerBlockPos)
	{
		this.pos = pos;
		this.minCornerBlockPos = minCornerBlockPos;
	}
	
	
	
	//==================//
	// buffer uploading //
	//==================//
	
	/** Should be run on a DH thread. */
	public synchronized CompletableFuture<LodBufferContainer> makeAndUploadBuffersAsync(LodQuadBuilder builder)
	{
		// separate variable to prevent race condition when checking null
		CompletableFuture<LodBufferContainer> future = this.uploadFuture;
		if (future != null)
		{
			// upload already in process
			return future;
		}
		
		// new upload needed
		future = new CompletableFuture<>();
		this.uploadFuture = future;
		
		boolean rustWholeFrameSemanticBuild = net.vulkanic.world.DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild();
		if (rustWholeFrameSemanticBuild)
		{
			return makeAndPublishRustSemanticBuffers(builder, future);
		}
		// DH rendering is off: Rust is the only LOD consumer, so there is
		// nothing to build or upload for this column.
		this.uploadFuture = null;
		future.complete(this);
		return future;
	}

	private CompletableFuture<LodBufferContainer> makeAndPublishRustSemanticBuffers(
		LodQuadBuilder builder,
		CompletableFuture<LodBufferContainer> future
	) {
		try {
			if (!net.vulkanic.world.DistantHorizonsSemanticCollector.enabled()) {
				throw new IllegalStateException("Rust whole-frame DH route selected without semantic collection");
			}
			LodQuadBuilder.SemanticVertexBufferBuild opaque = builder.makeOpaqueRustSemanticBuffers();
			LodQuadBuilder.SemanticVertexBufferBuild transparent = builder.makeTransparentRustSemanticBuffers();
			LodQuadBuilder.SemanticVertexBufferBuild transparentUp = builder.makeTransparentUpRustSemanticBuffers();
			LodQuadBuilder.SemanticVertexBufferBuild transparentWaterUp = builder.makeTransparentWaterUpRustSemanticBuffers();
			this.rustSemanticColumnLease = net.vulkanic.world.DistantHorizonsSemanticCollector.recordOwnedRustSemanticBuiltColumn(
				this.pos, this.minCornerBlockPos, builder.semanticMaterials(), builder.semanticQuadCoverage(),
				LodQuadBuilder.semanticQuadCoverage(opaque, transparent, transparentUp, transparentWaterUp),
				opaque, transparent, transparentUp, transparentWaterUp
			);
			this.rustSemanticColumnGeneration = this.rustSemanticColumnLease.generation();
			this.rustSemanticBuffersPublished = true;
			// This route intentionally owns no Java GL VBO and schedules no Java GL
			// upload. The immutable semantic packets now belong to the collector.
			this.uploadFuture = null;
			future.complete(this);
		} catch (Exception error) {
			this.uploadFuture = null;
			future.completeExceptionally(error);
			LOGGER.error("Unexpected issue building Rust semantic buffer [" + this.minCornerBlockPos + "]", error);
		}
		return future;
	}

	
	
	//================//
	// helper methods //
	//================//
	
	/** can be used when debugging */
	
	/** can be used when debugging */
	
	public boolean uploadInProgress() { return this.uploadFuture != null; }
	public boolean renderDataReady() { return this.buffersUploaded || this.rustSemanticBuffersPublished; }
	/** A completed Rust build with no drawable segments is still a valid DH
	 * quadtree result. It needs no zero-byte Vulkan asset. */
	public boolean rustSemanticBuildHasNoDrawableGeometry()
	{
		return this.rustSemanticBuffersPublished && this.rustSemanticColumnGeneration == 0L;
	}

	/** For the render walk: -1 before a Rust build is published (or after
	 * close), otherwise the generation built, 0 for no drawable geometry. The
	 * walk asks the collector whether it is still current
	 * ({@link #rustSemanticBuildLifecycleCurrent}), batched for the frame. */
	public long rustSemanticWalkGeneration()
	{
		return this.rustSemanticBuffersPublished ? this.rustSemanticColumnGeneration : -1L;
	}

	/** True while this container still owns the collector generation it built. */
	public boolean rustSemanticBuildLifecycleCurrent()
	{
		return this.rustSemanticBuffersPublished
			&& (this.rustSemanticColumnGeneration == 0L
				|| net.vulkanic.world.DistantHorizonsSemanticCollector.hasColumn(
					this.pos, this.rustSemanticColumnGeneration));
	}
	
	public void debugDumpStats(StatsMap statsMap)
	{
		statsMap.incStat("RenderBuffers");
		statsMap.incStat("SimpleRenderBuffers");
	}
	
	
	
	
	//================//
	// base overrides //
	//================//
	
	/**
	 * This method is called when object is no longer in use.
	 * Called either after uploadBuffers() returned false (On buffer Upload
	 * thread), or by others when the object is not being used. (not in build,
	 * upload, or render state). 
	 */
	@Override
	public synchronized void close()
	{
		this.buffersUploaded = false;
		boolean ownedRustSemanticLifecycle = this.rustSemanticBuffersPublished;
		this.rustSemanticBuffersPublished = false;
		// Keep the copied semantic asset lifecycle aligned with the legacy LOD
		// container. This touches no native renderer object or GL state.
		if (this.rustSemanticColumnLease != null)
		{
			this.rustSemanticColumnLease.close();
			this.rustSemanticColumnGeneration = 0L;
		}
		else if (!ownedRustSemanticLifecycle)
		{
			// Legacy observation snapshots do not retain their generation on this
			// container and therefore still retire by identity. A Rust semantic
			// empty build owns generation zero and must never erase a later non-empty generation
			// when this old lifecycle marker closes asynchronously.
			net.vulkanic.world.DistantHorizonsSemanticCollector.removeColumn(this.pos);
		}
	}
	
}
