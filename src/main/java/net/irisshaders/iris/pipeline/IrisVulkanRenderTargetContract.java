package net.irisshaders.iris.pipeline;

import net.logging.LogUtils;
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

		if (!descriptorPathEnabled || !VulkanicAPI.isVulkanBackendSelected() || VulkanicAPI.getCommandContext().isImmediate()) {
			return null;
		}

		VulkanicRenderTargetDescriptor descriptor = descriptorSupplier.get();
		boolean descriptorMatchesFramebuffer =
			VulkanicAPI.isRenderTargetDescriptorCompatibleWithFramebuffer(framebuffer, descriptor);
		if (TRACE_SHADER_RENDER_TARGETS) {
			LOGGER.info(
				"IrisShaderRenderTargetContract stage={} passName={} framebuffer={} descriptorMatchesFramebuffer={} {}",
				stage,
				passName != null ? passName : "(none)",
				framebuffer,
				descriptorMatchesFramebuffer ? "yes" : "no",
				descriptor.debugSignature()
			);
		}
		return descriptorMatchesFramebuffer ? descriptor : null;
	}

	public static String targetContractKey(
		int fallbackFramebuffer,
		@Nullable VulkanicRenderTargetDescriptor descriptor
	) {
		return descriptor != null ? descriptor.debugSignature() : "framebuffer:" + fallbackFramebuffer;
	}
}
