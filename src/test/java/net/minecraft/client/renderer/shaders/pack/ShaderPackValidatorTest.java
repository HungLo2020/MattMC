package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ShaderPackValidator.
 * Following Step 10 of NEW-SHADER-PLAN.md and IRIS validation patterns.
 */
class ShaderPackValidatorTest {
	
	/**
	 * Test validation of a complete, valid shader pack.
	 * Should pass with no errors or warnings.
	 */
	@Test
	void testValidCompleteShaderPack() throws IOException {
		// Given - A complete shader pack with all essential files
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("complete_pack");
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.fileExists("shaders/gbuffers_terrain.vsh")).thenReturn(true);
		when(source.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.fileExists("shaders/final.fsh")).thenReturn(true);
		when(source.listFiles("shaders")).thenReturn(List.of(
			"gbuffers_terrain.vsh", "gbuffers_terrain.fsh", "composite.fsh", "final.fsh"
		));
		
		// Mock ShaderProperties loading
		when(source.readFile("shaders.properties")).thenReturn(Optional.of(""));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result.isValid()).isTrue();
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.getWarnings()).isEmpty();
	}
	
	/**
	 * Test validation of pack with no shaders directory.
	 * Should fail - following IRIS's primary validation (Iris.java:494).
	 */
	@Test
	void testInvalidPackNoShadersDirectory() throws IOException {
		// Given - Pack with no shaders directory
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("no_shaders_pack");
		when(source.listFiles("shaders")).thenReturn(new ArrayList<>());
		when(source.fileExists(anyString())).thenReturn(false);
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result.isValid()).isFalse();
		assertThat(result.getErrors()).isNotEmpty();
		assertThat(result.getErrors().get(0))
			.contains("No shaders directory found");
	}
	
	/**
	 * Test validation of pack with no essential shader files.
	 * Should fail - no useful shaders present.
	 */
	@Test
	void testInvalidPackNoEssentialShaders() throws IOException {
		// Given - Pack has shaders directory but no essential shader files
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("empty_shaders_pack");
		when(source.listFiles("shaders")).thenReturn(List.of("random.txt"));
		when(source.fileExists(anyString())).thenReturn(false);
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.readFile("shaders.properties")).thenReturn(Optional.of(""));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result.isValid()).isFalse();
		assertThat(result.getErrors()).anyMatch(e -> 
			e.contains("No essential shader files found"));
	}
	
	/**
	 * Test validation of pack with missing shaders.properties.
	 * Should pass but with warning - following IRIS pattern.
	 */
	@Test
	void testValidPackMissingPropertiesFile() throws IOException {
		// Given - Pack has shaders but no shaders.properties
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("minimal_pack");
		when(source.fileExists("shaders.properties")).thenReturn(false);
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.listFiles("shaders")).thenReturn(List.of("composite.fsh"));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result.isValid()).isTrue();
		assertThat(result.getErrors()).isEmpty();
		assertThat(result.getWarnings()).anyMatch(w -> 
			w.contains("No shaders.properties file found"));
	}
	
	/**
	 * Test validation of pack with mismatched program pairs.
	 * Should warn about missing fragment/vertex shaders.
	 */
	@Test
	void testPackWithMismatchedProgramPairs() throws IOException {
		// Given - Pack has vertex shader but no fragment shader
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("mismatched_pack");
		when(source.fileExists("shaders/gbuffers_terrain.vsh")).thenReturn(true);
		when(source.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(false);
		when(source.fileExists("shaders/gbuffers_water.fsh")).thenReturn(true);
		when(source.fileExists("shaders/gbuffers_water.vsh")).thenReturn(false);
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.listFiles("shaders")).thenReturn(List.of(
			"gbuffers_terrain.vsh", "gbuffers_water.fsh", "composite.fsh"
		));
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.readFile("shaders.properties")).thenReturn(Optional.of(""));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result.isValid()).isTrue();
		assertThat(result.getWarnings()).anyMatch(w -> 
			w.contains("gbuffers_terrain.vsh") && w.contains("missing .fsh"));
		assertThat(result.getWarnings()).anyMatch(w -> 
			w.contains("gbuffers_water.fsh") && w.contains("missing .vsh"));
	}
	
	/**
	 * Test validation of pack with missing final.fsh.
	 * Should warn - final pass is common in IRIS packs.
	 */
	@Test
	void testPackMissingFinalPass() throws IOException {
		// Given - Pack without final.fsh
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("no_final_pack");
		when(source.fileExists("shaders/gbuffers_terrain.fsh")).thenReturn(true);
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.fileExists("shaders/final.fsh")).thenReturn(false);
		when(source.listFiles("shaders")).thenReturn(List.of(
			"gbuffers_terrain.fsh", "composite.fsh"
		));
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.readFile("shaders.properties")).thenReturn(Optional.of(""));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result.isValid()).isTrue();
		assertThat(result.getWarnings()).anyMatch(w -> 
			w.contains("No final.fsh found"));
	}
	
	/**
	 * Test validation of pack with custom properties.
	 * Should parse successfully even with unknown properties.
	 */
	@Test
	void testPackWithCustomProperties() throws IOException {
		// Given - Pack with custom property values
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("custom_props_pack");
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.listFiles("shaders")).thenReturn(List.of("composite.fsh"));
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.readFile("shaders.properties")).thenReturn(
			Optional.of("oldLighting=true\nsun=false\n")
		);
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then - Should parse successfully
		assertThat(result.isValid()).isTrue();
		// No errors expected for valid properties
		assertThat(result.getErrors()).isEmpty();
	}
	
	/**
	 * Test validation of pack with dimension folders but incomplete coverage.
	 * Should warn about missing dimension shaders.
	 */
	@Test
	void testPackWithIncompleteDimensionSupport() throws IOException {
		// Given - Pack has Overworld but not Nether/End
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("incomplete_dimension_pack");
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.listFiles("shaders")).thenReturn(List.of("composite.fsh"));
		when(source.fileExists("world0/composite.fsh")).thenReturn(true);
		when(source.fileExists("world-1/composite.fsh")).thenReturn(false);
		when(source.fileExists("world1/composite.fsh")).thenReturn(false);
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.readFile("shaders.properties")).thenReturn(Optional.of(""));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result.isValid()).isTrue();
		assertThat(result.getWarnings()).anyMatch(w -> 
			w.contains("Overworld") && w.contains("Nether"));
		assertThat(result.getWarnings()).anyMatch(w -> 
			w.contains("Overworld") && w.contains("End"));
	}
	
	/**
	 * Test validation of pack with dimension folders but no dimension.properties.
	 * Should warn about missing dimension.properties.
	 */
	@Test
	void testPackWithDimensionFoldersButNoProperties() throws IOException {
		// Given - Pack has dimension folders but no dimension.properties
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("dimension_no_props_pack");
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.listFiles("shaders")).thenReturn(List.of("composite.fsh"));
		when(source.fileExists("world0/composite.fsh")).thenReturn(true);
		when(source.fileExists("dimension.properties")).thenReturn(false);
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.readFile("shaders.properties")).thenReturn(Optional.of(""));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result.isValid()).isTrue();
		assertThat(result.getWarnings()).anyMatch(w -> 
			w.contains("dimension folders") && w.contains("dimension.properties"));
	}
	
	/**
	 * Test validation of pack with invalid properties file.
	 * Should error about parsing failure.
	 */
	@Test
	void testPackWithCorruptPropertiesFile() throws IOException {
		// Given - Pack with unparseable properties
		ShaderPackSource source = mock(ShaderPackSource.class);
		when(source.getName()).thenReturn("corrupt_props_pack");
		when(source.fileExists("shaders/composite.fsh")).thenReturn(true);
		when(source.listFiles("shaders")).thenReturn(List.of("composite.fsh"));
		when(source.fileExists("shaders.properties")).thenReturn(true);
		when(source.readFile("shaders.properties")).thenThrow(new IOException("Corrupt file"));
		
		// When
		ShaderPackValidator.ValidationResult result = ShaderPackValidator.validate(source);
		
		// Then
		assertThat(result.isValid()).isFalse();
		assertThat(result.getErrors()).anyMatch(e -> 
			e.contains("Failed to parse shaders.properties"));
	}
	
	/**
	 * Test ValidationResult logging.
	 * Should log errors, warnings, and success messages appropriately.
	 */
	@Test
	void testValidationResultLogging() {
		// Given - Results with various states
		List<String> errors = List.of("Error 1", "Error 2");
		List<String> warnings = List.of("Warning 1");
		
		ShaderPackValidator.ValidationResult failResult = 
			new ShaderPackValidator.ValidationResult(false, errors, warnings);
		ShaderPackValidator.ValidationResult passResult = 
			new ShaderPackValidator.ValidationResult(true, new ArrayList<>(), new ArrayList<>());
		
		// When/Then - Should not throw
		assertThatCode(() -> failResult.logResults()).doesNotThrowAnyException();
		assertThatCode(() -> passResult.logResults()).doesNotThrowAnyException();
	}
	
	/**
	 * Test ValidationResult immutability.
	 * Returned lists should be copies, not originals.
	 */
	@Test
	void testValidationResultImmutability() {
		// Given
		List<String> errors = new ArrayList<>();
		errors.add("Error 1");
		List<String> warnings = new ArrayList<>();
		warnings.add("Warning 1");
		
		ShaderPackValidator.ValidationResult result = 
			new ShaderPackValidator.ValidationResult(false, errors, warnings);
		
		// When - Modify returned lists
		result.getErrors().add("Error 2");
		result.getWarnings().add("Warning 2");
		
		// Then - Original result should be unchanged
		assertThat(result.getErrors()).hasSize(1);
		assertThat(result.getWarnings()).hasSize(1);
	}
}
