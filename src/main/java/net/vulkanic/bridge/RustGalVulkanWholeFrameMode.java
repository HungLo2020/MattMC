package net.vulkanic.bridge;

public final class RustGalVulkanWholeFrameMode {
	private static final String PROPERTY = "mattmc.dev.rustGalVulkanWholeFrame";
	/*
	 * Bootstrap can still need Java-owned metadata setup while the migration is
	 * incomplete.  Once the Rust windowed context has been created, however,
	 * normal backend access must not silently resolve to that bootstrap path.
	 */
	private static volatile boolean rustPresentationActive;
	/*
	 * Backend selection is the production admission signal.  The system
	 * property remains as a bootstrap/test override, but selecting Vulkan in the
	 * graphics options must not require a hidden JVM flag.
	 */
	private static volatile boolean vulkanBackendSelected;

	private RustGalVulkanWholeFrameMode() {
	}

	public static boolean enabled() {
		return Boolean.getBoolean(PROPERTY) || vulkanBackendSelected;
	}

	/** Records the explicit graphics-backend selection made by startup. */
	public static void markVulkanBackendSelected() {
		vulkanBackendSelected = true;
	}

	/** Clears the selection marker when the backend is explicitly reset. */
	public static void clearVulkanBackendSelection() {
		vulkanBackendSelected = false;
	}

	public static boolean enabledForBackend(boolean vulkanBackendSelected) {
		return enabled() && vulkanBackendSelected;
	}

	/**
	 * True only after the Rust Vulkan bridge owns the live presentation context.
	 * This deliberately differs from {@link #enabled()}: it allows narrowly
	 * scoped bootstrap work before the Rust context exists, but not after it is
	 * capable of receiving a frame.
	 */
	public static boolean isRustPresentationActive() {
		return enabled() && rustPresentationActive;
	}

	public static void activateRustPresentation() {
		if (!enabled()) {
			throw new IllegalStateException("cannot activate Rust Vulkan presentation while " + PROPERTY + " is disabled");
		}
		rustPresentationActive = true;
	}

	public static void deactivateRustPresentation() {
		rustPresentationActive = false;
	}

	public static String propertyName() {
		return PROPERTY;
	}
}
