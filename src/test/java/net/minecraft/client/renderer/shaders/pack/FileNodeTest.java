package net.minecraft.client.renderer.shaders.pack;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FileNode.
 * Tests #include directive parsing.
 */
class FileNodeTest {
	
	@Test
	void testFileNodeWithNoIncludes() {
		// Given
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/test.glsl");
		List<String> lines = Arrays.asList(
			"#version 330 core",
			"void main() {",
			"    gl_FragColor = vec4(1.0);",
			"}"
		);
		
		// When
		FileNode node = new FileNode(path, lines);
		
		// Then
		assertThat(node.getPath()).isEqualTo(path);
		assertThat(node.getLines()).hasSize(4);
		assertThat(node.getIncludes()).isEmpty();
	}
	
	@Test
	void testFileNodeWithSingleInclude() {
		// Given
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/test.glsl");
		List<String> lines = Arrays.asList(
			"#version 330 core",
			"#include \"lib/common.glsl\"",
			"void main() {}"
		);
		
		// When
		FileNode node = new FileNode(path, lines);
		
		// Then
		Map<Integer, AbsolutePackPath> includes = node.getIncludes();
		assertThat(includes).hasSize(1);
		assertThat(includes.get(1).getPathString()).isEqualTo("/shaders/lib/common.glsl");
	}
	
	@Test
	void testFileNodeWithMultipleIncludes() {
		// Given
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/test.glsl");
		List<String> lines = Arrays.asList(
			"#version 330 core",
			"#include \"lib/common.glsl\"",
			"#include \"lib/lighting.glsl\"",
			"void main() {}"
		);
		
		// When
		FileNode node = new FileNode(path, lines);
		
		// Then
		Map<Integer, AbsolutePackPath> includes = node.getIncludes();
		assertThat(includes).hasSize(2);
		assertThat(includes.get(1).getPathString()).isEqualTo("/shaders/lib/common.glsl");
		assertThat(includes.get(2).getPathString()).isEqualTo("/shaders/lib/lighting.glsl");
	}
	
	@Test
	void testFileNodeWithAbsoluteInclude() {
		// Given
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/programs/test.glsl");
		List<String> lines = Arrays.asList(
			"#version 330 core",
			"#include \"/lib/common.glsl\"",
			"void main() {}"
		);
		
		// When
		FileNode node = new FileNode(path, lines);
		
		// Then
		Map<Integer, AbsolutePackPath> includes = node.getIncludes();
		assertThat(includes).hasSize(1);
		assertThat(includes.get(1).getPathString()).isEqualTo("/lib/common.glsl");
	}
	
	@Test
	void testFileNodeWithIncludeWithoutQuotes() {
		// Given
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/test.glsl");
		List<String> lines = Arrays.asList(
			"#version 330 core",
			"#include lib/common.glsl",
			"void main() {}"
		);
		
		// When
		FileNode node = new FileNode(path, lines);
		
		// Then - Should still parse (IRIS behavior)
		Map<Integer, AbsolutePackPath> includes = node.getIncludes();
		assertThat(includes).hasSize(1);
		assertThat(includes.get(1).getPathString()).isEqualTo("/shaders/lib/common.glsl");
	}
	
	@Test
	void testFileNodeWithWhitespace() {
		// Given
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/test.glsl");
		List<String> lines = Arrays.asList(
			"#version 330 core",
			"  #include   \"lib/common.glsl\"  ",
			"void main() {}"
		);
		
		// When
		FileNode node = new FileNode(path, lines);
		
		// Then - Should handle whitespace
		Map<Integer, AbsolutePackPath> includes = node.getIncludes();
		assertThat(includes).hasSize(1);
		assertThat(includes.get(1).getPathString()).isEqualTo("/shaders/lib/common.glsl");
	}
	
	@Test
	void testFileNodeIgnoresNonIncludeLines() {
		// Given
		AbsolutePackPath path = AbsolutePackPath.fromAbsolutePath("/shaders/test.glsl");
		List<String> lines = Arrays.asList(
			"#version 330 core",
			"#define INCLUDE_TEST 1",  // Not an include
			"// #include \"commented.glsl\"",  // Commented out
			"#include \"lib/common.glsl\"",
			"void main() {}"
		);
		
		// When
		FileNode node = new FileNode(path, lines);
		
		// Then - Only the actual include
		Map<Integer, AbsolutePackPath> includes = node.getIncludes();
		assertThat(includes).hasSize(1);
		assertThat(includes.get(3).getPathString()).isEqualTo("/shaders/lib/common.glsl");
	}
}
