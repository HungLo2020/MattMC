package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VulkanBackendFramebufferCompatibilityTest {

	private static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE = 0x8CD0;
	private static final VulkanCommandContext TEST_CONTEXT = new VulkanCommandContext(1L, "framebuffer-test");

	@Test
	public void testNamedFramebufferTextureTracksColorAndDepthAttachments() {
		VulkanBackend backend = new VulkanBackend();
		int framebuffer = backend.createFramebuffer(TEST_CONTEXT);

		backend.namedFramebufferTexture(TEST_CONTEXT, framebuffer, VulkanicAPI.GL_COLOR_ATTACHMENT0, 41, 0);
		backend.namedFramebufferTexture(TEST_CONTEXT, framebuffer, VulkanicAPI.GL_DEPTH_ATTACHMENT, 77, 0);
		backend.bindFramebuffer(TEST_CONTEXT, VulkanicAPI.GL_FRAMEBUFFER, framebuffer);

		assertEquals(
			41,
			backend.getFramebufferAttachmentParameteri(
				TEST_CONTEXT,
				VulkanicAPI.GL_DRAW_FRAMEBUFFER,
				VulkanicAPI.GL_COLOR_ATTACHMENT0,
				VulkanicAPI.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
			)
		);
		assertEquals(
			VulkanicAPI.GL_TEXTURE,
			backend.getFramebufferAttachmentParameteri(
				TEST_CONTEXT,
				VulkanicAPI.GL_DRAW_FRAMEBUFFER,
				VulkanicAPI.GL_COLOR_ATTACHMENT0,
				GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
			)
		);
		assertEquals(
			77,
			backend.getFramebufferAttachmentParameteri(
				TEST_CONTEXT,
				VulkanicAPI.GL_DRAW_FRAMEBUFFER,
				VulkanicAPI.GL_DEPTH_ATTACHMENT,
				VulkanicAPI.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
			)
		);
	}

	@Test
	public void testFramebufferTexture2DTracksBoundFramebufferAttachments() {
		VulkanBackend backend = new VulkanBackend();
		int framebuffer = backend.createFramebuffer(TEST_CONTEXT);

		backend.bindFramebuffer(TEST_CONTEXT, VulkanicAPI.GL_FRAMEBUFFER, framebuffer);
		backend.framebufferTexture2D(TEST_CONTEXT, VulkanicAPI.GL_FRAMEBUFFER, VulkanicAPI.GL_COLOR_ATTACHMENT0, VulkanicAPI.GL_TEXTURE_2D, 12, 0);

		assertEquals(
			12,
			backend.getFramebufferAttachmentParameteri(
				TEST_CONTEXT,
				VulkanicAPI.GL_DRAW_FRAMEBUFFER,
				VulkanicAPI.GL_COLOR_ATTACHMENT0,
				VulkanicAPI.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
			)
		);
	}

	@Test
	public void testFramebufferReadAndDrawBuffersRestorePerFramebufferStateOnBind() throws Exception {
		VulkanBackend backend = new VulkanBackend();
		int framebufferA = backend.createFramebuffer(TEST_CONTEXT);
		int framebufferB = backend.createFramebuffer(TEST_CONTEXT);

		backend.namedFramebufferReadBuffer(TEST_CONTEXT, framebufferA, VulkanicAPI.colorAttachment(2));
		backend.namedFramebufferDrawBuffers(TEST_CONTEXT, framebufferA, new int[]{VulkanicAPI.colorAttachment(3)});
		backend.namedFramebufferReadBuffer(TEST_CONTEXT, framebufferB, VulkanicAPI.colorAttachment(0));
		backend.namedFramebufferDrawBuffers(TEST_CONTEXT, framebufferB, new int[]{VulkanicAPI.colorAttachment(1)});

		backend.bindFramebuffer(TEST_CONTEXT, VulkanicAPI.GL_FRAMEBUFFER, framebufferA);
		assertEquals(VulkanicAPI.colorAttachment(2), getPrivateInt(backend, "pendingReadBuffer"));
		assertEquals(VulkanicAPI.colorAttachment(3), getPrivateInt(backend, "pendingDrawBuffer"));

		backend.bindFramebuffer(TEST_CONTEXT, VulkanicAPI.GL_FRAMEBUFFER, framebufferB);
		assertEquals(VulkanicAPI.colorAttachment(0), getPrivateInt(backend, "pendingReadBuffer"));
		assertEquals(VulkanicAPI.colorAttachment(1), getPrivateInt(backend, "pendingDrawBuffer"));
	}

	private static int getPrivateInt(VulkanBackend backend, String fieldName) throws Exception {
		Field field = VulkanBackend.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.getInt(backend);
	}
}