package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
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
	
	public GLVertexBuffer[] vbos;
	public GLVertexBuffer[] vbosTransparent;
	public GLVertexBuffer[] vbosTransparentUp;
	public GLVertexBuffer[] vbosTransparentWaterUp;
	
	private CompletableFuture<LodBufferContainer> uploadFuture = null;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public LodBufferContainer(long pos, DhBlockPos minCornerBlockPos)
	{
		this.pos = pos;
		this.minCornerBlockPos = minCornerBlockPos;
		this.vbos = new GLVertexBuffer[0];
		this.vbosTransparent = new GLVertexBuffer[0];
		this.vbosTransparentUp = new GLVertexBuffer[0];
		this.vbosTransparentWaterUp = new GLVertexBuffer[0];
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
		
		
		
			// make the buffers
			boolean captureSemanticMaterials = net.vulkanic.world.DistantHorizonsSemanticCollector.enabled();
			LodQuadBuilder.VertexBufferBuild opaqueBuild = captureSemanticMaterials
				? builder.makeOpaqueVertexBuffersWithSemanticMaterials()
				: new LodQuadBuilder.VertexBufferBuild(builder.makeOpaqueVertexBuffers(), List.of());
			LodQuadBuilder.VertexBufferBuild transparentBuild = captureSemanticMaterials
				? builder.makeTransparentVertexBuffersWithSemanticMaterials()
				: new LodQuadBuilder.VertexBufferBuild(builder.makeTransparentVertexBuffers(), List.of());
			LodQuadBuilder.VertexBufferBuild transparentUpBuild = captureSemanticMaterials
				? builder.makeTransparentUpVertexBuffersWithSemanticMaterials()
				: new LodQuadBuilder.VertexBufferBuild(builder.makeTransparentUpVertexBuffers(), List.of());
			LodQuadBuilder.VertexBufferBuild transparentWaterUpBuild = captureSemanticMaterials
				? builder.makeTransparentWaterUpVertexBuffersWithSemanticMaterials()
				: new LodQuadBuilder.VertexBufferBuild(builder.makeTransparentWaterUpVertexBuffers(), List.of());
			ArrayList<ByteBuffer> opaqueBuffers = new ArrayList<>(opaqueBuild.vertexBuffers());
			ArrayList<ByteBuffer> transparentBuffers = new ArrayList<>(transparentBuild.vertexBuffers());
			ArrayList<ByteBuffer> transparentUpBuffers = new ArrayList<>(transparentUpBuild.vertexBuffers());
			ArrayList<ByteBuffer> transparentWaterUpBuffers = new ArrayList<>(transparentWaterUpBuild.vertexBuffers());
			// Capture only copied CPU semantics before the legacy path uploads and
			// frees these direct buffers. This is private diagnostic groundwork for
			// a future Rust LOD route, never a GL/Vulkan handle bridge.
			net.vulkanic.world.DistantHorizonsSemanticCollector.recordBuiltColumn(
				this.pos,
				this.minCornerBlockPos,
				builder.semanticMaterials(),
				builder.semanticQuadCoverage(),
				LodQuadBuilder.semanticQuadCoverage(
					opaqueBuild, transparentBuild, transparentUpBuild, transparentWaterUpBuild
				),
				opaqueBuild,
				transparentBuild,
				transparentUpBuild,
				transparentWaterUpBuild
			);
			if (net.vulkanic.world.DistantHorizonsSemanticCollector.usesRustWholeFrameSemanticBuild())
			{
				// The Rust whole-frame route owns the GPU asset. Completing this
				// CPU stage releases DH's temporary buffers without creating a Java
				// VBO or queuing a GL upload.
				freeBuffers(opaqueBuffers, transparentBuffers, transparentUpBuffers, transparentWaterUpBuffers);
				this.uploadFuture = null;
				future.complete(this);
				return future;
			}
		
		this.vbos = resizeBuffer(this.vbos, opaqueBuffers.size());
		this.vbosTransparent = resizeBuffer(this.vbosTransparent, transparentBuffers.size());
		this.vbosTransparentUp = resizeBuffer(this.vbosTransparentUp, transparentUpBuffers.size());
		this.vbosTransparentWaterUp = resizeBuffer(this.vbosTransparentWaterUp, transparentWaterUpBuffers.size());
		
		
		// upload on MC's render thread
		GLProxy.queueRunningOnRenderThread(() ->
		{
			try
			{
				// skip this event if requested
				if (Thread.interrupted() 
					|| this.uploadFuture.isCancelled())
				{
					throw new InterruptedException();
				}
				
				EDhApiGpuUploadMethod gpuUploadMethod = GLProxy.getInstance().getGpuUploadMethod();
				
				// upload on the render thread
				uploadBuffersDirect(this.vbos, opaqueBuffers, gpuUploadMethod);
				uploadBuffersDirect(this.vbosTransparent, transparentBuffers, gpuUploadMethod);
				uploadBuffersDirect(this.vbosTransparentUp, transparentUpBuffers, gpuUploadMethod);
				uploadBuffersDirect(this.vbosTransparentWaterUp, transparentWaterUpBuffers, gpuUploadMethod);
				this.buffersUploaded = true;
				
				// success
				this.uploadFuture.complete(this);
				this.uploadFuture = null;
			}
			catch (InterruptedException ignore) 
			{
				this.uploadFuture.complete(this);
				this.uploadFuture = null;
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected issue uploading buffer ["+this.minCornerBlockPos +"], error: ["+e.getMessage()+"].", e);
				
				this.uploadFuture.completeExceptionally(e);
				this.uploadFuture = null;
			}
			finally
			{
				// all the buffers must be manually freed to prevent memory leaks
				
				freeBuffers(opaqueBuffers, transparentBuffers, transparentUpBuffers, transparentWaterUpBuffers);
			}
		});
		
		return future;
	}

	private static void freeBuffers(
		List<ByteBuffer> opaqueBuffers,
		List<ByteBuffer> transparentBuffers,
		List<ByteBuffer> transparentUpBuffers,
		List<ByteBuffer> transparentWaterUpBuffers
	)
	{
		for (ByteBuffer buffer : opaqueBuffers) MemoryUtil.memFree(buffer);
		for (ByteBuffer buffer : transparentBuffers) MemoryUtil.memFree(buffer);
		for (ByteBuffer buffer : transparentUpBuffers) MemoryUtil.memFree(buffer);
		for (ByteBuffer buffer : transparentWaterUpBuffers) MemoryUtil.memFree(buffer);
	}
	private static GLVertexBuffer[] resizeBuffer(GLVertexBuffer[] vbos, int newSize)
	{
		if (vbos.length == newSize)
		{
			return vbos;
		}
		
		GLVertexBuffer[] newVbos = new GLVertexBuffer[newSize];
		System.arraycopy(vbos, 0, newVbos, 0, Math.min(vbos.length, newSize));
		if (newSize < vbos.length)
		{
			for (int i = newSize; i < vbos.length; i++)
			{
				if (vbos[i] != null)
				{
					vbos[i].close();
				}
			}
		}
		return newVbos;
	}
	private static void uploadBuffersDirect(
			GLVertexBuffer[] vbos, ArrayList<ByteBuffer> byteBuffers, 
			EDhApiGpuUploadMethod uploadMethod) throws InterruptedException
	{
		int vboIndex = 0;
		for (int i = 0; i < byteBuffers.size(); i++)
		{
			if (vboIndex >= vbos.length)
			{
				throw new RuntimeException("Too many vertex buffers!!");
			}
			
			
			// get or create the VBO
			if (vbos[vboIndex] == null)
			{
				vbos[vboIndex] = new GLVertexBuffer(uploadMethod.useBufferStorage);
			}
			GLVertexBuffer vbo = vbos[vboIndex];
			
			
			ByteBuffer buffer = byteBuffers.get(i);
			int size = buffer.limit() - buffer.position();
			
			try
			{
				vbo.bind();
				vbo.uploadBuffer(buffer, size / LodUtil.LOD_VERTEX_FORMAT.getByteSize(), uploadMethod, FULL_SIZED_BUFFER);
			}
			catch (Exception e)
			{
				vbos[vboIndex] = null;
				vbo.close();
				LOGGER.error("Failed to upload buffer. Error: ["+e.getMessage()+"].", e);
			}
			
			vboIndex++;
		}
		
		if (vboIndex < vbos.length)
		{
			throw new RuntimeException("Too few vertex buffers!!");
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** can be used when debugging */
	public boolean hasNonNullVbos() { return this.vbos != null || this.vbosTransparent != null || this.vbosTransparentUp != null || this.vbosTransparentWaterUp != null; }
	
	/** can be used when debugging */
	public int vboBufferCount() 
	{
		int count = 0;
		
		if (this.vbos != null)
		{
			count += this.vbos.length;
		}
		
		if (this.vbosTransparent != null)
		{
			count += this.vbosTransparent.length;
		}

		if (this.vbosTransparentUp != null)
		{
			count += this.vbosTransparentUp.length;
		}

		if (this.vbosTransparentWaterUp != null)
		{
			count += this.vbosTransparentWaterUp.length;
		}
		
		return count;
	}
	
	public boolean uploadInProgress() { return this.uploadFuture != null; }
	
	public void debugDumpStats(StatsMap statsMap)
	{
		statsMap.incStat("RenderBuffers");
		statsMap.incStat("SimpleRenderBuffers");
		for (GLVertexBuffer vertexBuffer : vbos)
		{
			if (vertexBuffer != null)
			{
				statsMap.incStat("VBOs");
				if (vertexBuffer.getSize() == FULL_SIZED_BUFFER)
				{
					statsMap.incStat("FullsizedVBOs");
				}
				
				if (vertexBuffer.getSize() == 0)
				{
					GLProxy.LOGGER.warn("VBO with size 0");
				}
				statsMap.incBytesStat("TotalUsage", vertexBuffer.getSize());
			}
		}
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
	public void close()
	{
		this.buffersUploaded = false;
		// Keep the copied semantic asset lifecycle aligned with the legacy LOD
		// container. This touches no native renderer object or GL state.
		net.vulkanic.world.DistantHorizonsSemanticCollector.removeColumn(this.pos);
		
		GLProxy.queueRunningOnRenderThread(() ->
		{
			for (GLVertexBuffer buffer : this.vbos)
			{
				if (buffer != null)
				{
					buffer.destroyAsync();
				}
			}
			
			for (GLVertexBuffer buffer : this.vbosTransparent)
			{
				if (buffer != null)
				{
					buffer.destroyAsync();
				}
			}

			for (GLVertexBuffer buffer : this.vbosTransparentUp)
			{
				if (buffer != null)
				{
					buffer.destroyAsync();
				}
			}

			for (GLVertexBuffer buffer : this.vbosTransparentWaterUp)
			{
				if (buffer != null)
				{
					buffer.destroyAsync();
				}
			}
		});
	}
	
}
