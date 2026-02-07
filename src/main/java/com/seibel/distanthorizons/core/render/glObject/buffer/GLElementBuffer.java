package com.seibel.distanthorizons.core.render.glObject.buffer;

import net.vulkanic.VulkanicAPI;

/**
 * This is a container for a OpenGL
 * VBO (Vertex Buffer Object).
 *
 * @author James Seibel
 * @version 11-20-2021
 */
public class GLElementBuffer extends GLBuffer
{
	/**
	 * When uploading to a buffer that is too small, recreate it this many times
	 * bigger than the upload payload
	 */
	protected int indicesCount = 0;
	public int getIndicesCount() { return this.indicesCount; }
	protected int type = VulkanicAPI.GL_UNSIGNED_INT;
	public int getType() { return type; }
	
	public GLElementBuffer(boolean isBufferStorage)
	{
		super(isBufferStorage);
	}
	
	@Override
	public void destroyAsync()
	{
		super.destroyAsync();
		this.indicesCount = 0;
	}
	
	@Override
	public int getBufferBindingTarget()
	{
		return VulkanicAPI.GL_ELEMENT_ARRAY_BUFFER;
	}
	
}