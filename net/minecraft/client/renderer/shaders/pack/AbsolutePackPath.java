package net.minecraft.client.renderer.shaders.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Represents an absolute path within a shader pack.
 * Paths always start with "/" and use forward slashes as separators.
 * 
 * Based on IRIS's AbsolutePackPath (verbatim pattern match).
 * Reference: frnsrc/Iris-1.21.9/.../include/AbsolutePackPath.java
 * 
 * This class handles path normalization (resolving . and .. segments).
 */
public class AbsolutePackPath {
	private final String path;
	
	private AbsolutePackPath(String absolute) {
		this.path = absolute;
	}
	
	/**
	 * Creates an AbsolutePackPath from an absolute path string.
	 * The path must start with "/".
	 * 
	 * @param absolutePath The absolute path (must start with "/")
	 * @return The normalized AbsolutePackPath
	 */
	public static AbsolutePackPath fromAbsolutePath(String absolutePath) {
		return new AbsolutePackPath(normalizeAbsolutePath(absolutePath));
	}
	
	/**
	 * Normalizes an absolute path by resolving . and .. segments.
	 * Matches IRIS's normalization logic exactly.
	 * 
	 * @param path The path to normalize
	 * @return The normalized path string
	 */
	private static String normalizeAbsolutePath(String path) {
		if (!path.startsWith("/")) {
			throw new IllegalArgumentException("Not an absolute path: " + path);
		}
		
		String[] segments = path.split(Pattern.quote("/"));
		List<String> parsedSegments = new ArrayList<>();
		
		for (String segment : segments) {
			if (segment.isEmpty() || segment.equals(".")) {
				continue;
			}
			
			if (segment.equals("..")) {
				if (!parsedSegments.isEmpty()) {
					parsedSegments.removeLast();
				}
			} else {
				parsedSegments.add(segment);
			}
		}
		
		if (parsedSegments.isEmpty()) {
			return "/";
		}
		
		StringBuilder normalized = new StringBuilder();
		
		for (String segment : parsedSegments) {
			normalized.append('/');
			normalized.append(segment);
		}
		
		return normalized.toString();
	}
	
	/**
	 * Gets the parent directory of this path.
	 * 
	 * @return The parent path, or empty if this is the root
	 */
	public Optional<AbsolutePackPath> parent() {
		if (path.equals("/")) {
			return Optional.empty();
		}
		
		int lastSlash = path.lastIndexOf('/');
		
		return Optional.of(new AbsolutePackPath(path.substring(0, lastSlash)));
	}
	
	/**
	 * Resolves a path relative to this path.
	 * If the given path is absolute (starts with "/"), returns it directly.
	 * Otherwise, resolves it relative to this path.
	 * 
	 * @param path The path to resolve
	 * @return The resolved AbsolutePackPath
	 */
	public AbsolutePackPath resolve(String path) {
		if (path.startsWith("/")) {
			return fromAbsolutePath(path);
		}
		
		String merged;
		
		if (!this.path.endsWith("/") & !path.startsWith("/")) {
			merged = this.path + "/" + path;
		} else {
			merged = this.path + path;
		}
		
		return fromAbsolutePath(merged);
	}
	
	/**
	 * Gets the path string.
	 * @return The path string
	 */
	public String getPathString() {
		return path;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		AbsolutePackPath that = (AbsolutePackPath) o;
		return Objects.equals(path, that.path);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(path);
	}
	
	@Override
	public String toString() {
		return "AbsolutePackPath {" + getPathString() + "}";
	}
}
