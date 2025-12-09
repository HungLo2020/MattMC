package net.minecraft.client.renderer.shaders.pack;

import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ResourceShaderPackSource.
 * Tests reading files from the ResourceManager.
 */
class ResourceShaderPackSourceTest {
	
	@Mock
	private ResourceManager resourceManager;
	
	private ResourceShaderPackSource source;
	
	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		source = new ResourceShaderPackSource(resourceManager, "test_pack");
	}
	
	@Test
	void testGetName() {
		// When
		String name = source.getName();
		
		// Then
		assertThat(name).isEqualTo("test_pack");
	}
	
	@Test
	void testConstructorSetsBasePath() {
		// When
		ResourceShaderPackSource testSource = new ResourceShaderPackSource(resourceManager, "my_shader");
		
		// Then
		assertThat(testSource.getName()).isEqualTo("my_shader");
	}
	
	@Test
	void testListFilesReturnsEmptyList() throws IOException {
		// When
		var files = source.listFiles("shaders");
		
		// Then - listFiles is not yet implemented, returns empty
		assertThat(files).isEmpty();
	}
	
	@Test
	void testReadFileReturnsEmptyOnMissingResource() throws IOException {
		// Given - Mock resource manager with no resources
		// When
		Optional<String> content = source.readFile("nonexistent.fsh");
		
		// Then
		assertThat(content).isEmpty();
	}
}
