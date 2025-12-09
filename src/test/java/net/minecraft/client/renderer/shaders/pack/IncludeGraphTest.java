package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for IncludeGraph.
 * Tests graph construction and cycle detection.
 */
class IncludeGraphTest {
	
	@Test
	void testIncludeGraphWithSingleFile() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"test.glsl", "#version 330 core\nvoid main() {}"
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/test.glsl");
		
		// When
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		
		// Then
		assertThat(graph.getNodes()).hasSize(1);
		assertThat(graph.getNodes()).containsKey(start);
		assertThat(graph.getFailures()).isEmpty();
	}
	
	@Test
	void testIncludeGraphWithInclude() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"test.glsl", "#version 330 core\n#include \"lib/common.glsl\"\nvoid main() {}",
			"lib/common.glsl", "#define PI 3.14159"
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/test.glsl");
		
		// When
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		
		// Then
		assertThat(graph.getNodes()).hasSize(2);
		assertThat(graph.getFailures()).isEmpty();
	}
	
	@Test
	void testIncludeGraphWithMissingFile() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"test.glsl", "#version 330 core\n#include \"missing.glsl\"\nvoid main() {}"
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/test.glsl");
		
		// When
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		
		// Then
		assertThat(graph.getNodes()).hasSize(1);  // Only the starting file
		assertThat(graph.getFailures()).hasSize(1);
		assertThat(graph.getFailures()).containsKey(AbsolutePackPath.fromAbsolutePath("/missing.glsl"));
	}
	
	@Test
	void testIncludeGraphDetectsSelfInclude() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"test.glsl", "#version 330 core\n#include \"test.glsl\"\nvoid main() {}"
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/test.glsl");
		
		// When
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		
		// Then - Self-include is detected as failure
		assertThat(graph.getNodes()).isEmpty();
		assertThat(graph.getFailures()).hasSize(1);
		assertThat(graph.getFailures()).containsKey(start);
	}
	
	@Test
	void testIncludeGraphDetectsCycle() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"a.glsl", "#include \"b.glsl\"",
			"b.glsl", "#include \"c.glsl\"",
			"c.glsl", "#include \"a.glsl\""
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/a.glsl");
		
		// When & Then - Should throw on cycle detection
		assertThatThrownBy(() -> new IncludeGraph(mockSource, List.of(start)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cycle detected");
	}
	
	@Test
	void testIncludeGraphWithNestedIncludes() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"main.glsl", "#include \"a.glsl\"",
			"a.glsl", "#include \"b.glsl\"",
			"b.glsl", "#define TEST 1"
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/main.glsl");
		
		// When
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		
		// Then - All three files should be loaded
		assertThat(graph.getNodes()).hasSize(3);
		assertThat(graph.getFailures()).isEmpty();
	}
	
	@Test
	void testIncludeGraphWithSharedInclude() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"a.glsl", "#include \"common.glsl\"",
			"b.glsl", "#include \"common.glsl\"",
			"common.glsl", "#define SHARED 1"
		));
		List<AbsolutePackPath> starts = List.of(
			AbsolutePackPath.fromAbsolutePath("/a.glsl"),
			AbsolutePackPath.fromAbsolutePath("/b.glsl")
		);
		
		// When
		IncludeGraph graph = new IncludeGraph(mockSource, starts);
		
		// Then - Common file loaded once, but included by both
		assertThat(graph.getNodes()).hasSize(3);
		assertThat(graph.getFailures()).isEmpty();
	}
	
	private ShaderPackSource createMockSource(Map<String, String> files) {
		return new ShaderPackSource() {
			@Override
			public String getName() {
				return "test";
			}
			
			@Override
			public java.util.Optional<String> readFile(String relativePath) {
				return java.util.Optional.ofNullable(files.get(relativePath));
			}
			
			@Override
			public boolean fileExists(String relativePath) {
				return files.containsKey(relativePath);
			}
			
			@Override
			public List<String> listFiles(String directory) {
				return List.of();
			}
		};
	}
}
