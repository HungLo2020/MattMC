package net.minecraft.client.renderer.shaders.pack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single file in the shader pack include graph.
 * Contains the file's lines and tracks which lines are #include directives.
 * 
 * Based on IRIS's FileNode (verbatim pattern match).
 * Reference: frnsrc/Iris-1.21.9/.../include/FileNode.java
 * 
 * This is a core component of the include graph data structure.
 */
public class FileNode {
	private final AbsolutePackPath path;
	private final List<String> lines;
	private final Map<Integer, AbsolutePackPath> includes;
	
	/**
	 * Creates a FileNode by parsing the given lines for #include directives.
	 * 
	 * @param path The absolute path to this file
	 * @param lines The lines of the file
	 */
	public FileNode(AbsolutePackPath path, List<String> lines) {
		this.path = path;
		this.lines = List.copyOf(lines);
		
		AbsolutePackPath currentDirectory = path.parent().orElseThrow(
			() -> new IllegalArgumentException("Not a valid shader file name: " + path));
		
		this.includes = findIncludes(currentDirectory, lines);
	}
	
	/**
	 * Finds all #include directives in the given lines.
	 * Matches IRIS's parsing logic exactly.
	 * 
	 * @param currentDirectory The directory containing this file
	 * @param lines The lines to parse
	 * @return A map from line number to included file path
	 */
	private static Map<Integer, AbsolutePackPath> findIncludes(AbsolutePackPath currentDirectory,
	                                                             List<String> lines) {
		Map<Integer, AbsolutePackPath> foundIncludes = new HashMap<>();
		
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			
			if (!line.startsWith("#include")) {
				continue;
			}
			
			// Remove the "#include " part so that we just have the file path
			String target = line.substring("#include ".length()).trim();
			
			// Remove quotes if they're present
			// All include directives should have quotes, but I'm not sure whether they're required to.
			// TODO: Check if quotes are required, and don't permit mismatched quotes
			// TODO: This shouldn't be accepted:
			//       #include "test.glsl
			//       #include test.glsl"
			if (target.startsWith("\"")) {
				target = target.substring(1);
			}
			
			if (target.endsWith("\"")) {
				target = target.substring(0, target.length() - 1);
			}
			
			foundIncludes.put(i, currentDirectory.resolve(target));
		}
		
		return foundIncludes;
	}
	
	/**
	 * Gets the path of this file.
	 * @return The absolute pack path
	 */
	public AbsolutePackPath getPath() {
		return path;
	}
	
	/**
	 * Gets the lines of this file.
	 * @return The lines
	 */
	public List<String> getLines() {
		return lines;
	}
	
	/**
	 * Gets the include directives in this file.
	 * @return A map from line number to included file path
	 */
	public Map<Integer, AbsolutePackPath> getIncludes() {
		return includes;
	}
}
