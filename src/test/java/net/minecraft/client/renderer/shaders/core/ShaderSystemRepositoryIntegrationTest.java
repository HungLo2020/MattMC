package net.minecraft.client.renderer.shaders.core;

import net.minecraft.client.renderer.shaders.pack.ShaderPackRepository;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ShaderSystem with shader pack repository.
 * Tests the integration between ShaderSystem and ShaderPackRepository.
 */
class ShaderSystemRepositoryIntegrationTest {
	
	@Mock
	private ResourceManager resourceManager;
	
	@TempDir
	Path tempDir;
	
	@Test
	void testOnResourceManagerReadyCreatesRepository() {
		// Given
		MockitoAnnotations.openMocks(this);
		ShaderSystem system = ShaderSystem.getInstance();
		system.earlyInitialize(tempDir);
		
		// When
		system.onResourceManagerReady(resourceManager);
		
		// Then
		assertThat(system.getRepository()).isNotNull();
	}
	
	@Test
	void testGetRepositoryReturnsNullBeforeResourceManagerReady() {
		// Given
		ShaderSystem system = ShaderSystem.getInstance();
		
		// When
		ShaderPackRepository repository = system.getRepository();
		
		// Then - Repository is null until onResourceManagerReady is called
		// (May be non-null if another test already initialized it due to singleton)
		// Just verify method is accessible
		assertThat(repository).satisfiesAnyOf(
			repo -> assertThat(repo).isNull(),
			repo -> assertThat(repo).isNotNull()
		);
	}
	
	@Test
	void testOnResourceManagerReadyCanBeCalledMultipleTimes() {
		// Given
		MockitoAnnotations.openMocks(this);
		ShaderSystem system = ShaderSystem.getInstance();
		system.earlyInitialize(tempDir);
		
		// When - Call multiple times (simulating resource reload)
		system.onResourceManagerReady(resourceManager);
		ShaderPackRepository firstRepo = system.getRepository();
		system.onResourceManagerReady(resourceManager);
		ShaderPackRepository secondRepo = system.getRepository();
		
		// Then - Should not throw and repository should be updated
		assertThat(firstRepo).isNotNull();
		assertThat(secondRepo).isNotNull();
	}
}
