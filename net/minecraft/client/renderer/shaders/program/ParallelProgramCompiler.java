// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.program;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Compiles shader programs in parallel using a thread pool.
 * 
 * Based on IRIS's parallel compilation pattern in ProgramSet.java.
 * Reference: frnsrc/Iris-1.21.9/.../shaderpack/programs/ProgramSet.java:64-90
 * 
 * IRIS uses ExecutorService with a fixed thread pool of 10 threads to compile
 * multiple shader programs concurrently, significantly reducing load times.
 * 
 * Step 14 of NEW-SHADER-PLAN.md
 */
public class ParallelProgramCompiler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ParallelProgramCompiler.class);
	
	// IRIS uses 10 threads (ProgramSet.java:64)
	private static final int THREAD_POOL_SIZE = 10;
	
	/**
	 * Compiles multiple programs in parallel.
	 * 
	 * Following IRIS's ProgramSet pattern (ProgramSet.java:64-90).
	 * Uses ExecutorService with fixed thread pool for parallel compilation.
	 * 
	 * @param sources List of program sources to compile
	 * @return List of compiled programs (in same order as sources)
	 * @throws CompilationException if any compilation fails
	 */
	public static List<Program> compileParallel(List<ProgramSource> sources) {
		if (sources == null || sources.isEmpty()) {
			return new ArrayList<>();
		}
		
		long startTime = System.currentTimeMillis();
		LOGGER.info("Starting parallel compilation of {} programs with {} threads", 
			sources.size(), THREAD_POOL_SIZE);
		
		// IRIS ProgramSet.java:64 - try-with-resources ensures proper shutdown
		try (ExecutorService service = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
			// IRIS ProgramSet.java:79 - Array of Futures to collect results
			List<Future<Program>> futures = new ArrayList<>(sources.size());
			
			// IRIS ProgramSet.java:81-83 - Submit compilation tasks
			for (ProgramSource source : sources) {
				Future<Program> future = service.submit(() -> compileProgram(source));
				futures.add(future);
			}
			
			// IRIS ProgramSet.java:85-87 - Collect results from Futures
			List<Program> programs = new ArrayList<>(sources.size());
			for (Future<Program> future : futures) {
				try {
					Program program = future.get();
					programs.add(program);
				} catch (ExecutionException e) {
					// Unwrap the actual exception
					Throwable cause = e.getCause();
					if (cause instanceof ShaderCompileException) {
						throw (ShaderCompileException) cause;
					} else if (cause instanceof RuntimeException) {
						throw (RuntimeException) cause;
					} else {
						throw new RuntimeException("Compilation failed", cause);
					}
				}
			}
			
			long duration = System.currentTimeMillis() - startTime;
			LOGGER.info("Parallel compilation completed: {} programs in {}ms", 
				programs.size(), duration);
			
			return programs;
			
		} catch (InterruptedException e) {
			// IRIS ProgramSet.java:88-90 - Handle interruption
			Thread.currentThread().interrupt();
			throw new RuntimeException("Parallel compilation interrupted", e);
		}
	}
	
	/**
	 * Compiles a single program (helper method for parallel execution).
	 * 
	 * @param source The program source to compile
	 * @return The compiled program
	 * @throws ShaderCompileException if compilation fails
	 */
	private static Program compileProgram(ProgramSource source) {
		LOGGER.debug("Compiling program: {}", source.getName());
		
		// Use ProgramBuilder to compile the program
		ProgramBuilder builder = ProgramBuilder.begin(
			source.getName(),
			source.getVertexSource().orElse(null),
			source.getGeometrySource().orElse(null),
			source.getFragmentSource().orElse(null)
		);
		
		return builder.build();
	}
	
	/**
	 * Compiles multiple programs in parallel and caches them.
	 * 
	 * Convenience method that combines parallel compilation with caching.
	 * 
	 * @param sources List of program sources to compile
	 * @param cache The cache to store compiled programs
	 * @return List of compiled programs (in same order as sources)
	 * @throws CompilationException if any compilation fails
	 */
	public static List<Program> compileAndCache(List<ProgramSource> sources, ProgramCache cache) {
		if (sources == null || sources.isEmpty()) {
			return new ArrayList<>();
		}
		
		if (cache == null) {
			throw new IllegalArgumentException("Cache cannot be null");
		}
		
		// Check cache first for each source
		List<ProgramSource> toCompile = new ArrayList<>();
		List<Program> results = new ArrayList<>(sources.size());
		
		for (ProgramSource source : sources) {
			Program cached = cache.get(source.getName());
			if (cached != null) {
				LOGGER.debug("Using cached program: {}", source.getName());
				results.add(cached);
			} else {
				toCompile.add(source);
				results.add(null);  // Placeholder
			}
		}
		
		// Compile uncached programs in parallel
		if (!toCompile.isEmpty()) {
			List<Program> compiled = compileParallel(toCompile);
			
			// Store in cache and update results
			int compiledIndex = 0;
			for (int i = 0; i < results.size(); i++) {
				if (results.get(i) == null) {
					Program program = compiled.get(compiledIndex++);
					cache.put(sources.get(i).getName(), program);
					results.set(i, program);
				}
			}
		}
		
		return results;
	}
	
	/**
	 * Exception thrown when parallel compilation fails.
	 */
	public static class CompilationException extends RuntimeException {
		public CompilationException(String message) {
			super(message);
		}
		
		public CompilationException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
