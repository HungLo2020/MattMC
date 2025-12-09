package net.minecraft.client.renderer.shaders.pack;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates potential shader source file names to discover in a shader pack.
 * 
 * Based on IRIS's ShaderPackSourceNames (simplified for Step 7).
 * Reference: frnsrc/Iris-1.21.9/.../include/ShaderPackSourceNames.java
 * 
 * This provides the list of shader files that should be searched for when
 * building the include graph. Full IRIS program enumeration will be added
 * in later steps (11-15) when the compilation system is implemented.
 */
public class ShaderPackSourceNames {
	
	/**
	 * Gets the list of potential shader file names to search for.
	 * 
	 * This is a simplified version for Step 7. The full IRIS list includes
	 * all ProgramId and ProgramArrayId entries with compute shaders.
	 * We start with the most common shader files for now.
	 * 
	 * @return List of shader file names
	 */
	public static List<String> getPotentialStarts() {
		List<String> potentialFileNames = new ArrayList<>();
		
		// Shadow programs
		addStarts(potentialFileNames, "shadow");
		addStarts(potentialFileNames, "shadow_solid");
		addStarts(potentialFileNames, "shadow_cutout");
		
		// Gbuffer programs (most common)
		addStarts(potentialFileNames, "gbuffers_basic");
		addStarts(potentialFileNames, "gbuffers_textured");
		addStarts(potentialFileNames, "gbuffers_textured_lit");
		addStarts(potentialFileNames, "gbuffers_skybasic");
		addStarts(potentialFileNames, "gbuffers_skytextured");
		addStarts(potentialFileNames, "gbuffers_clouds");
		addStarts(potentialFileNames, "gbuffers_terrain");
		addStarts(potentialFileNames, "gbuffers_terrain_solid");
		addStarts(potentialFileNames, "gbuffers_terrain_cutout");
		addStarts(potentialFileNames, "gbuffers_damagedblock");
		addStarts(potentialFileNames, "gbuffers_block");
		addStarts(potentialFileNames, "gbuffers_beaconbeam");
		addStarts(potentialFileNames, "gbuffers_item");
		addStarts(potentialFileNames, "gbuffers_entities");
		addStarts(potentialFileNames, "gbuffers_entities_glowing");
		addStarts(potentialFileNames, "gbuffers_armor_glint");
		addStarts(potentialFileNames, "gbuffers_spidereyes");
		addStarts(potentialFileNames, "gbuffers_hand");
		addStarts(potentialFileNames, "gbuffers_weather");
		addStarts(potentialFileNames, "gbuffers_water");
		addStarts(potentialFileNames, "gbuffers_hand_water");
		
		// Composite programs (post-processing)
		for (int i = 0; i < 16; i++) {
			String baseName = i == 0 ? "composite" : "composite" + i;
			addStarts(potentialFileNames, baseName);
		}
		
		// Deferred programs
		for (int i = 0; i < 16; i++) {
			String baseName = i == 0 ? "deferred" : "deferred" + i;
			addStarts(potentialFileNames, baseName);
		}
		
		// Final program
		addStarts(potentialFileNames, "final");
		
		// Prepare programs
		for (int i = 0; i < 16; i++) {
			String baseName = i == 0 ? "prepare" : "prepare" + i;
			addStarts(potentialFileNames, baseName);
		}
		
		return potentialFileNames;
	}
	
	/**
	 * Adds all shader stage extensions for a base name.
	 * Matches IRIS's addStarts() method.
	 * 
	 * @param list The list to add to
	 * @param baseName The base shader name (e.g., "gbuffers_terrain")
	 */
	private static void addStarts(List<String> list, String baseName) {
		list.add(baseName + ".vsh");  // Vertex shader
		list.add(baseName + ".tcs");  // Tessellation control shader
		list.add(baseName + ".tes");  // Tessellation evaluation shader
		list.add(baseName + ".gsh");  // Geometry shader
		list.add(baseName + ".fsh");  // Fragment shader
	}
	
	/**
	 * Finds present shader sources in the given pack source.
	 * 
	 * @param packSource The shader pack to search
	 * @param directory The directory to search in (e.g., "/shaders/")
	 * @param candidates The list of candidate file names
	 * @return List of found absolute paths
	 */
	public static List<AbsolutePackPath> findPresentSources(
			ShaderPackSource packSource, 
			String directory,
			List<String> candidates) {
		
		List<AbsolutePackPath> found = new ArrayList<>();
		
		// Ensure directory starts with /
		if (!directory.startsWith("/")) {
			directory = "/" + directory;
		}
		
		// Ensure directory ends with /
		if (!directory.endsWith("/")) {
			directory = directory + "/";
		}
		
		for (String candidate : candidates) {
			String fullPath = directory + candidate;
			
			// Remove leading / for fileExists check
			String checkPath = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
			
			if (packSource.fileExists(checkPath)) {
				try {
					found.add(AbsolutePackPath.fromAbsolutePath(fullPath));
				} catch (Exception e) {
					// Skip invalid paths
				}
			}
		}
		
		return found;
	}
}
