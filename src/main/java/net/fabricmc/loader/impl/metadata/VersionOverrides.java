package net.fabricmc.loader.impl.metadata;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.version.VersionParser;

public final class VersionOverrides {
	private final Map<String, Version> replacements = new HashMap<>();

	public VersionOverrides() {
		String property = System.getProperty(SystemProperties.DEBUG_REPLACE_VERSION);
		if (property == null) return;

		for (String entry : property.split(",")) {
			int pos = entry.indexOf(":");
			if (pos <= 0 || pos >= entry.length() - 1) throw new RuntimeException("invalid version replacement entry: "+entry);

			String id = entry.substring(0, pos);
			String rawVersion = entry.substring(pos + 1);
			Version version;

			try {
				version = VersionParser.parse(rawVersion, false);
			} catch (VersionParsingException e) {
				throw new RuntimeException(String.format("Invalid replacement version for mod %s: %s", id, rawVersion), e);
			}

			replacements.put(id, version);
		}
	}

	public void apply(LoaderModMetadata metadata) {
		if (replacements.isEmpty()) return;

		Version replacement = replacements.get(metadata.getId());

		if (replacement != null) {
			metadata.setVersion(replacement);
		}
	}

	public Collection<String> getAffectedModIds() {
		return replacements.keySet();
	}
}
