package net.minecraft.client.renderer.shaders.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for VanillaRenderingPipeline.
 * Tests vanilla pipeline behavior.
 */
class VanillaRenderingPipelineTest {
	
	@Test
	void testVanillaPipelineCreation() {
		// When
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// Then
		assertThat(pipeline).isNotNull();
	}
	
	@Test
	void testGetPhaseReturnsNone() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When
		WorldRenderingPhase phase = pipeline.getPhase();
		
		// Then - Vanilla always returns NONE
		assertThat(phase).isEqualTo(WorldRenderingPhase.NONE);
	}
	
	@Test
	void testSetPhaseDoesNothing() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When - Setting phase should not throw
		pipeline.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
		
		// Then - Phase still returns NONE
		assertThat(pipeline.getPhase()).isEqualTo(WorldRenderingPhase.NONE);
	}
	
	@Test
	void testShouldDisableFrustumCullingReturnsFalse() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When
		boolean shouldDisable = pipeline.shouldDisableFrustumCulling();
		
		// Then - Vanilla uses normal frustum culling
		assertThat(shouldDisable).isFalse();
	}
	
	@Test
	void testShouldDisableOcclusionCullingReturnsFalse() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When
		boolean shouldDisable = pipeline.shouldDisableOcclusionCulling();
		
		// Then - Vanilla uses normal occlusion culling
		assertThat(shouldDisable).isFalse();
	}
	
	@Test
	void testShouldRenderUnderwaterOverlayReturnsTrue() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When
		boolean shouldRender = pipeline.shouldRenderUnderwaterOverlay();
		
		// Then - Vanilla renders underwater overlay
		assertThat(shouldRender).isTrue();
	}
	
	@Test
	void testShouldRenderVignetteReturnsTrue() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When
		boolean shouldRender = pipeline.shouldRenderVignette();
		
		// Then - Vanilla renders vignette
		assertThat(shouldRender).isTrue();
	}
	
	@Test
	void testShouldRenderSunReturnsTrue() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When
		boolean shouldRender = pipeline.shouldRenderSun();
		
		// Then - Vanilla renders sun
		assertThat(shouldRender).isTrue();
	}
	
	@Test
	void testShouldRenderMoonReturnsTrue() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When
		boolean shouldRender = pipeline.shouldRenderMoon();
		
		// Then - Vanilla renders moon
		assertThat(shouldRender).isTrue();
	}
	
	@Test
	void testShouldRenderWeatherReturnsTrue() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When
		boolean shouldRender = pipeline.shouldRenderWeather();
		
		// Then - Vanilla renders weather
		assertThat(shouldRender).isTrue();
	}
	
	@Test
	void testBeginLevelRenderingDoesNotThrow() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When & Then - Should not throw
		assertThatNoException().isThrownBy(() -> pipeline.beginLevelRendering());
	}
	
	@Test
	void testFinalizeLevelRenderingDoesNotThrow() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When & Then - Should not throw
		assertThatNoException().isThrownBy(() -> pipeline.finalizeLevelRendering());
	}
	
	@Test
	void testDestroyDoesNotThrow() {
		// Given
		VanillaRenderingPipeline pipeline = new VanillaRenderingPipeline();
		
		// When & Then - Should not throw
		assertThatNoException().isThrownBy(() -> pipeline.destroy());
	}
}
