package net.distanthorizons.core.render.renderer;

import net.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import net.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import net.distanthorizons.core.render.glObject.vertexAttribute.AbstractVertexAttribute;
import net.distanthorizons.core.render.glObject.vertexAttribute.VertexPointer;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Renders a full-screen textured quad to the screen. 
 * Used in composite / deferred rendering (IE fog).
 */
public class ScreenQuad
{
	public static ScreenQuad INSTANCE = new ScreenQuad();
	
	private static final float[] box_vertices = {
			-1, -1,
			1, -1,
			1, 1,
			-1, -1,
			1, 1,
			-1, 1,
	};
	
	private GLVertexBuffer boxBuffer;
	private AbstractVertexAttribute va;
	private boolean init = false;

	
	//=============//
	// constructor //
	//=============//
	
	private ScreenQuad() { }
	
	public void init()
	{
		if (this.init) return;
		this.init = true;
		
		this.va = AbstractVertexAttribute.create();
		this.va.bind();
		
		// Pos
		this.va.setVertexAttribute(0, 0, VertexPointer.addVec2Pointer(false));
		this.va.completeAndCheck(Float.BYTES * 2);
		
		// Framebuffer
		this.createBuffer();
	}
	
	public void render()
	{
		this.init();
		
		this.boxBuffer.bind();
		
		this.va.bind();
		this.va.bindBufferToAllBindingPoints(this.boxBuffer.getId());
		
		GL32.glDrawArrays(GL32.GL_TRIANGLES, 0, 6);
	}
	
	private void createBuffer()
	{
		ByteBuffer buffer = MemoryUtil.memAlloc(box_vertices.length * Float.BYTES);
		buffer.asFloatBuffer().put(box_vertices);
		buffer.rewind();
		
		this.boxBuffer = new GLVertexBuffer(false);
		this.boxBuffer.bind();
		this.boxBuffer.uploadBuffer(buffer, box_vertices.length, EDhApiGpuUploadMethod.DATA, box_vertices.length * Float.BYTES);
		MemoryUtil.memFree(buffer);
	}
	
}
