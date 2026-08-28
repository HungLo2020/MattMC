package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.AbstractVertexAttribute;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexPointer;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;

import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicPolygonFace;
import net.vulkanic.VulkanicPolygonMode;
import net.vulkanic.VulkanicPrimitiveMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class TestRenderer
{
	public static final DhLogger LOGGER = new DhLoggerBuilder().build(); 
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	
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
		if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
			|| net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled())
		{
			// This diagnostic square is a legacy Java draw path, not a semantic
			// world feature. Never let it acquire a command context on Rust Vulkan.
			return;
		}
		// TODO fix for MC 1.21.5+
		this.init();
		
		CommandContext ctx = VulkanicAPI.getCommandContext();
		if (!MC_RENDER.bindTargetRenderTarget(ctx))
		{
			return;
		}

		VulkanicAPI.setDynamicViewport(ctx, 0, 0, MC_RENDER.getTargetFramebufferViewportWidth(), MC_RENDER.getTargetFramebufferViewportHeight());
		VulkanicAPI.setPolygonMode(ctx, VulkanicPolygonFace.FRONT_AND_BACK, VulkanicPolygonMode.FILL);
		
		VulkanicAPI.setCullFaceEnabled(ctx, false);
		VulkanicAPI.setDepthTestEnabled(ctx, false);
		VulkanicAPI.setBlendEnabled(ctx, false);
		VulkanicAPI.setScissorTestEnabled(ctx, false);
		
		this.basicShader.bind(ctx);
		this.va.bind(ctx);
		
		this.vbo.bind();
		this.va.bindBufferToAllBindingPoints(this.vbo.getId());
		
		// Render the square
		VulkanicAPI.drawArrays(ctx, VulkanicPrimitiveMode.TRIANGLE_FAN, 0, 4);
		VulkanicAPI.clearDepthBuffer(ctx);
	}
	
	
	
}
