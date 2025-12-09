package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ShaderPackSourceNames.
 * 
 * Verifies Step 7: Shader file name discovery.
 */
public class ShaderPackSourceNamesTest {
	
	@Test
	public void testGetPotentialStarts() {
		List<String> potentialStarts = ShaderPackSourceNames.getPotentialStarts();
		
		assertNotNull(potentialStarts);
		assertFalse(potentialStarts.isEmpty());
		
		// Verify common shader files are included
		assertTrue(potentialStarts.contains("gbuffers_terrain.fsh"));
		assertTrue(potentialStarts.contains("gbuffers_terrain.vsh"));
		assertTrue(potentialStarts.contains("gbuffers_water.fsh"));
		assertTrue(potentialStarts.contains("gbuffers_entities.fsh"));
		assertTrue(potentialStarts.contains("composite.fsh"));
		assertTrue(potentialStarts.contains("final.fsh"));
		
		// Verify all shader stages for a program
		assertTrue(potentialStarts.contains("shadow.vsh"));
		assertTrue(potentialStarts.contains("shadow.fsh"));
		assertTrue(potentialStarts.contains("shadow.gsh"));
		assertTrue(potentialStarts.contains("shadow.tcs"));
		assertTrue(potentialStarts.contains("shadow.tes"));
	}
	
	@Test
	public void testCompositePrograms() {
		List<String> potentialStarts = ShaderPackSourceNames.getPotentialStarts();
		
		// Verify composite programs (composite, composite1...composite15)
		assertTrue(potentialStarts.contains("composite.fsh"));
		assertTrue(potentialStarts.contains("composite1.fsh"));
		assertTrue(potentialStarts.contains("composite15.fsh"));
	}
	
	@Test
	public void testDeferredPrograms() {
		List<String> potentialStarts = ShaderPackSourceNames.getPotentialStarts();
		
		// Verify deferred programs
		assertTrue(potentialStarts.contains("deferred.fsh"));
		assertTrue(potentialStarts.contains("deferred1.fsh"));
		assertTrue(potentialStarts.contains("deferred15.fsh"));
	}
	
	@Test
	public void testPreparePrograms() {
		List<String> potentialStarts = ShaderPackSourceNames.getPotentialStarts();
		
		// Verify prepare programs
		assertTrue(potentialStarts.contains("prepare.fsh"));
		assertTrue(potentialStarts.contains("prepare1.fsh"));
		assertTrue(potentialStarts.contains("prepare15.fsh"));
	}
	
	@Test
	public void testFindPresentSources() {
		MockitoAnnotations.openMocks(this);
		ShaderPackSource mockPackSource = mock(ShaderPackSource.class);
		
		// Mock some existing files
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.vsh")).thenReturn(true);
		when(mockPackSource.fileExists("shaders/gbuffers_water.fsh")).thenReturn(true);
		when(mockPackSource.fileExists("shaders/composite.fsh")).thenReturn(true);
		
		// All other files don't exist
		when(mockPackSource.fileExists(argThat(path -> 
			!path.equals("shaders/gbuffers_terrain.fsh") && 
			!path.equals("shaders/gbuffers_terrain.vsh") &&
			!path.equals("shaders/gbuffers_water.fsh") &&
			!path.equals("shaders/composite.fsh")
		))).thenReturn(false);
		
		List<String> candidates = List.of(
			"gbuffers_terrain.fsh",
			"gbuffers_terrain.vsh",
			"gbuffers_water.fsh",
			"gbuffers_water.vsh",
			"composite.fsh",
			"final.fsh"
		);
		
		List<AbsolutePackPath> found = ShaderPackSourceNames.findPresentSources(
			mockPackSource,
			"/shaders/",
			candidates
		);
		
		assertEquals(4, found.size());
		
		// Verify found paths
		assertTrue(found.stream().anyMatch(p -> p.getPathString().equals("/shaders/gbuffers_terrain.fsh")));
		assertTrue(found.stream().anyMatch(p -> p.getPathString().equals("/shaders/gbuffers_terrain.vsh")));
		assertTrue(found.stream().anyMatch(p -> p.getPathString().equals("/shaders/gbuffers_water.fsh")));
		assertTrue(found.stream().anyMatch(p -> p.getPathString().equals("/shaders/composite.fsh")));
	}
	
	@Test
	public void testFindPresentSourcesWithoutLeadingSlash() {
		MockitoAnnotations.openMocks(this);
		ShaderPackSource mockPackSource = mock(ShaderPackSource.class);
		
		when(mockPackSource.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		
		List<String> candidates = List.of("gbuffers_terrain.fsh");
		
		// Test with directory without leading slash
		List<AbsolutePackPath> found = ShaderPackSourceNames.findPresentSources(
			mockPackSource,
			"shaders",
			candidates
		);
		
		assertEquals(1, found.size());
		assertEquals("/shaders/gbuffers_terrain.fsh", found.get(0).getPathString());
	}
	
	@Test
	public void testFindPresentSourcesEmptyResult() {
		MockitoAnnotations.openMocks(this);
		ShaderPackSource mockPackSource = mock(ShaderPackSource.class);
		
		// No files exist
		when(mockPackSource.fileExists(anyString())).thenReturn(false);
		
		List<String> candidates = List.of(
			"gbuffers_terrain.fsh",
			"gbuffers_water.fsh"
		);
		
		List<AbsolutePackPath> found = ShaderPackSourceNames.findPresentSources(
			mockPackSource,
			"/shaders/",
			candidates
		);
		
		assertTrue(found.isEmpty());
	}
}
