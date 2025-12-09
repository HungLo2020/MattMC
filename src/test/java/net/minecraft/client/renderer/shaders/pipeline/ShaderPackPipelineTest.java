package net.minecraft.client.renderer.shaders.pipeline;

import net.minecraft.client.renderer.shaders.pack.ShaderPackSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ShaderPackPipeline.
 * Tests shader pipeline behavior.
 */
class ShaderPackPipelineTest {
	
	@Test
	void testShaderPackPipelineCreation() throws IOException {
		// Given
		ShaderPackSource mockSource = createMockPackSource("test_pack", "");
		
		// When
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// Then
		assertThat(pipeline).isNotNull();
		assertThat(pipeline.getPackName()).isEqualTo("test_pack");
		assertThat(pipeline.getDimension()).isEqualTo("minecraft:overworld");
	}
	
	@Test
	void testGetPhaseInitiallyReturnsNone() throws IOException {
		// Given
		ShaderPackSource mockSource = createMockPackSource("test_pack", "");
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When
		WorldRenderingPhase phase = pipeline.getPhase();
		
		// Then
		assertThat(phase).isEqualTo(WorldRenderingPhase.NONE);
	}
	
	@Test
	void testSetPhaseUpdatesPhase() throws IOException {
		// Given
		ShaderPackSource mockSource = createMockPackSource("test_pack", "");
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When
		pipeline.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
		
		// Then
		assertThat(pipeline.getPhase()).isEqualTo(WorldRenderingPhase.TERRAIN_SOLID);
	}
	
	@Test
	void testShouldRenderSunUsesShaderProperties() throws IOException {
		// Given
		String properties = "sun=false\n";
		ShaderPackSource mockSource = createMockPackSource("test_pack", properties);
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When
		boolean shouldRender = pipeline.shouldRenderSun();
		
		// Then - Should use shader properties
		assertThat(shouldRender).isFalse();
	}
	
	@Test
	void testShouldRenderMoonUsesShaderProperties() throws IOException {
		// Given
		String properties = "moon=false\n";
		ShaderPackSource mockSource = createMockPackSource("test_pack", properties);
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When
		boolean shouldRender = pipeline.shouldRenderMoon();
		
		// Then - Should use shader properties
		assertThat(shouldRender).isFalse();
	}
	
	@Test
	void testShouldRenderWeatherUsesShaderProperties() throws IOException {
		// Given
		String properties = "weather=false\n";
		ShaderPackSource mockSource = createMockPackSource("test_pack", properties);
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When
		boolean shouldRender = pipeline.shouldRenderWeather();
		
		// Then - Should use shader properties
		assertThat(shouldRender).isFalse();
	}
	
	@Test
	void testShouldRenderVignetteUsesShaderProperties() throws IOException {
		// Given
		String properties = "vignette=false\n";
		ShaderPackSource mockSource = createMockPackSource("test_pack", properties);
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When
		boolean shouldRender = pipeline.shouldRenderVignette();
		
		// Then - Should use shader properties
		assertThat(shouldRender).isFalse();
	}
	
	@Test
	void testShouldRenderUnderwaterOverlayUsesShaderProperties() throws IOException {
		// Given
		String properties = "underwaterOverlay=false\n";
		ShaderPackSource mockSource = createMockPackSource("test_pack", properties);
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When
		boolean shouldRender = pipeline.shouldRenderUnderwaterOverlay();
		
		// Then - Should use shader properties
		assertThat(shouldRender).isFalse();
	}
	
	@Test
	void testShouldRenderPropertiesUseDefaultWhenNotSpecified() throws IOException {
		// Given - Empty properties
		ShaderPackSource mockSource = createMockPackSource("test_pack", "");
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When & Then - Should use defaults (true)
		assertThat(pipeline.shouldRenderSun()).isTrue();
		assertThat(pipeline.shouldRenderMoon()).isTrue();
		assertThat(pipeline.shouldRenderWeather()).isTrue();
		assertThat(pipeline.shouldRenderVignette()).isTrue();
		assertThat(pipeline.shouldRenderUnderwaterOverlay()).isTrue();
	}
	
	@Test
	void testBeginLevelRenderingDoesNotThrow() throws IOException {
		// Given
		ShaderPackSource mockSource = createMockPackSource("test_pack", "");
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When & Then - Should not throw
		assertThatNoException().isThrownBy(() -> pipeline.beginLevelRendering());
	}
	
	@Test
	void testFinalizeLevelRenderingDoesNotThrow() throws IOException {
		// Given
		ShaderPackSource mockSource = createMockPackSource("test_pack", "");
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When & Then - Should not throw
		assertThatNoException().isThrownBy(() -> pipeline.finalizeLevelRendering());
	}
	
	@Test
	void testDestroyDoesNotThrow() throws IOException {
		// Given
		ShaderPackSource mockSource = createMockPackSource("test_pack", "");
		ShaderPackPipeline pipeline = new ShaderPackPipeline("test_pack", "minecraft:overworld", mockSource);
		
		// When & Then - Should not throw
		assertThatNoException().isThrownBy(() -> pipeline.destroy());
	}
	
	private ShaderPackSource createMockPackSource(String name, String properties) {
		return new ShaderPackSource() {
			@Override
			public String getName() {
				return name;
			}
			
			@Override
			public java.util.Optional<String> readFile(String relativePath) throws IOException {
				if ("shaders.properties".equals(relativePath)) {
					return properties.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(properties);
				}
				return java.util.Optional.empty();
			}
			
			@Override
			public boolean fileExists(String relativePath) {
				return "shaders.properties".equals(relativePath) && !properties.isEmpty();
			}
			
			@Override
			public java.util.List<String> listFiles(String directory) throws IOException {
				return java.util.List.of();
			}
		};
	}
}
