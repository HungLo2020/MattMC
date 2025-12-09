package net.minecraft.client.renderer.shaders.pack;

import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the include system.
 * Tests the full workflow from shader pack source through include processing.
 */
class IncludeSystemIntegrationTest {
	
	@TempDir
	Path tempDir;
	
	private ShaderPackSource packSource;
	
	@BeforeEach
	void setUp() throws IOException {
		// Create a mock pack source with test files
		packSource = new ShaderPackSource() {
			@Override
			public String getName() {
				return "test_shader";
			}
			
			@Override
			public Optional<String> readFile(String relativePath) throws IOException {
				if ("shaders/gbuffers_terrain.fsh".equals(relativePath)) {
					return Optional.of(
						"#version 330 core\n" +
						"#include \"/lib/common.glsl\"\n" +
						"out vec4 fragColor;\n" +
						"void main() {\n" +
						"    vec3 lighting = calculateLighting(vec3(0.0, 1.0, 0.0));\n" +
						"    fragColor = vec4(lighting, 1.0);\n" +
						"}"
					);
				} else if ("lib/common.glsl".equals(relativePath)) {
					return Optional.of(
						"// Common definitions\n" +
						"#define PI 3.14159265359\n" +
						"#define TAU 6.28318530718\n" +
						"vec3 calculateLighting(vec3 normal) {\n" +
						"    return normal * 0.5 + 0.5;\n" +
						"}"
					);
				}
				return Optional.empty();
			}
			
			@Override
			public boolean fileExists(String relativePath) {
				return "shaders/gbuffers_terrain.fsh".equals(relativePath) ||
					   "lib/common.glsl".equals(relativePath);
			}
			
			@Override
			public List<String> listFiles(String directory) {
				return List.of();
			}
		};
	}
	
	@Test
	void testFullIncludeWorkflow() {
		// Given
		AbsolutePackPath shaderPath = AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh");
		IncludeGraph graph = new IncludeGraph(packSource, List.of(shaderPath));
		IncludeProcessor processor = new IncludeProcessor(graph);
		
		// When
		List<String> processedLines = processor.getIncludedFile(shaderPath);
		
		// Then - Include should be expanded
		assertThat(processedLines).isNotNull();
		assertThat(processedLines).hasSizeGreaterThan(7);
		
		// Check that common.glsl content is included
		String joined = String.join("\n", processedLines);
		assertThat(joined).contains("PI 3.14159265359");
		assertThat(joined).contains("calculateLighting");
		assertThat(joined).doesNotContain("#include");  // Include directive should be gone
	}
	
	@Test
	void testIncludeGraphHasNoFailures() {
		// Given
		AbsolutePackPath shaderPath = AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh");
		
		// When
		IncludeGraph graph = new IncludeGraph(packSource, List.of(shaderPath));
		
		// Then
		assertThat(graph.getFailures()).isEmpty();
		assertThat(graph.getNodes()).hasSize(2);  // Main shader + included file
	}
	
	@Test
	void testProcessedShaderContainsBothFiles() {
		// Given
		AbsolutePackPath shaderPath = AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh");
		IncludeGraph graph = new IncludeGraph(packSource, List.of(shaderPath));
		IncludeProcessor processor = new IncludeProcessor(graph);
		
		// When
		List<String> processedLines = processor.getIncludedFile(shaderPath);
		
		// Then - Should contain content from both files
		String joined = String.join("\n", processedLines);
		
		// From common.glsl
		assertThat(joined).contains("PI 3.14159265359");
		assertThat(joined).contains("TAU 6.28318530718");
		
		// From gbuffers_terrain.fsh
		assertThat(joined).contains("#version 330 core");
		assertThat(joined).contains("out vec4 fragColor");
		assertThat(joined).contains("void main()");
	}
	
	@Test
	void testProcessedShaderHasCorrectLineOrder() {
		// Given
		AbsolutePackPath shaderPath = AbsolutePackPath.fromAbsolutePath("/shaders/gbuffers_terrain.fsh");
		IncludeGraph graph = new IncludeGraph(packSource, List.of(shaderPath));
		IncludeProcessor processor = new IncludeProcessor(graph);
		
		// When
		List<String> processedLines = processor.getIncludedFile(shaderPath);
		
		// Then - Include content should come between version and main
		assertThat(processedLines.get(0)).isEqualTo("#version 330 core");
		
		// Find where PI definition is (from included file)
		int piIndex = -1;
		for (int i = 0; i < processedLines.size(); i++) {
			if (processedLines.get(i).contains("PI 3.14159265359")) {
				piIndex = i;
				break;
			}
		}
		assertThat(piIndex).isGreaterThan(0);
		
		// Find where main function is
		int mainIndex = -1;
		for (int i = 0; i < processedLines.size(); i++) {
			if (processedLines.get(i).contains("void main()")) {
				mainIndex = i;
				break;
			}
		}
		assertThat(mainIndex).isGreaterThan(piIndex);
	}
}
