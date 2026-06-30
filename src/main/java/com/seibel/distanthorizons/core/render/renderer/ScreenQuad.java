package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.api.enums.config.EDhApiGpuUploadMethod;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.glObject.texture.DhFramebuffer;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.AbstractVertexAttribute;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexPointer;
import net.blaze3d.systems.RenderPass;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicPrimitiveMode;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import org.jetbrains.annotations.Nullable;
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
		this.render(VulkanicAPI.getCommandContext());
	}

	public void render(CommandContext ctx)
	{
		this.render(ctx, null);
	}

	public void render(CommandContext ctx, @Nullable DhFramebuffer targetFramebuffer)
	{
		this.init();
		
		this.boxBuffer.bind();
		
		this.va.bind();
		this.va.bindBufferToAllBindingPoints(this.boxBuffer.getId());
		
		try (RenderPass ignored = createVulkanCompatibilityRenderPass(targetFramebuffer))
		{
			VulkanicAPI.drawArrays(ctx, VulkanicPrimitiveMode.TRIANGLES, 0, 6);
		}
	}

	private static RenderPass createVulkanCompatibilityRenderPass(@Nullable DhFramebuffer targetFramebuffer)
	{
		if (!VulkanicAPI.isVulkanBackendSelected())
		{
			return null;
		}

		CommandContext ctx = VulkanicAPI.getCommandContext();
		if (targetFramebuffer != null
				&& targetFramebuffer.getId() == VulkanicAPI.getDrawFramebufferBinding()
				&& targetFramebuffer.canCreateRenderTargetDescriptor())
		{
			VulkanicRenderTargetDescriptor descriptor =
					targetFramebuffer.createRenderTargetDescriptor(() -> "Distant Horizons screen quad");
			boolean preferDescriptor = true;
			return VulkanicAPI.createCommandEncoder().createRenderPass(
					descriptor,
					targetFramebuffer.getId(),
					preferDescriptor);
		}

		return VulkanicAPI.createRenderPass(
				() -> "Distant Horizons screen quad",
				VulkanicAPI.getDrawFramebufferBinding(),
				VulkanicAPI.getFramebufferDepthAttachmentObjectName(ctx, VulkanicAPI.GL_DRAW_FRAMEBUFFER) > 0);
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
