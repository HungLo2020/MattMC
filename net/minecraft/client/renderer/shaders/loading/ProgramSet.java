// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.loading;

import net.minecraft.client.renderer.shaders.program.ProgramSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Optional;

/**
 * Manages a complete set of shader programs for a shader pack.
 * 
 * Based on IRIS's ProgramSet.java structure.
 * Reference: frnsrc/Iris-1.21.9/.../shaderpack/programs/ProgramSet.java
 * 
 * This implementation provides the core structure matching IRIS.
 * Full loading/compilation logic will be integrated in rendering phases (Steps 21-25).
 * 
 * Step 15 of NEW-SHADER-PLAN.md
 */
public class ProgramSet {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProgramSet.class);
	
	// IRIS ProgramSet.java:40-42 - EnumMaps for program storage
	private final EnumMap<ProgramId, ProgramSource> gbufferPrograms = new EnumMap<>(ProgramId.class);
	private final EnumMap<ProgramArrayId, ProgramSource[]> compositePrograms = new EnumMap<>(ProgramArrayId.class);
	
	/**
	 * Creates an empty program set.
	 * 
	 * Note: In full implementation (Steps 21-25), this will load and compile
	 * all programs from a shader pack using parallel compilation.
	 */
	public ProgramSet() {
		// Empty constructor for Step 15
		// Full initialization will be added in rendering phases
	}
	
	/**
	 * Gets a program source by its ID, following fallback chain if not found.
	 * 
	 * IRIS ProgramSet.java:279-286
	 * 
	 * @param programId The program to retrieve
	 * @return Optional containing the program source, or empty if not found
	 */
	public Optional<ProgramSource> get(ProgramId programId) {
		// IRIS ProgramSet.java:280-285
		ProgramSource source = gbufferPrograms.getOrDefault(programId, null);
		if (source != null) {
			return source.requireValid();
		}
		
		// Check fallback chain
		Optional<ProgramId> fallback = programId.getFallback();
		if (fallback.isPresent()) {
			return get(fallback.get());
		}
		
		return Optional.empty();
	}
	
	/**
	 * Adds a program source to this set.
	 * 
	 * @param programId The program ID
	 * @param source The program source
	 */
	public void put(ProgramId programId, ProgramSource source) {
		if (programId == null || source == null) {
			throw new IllegalArgumentException("Program ID and source cannot be null");
		}
		gbufferPrograms.put(programId, source);
	}
	
	/**
	 * Gets a program array (composite, deferred, etc.).
	 * 
	 * IRIS ProgramSet.java:304-306
	 * 
	 * @param programArrayId The array identifier
	 * @return Array of program sources
	 */
	public ProgramSource[] getComposite(ProgramArrayId programArrayId) {
		// IRIS ProgramSet.java:305
		return compositePrograms.getOrDefault(programArrayId, new ProgramSource[programArrayId.getNumPrograms()]);
	}
	
	/**
	 * Sets a program array (composite, deferred, etc.).
	 * 
	 * @param programArrayId The array identifier
	 * @param sources Array of program sources
	 */
	public void putComposite(ProgramArrayId programArrayId, ProgramSource[] sources) {
		if (programArrayId == null || sources == null) {
			throw new IllegalArgumentException("Program array ID and sources cannot be null");
		}
		compositePrograms.put(programArrayId, sources);
	}
	
	/**
	 * Checks if a program exists in this set.
	 * 
	 * @param programId The program to check
	 * @return true if the program exists
	 */
	public boolean has(ProgramId programId) {
		return gbufferPrograms.containsKey(programId);
	}
	
	/**
	 * Gets the number of programs in this set.
	 * 
	 * @return Program count
	 */
	public int size() {
		return gbufferPrograms.size();
	}
	
	/**
	 * Clears all programs from this set.
	 */
	public void clear() {
		gbufferPrograms.clear();
		compositePrograms.clear();
	}
	
	/**
	 * Gets all program IDs that have sources.
	 * 
	 * @return Array of program IDs
	 */
	public ProgramId[] getProgramIds() {
		return gbufferPrograms.keySet().toArray(new ProgramId[0]);
	}
}
