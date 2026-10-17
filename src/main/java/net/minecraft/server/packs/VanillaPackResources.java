package net.minecraft.server.packs;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The built-in pack over the game's own asset and data roots, read by Rust
 * like any other pack; missing `pack.mcmeta` sections fall back to the
 * built-in metadata.
 */
public final class VanillaPackResources extends NativePackResources {
	private VanillaPackResources(PackLocationInfo location, long handle, BuiltInMetadata builtInMetadata, Map<PackType, List<Path>> pathsForType) {
		super(location, handle, builtInMetadata, pathsForType);
	}

	static VanillaPackResources open(
		PackLocationInfo location, BuiltInMetadata builtInMetadata, Set<String> namespaces, List<Path> rootPaths, Map<PackType, List<Path>> pathsForType
	) {
		long handle = openVanilla(location, namespaces, rootPaths, pathsForType);
		return new VanillaPackResources(location, handle, builtInMetadata, Map.copyOf(pathsForType));
	}
}
