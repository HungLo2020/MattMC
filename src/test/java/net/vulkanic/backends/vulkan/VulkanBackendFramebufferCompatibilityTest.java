package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicAPI;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.VK10;

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
		Class<?> planner = Class.forName("net.vulkanic.backends.vulkan.VulkanSynchronizationPlanner");
		Method accessMaskForLayout = planner.getDeclaredMethod("accessMaskForLayout", int.class);
		Method stageMaskForLayout = planner.getDeclaredMethod("stageMaskForLayout", int.class);
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
		String plannerSource = Files.readString(PROJECT_ROOT
			.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanDescriptorBindingPlanner.java"))
			.replace("\r\n", "\n")
			.replace('\r', '\n');
		String imageCoordinatorSource = Files.readString(PROJECT_ROOT
			.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanImageResourceViewCoordinator.java"))
			.replace("\r\n", "\n")
			.replace('\r', '\n');

		String descriptorLayoutHelper = imageCoordinatorSource.substring(
			imageCoordinatorSource.indexOf("int descriptorImageLayoutFor"),
			imageCoordinatorSource.indexOf("static int layerCount")
		);
		assertTrue(descriptorLayoutHelper.contains("storage.hasDepthAspect()"),
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
			source.indexOf("private DescriptorWritePlan buildDescriptorWritePlan")
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
	public void testDepthOnlyBlitsCanCopyFromCombinedDepthStencilIntoDepthOnlyTarget() throws Exception {
		Class<?> nativeSpine = Class.forName("net.vulkanic.backends.vulkan.VulkanBackend$NativeSpine");
		Method helper = nativeSpine.getDeclaredMethod(
			"blitOperationAspectMask",
			int.class,
			int.class,
			int.class,
			String.class
		);
		helper.setAccessible(true);

		int depthOnly = (Integer) helper.invoke(
			null,
			VulkanicAPI.GL_DEPTH_BUFFER_BIT,
			VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT,
			VK10.VK_IMAGE_ASPECT_DEPTH_BIT,
			"test"
		);

		assertEquals(VK10.VK_IMAGE_ASPECT_DEPTH_BIT, depthOnly,
			"A GL depth-only blit must select the depth aspect even when the source image also has stencil");
	}

	@Test
	public void testCombinedDepthStencilBlitBarriersTransitionBothAspects() throws Exception {
		Class<?> nativeSpine = Class.forName("net.vulkanic.backends.vulkan.VulkanBackend$NativeSpine");
		Method helper = nativeSpine.getDeclaredMethod("blitTransitionAspectMask", int.class, int.class);
		helper.setAccessible(true);

		int combinedTransition = (Integer) helper.invoke(
			null,
			VK10.VK_IMAGE_ASPECT_DEPTH_BIT,
			VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT
		);
		int depthOnlyTransition = (Integer) helper.invoke(
			null,
			VK10.VK_IMAGE_ASPECT_DEPTH_BIT,
			VK10.VK_IMAGE_ASPECT_DEPTH_BIT
		);

		assertEquals(VK10.VK_IMAGE_ASPECT_DEPTH_BIT | VK10.VK_IMAGE_ASPECT_STENCIL_BIT, combinedTransition,
			"Combined depth/stencil images need full depth-stencil layout barriers unless separate layouts are enabled");
		assertEquals(VK10.VK_IMAGE_ASPECT_DEPTH_BIT, depthOnlyTransition,
			"Depth-only images should keep depth-only layout barriers");
	}

	@Test
	public void testLegacyBlitUsesOperationAspectInsteadOfWholeTextureAspect() throws Exception {
		String source = Files.readString(PROJECT_ROOT
			.resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"))
			.replace("\r\n", "\n")
			.replace('\r', '\n');

		String helper = source.substring(
			source.indexOf("private void blitLegacyTextureRegion"),
			source.indexOf("private static int toVulkanImageY")
		);
		String normalizedHelper = helper.replaceAll("\\s+", " ");

		assertFalse(helper.contains("sourceTexture.aspectMask != destTexture.aspectMask"),
			"Depth-only blits from DEPTH24_STENCIL8 into DEPTH32 must not require whole texture aspect equality");
		assertTrue(normalizedHelper.contains("int operationAspectMask = blitOperationAspectMask( mask, sourceTexture.aspectMask, destTexture.aspectMask, operation );"),
			"The Vulkan blit path should derive the active aspect from the GL blit mask");
		assertTrue(normalizedHelper.contains(".aspectMask(operationAspectMask) .mipLevel(sourceLevel)"),
			"VkImageBlit source subresource must use the requested operation aspect");
		assertTrue(normalizedHelper.contains(".aspectMask(operationAspectMask) .mipLevel(destLevel)"),
			"VkImageBlit destination subresource must use the requested operation aspect");
		assertTrue(normalizedHelper.contains("int sourceTransitionAspectMask = blitTransitionAspectMask(operationAspectMask, sourceTexture.aspectMask);"),
			"Source layout barriers should account for combined depth/stencil texture restrictions");
		assertTrue(normalizedHelper.contains("int destTransitionAspectMask = blitTransitionAspectMask(operationAspectMask, destTexture.aspectMask);"),
			"Destination layout barriers should account for combined depth/stencil texture restrictions");
		assertTrue(normalizedHelper.contains("sourceTexture.imageHandle, sourceTransitionAspectMask, sourceOriginalLayout"),
			"Source layout transitions for blits must use the Vulkan-legal transition aspect");
		assertTrue(normalizedHelper.contains("destTexture.imageHandle, destTransitionAspectMask, destOriginalLayout"),
			"Destination layout transitions for blits must use the Vulkan-legal transition aspect");
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
	public void testTextureViewDepthRenderPassDependenciesIncludeFragmentTests() {
		VulkanRenderPassCompatibilityKey key = VulkanRenderPassCompatibilityKey.textureView(
			List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
			VK10.VK_FORMAT_D32_SFLOAT,
			false
		);

		List<VulkanRenderPassLayoutPlanner.SubpassDependencyPlan> dependencies =
			VulkanRenderPassLayoutPlanner.dependencyIntent(key);
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

	@Test
	public void testFramebufferRenderPassDependenciesIncludeGraphicsBarrierSelfDependency() {
		VulkanRenderPassCompatibilityKey key = VulkanRenderPassCompatibilityKey.framebuffer(
			List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
			VK10.VK_FORMAT_D32_SFLOAT,
			false
		);

		List<VulkanRenderPassLayoutPlanner.SubpassDependencyPlan> dependencies =
			VulkanRenderPassLayoutPlanner.dependencyIntent(key);
		VulkanRenderPassLayoutPlanner.SubpassDependencyPlan selfDependency = dependencies.get(2);

		assertEquals(3, dependencies.size());
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

	@Test
	public void testTextureViewFeedbackDependenciesKeepDedicatedFeedbackLoopDependency() {
		VulkanRenderPassCompatibilityKey key = VulkanRenderPassCompatibilityKey.textureView(
			List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
			VK10.VK_FORMAT_UNDEFINED,
			true
		);

		List<VulkanRenderPassLayoutPlanner.SubpassDependencyPlan> dependencies =
			VulkanRenderPassLayoutPlanner.dependencyIntent(key);
		VulkanRenderPassLayoutPlanner.SubpassDependencyPlan graphicsSelfDependency = dependencies.get(2);
		VulkanRenderPassLayoutPlanner.SubpassDependencyPlan feedbackDependency = dependencies.get(3);

		assertEquals(4, dependencies.size());
		assertEquals(0, graphicsSelfDependency.srcSubpass());
		assertEquals(0, graphicsSelfDependency.dstSubpass());
		assertEquals(0, graphicsSelfDependency.srcStageMask() & VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
		assertTrue((graphicsSelfDependency.dependencyFlags() & VK10.VK_DEPENDENCY_BY_REGION_BIT) != 0);
		assertTrue((feedbackDependency.dependencyFlags()
			& EXTAttachmentFeedbackLoopLayout.VK_DEPENDENCY_FEEDBACK_LOOP_BIT_EXT) != 0);
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
