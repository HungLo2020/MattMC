package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkSubpassDependency;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanBackendFramebufferCompatibilityTest {

	private static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE = 0x8CD0;
	private static final VulkanCommandContext TEST_CONTEXT = new VulkanCommandContext(1L, "framebuffer-test");
	private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

	@Test
	public void testDepthReadOnlyLayoutBarrierStageSupportsShaderReadAccess() throws Exception {
		Class<?> nativeSpine = Class.forName("net.vulkanic.backends.vulkan.VulkanBackend$NativeSpine");
		Method accessMaskForLayout = nativeSpine.getDeclaredMethod("accessMaskForLayout", int.class);
		Method stageMaskForLayout = nativeSpine.getDeclaredMethod("stageMaskForLayout", int.class);
		accessMaskForLayout.setAccessible(true);
		stageMaskForLayout.setAccessible(true);

		int accessMask = (Integer) accessMaskForLayout.invoke(null, VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);
		int stageMask = (Integer) stageMaskForLayout.invoke(null, VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);

		assertTrue((accessMask & VK10.VK_ACCESS_SHADER_READ_BIT) != 0,
			"Depth read-only layout is used for sampled depth descriptors and must include shader-read access");
		assertTrue((stageMask & VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT) != 0,
			"Shader-read access is invalid with only depth-test stages; fragment shader stage must be included");
		assertTrue((stageMask & VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT) != 0);
		assertTrue((stageMask & VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT) != 0);
	}

	@Test
	public void testInferredFeedbackLoopInitialLayoutStillPreTransitionsBeforeRenderPass() throws Exception {
		String source = Files.readString(PROJECT_ROOT
			.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"))
			.replace("\r\n", "\n")
			.replace('\r', '\n');

		String helper = source.substring(
			source.indexOf("private void ensureTextureLayoutBeforeRenderPass"),
			source.indexOf("private void uploadToLegacyTextureRegion")
		);
		String normalizedHelper = helper.replaceAll("\\s+", " ");

		assertFalse(helper.contains("usage == VulkanicResourceUsage.INFERRED"),
			"A framebuffer pass may infer an explicit feedback-loop initial layout; INFERRED must not skip that pre-barrier");
		assertTrue(normalizedHelper.contains("Objects.requireNonNull(usage, \"usage must not be null\");"),
			"The usage parameter should remain validated even though explicit target layout drives the transition decision");
		assertTrue(normalizedHelper.contains("transitionImageLayout(texture, trackedLayout, targetLayout, 0, 1);"),
			"The helper must still emit an actual pre-render-pass layout barrier");
	}

	@Test
	public void testDepthStencilSampledDescriptorsUseDepthOnlyViewAndLayout() throws Exception {
		String source = Files.readString(PROJECT_ROOT
			.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"))
			.replace("\r\n", "\n")
			.replace('\r', '\n');

		String descriptorLayoutHelper = source.substring(
			source.indexOf("private int descriptorImageLayoutFor"),
			source.indexOf("private boolean shouldUseFeedbackLoopLayoutForSampling")
		);
		assertTrue(descriptorLayoutHelper.contains("hasDepthAspect(texture)"),
			"Depth/stencil textures sampled by shaders must use the depth-read descriptor layout");
		assertFalse(descriptorLayoutHelper.contains("texture.aspectMask == VK10.VK_IMAGE_ASPECT_DEPTH_BIT"),
			"A combined depth/stencil image still has a depth aspect; equality would misclassify it as color");

		String sampleTransitionHelper = source.substring(
			source.indexOf("private void transitionLegacyTextureToSampleLayout(@Nullable LegacyTextureObject texture,"),
			source.indexOf("private void transitionLegacyTextureToStorageImageLayout")
		);
		assertTrue(sampleTransitionHelper.contains("hasDepthAspect(texture)"),
			"Sampler layout transitions must classify combined depth/stencil images the same way descriptor writes do");
		assertFalse(sampleTransitionHelper.contains("texture.aspectMask == VK10.VK_IMAGE_ASPECT_DEPTH_BIT"),
			"A depth/stencil texture must not be transitioned toward color shader-read layout before sampling");

		String descriptorViewHelper = source.substring(
			source.indexOf("private long descriptorImageViewHandleForSampler"),
			source.indexOf("private boolean shouldUseFeedbackLoopLayoutForSampling")
		);
		String normalizedDescriptorViewHelper = descriptorViewHelper.replaceAll("\\s+", " ");
		assertTrue(normalizedDescriptorViewHelper.contains("!hasDepthAspect(texture) || !hasStencilAspect(texture)"),
			"Only combined depth/stencil textures need a sampler-specific view remap");
		assertTrue(descriptorViewHelper.contains("VK10.VK_IMAGE_ASPECT_DEPTH_BIT"),
			"Sampled descriptors for combined depth/stencil images must bind a depth-only image view");
		assertTrue(descriptorViewHelper.contains("texture.sampledDepthViewHandles.put(key, viewHandle);"),
			"Sampler-specific depth views should be cached instead of recreated for every descriptor write");
	}

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
	public void testTextureViewDepthRenderPassDependenciesIncludeFragmentTests() throws Exception {
		Method allocator = Class.forName("net.vulkanic.backends.vulkan.VulkanBackend$NativeSpine")
			.getDeclaredMethod(
				"allocateTextureViewDependencies",
				MemoryStack.class,
				VulkanRenderPassCompatibilityKey.class
			);
		allocator.setAccessible(true);

		VulkanRenderPassCompatibilityKey key = VulkanRenderPassCompatibilityKey.textureView(
			List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
			VK10.VK_FORMAT_D32_SFLOAT,
			false
		);

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkSubpassDependency.Buffer dependencies = (VkSubpassDependency.Buffer) allocator.invoke(null, stack, key);
			int entryStages = dependencies.get(0).dstStageMask();
			int entryAccess = dependencies.get(0).dstAccessMask();
			int exitStages = dependencies.get(1).srcStageMask();
			int exitAccess = dependencies.get(1).srcAccessMask();

			assertTrue((entryStages & VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT) != 0);
			assertTrue((entryStages & VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT) != 0);
			assertTrue((entryAccess & VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT) != 0);
			assertTrue((entryAccess & VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT) != 0);
			assertTrue((exitStages & VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT) != 0);
			assertTrue((exitStages & VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT) != 0);
			assertTrue((exitAccess & VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT) != 0);
			assertTrue((exitAccess & VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT) != 0);
		}
	}

	@Test
	public void testFramebufferRenderPassDependenciesIncludeGraphicsBarrierSelfDependency() throws Exception {
		Method allocator = Class.forName("net.vulkanic.backends.vulkan.VulkanBackend$NativeSpine")
			.getDeclaredMethod(
				"allocateFramebufferDependencies",
				MemoryStack.class,
				VulkanRenderPassCompatibilityKey.class
			);
		allocator.setAccessible(true);

		VulkanRenderPassCompatibilityKey key = VulkanRenderPassCompatibilityKey.framebuffer(
			List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
			VK10.VK_FORMAT_D32_SFLOAT,
			false
		);

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkSubpassDependency.Buffer dependencies = (VkSubpassDependency.Buffer) allocator.invoke(null, stack, key);
			VkSubpassDependency selfDependency = dependencies.get(2);

			assertEquals(3, dependencies.capacity());
			assertEquals(0, selfDependency.srcSubpass());
			assertEquals(0, selfDependency.dstSubpass());
			assertEquals(0, selfDependency.srcStageMask() & VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
			assertEquals(0, selfDependency.dstStageMask() & VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
			assertEquals(0, selfDependency.srcStageMask() & VK10.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT);
			assertEquals(0, selfDependency.dstStageMask() & VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
			assertTrue((selfDependency.srcStageMask() & VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT) != 0);
			assertTrue((selfDependency.dstStageMask() & VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT) != 0);
			assertTrue((selfDependency.srcAccessMask() & VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0);
			assertTrue((selfDependency.dstAccessMask() & VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT) != 0);
			assertTrue((selfDependency.dependencyFlags() & VK10.VK_DEPENDENCY_BY_REGION_BIT) != 0);
		}
	}

	@Test
	public void testTextureViewFeedbackDependenciesKeepDedicatedFeedbackLoopDependency() throws Exception {
		Method allocator = Class.forName("net.vulkanic.backends.vulkan.VulkanBackend$NativeSpine")
			.getDeclaredMethod(
				"allocateTextureViewDependencies",
				MemoryStack.class,
				VulkanRenderPassCompatibilityKey.class
			);
		allocator.setAccessible(true);

		VulkanRenderPassCompatibilityKey key = VulkanRenderPassCompatibilityKey.textureView(
			List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
			VK10.VK_FORMAT_UNDEFINED,
			true
		);

		try (MemoryStack stack = MemoryStack.stackPush()) {
			VkSubpassDependency.Buffer dependencies = (VkSubpassDependency.Buffer) allocator.invoke(null, stack, key);
			VkSubpassDependency graphicsSelfDependency = dependencies.get(2);
			VkSubpassDependency feedbackDependency = dependencies.get(3);

			assertEquals(4, dependencies.capacity());
			assertEquals(0, graphicsSelfDependency.srcSubpass());
			assertEquals(0, graphicsSelfDependency.dstSubpass());
			assertEquals(0, graphicsSelfDependency.srcStageMask() & VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
			assertTrue((graphicsSelfDependency.dependencyFlags() & VK10.VK_DEPENDENCY_BY_REGION_BIT) != 0);
			assertTrue((feedbackDependency.dependencyFlags()
				& EXTAttachmentFeedbackLoopLayout.VK_DEPENDENCY_FEEDBACK_LOOP_BIT_EXT) != 0);
		}
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

	@Test
	public void testLegacyTextureClearsPreserveUntrackedLayouts() throws Exception {
		String backendSource = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

		Pattern preservationFallback = Pattern.compile(
			"int oldLayout = trackedLayoutForLevel\\(texture, 0\\);\\s+"
				+ "if \\(oldLayout == VK10\\.VK_IMAGE_LAYOUT_UNDEFINED\\) \\{\\s+"
				+ "oldLayout = preferredIdleLayout\\(texture\\);\\s+"
				+ "\\}\\s+transitionImageLayout\\(");
		assertTrue(preservationFallback.matcher(backendSource).results().count() >= 2,
			"Legacy color/depth clears must not transition from UNDEFINED and discard shader-readable texture contents");
	}

	@Test
	public void testGeneralFramebufferRenderPassesPromoteResolvableTargetsToNativeEncoder() throws Exception {
		String backendSource = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
		String nativeEncoderSource = Files.readString(PROJECT_ROOT.resolve(
			"src/main/java/net/vulkanic/backends/vulkan/VulkanNativeCommandEncoder.java"));

		assertTrue(backendSource.contains("return new VulkanNativeCommandEncoder(this).createRenderPass(supplier, framebuffer, hasDepthTexture);"),
			"VulkanBackend framebuffer render-pass creation should route through the native encoder");
		assertTrue(backendSource.contains("boolean canCreateNativeFramebufferRenderPass(int framebuffer, boolean includeDepthAttachment)")
				&& backendSource.contains("resolveFramebufferRenderTargetPlan("),
			"VulkanBackend should expose a preflight that proves framebuffer attachments can be resolved before opening a command buffer");
		assertFalse(backendSource.contains("return createCompatibilityCommandEncoder().createRenderPass(supplier, framebuffer, hasDepthTexture);"),
			"VulkanBackend should not blanket-route framebuffer render passes through GlCommandEncoder");
		assertTrue(nativeEncoderSource.contains("!this.backend.canCreateNativeFramebufferRenderPass(framebuffer, hasDepthTexture)")
				&& nativeEncoderSource.contains("this.backend.createCompatibilityCommandEncoder().createRenderPass(label, framebuffer, hasDepthTexture)")
				&& nativeEncoderSource.contains("this.backend.beginRenderPass(ctx, label, framebuffer, hasDepthTexture)"),
			"VulkanNativeCommandEncoder should use native framebuffer render passes when resolvable and keep only unresolved fallback explicit");
	}

	private static int getPrivateInt(VulkanBackend backend, String fieldName) throws Exception {
		Field field = VulkanBackend.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.getInt(backend);
	}
}
