package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.AbstractVertexAttribute;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexPointer;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;

import net.vulkanic.VulkanicAPI;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class TestRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build(); 
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	private static final IMinecraftGLWrapper GLMC = SingletonInjector.INSTANCE.get(IMinecraftGLWrapper.class);
	
	
	
	ShaderProgram basicShader;
	GLVertexBuffer vbo;
	AbstractVertexAttribute va;
	boolean init = false;
	
	
	
	
	public TestRenderer() { }
	
	public void init()
	{
		if (this.init)
		{
			return;
		}
		
		LOGGER.info("init");
		this.init = true;
		this.va = AbstractVertexAttribute.create();
		this.va.bind();
		// Pos
		this.va.setVertexAttribute(0, 0, VertexPointer.addVec2Pointer(false));
		// Color
		this.va.setVertexAttribute(0, 1, VertexPointer.addVec4Pointer(false));
		this.va.completeAndCheck(Float.BYTES * 6);
		this.basicShader = new ShaderProgram("shaders/test/vert.vert", "shaders/test/frag.frag",
				"fragColor", new String[]{"vPosition", "color"});
		
		this.createBuffer();
	}
	
	// Render a square with uv color
	private static final float[] vertices = {
			// PosX,Y, ColorR,G,B,A
			-0.5f, -0.5f, 0.0f, 0.0f, 0.0f, 1.0f,
			0.4f, -0.4f, 1.0f, 0.0f, 0.0f, 1.0f,
			0.3f, 0.3f, 1.0f, 1.0f, 0.0f, 0.0f,
			-0.2f, 0.2f, 0.0f, 1.0f, 1.0f, 1.0f
	};
	
	private void createBuffer()
	{
		ByteBuffer buffer = ByteBuffer.allocateDirect(vertices.length * Float.BYTES);
		// Fill buffer with vertices.
		buffer.order(ByteOrder.nativeOrder());
		buffer.asFloatBuffer().put(vertices);
		buffer.rewind();
		
		this.vbo = new GLVertexBuffer(false);
		this.vbo.bind();
		this.vbo.uploadBuffer(buffer, 4, EDhApiGpuUploadMethod.DATA, vertices.length * Float.BYTES);
	}
	
	public void render()
	{
		// TODO fix for MC 1.21.5+
		this.init();
		
		GLMC.glBindFramebuffer(VulkanicAPI.GL_FRAMEBUFFER, MC_RENDER.getTargetFramebuffer());
		VulkanicAPI.setDynamicViewport(0, 0, MC_RENDER.getTargetFramebufferViewportWidth(), MC_RENDER.getTargetFramebufferViewportHeight());
		VulkanicAPI.glPolygonMode(VulkanicAPI.GL_FRONT_AND_BACK, VulkanicAPI.GL_FILL);
		
		GLMC.disableFaceCulling();
		GLMC.disableDepthTest();
		GLMC.disableBlend();
		GLMC.disableScissorTest();
		
		this.basicShader.bind();
		this.va.bind();
		
		this.vbo.bind();
		this.va.bindBufferToAllBindingPoints(this.vbo.getId());
		
		// Render the square
		VulkanicAPI.cmdDrawArrays(VulkanicAPI.GL_TRIANGLE_FAN, 0, 4);
		VulkanicAPI.clearAttachments(false, true);
	}
	
	
	
}
