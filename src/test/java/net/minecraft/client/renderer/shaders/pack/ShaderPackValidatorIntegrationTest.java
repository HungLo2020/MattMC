package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for ShaderPackValidator with shader pack structures.
 * Tests validation logic with realistic pack configurations.
 */
class ShaderPackValidatorIntegrationTest {
	
	/**
	 * Test validation of a pack mimicking test_shader structure.
	 * The test_shader pack has: shaders.properties, gbuffers_terrain.fsh, dimension folders.
	 */
	@Test
	void testValidateCompletePackLikeTestShader() throws IOException {
		// Given - Pack structure like test_shader
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("test_shader");
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(source.fileExists("shaders/gbuffers_terrain.vsh")).thenReturn(false);
		when(source.listFiles("shaders")).thenReturn(List.of(
			"gbuffers_terrain.fsh", "lib/common.glsl"
		));
		when(source.fileExists("world0/composite.fsh")).thenReturn(true);
		when(source.fileExists("world-1/composite.fsh")).thenReturn(true);
		when(source.fileExists("world1/composite.fsh")).thenReturn(true);
		when(source.fileExists("dimension.properties")).thenReturn(true);
		when(source.readFile("shaders.properties")).thenReturn(Optional.of(""));
		when(source.readFile("dimension.properties")).thenReturn(Optional.of(
			"dimension.world0=minecraft:overworld\n" +
			"dimension.world-1=minecraft:the_nether\n" +
			"dimension.world1=minecraft:the_end\n"
		));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result).isNotNull();
		assertThat(result.isValid()).isTrue();
		
		// Log results for visibility
		result.logResults();
	}
	
	/**
	 * Test validation of repository with non-existent pack.
	 * Should return invalid result with error.
	 */
	@Test
	void testValidateNonExistentPackThroughRepository() {
		// Given
		ShaderPackRepository repository = mock(ShaderPackRepository.class);
		
		// When - getPackSource returns null for non-existent pack
		when(repository.getPackSource("nonexistent_pack")).thenReturn(null);
		when(repository.validatePack("nonexistent_pack")).thenCallRealMethod();
		
		ShaderPackValidator.ValidationResult result = repository.validatePack("nonexistent_pack");
		
		// Then
		assertThat(result).isNotNull();
		assertThat(result.isValid()).isFalse();
		assertThat(result.getErrors()).anyMatch(e -> 
			e.contains("not found") && e.contains("nonexistent_pack"));
	}
	
	/**
	 * Test validation result structure for complete pack.
	 * Should have no errors, may have warnings.
	 */
	@Test
	void testValidationResultStructure() throws IOException {
		// Given - Complete pack structure
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("complete_pack");
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.fileExists("shaders/final.fsh")).thenReturn(true);
		when(source.listFiles("shaders")).thenReturn(List.of("composite.fsh", "final.fsh"));
		when(source.readFile("shaders.properties")).thenReturn(Optional.of(""));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.getWarnings()).isNotNull();
	}
	
	/**
	 * Test that validation checks for shaders.properties.
	 * Pack with properties should not generate warning.
	 */
	@Test
	void testValidationChecksForProperties() throws IOException {
		// Given - Pack with shaders.properties
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("props_pack");
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.listFiles("shaders")).thenReturn(List.of("composite.fsh"));
		when(source.readFile("shaders.properties")).thenReturn(Optional.of(""));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then - Should not warn about missing properties
		assertThat(result.getWarnings()).noneMatch(w -> 
			w.contains("No shaders.properties file found"));
	}
	
	/**
	 * Test that validation checks for dimension support.
	 * Pack with all dimension folders should not generate warnings.
	 */
	@Test
	void testValidationChecksDimensionSupport() throws IOException {
		// Given - Pack with all dimension folders
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("dimension_pack");
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.listFiles("shaders")).thenReturn(List.of("composite.fsh"));
		when(source.fileExists("world0/composite.fsh")).thenReturn(true);
		when(source.fileExists("world-1/composite.fsh")).thenReturn(true);
		when(source.fileExists("world1/composite.fsh")).thenReturn(true);
		when(source.fileExists("dimension.properties")).thenReturn(true);
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.readFile("shaders.properties")).thenReturn(Optional.of(""));
		when(source.readFile("dimension.properties")).thenReturn(Optional.of(""));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then - Should not warn about missing dimensions
		assertThat(result.getWarnings()).noneMatch(w -> 
			w.contains("missing Nether") || w.contains("missing End"));
	}
	
	/**
	 * Test validation of multiple pack scenarios.
	 * Ensures validator handles various pack types.
	 */
	@Test
	void testValidateMultiplePackScenarios() throws IOException {
		// Scenario 1: Minimal valid pack
		ShaderPackSource minimal = mock(ShaderPackSource.class);
		when(minimal.getName()).thenReturn("minimal");
		when(minimal.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(minimal.listFiles("shaders")).thenReturn(List.of("composite.fsh"));
		
		ShaderPackValidator.ValidationResult minimalResult = ShaderPackValidator.validate(minimal);
		assertThat(minimalResult.isValid()).isTrue();
		
		// Scenario 2: Pack with all features
		ShaderPackSource complete = mock(ShaderPackSource.class);
		when(complete.getName()).thenReturn("complete");
		when(complete.fileExists("shaders.properties")).thenReturn(true);
		when(complete.fileExists("shaders/gbuffers_terrain.vsh")).thenReturn(true);
		when(complete.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(complete.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(complete.fileExists("shaders/final.fsh")).thenReturn(true);
		when(complete.listFiles("shaders")).thenReturn(List.of(
			"gbuffers_terrain.vsh", "gbuffers_terrain.fsh", "composite.fsh", "final.fsh"
		));
		when(complete.fileExists("world0/composite.fsh")).thenReturn(true);
		when(complete.fileExists("world-1/composite.fsh")).thenReturn(true);
		when(complete.fileExists("world1/composite.fsh")).thenReturn(true);
		when(complete.fileExists("dimension.properties")).thenReturn(true);
		when(complete.readFile("shaders.properties")).thenReturn(Optional.of(""));
		when(complete.readFile("dimension.properties")).thenReturn(Optional.of(""));
		
		ShaderPackValidator.ValidationResult completeResult = ShaderPackValidator.validate(complete);
		assertThat(completeResult.isValid()).isTrue();
		assertThat(completeResult.getErrors()).isEmpty();
	}
}
