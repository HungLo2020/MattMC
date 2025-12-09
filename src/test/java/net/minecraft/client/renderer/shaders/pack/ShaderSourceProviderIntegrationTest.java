package net.minecraft.client.renderer.shaders.pack;

import net.minecraft.client.renderer.shaders.core.ShaderSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for ShaderSourceProvider with real shader files.
 * 
 * Verifies Step 7: Source loading with includes from test_shader pack.
 */
public class ShaderSourceProviderIntegrationTest {
	
	@Mock
	private ShaderPackRepository mockRepository;
	
	@Mock
	private Path mockGameDir;
	
	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
	}
	
	@Test
	public void testLoadSourceWithIncludes() throws java.io.IOException {
		// Create a mock pack source that has the test shader files
		ShaderPackSource mockPackSource = mock(ShaderPackSource.class);
		when(mockPackSource.getName()).thenReturn("test_shader");
		
		// Mock both files to exist
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.fileExists("shaders/lib/common.glsl")).thenReturn(true);
		
		// Mock reading the terrain shader that includes common.glsl
		when(mockPackSource.readFile("shaders/gbuffers_terrain.fsh")).thenReturn(
			java.util.Optional.of(
				"#version 120\n" +
				"#include \"/shaders/lib/common.glsl\"\n" +
				"\n" +
				"void main() {\n" +
				"  float light = calculateLighting(1.0);\n" +
				"  gl_FragColor = vec4(light, light, light, 1.0);\n" +
				"}"
			)
		);
		
		// Mock the common.glsl include file
		when(mockPackSource.readFile("shaders/lib/common.glsl")).thenReturn(
			java.util.Optional.of(
				"// Common shader definitions\n" +
				"const float PI = 3.14159265359;\n" +
				"const float TAU = 6.28318530718;\n" +
				"\n" +
				"float calculateLighting(float baseLight) {\n" +
				"  return baseLight * 0.8;\n" +
				"}"
			)
		);
		
		// Setup starting paths
		List<AbsolutePackPath> startingPaths = List.of(
			AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh")
		);
		
		// Create provider
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		// Get source (should have includes expanded)
		String source = provider.getShaderSource("gbuffers_terrain.fsh");
		
		assertNotNull(source);
		
		// Verify include was expanded
		assertTrue(source.contains("const float PI"));
		assertTrue(source.contains("calculateLighting"));
		assertFalse(source.contains("#include")); // Include directive should be replaced
		
		// Verify main function is still there
		assertTrue(source.contains("void main()"));
		assertTrue(source.contains("gl_FragColor"));
	}
	
	@Test
	public void testMultipleShaderFiles() throws java.io.IOException {
		ShaderPackSource mockPackSource = mock(ShaderPackSource.class);
		when(mockPackSource.getName()).thenReturn("test_shader");
		
		// Mock multiple shader files
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.readFile("shaders/gbuffers_terrain.fsh")).thenReturn(
			java.util.Optional.of("void main() { gl_FragColor = vec4(1.0); }")
		);
		
		when(mockPackSource.fileExists("shaders/gbuffers_water.fsh")).thenReturn(true);
		when(mockPackSource.readFile("shaders/gbuffers_water.fsh")).thenReturn(
			java.util.Optional.of("void main() { gl_FragColor = vec4(0.0, 0.0, 1.0, 0.5); }")
		);
		
		List<AbsolutePackPath> startingPaths = List.of(
			AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh"),
			AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_water.fsh")
		);
		
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		// Load both shaders
		String terrainSource = provider.getShaderSource("gbuffers_terrain.fsh");
		String waterSource = provider.getShaderSource("gbuffers_water.fsh");
		
		assertNotNull(terrainSource);
		assertNotNull(waterSource);
		
		assertTrue(terrainSource.contains("vec4(1.0)"));
		assertTrue(waterSource.contains("vec4(0.0, 0.0, 1.0, 0.5)"));
	}
	
	@Test
	public void testSourceProviderWithFailures() throws java.io.IOException {
		ShaderPackSource mockPackSource = mock(ShaderPackSource.class);
		when(mockPackSource.getName()).thenReturn("test_shader");
		
		// Mock a shader that includes a non-existent file
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.readFile("shaders/gbuffers_terrain.fsh")).thenReturn(
			java.util.Optional.of(
				"#include \"/lib/missing.glsl\"\n" +
				"void main() {}"
			)
		);
		
		// Missing file doesn't exist
		when(mockPackSource.fileExists("shaders/lib/missing.glsl")).thenReturn(false);
		when(mockPackSource.readFile("shaders/lib/missing.glsl")).thenReturn(
			java.util.Optional.empty()
		);
		
		List<AbsolutePackPath> startingPaths = List.of(
			AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh")
		);
		
		// Create provider (should handle failure gracefully)
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		// Include graph should have failures
		assertFalse(provider.getIncludeGraph().getFailures().isEmpty());
		
		// Getting source should return null because of failed include
		String source = provider.getShaderSource("gbuffers_terrain.fsh");
		assertNull(source);
	}
	
	@Test
	public void testCachePerformance() throws java.io.IOException {
		ShaderPackSource mockPackSource = mock(ShaderPackSource.class);
		when(mockPackSource.getName()).thenReturn("test_shader");
		
		String largeSource = "void main() {}\n".repeat(1000);
		
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.readFile("shaders/gbuffers_terrain.fsh")).thenReturn(
			java.util.Optional.of(largeSource)
		);
		
		List<AbsolutePackPath> startingPaths = List.of(
			AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh")
		);
		
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		// First load
		long start1 = System.nanoTime();
		String source1 = provider.getShaderSource("gbuffers_terrain.fsh");
		long time1 = System.nanoTime() - start1;
		
		// Second load (cached)
		long start2 = System.nanoTime();
		String source2 = provider.getShaderSource("gbuffers_terrain.fsh");
		long time2 = System.nanoTime() - start2;
		
		assertNotNull(source1);
		assertNotNull(source2);
		assertSame(source1, source2);
		
		// Cached access should be faster
		assertTrue(time2 < time1, 
			String.format("Cached access (%dns) should be faster than first load (%dns)", time2, time1));
	}
}
