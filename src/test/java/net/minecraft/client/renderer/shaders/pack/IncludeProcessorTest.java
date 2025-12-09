package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for IncludeProcessor.
 * Tests include expansion and caching.
 */
class IncludeProcessorTest {
	
	@Test
	void testProcessFileWithNoIncludes() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"test.glsl", "#version 330 core\nvoid main() {}"
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/test.glsl");
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		IncludeProcessor processor = new IncludeProcessor(graph);
		
		// When
		List<String> lines = processor.getIncludedFile(start);
		
		// Then
		assertThat(lines).hasSize(2);
		assertThat(lines).contains("#version 330 core");
		assertThat(lines).contains("void main() {}");
	}
	
	@Test
	void testProcessFileWithInclude() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"test.glsl", "#version 330 core\n#include \"common.glsl\"\nvoid main() {}",
			"common.glsl", "#define PI 3.14159"
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/test.glsl");
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		IncludeProcessor processor = new IncludeProcessor(graph);
		
		// When
		List<String> lines = processor.getIncludedFile(start);
		
		// Then - Include should be expanded
		assertThat(lines).hasSize(3);
		assertThat(lines.get(0)).isEqualTo("#version 330 core");
		assertThat(lines.get(1)).isEqualTo("#define PI 3.14159");
		assertThat(lines.get(2)).isEqualTo("void main() {}");
	}
	
	@Test
	void testProcessFileWithNestedIncludes() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"main.glsl", "#include \"a.glsl\"",
			"a.glsl", "#include \"b.glsl\"",
			"b.glsl", "#define TEST 1"
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/main.glsl");
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		IncludeProcessor processor = new IncludeProcessor(graph);
		
		// When
		List<String> lines = processor.getIncludedFile(start);
		
		// Then - Both includes should be expanded
		assertThat(lines).hasSize(1);
		assertThat(lines.get(0)).isEqualTo("#define TEST 1");
	}
	
	@Test
	void testProcessFileCachesResults() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"test.glsl", "#version 330 core\nvoid main() {}"
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/test.glsl");
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		IncludeProcessor processor = new IncludeProcessor(graph);
		
		// When
		List<String> lines1 = processor.getIncludedFile(start);
		List<String> lines2 = processor.getIncludedFile(start);
		
		// Then - Should return same instance (cached)
		assertThat(lines1).isSameAs(lines2);
	}
	
	@Test
	void testProcessFileWithSharedInclude() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"a.glsl", "#include \"common.glsl\"\n#define A 1",
			"b.glsl", "#include \"common.glsl\"\n#define B 1",
			"common.glsl", "#define SHARED 1"
		));
		List<AbsolutePackPath> starts = List.of(
			AbsolutePackPath.fromAbsolutePath("/a.glsl"),
			AbsolutePackPath.fromAbsolutePath("/b.glsl")
		);
		IncludeGraph graph = new IncludeGraph(mockSource, starts);
		IncludeProcessor processor = new IncludeProcessor(graph);
		
		// When
		List<String> linesA = processor.getIncludedFile(AbsolutePackPath.fromAbsolutePath("/a.glsl"));
		List<String> linesB = processor.getIncludedFile(AbsolutePackPath.fromAbsolutePath("/b.glsl"));
		
		// Then - Both should have common included
		assertThat(linesA).hasSize(2);
		assertThat(linesA.get(0)).isEqualTo("#define SHARED 1");
		assertThat(linesA.get(1)).isEqualTo("#define A 1");
		
		assertThat(linesB).hasSize(2);
		assertThat(linesB.get(0)).isEqualTo("#define SHARED 1");
		assertThat(linesB.get(1)).isEqualTo("#define B 1");
	}
	
	@Test
	void testProcessFileWithMissingInclude() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"test.glsl", "#include \"missing.glsl\""
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/test.glsl");
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		IncludeProcessor processor = new IncludeProcessor(graph);
		
		// When & Then - Should throw NullPointerException (matches IRIS behavior)
		// IRIS's IncludeProcessor has "Objects.requireNonNull" which throws on missing includes
		assertThatThrownBy(() -> processor.getIncludedFile(start))
			.isInstanceOf(NullPointerException.class);
	}
	
	@Test
	void testProcessNonexistentFile() {
		// Given
		ShaderPackSource mockSource = createMockSource(Map.of(
			"test.glsl", "#version 330 core"
		));
		AbsolutePackPath start = AbsolutePackPath.fromAbsolutePath("/test.glsl");
		IncludeGraph graph = new IncludeGraph(mockSource, List.of(start));
		IncludeProcessor processor = new IncludeProcessor(graph);
		
		// When
		List<String> lines = processor.getIncludedFile(AbsolutePackPath.fromAbsolutePath("/nonexistent.glsl"));
		
		// Then
		assertThat(lines).isNull();
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
