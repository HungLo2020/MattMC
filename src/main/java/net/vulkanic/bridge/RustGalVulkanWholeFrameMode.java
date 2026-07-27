package net.vulkanic.bridge;

public final class RustGalVulkanWholeFrameMode {
	private static final String PROPERTY = "mattmc.dev.rustGalVulkanWholeFrame";

	private RustGalVulkanWholeFrameMode() {
	}

	public static boolean enabled() {
		return Boolean.getBoolean(PROPERTY);
	}

	public static String propertyName() {
		return PROPERTY;
	}
}
