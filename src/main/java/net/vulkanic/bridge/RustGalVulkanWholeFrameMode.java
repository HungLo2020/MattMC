package net.vulkanic.bridge;

public final class RustGalVulkanWholeFrameMode {
	private static final String PROPERTY = "mattmc.dev.rustGalVulkanWholeFrame";

	private RustGalVulkanWholeFrameMode() {
	}

	public static boolean enabled() {
		return Boolean.getBoolean(PROPERTY);
	}

	public static boolean enabledForBackend(boolean vulkanBackendSelected) {
		return enabled() && vulkanBackendSelected;
	}

	public static String propertyName() {
		return PROPERTY;
	}
}
