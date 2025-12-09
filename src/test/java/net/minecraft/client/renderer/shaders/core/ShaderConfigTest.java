package net.minecraft.client.renderer.shaders.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ShaderConfig functionality.
 * Tests configuration persistence, loading, and option management.
 */
class ShaderConfigTest {
	
	@TempDir
	Path tempDir;
	
	private ShaderConfig config;
	
	@BeforeEach
	void setUp() {
		config = new ShaderConfig();
	}
	
	@Test
	void testDefaultValues() {
		// When
		config.initialize(tempDir);
		
		// Then
		assertThat(config.areShadersEnabled()).isTrue();
		assertThat(config.getSelectedPack()).isNull();
	}
	
	@Test
	void testSetShadersEnabled() {
		// Given
		config.initialize(tempDir);
		
		// When
		config.setShadersEnabled(false);
		
		// Then
		assertThat(config.areShadersEnabled()).isFalse();
	}
	
	@Test
	void testSetSelectedPack() {
		// Given
		config.initialize(tempDir);
		
		// When
		config.setSelectedPack("test_shader");
		
		// Then
		assertThat(config.getSelectedPack()).isEqualTo("test_shader");
	}
	
	@Test
	void testSetPackOption() {
		// Given
		config.initialize(tempDir);
		
		// When
		config.setPackOption("shadowMapResolution", "2048");
		
		// Then
		assertThat(config.getPackOption("shadowMapResolution", "1024")).isEqualTo("2048");
	}
	
	@Test
	void testGetPackOptionWithDefault() {
		// Given
		config.initialize(tempDir);
		
		// When
		String value = config.getPackOption("nonexistent", "default_value");
		
		// Then
		assertThat(value).isEqualTo("default_value");
	}
	
	@Test
	void testConfigurationPersistence() throws IOException {
		// Given
		config.initialize(tempDir);
		config.setShadersEnabled(false);
		config.setSelectedPack("complimentary");
		config.setPackOption("shadowMapResolution", "4096");
		
		// Verify file was created
		Path configFile = tempDir.resolve("shader-config.json");
		assertThat(Files.exists(configFile)).isTrue();
		
		// When - Load configuration in a new instance
		ShaderConfig newConfig = new ShaderConfig();
		newConfig.initialize(tempDir);
		
		// Then
		assertThat(newConfig.areShadersEnabled()).isFalse();
		assertThat(newConfig.getSelectedPack()).isEqualTo("complimentary");
		assertThat(newConfig.getPackOption("shadowMapResolution", "1024")).isEqualTo("4096");
	}
	
	@Test
	void testLoadWithNonexistentFile() {
		// When
		config.initialize(tempDir);
		
		// Then - Should use defaults without throwing
		assertThat(config.areShadersEnabled()).isTrue();
		assertThat(config.getSelectedPack()).isNull();
	}
	
	@Test
	void testSaveCreatesFile() {
		// Given
		config.initialize(tempDir);
		
		// When
		config.save();
		
		// Then
		Path configFile = tempDir.resolve("shader-config.json");
		assertThat(Files.exists(configFile)).isTrue();
	}
	
	@Test
	void testConfigFileFormat() throws IOException {
		// Given
		config.initialize(tempDir);
		config.setShadersEnabled(true);
		config.setSelectedPack("test_pack");
		
		// When
		Path configFile = tempDir.resolve("shader-config.json");
		String content = Files.readString(configFile);
		
		// Then - Verify JSON format
		assertThat(content).contains("\"shadersEnabled\"");
		assertThat(content).contains("\"selectedPack\"");
		assertThat(content).contains("test_pack");
	}
}
