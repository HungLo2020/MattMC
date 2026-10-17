package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.util.LodUtil;

import java.util.concurrent.*;

/**
 * One render section's built LOD column, published to the Rust collector.
 *
 * @see ColumnRenderBufferBuilder
 */
public class LodBufferContainer implements AutoCloseable
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	/** number of bytes a single quad takes */
	public static final int QUADS_BYTE_SIZE = LodUtil.LOD_VERTEX_FORMAT.getByteSize() * 4;
	
	
	/** the position closest to minimum X/Z infinity and the level's lowest Y */
	public final DhBlockPos minCornerBlockPos;
	public final long pos;
	
	/** True after this container published an immutable CPU semantic asset for
	 * the selected Rust whole-frame route.  This is a lifecycle state, not a
	 * Java GPU upload: retaining the container lets the owning render section
	 * retire that asset from {@link #close()} at the normal DH boundary. */
	private boolean rustSemanticBuffersPublished = false;
    private Runnable rustVisibilityCloseListener;
    public synchronized void setRustVisibilityCloseListener(Runnable listener) {
        this.rustVisibilityCloseListener=listener;
        if(!this.rustSemanticBuffersPublished) {this.rustVisibilityCloseListener=null;listener.run();}
    }
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
			try (NativeDhBuiltColumn column = builder.makeNativeRustSemanticColumn()) {
				this.rustSemanticColumnLease = net.vulkanic.world.DistantHorizonsSemanticCollector.recordOwnedNativeRustSemanticBuiltColumn(
					this.pos, this.minCornerBlockPos, builder.semanticMaterials(), builder.semanticQuadCoverage(), column);
			}
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
	
	public boolean renderDataReady() { return this.rustSemanticBuffersPublished; }
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

	/** The collector generation this container built; 0 for none or an empty build. */
	public long rustSemanticColumnGeneration() { return this.rustSemanticColumnGeneration; }

	/** True while this container still owns the collector generation it built. */
	public boolean rustSemanticBuildLifecycleCurrent()
	{
		return this.rustSemanticBuffersPublished
			&& (this.rustSemanticColumnGeneration == 0L
				|| net.vulkanic.world.DistantHorizonsSemanticCollector.hasColumn(
					this.pos, this.rustSemanticColumnGeneration));
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
		this.rustSemanticBuffersPublished = false;
        Runnable listener=this.rustVisibilityCloseListener;this.rustVisibilityCloseListener=null;
        if(listener!=null) listener.run();
		// The published column lives exactly as long as this container.
		// This touches no native renderer object or GL state.
		// Only the lease retires anything: a container that never recorded a
		// column owns no generation, so it must not retire the position by key
		// (that would erase a newer section's generation at the same position).
		if (this.rustSemanticColumnLease != null)
		{
			this.rustSemanticColumnLease.close();
			this.rustSemanticColumnGeneration = 0L;
		}
	}
	
}
