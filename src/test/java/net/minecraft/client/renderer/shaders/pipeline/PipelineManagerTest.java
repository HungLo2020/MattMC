package net.minecraft.client.renderer.shaders.pipeline;

import net.minecraft.client.renderer.shaders.core.ShaderSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PipelineManager.
 * Tests pipeline creation, caching, and lifecycle.
 */
class PipelineManagerTest {

@TempDir
Path tempDir;

@BeforeEach
void setUp() {
// Initialize shader system for tests
ShaderSystem.getInstance().earlyInitialize(tempDir);
}

@Test
void testPreparePipelineCreatesVanillaByDefault() {
// Given
PipelineManager manager = new PipelineManager();

// When
WorldRenderingPipeline pipeline = manager.preparePipeline("minecraft:overworld");

// Then
assertThat(pipeline).isInstanceOf(VanillaRenderingPipeline.class);
}

@Test
void testPreparePipelineCachesPipeline() {
// Given
PipelineManager manager = new PipelineManager();

// When
WorldRenderingPipeline pipeline1 = manager.preparePipeline("minecraft:overworld");
WorldRenderingPipeline pipeline2 = manager.preparePipeline("minecraft:overworld");

// Then - Should return same instance (cached)
assertThat(pipeline1).isSameAs(pipeline2);
}

@Test
void testPreparePipelineCreatesDifferentPipelinePerDimension() {
// Given
PipelineManager manager = new PipelineManager();

// When
WorldRenderingPipeline overworldPipeline = manager.preparePipeline("minecraft:overworld");
WorldRenderingPipeline netherPipeline = manager.preparePipeline("minecraft:the_nether");

// Then - Should be different instances
assertThat(overworldPipeline).isNotSameAs(netherPipeline);
}

@Test
void testGetPipelineNullableReturnsCurrentPipeline() {
// Given
PipelineManager manager = new PipelineManager();
manager.preparePipeline("minecraft:overworld");

// When
WorldRenderingPipeline pipeline = manager.getPipelineNullable();

// Then
assertThat(pipeline).isNotNull();
assertThat(pipeline).isInstanceOf(VanillaRenderingPipeline.class);
}

@Test
void testGetPipelineNullableInitiallyReturnsVanilla() {
// Given
PipelineManager manager = new PipelineManager();

// When
WorldRenderingPipeline pipeline = manager.getPipelineNullable();

// Then - Initial pipeline is vanilla
assertThat(pipeline).isInstanceOf(VanillaRenderingPipeline.class);
}

@Test
void testDestroyPipelineClearsAllPipelines() {
// Given
PipelineManager manager = new PipelineManager();
manager.preparePipeline("minecraft:overworld");
manager.preparePipeline("minecraft:the_nether");

// When
manager.destroyPipeline();

// Then - Pipeline should be null
assertThat(manager.getPipelineNullable()).isNull();
}

@Test
void testReloadPipelinesCreatesNewVanillaPipeline() {
// Given
PipelineManager manager = new PipelineManager();
WorldRenderingPipeline oldPipeline = manager.preparePipeline("minecraft:overworld");

// When
manager.reloadPipelines();

// Then - Should have a new vanilla pipeline
WorldRenderingPipeline newPipeline = manager.getPipelineNullable();
assertThat(newPipeline).isNotNull();
assertThat(newPipeline).isInstanceOf(VanillaRenderingPipeline.class);
assertThat(newPipeline).isNotSameAs(oldPipeline);
}
}
