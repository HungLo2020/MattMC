package net.minecraft.client.renderer.shaders.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ShaderSystem.
 * Tests integration with the full system initialization path.
 */
class ShaderSystemIntegrationTest {
	
	@TempDir
	Path tempDir;
	
	@Test
	void testShaderSystemInitializesCorrectly() {
		// Given - Fresh shader system instance
		ShaderSystem system = ShaderSystem.getInstance();
		
		// When - Initialize with a game directory
		system.earlyInitialize(tempDir);
		
		// Then - System should be fully initialized
		assertThat(system.isInitialized()).isTrue();
		assertThat(system.getConfig()).isNotNull();
		assertThat(system.getGameDirectory()).isNotNull();
	}
	
	@Test
	void testShaderConfigLoadsSavedState() {
		// Given - Create a fresh config in a specific temp directory
		ShaderConfig config = new ShaderConfig();
		config.initialize(tempDir);
		
		// Set specific values
		config.setShadersEnabled(false);
		config.setSelectedPack("test_shader_pack");
		config.setPackOption("shadowMapResolution", "4096");
		
		// When - Create a new config instance (simulating restart)
		ShaderConfig newConfig = new ShaderConfig();
		newConfig.initialize(tempDir);
		
		// Then - Configuration should be loaded from disk
		assertThat(newConfig.areShadersEnabled()).isFalse();
		assertThat(newConfig.getSelectedPack()).isEqualTo("test_shader_pack");
		assertThat(newConfig.getPackOption("shadowMapResolution", "1024")).isEqualTo("4096");
	}
	
	@Test
	void testMultipleShaderPackOptions() {
		// Given
		ShaderSystem system = ShaderSystem.getInstance();
		system.earlyInitialize(tempDir);
		ShaderConfig config = system.getConfig();
		
		// When - Set multiple options
		config.setPackOption("shadowMapResolution", "2048");
		config.setPackOption("sunPathRotation", "25.0");
		config.setPackOption("renderQuality", "HIGH");
		
		// Then - All options should be retrievable
		assertThat(config.getPackOption("shadowMapResolution", "")).isEqualTo("2048");
		assertThat(config.getPackOption("sunPathRotation", "")).isEqualTo("25.0");
		assertThat(config.getPackOption("renderQuality", "")).isEqualTo("HIGH");
	}
	
	@Test
	void testShaderSystemLogsInitialization() {
		// Given
		ShaderSystem system = ShaderSystem.getInstance();
		
		// When - Initialize (this will log)
		system.earlyInitialize(tempDir);
		
		// Then - Should not throw and should be initialized
		// (Actual log verification would require a logging framework spy)
		assertThat(system.isInitialized()).isTrue();
	}
}
