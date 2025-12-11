package net.minecraft.client.renderer.shaders.pack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * A directed graph data structure that holds the loaded source of all shader programs
 * and the files included by each source file.
 * 
 * Based on IRIS's IncludeGraph (adapted for MattMC's baked-in design).
 * Reference: frnsrc/Iris-1.21.9/.../include/IncludeGraph.java
 * 
 * This data structure allows efficient processing of #include directives:
 * - Each file is read exactly once
 * - #include directives are parsed once per file
 * - Cyclic inclusions are detected and reported
 * - Deferred processing allows efficient transformations
 */
public class IncludeGraph {
	private static final Logger LOGGER = LoggerFactory.getLogger(IncludeGraph.class);
	
	private final Map<AbsolutePackPath, FileNode> nodes;
	private final Map<AbsolutePackPath, String> failures;
	
	/**
	 * Creates an include graph by loading files from the given shader pack source.
	 * 
	 * @param packSource The shader pack source to load files from
	 * @param startingPaths The initial files to load (e.g., shader programs)
	 */
	public IncludeGraph(ShaderPackSource packSource, List<AbsolutePackPath> startingPaths) {
		Map<AbsolutePackPath, AbsolutePackPath> cameFrom = new HashMap<>();
		Map<AbsolutePackPath, Integer> lineNumberInclude = new HashMap<>();
		
		Map<AbsolutePackPath, FileNode> nodes = new HashMap<>();
		Map<AbsolutePackPath, String> failures = new HashMap<>();
		
		List<AbsolutePackPath> queue = new ArrayList<>(startingPaths);
		Set<AbsolutePackPath> seen = new HashSet<>(startingPaths);
		
		while (!queue.isEmpty()) {
			AbsolutePackPath next = queue.removeLast();
			
			String source;
			
			try {
				// Convert AbsolutePackPath to resource path
				// AbsolutePackPath starts with "/", resource paths don't
				String resourcePath = next.getPathString().substring(1);
				
				// Try reading the file directly first
				Optional<String> content = packSource.readFile(resourcePath);
				
				// If not found and path doesn't start with "shaders/", try with "shaders/" prefix
				// This handles #include "/program/..." which should resolve to "shaders/program/..."
				if (content.isEmpty() && !resourcePath.startsWith("shaders/")) {
					String shadersPath = "shaders/" + resourcePath;
					content = packSource.readFile(shadersPath);
					if (content.isPresent()) {
						LOGGER.debug("Resolved {} to {}", resourcePath, shadersPath);
					}
				}
				
				if (content.isEmpty()) {
					throw new IOException("File not found: " + resourcePath);
				}
				source = content.get();
			} catch (IOException e) {
				AbsolutePackPath src = cameFrom.get(next);
				
				if (src == null) {
					throw new RuntimeException("unexpected error: failed to read " + next.getPathString(), e);
				}
				
				String topLevelMessage = "failed to resolve #include directive: " + e.getMessage();
				
				failures.put(next, topLevelMessage);
				LOGGER.error("Failed to load included file {}: {}", next.getPathString(), e.getMessage());
				
				continue;
			}
			
			List<String> lines = Arrays.asList(source.split("\\R"));
			
			FileNode node = new FileNode(next, lines);
			boolean selfInclude = false;
			
			for (Map.Entry<Integer, AbsolutePackPath> include : node.getIncludes().entrySet()) {
				int line = include.getKey();
				AbsolutePackPath included = include.getValue();
				
				if (next.equals(included)) {
					selfInclude = true;
					String errorMsg = "trivial #include cycle detected: file includes itself at line " + (line + 1);
					failures.put(next, errorMsg);
					LOGGER.error("Include cycle in {}: {}", next.getPathString(), errorMsg);
					
					break;
				} else if (!seen.contains(included)) {
					queue.add(included);
					seen.add(included);
					cameFrom.put(included, next);
					lineNumberInclude.put(included, line);
				}
			}
			
			if (!selfInclude) {
				nodes.put(next, node);
			}
		}
		
		this.nodes = Map.copyOf(nodes);
		this.failures = Map.copyOf(failures);
		
		detectCycle();
	}
	
	/**
	 * Detects cycles in the include graph using depth-first search.
	 * Matches IRIS's cycle detection algorithm.
	 */
	private void detectCycle() {
		List<AbsolutePackPath> cycle = new ArrayList<>();
		Set<AbsolutePackPath> visited = new HashSet<>();
		
		for (AbsolutePackPath start : nodes.keySet()) {
			if (exploreForCycles(start, cycle, visited)) {
				AbsolutePackPath lastFilePath = null;
				
				StringBuilder error = new StringBuilder();
				error.append("#include cycle detected:\n");
				
				for (AbsolutePackPath node : cycle) {
					if (lastFilePath == null) {
						lastFilePath = node;
						continue;
					}
					
					FileNode lastFile = nodes.get(lastFilePath);
					int lineNumber = -1;
					
					for (Map.Entry<Integer, AbsolutePackPath> include : lastFile.getIncludes().entrySet()) {
						if (include.getValue().equals(node)) {
							lineNumber = include.getKey() + 1;
						}
					}
					
					error.append("  ").append(lastFilePath.getPathString())
						.append(":").append(lineNumber)
						.append(" -> ").append(node.getPathString()).append("\n");
					
					lastFilePath = node;
				}
				
				error.append("note: #include directives are resolved before any other preprocessor directives, ");
				error.append("any form of #include guard will not work\n");
				
				LOGGER.error(error.toString());
				
				throw new IllegalStateException("Cycle detected in #include graph, see previous messages for details");
			}
		}
	}
	
	/**
	 * Explores the graph for cycles using DFS.
	 * Returns true if a cycle is detected.
	 */
	private boolean exploreForCycles(AbsolutePackPath frontier, List<AbsolutePackPath> path, Set<AbsolutePackPath> visited) {
		if (visited.contains(frontier)) {
			path.add(frontier);
			return true;
		}
		
		path.add(frontier);
		visited.add(frontier);
		
		for (AbsolutePackPath included : nodes.get(frontier).getIncludes().values()) {
			if (!nodes.containsKey(included)) {
				// file that failed to load for another reason, error should already be reported
				continue;
			}
			
			if (exploreForCycles(included, path, visited)) {
				return true;
			}
		}
		
		path.removeLast();
		visited.remove(frontier);
		
		return false;
	}
	
	/**
	 * Gets all nodes in the graph.
	 * @return The nodes map
	 */
	public Map<AbsolutePackPath, FileNode> getNodes() {
		return nodes;
	}
	
	/**
	 * Gets all files that failed to load.
	 * @return The failures map
	 */
	public Map<AbsolutePackPath, String> getFailures() {
		return failures;
	}
}
