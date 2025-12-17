/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.impl.discovery;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

/**
 * Registry for internal mods (Sodium, Iris) that are hardcoded instead of discovered at runtime.
 * Part of the deep integration plan to eliminate dynamic mod loading.
 */
public final class InternalMods {
	
	/**
	 * Get all internal mod candidates.
	 * These are hardcoded replacements for dynamically discovered mods.
	 * 
	 * @param versionOverrides Version overrides to apply
	 * @param depOverrides Dependency overrides to apply
	 * @return List of ModCandidateImpl for Sodium and Iris
	 */
	public static List<ModCandidateImpl> getAll(VersionOverrides versionOverrides, DependencyOverrides depOverrides) {
		List<ModCandidateImpl> internalMods = new ArrayList<>();
		
		// NOTE: Sodium is now fully integrated into the base game, no longer loaded as a JAR mod
		// Keeping this comment as a reminder of the integration status
		Log.info(LogCategory.DISCOVERY, "Sodium is integrated - not loading as separate mod");
		
		// Add Iris as internal mod
		ModCandidateImpl iris = createModCandidate("iris", versionOverrides, depOverrides);
		if (iris != null) {
			internalMods.add(iris);
			Log.info(LogCategory.DISCOVERY, "Registered internal mod: Iris");
		}
		
		return internalMods;
	}
	
	/**
	 * Create ModCandidate for a mod from its JAR in mods/.
	 */
	private static ModCandidateImpl createModCandidate(String modId, VersionOverrides versionOverrides, DependencyOverrides depOverrides) {
		try {
			// Look for mod JAR in mods/ directory (relative to working directory, which is 'run' during development)
			Path modsDir = Paths.get("mods");
			if (!Files.exists(modsDir)) {
				Log.warn(LogCategory.DISCOVERY, "mods/ directory not found, " + modId + " not loaded as internal mod");
				return null;
			}
			
			// Find mod JAR
			Path modJar = Files.list(modsDir)
				.filter(p -> p.getFileName().toString().startsWith(modId + "-") && p.getFileName().toString().endsWith(".jar"))
				.findFirst()
				.orElse(null);
			
			if (modJar == null || !Files.exists(modJar)) {
				Log.warn(LogCategory.DISCOVERY, modId + " JAR not found in mods/, not loaded as internal mod");
				return null;
			}
			
			// Load metadata from JAR's fabric.mod.json
			LoaderModMetadata metadata = loadMetadataFromJar(modJar, modId, versionOverrides, depOverrides);
			if (metadata == null) {
				return null;
			}
			
			// Create mod candidate using createPlain factory method
			ModCandidateImpl candidate = ModCandidateImpl.createPlain(
				Collections.singletonList(modJar),
				metadata,
				false, // requiresRemap - false since these are pre-built JARs
				Collections.emptyList() // nestedMods
			);
			
			return candidate;
			
		} catch (Exception e) {
			Log.warn(LogCategory.DISCOVERY, "Failed to create " + modId + " internal mod candidate", e);
			return null;
		}
	}
	
	/**
	 * Load mod metadata from a JAR file's fabric.mod.json.
	 */
	private static LoaderModMetadata loadMetadataFromJar(Path jarPath, String expectedModId, 
			VersionOverrides versionOverrides, DependencyOverrides depOverrides) {
		try (ZipFile zf = new ZipFile(jarPath.toFile())) {
			ZipEntry entry = zf.getEntry("fabric.mod.json");
			
			if (entry == null) {
				Log.warn(LogCategory.DISCOVERY, "No fabric.mod.json found in " + jarPath);
				return null;
			}
			
			try (InputStream is = zf.getInputStream(entry)) {
				// Parse metadata using the same method as ModDiscoverer
				LoaderModMetadata metadata = ModMetadataParser.parseMetadata(
					is,
					jarPath.toString(), // modPath
					Collections.emptyList(), // modParentPaths
					versionOverrides,
					depOverrides,
					FabricLoaderImpl.INSTANCE.isDevelopmentEnvironment()
				);
				
				return metadata;
			}
		} catch (ParseMetadataException | IOException e) {
			Log.warn(LogCategory.DISCOVERY, "Failed to parse metadata for " + expectedModId + " from " + jarPath, e);
			return null;
		}
	}
}
