package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for ShaderSourceProvider.
 * 
 * Verifies Step 7: Shader Source Provider functionality.
 */
public class ShaderSourceProviderTest {
	
	@Mock
	private ShaderPackSource mockPackSource;
	
	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
		when(mockPackSource.getName()).thenReturn("test_pack");
	}
	
	@Test
	public void testSourceProviderCreation() throws java.io.IOException {
		// Setup mock pack source with a simple shader file
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.readFile("shaders/gbuffers_terrain.fsh")).thenReturn(
			Optional.of("void main() {\n  gl_FragColor = vec4(1.0);\n}")
		);
		
		List<AbsolutePackPath> startingPaths = new ArrayList<>();
		startingPaths.add(AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh"));
		
		// Create provider
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		assertNotNull(provider);
		assertEquals(mockPackSource, provider.getPackSource());
		assertNotNull(provider.getSourceProvider());
		assertNotNull(provider.getIncludeGraph());
	}
	
	@Test
	public void testGetShaderSource() throws java.io.IOException {
		// Setup mock pack source
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.readFile("shaders/gbuffers_terrain.fsh")).thenReturn(
			Optional.of("void main() {\n  gl_FragColor = vec4(1.0);\n}")
		);
		
		List<AbsolutePackPath> startingPaths = new ArrayList<>();
		startingPaths.add(AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh"));
		
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		// Get source
		String source = provider.getShaderSource("gbuffers_terrain.fsh");
		
		assertNotNull(source);
		assertTrue(source.contains("void main()"));
		assertTrue(source.contains("gl_FragColor"));
	}
	
	@Test
	public void testSourceCaching() throws java.io.IOException {
		// Setup mock pack source
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.readFile("shaders/gbuffers_terrain.fsh")).thenReturn(
			Optional.of("void main() {}")
		);
		
		List<AbsolutePackPath> startingPaths = new ArrayList<>();
		startingPaths.add(AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh"));
		
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		// First load
		String source1 = provider.getShaderSource("gbuffers_terrain.fsh");
		
		// Second load (should be cached)
		String source2 = provider.getShaderSource("gbuffers_terrain.fsh");
		
		assertNotNull(source1);
		assertNotNull(source2);
		assertSame(source1, source2); // Same object due to caching
		
		// Verify file was only read during graph construction
		verify(mockPackSource, times(1)).readFile("shaders/gbuffers_terrain.fsh");
	}
	
	@Test
	public void testMissingFile() {
		// Setup mock pack source with no files
		when(mockPackSource.fileExists(anyString())).thenReturn(false);
		
		List<AbsolutePackPath> startingPaths = new ArrayList<>();
		
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		// Try to get non-existent file
		String source = provider.getShaderSource("nonexistent.fsh");
		
		assertNull(source);
	}
	
	@Test
	public void testHasShaderFile() {
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.fileExists("shaders/nonexistent.fsh")).thenReturn(false);
		
		List<AbsolutePackPath> startingPaths = new ArrayList<>();
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		assertTrue(provider.hasShaderFile("gbuffers_terrain.fsh"));
		assertFalse(provider.hasShaderFile("nonexistent.fsh"));
	}
	
	@Test
	public void testClearCache() throws java.io.IOException {
		// Setup mock pack source
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.readFile("shaders/gbuffers_terrain.fsh")).thenReturn(
			Optional.of("void main() {}")
		);
		
		List<AbsolutePackPath> startingPaths = new ArrayList<>();
		startingPaths.add(AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh"));
		
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		// Load source
		provider.getShaderSource("gbuffers_terrain.fsh");
		
		// Verify cache has content
		Map<String, String> cachedBefore = provider.getAllCachedSources();
		assertEquals(1, cachedBefore.size());
		
		// Clear cache
		provider.clearCache();
		
		// Verify cache is empty
		Map<String, String> cachedAfter = provider.getAllCachedSources();
		assertEquals(0, cachedAfter.size());
	}
	
	@Test
	public void testGetAllCachedSources() throws java.io.IOException {
		// Setup mock pack source with multiple files
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.fileExists("shaders/gbuffers_water.fsh")).thenReturn(true);
		when(mockPackSource.readFile("shaders/gbuffers_terrain.fsh")).thenReturn(
			Optional.of("// terrain")
		);
		when(mockPackSource.readFile("shaders/gbuffers_water.fsh")).thenReturn(
			Optional.of("// water")
		);
		
		List<AbsolutePackPath> startingPaths = new ArrayList<>();
		startingPaths.add(AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh"));
		startingPaths.add(AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_water.fsh"));
		
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		// Load both sources
		provider.getShaderSource("gbuffers_terrain.fsh");
		provider.getShaderSource("gbuffers_water.fsh");
		
		// Get all cached
		Map<String, String> cached = provider.getAllCachedSources();
		
		assertEquals(2, cached.size());
		assertTrue(cached.containsKey("gbuffers_terrain.fsh"));
		assertTrue(cached.containsKey("gbuffers_water.fsh"));
	}
	
	@Test
	public void testSourceProviderFunction() throws java.io.IOException {
		// Setup mock pack source
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.readFile("shaders/gbuffers_terrain.fsh")).thenReturn(
			Optional.of("void main() {}")
		);
		
		List<AbsolutePackPath> startingPaths = new ArrayList<>();
		startingPaths.add(AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh"));
		
		ShaderSourceProvider provider = new ShaderSourceProvider(mockPackSource, startingPaths);
		
		// Get the source provider function
		Function<AbsolutePackPath, String> sourceProviderFunc = provider.getSourceProvider();
		assertNotNull(sourceProviderFunc);
		
		// Use it directly
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh");
		String source = sourceProviderFunc.apply(path);
		
		assertNotNull(source);
		assertTrue(source.contains("void main()"));
	}
}
