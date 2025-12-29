package net.irisshaders.iris;

import net.irisshaders.iris.config.IrisConfig;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * Update checker functionality has been disabled.
 * This class is kept as a stub to maintain compatibility.
 */
public class UpdateChecker {
	private final String currentVersion;

	public UpdateChecker(String currentVersion) {
		this.currentVersion = currentVersion;
		// Update checking disabled - no network calls will be made
	}

	/**
	 * No-op method - update checking has been disabled
	 */
	public void checkForUpdates(IrisConfig irisConfig) {
		// Update checking disabled - no network calls will be made
	}

	/**
	 * Always returns null - no update info available
	 */
	@Nullable
	public UpdateInfo getUpdateInfo() {
		return null;
	}

	/**
	 * Always returns empty - no beta info available
	 */
	@Nullable
	public Optional<BetaInfo> getBetaInfo() {
		return Optional.empty();
	}

	/**
	 * Always returns empty - no update messages
	 */
	public Optional<Component> getUpdateMessage() {
		return Optional.empty();
	}

	/**
	 * Always returns empty - no update links
	 */
	public Optional<URI> getUpdateLink() {
		return Optional.empty();
	}

	public static class UpdateInfo {
		public String semanticVersion;
		public Map<String, String> updateInfo;
		public String modHost;
		public URI modDownload;
		public URI installer;
	}

	public static class BetaInfo {
		public String betaTag;
		public int betaVersion;
	}
}
