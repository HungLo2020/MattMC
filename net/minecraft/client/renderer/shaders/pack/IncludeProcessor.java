package net.minecraft.client.renderer.shaders.pack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes #include directives in shader files by recursively expanding them.
 * 
 * Based on IRIS's IncludeProcessor (verbatim pattern match).
 * Reference: frnsrc/Iris-1.21.9/.../include/IncludeProcessor.java
 * 
 * This processor takes an IncludeGraph and expands #include directives,
 * caching the results for efficiency.
 */
public class IncludeProcessor {
	private final IncludeGraph graph;
	private final Map<AbsolutePackPath, List<String>> cache;
	
	/**
	 * Creates an IncludeProcessor for the given include graph.
	 * 
	 * @param graph The include graph to process
	 */
	public IncludeProcessor(IncludeGraph graph) {
		this.graph = graph;
		this.cache = new HashMap<>();
	}
	
	/**
	 * Gets the fully processed (with includes expanded) content of a file.
	 * Results are cached for efficiency.
	 * 
	 * @param path The absolute path to the file
	 * @return The processed lines, or null if the file wasn't found
	 */
	public List<String> getIncludedFile(AbsolutePackPath path) {
		List<String> lines = cache.get(path);
		
		if (lines == null) {
			lines = process(path);
			cache.put(path, lines);
		}
		
		return lines;
	}
	
	/**
	 * Processes a file by expanding all #include directives recursively.
	 * Matches IRIS's processing logic exactly.
	 * 
	 * @param path The absolute path to the file
	 * @return The processed lines
	 */
	private List<String> process(AbsolutePackPath path) {
		FileNode fileNode = graph.getNodes().get(path);
		
		if (fileNode == null) {
			return null;
		}
		
		List<String> builder = new ArrayList<>();
		
		List<String> lines = fileNode.getLines();
		Map<Integer, AbsolutePackPath> includes = fileNode.getIncludes();
		
		for (int i = 0; i < lines.size(); i++) {
			AbsolutePackPath include = includes.get(i);
			
			if (include != null) {
				// Get the included file content
				List<String> includedContent = getIncludedFile(include);
				
				if (includedContent != null) {
					builder.addAll(includedContent);
				} else {
					// Include failed - add error comment and continue
					// This allows partial shader compilation for debugging
					builder.add("// ERROR: Failed to include: " + include.getPathString());
					builder.add(lines.get(i)); // Keep original #include line as comment reference
				}
			} else {
				builder.add(lines.get(i));
			}
		}
		
		return builder;
	}
}
