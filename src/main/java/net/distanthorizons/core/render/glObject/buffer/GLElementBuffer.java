package net.distanthorizons.core.render.glObject.buffer;

import org.lwjgl.opengl.GL32;

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
	protected int type = GL32.GL_UNSIGNED_INT;
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
		return GL32.GL_ELEMENT_ARRAY_BUFFER;
	}
	
}