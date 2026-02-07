package com.seibel.distanthorizons.core.render.glObject.buffer;

import java.nio.ByteBuffer;

import net.vulkanic.VulkanicAPI;

import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;

/**
 * This is a container for a OpenGL
 * VBO (Vertex Buffer Object).
 *
 * @author James Seibel
 * @version 11-20-2021
 */
public class GLVertexBuffer extends GLBuffer
{
	/**
	 * When uploading to a buffer that is too small, recreate it this many times
	 * bigger than the upload payload
	 */
	protected int vertexCount = 0;
	public int getVertexCount() { return this.vertexCount; }
	// FIXME: This setter is needed for premapping buffer to manually set the vertexCount. Fix this.
	public void setVertexCount(int vertexCount) { this.vertexCount = vertexCount; }
	
	
	public GLVertexBuffer(boolean isBufferStorage)
	{
		super(isBufferStorage);
	}
	
	
	
	@Override
	public void destroyAsync()
	{
		super.destroyAsync();
		this.vertexCount = 0;
	}
	
	@Override
	public int getBufferBindingTarget() { return VulkanicAPI.GL_ARRAY_BUFFER; }
	
	public void uploadBuffer(ByteBuffer byteBuffer, int vertCount, EDhApiGpuUploadMethod uploadMethod, int maxExpensionSize)
	{
		if (vertCount < 0)
		{
			throw new IllegalArgumentException("VertCount is negative!");
		}
		
		// If size is zero, just ignore it.
		if (byteBuffer.limit() - byteBuffer.position() != 0)
		{
			boolean useBuffStorage = uploadMethod.useBufferStorage;
			super.uploadBuffer(byteBuffer, uploadMethod, maxExpensionSize, useBuffStorage ? 0 : VulkanicAPI.GL_STATIC_DRAW);
		}
		this.vertexCount = vertCount;
	}
	
	public ByteBuffer mapBuffer(int targetSize, EDhApiGpuUploadMethod uploadMethod, int maxExpansionSize)
	{
		return super.mapBuffer(targetSize, uploadMethod, maxExpansionSize,
				uploadMethod.useBufferStorage ? VulkanicAPI.GL_MAP_WRITE_BIT :
						uploadMethod.useEarlyMapping ? VulkanicAPI.GL_DYNAMIC_DRAW : VulkanicAPI.GL_STATIC_DRAW,
				VulkanicAPI.GL_MAP_WRITE_BIT | VulkanicAPI.GL_MAP_UNSYNCHRONIZED_BIT | VulkanicAPI.GL_MAP_INVALIDATE_BUFFER_BIT);
	}
	
}