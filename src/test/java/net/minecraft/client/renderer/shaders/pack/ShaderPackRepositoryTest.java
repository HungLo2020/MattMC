package net.minecraft.client.renderer.shaders.pack;

import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ShaderPackRepository.
 * Tests shader pack discovery and management.
 */
class ShaderPackRepositoryTest {
	
	@Mock
	private ResourceManager resourceManager;
	
	private ShaderPackRepository repository;
	
	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		repository = new ShaderPackRepository(resourceManager);
	}
	
	@Test
	void testInitialStateHasNoPacks() {
		// When
		List<String> packs = repository.getAvailablePacks();
		
		// Then
		assertThat(packs).isEmpty();
		assertThat(repository.hasShaderPacks()).isFalse();
	}
	
	@Test
	void testScanForPacksWithEmptyResourceManager() {
		// When
		repository.scanForPacks();
		
		// Then
		assertThat(repository.getAvailablePacks()).isEmpty();
		assertThat(repository.hasShaderPacks()).isFalse();
	}
	
	@Test
	void testGetPackSourceReturnsNullForNonexistentPack() {
		// When
		ShaderPackSource source = repository.getPackSource("nonexistent");
		
		// Then
		assertThat(source).isNull();
	}
	
	@Test
	void testGetAvailablePacksReturnsCopy() {
		// When
		List<String> packs1 = repository.getAvailablePacks();
		List<String> packs2 = repository.getAvailablePacks();
		
		// Then - Should return different list instances (defensive copy)
		assertThat(packs1).isNotSameAs(packs2);
	}
}
