package net.irisshaders.iris.pipeline;

import net.blaze3d.systems.RenderPass;
import net.logging.LogUtils;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicRenderTargetDescriptor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Shared policy for migrating Iris shader passes from framebuffer-id rendering
 * to explicit Vulkanic render-target descriptors.
 */
public final class IrisVulkanRenderTargetContract {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final boolean TRACE_SHADER_RENDER_TARGETS =
		Boolean.getBoolean("mattmc.vulkan.traceShaderRenderTargets");

	private IrisVulkanRenderTargetContract() {
	}

	public static TargetSelection selectTarget(
		String stage,
		@Nullable String passName,
		int fallbackFramebuffer,
		boolean fallbackHasDepthAttachment,
		boolean descriptorPathEnabled,
		Supplier<VulkanicRenderTargetDescriptor> descriptorSupplier
	) {
		Objects.requireNonNull(stage, "stage must not be null");
		Objects.requireNonNull(descriptorSupplier, "descriptorSupplier must not be null");

		boolean vulkanRecordedPass = VulkanicAPI.isVulkanBackendSelected() && !VulkanicAPI.getCommandContext().isImmediate();
		if (!descriptorPathEnabled || !vulkanRecordedPass) {
			return new TargetSelection(fallbackFramebuffer, fallbackHasDepthAttachment, null, false);
		}

		VulkanicRenderTargetDescriptor descriptor = descriptorSupplier.get();
		boolean descriptorMatchesFramebuffer =
			VulkanicAPI.isRenderTargetDescriptorEquivalentToFramebuffer(fallbackFramebuffer, descriptor);
		if (TRACE_SHADER_RENDER_TARGETS) {
			LOGGER.info(
				"IrisShaderRenderTargetContract stage={} passName={} framebuffer={} descriptorMatchesFramebuffer={} {}",
				stage,
				passName != null ? passName : "(none)",
				fallbackFramebuffer,
				descriptorMatchesFramebuffer ? "yes" : "no",
				descriptor.debugSignature()
			);
		}

		return new TargetSelection(
			fallbackFramebuffer,
			fallbackHasDepthAttachment,
			descriptorMatchesFramebuffer ? descriptor : null,
			vulkanRecordedPass
		);
	}

	@Nullable
	public static VulkanicRenderTargetDescriptor selectDescriptorBackedTarget(
		String stage,
		@Nullable String passName,
		int framebuffer,
		boolean descriptorPathEnabled,
		Supplier<VulkanicRenderTargetDescriptor> descriptorSupplier
	) {
		Objects.requireNonNull(stage, "stage must not be null");
		Objects.requireNonNull(descriptorSupplier, "descriptorSupplier must not be null");

		return selectTarget(stage, passName, framebuffer, false, descriptorPathEnabled, descriptorSupplier).descriptor();
	}

	public static String targetContractKey(
		int fallbackFramebuffer,
		@Nullable VulkanicRenderTargetDescriptor descriptor
	) {
		return descriptor != null ? descriptor.debugSignature() : "framebuffer:" + fallbackFramebuffer;
	}

	public record TargetSelection(
		int fallbackFramebuffer,
		boolean fallbackHasDepthAttachment,
		@Nullable VulkanicRenderTargetDescriptor descriptor,
		boolean vulkanRecordedPass
	) {
		public TargetSelection {
			if (fallbackFramebuffer < 0) {
				throw new IllegalArgumentException("fallbackFramebuffer must be >= 0");
			}
		}

		public boolean descriptorBacked() {
			return this.descriptor != null;
		}

		public boolean usesFramebufferFallback() {
			return this.descriptor == null;
		}

		public String contractKey() {
			return targetContractKey(this.fallbackFramebuffer, this.descriptor);
		}

		public PipelineHandle createPipeline(PipelineDescriptor descriptor) {
			Objects.requireNonNull(descriptor, "descriptor must not be null");
			return this.descriptor != null
				? VulkanicAPI.createPipeline(descriptor, this.descriptor)
				: VulkanicAPI.createPipeline(descriptor, this.fallbackFramebuffer);
		}

		public RenderPass createRenderPass(Supplier<String> label) {
			Objects.requireNonNull(label, "label must not be null");
			return this.descriptor != null
				? VulkanicAPI.createRenderPass(this.descriptor)
				: VulkanicAPI.createRenderPass(label, this.fallbackFramebuffer, this.fallbackHasDepthAttachment);
		}
	}
}
