package net.minecraft.client.renderer.shaders.program;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ProgramSource class.
 * Follows Step 12 of NEW-SHADER-PLAN.md.
 */
class ProgramSourceTest {
	
	@Test
	void testProgramSourceCreation() {
		// Given
		String name = "gbuffers_terrain";
		String vertex = "#version 330\nvoid main() {}";
		String fragment = "#version 330\nvoid main() {}";
		
		// When
		ProgramSource source = new ProgramSource(name, vertex, null, null, null, fragment);
		
		// Then
		assertThat(source.getName()).isEqualTo(name);
		assertThat(source.getVertexSource()).isPresent().contains(vertex);
		assertThat(source.getFragmentSource()).isPresent().contains(fragment);
		assertThat(source.getGeometrySource()).isEmpty();
	}
	
	@Test
	void testProgramSourceWithGeometry() {
		// Given
		String geometry = "#version 330\nlayout(triangles) in;";
		
		// When
		ProgramSource source = new ProgramSource("test", "vertex", geometry, null, null, "fragment");
		
		// Then
		assertThat(source.getGeometrySource()).isPresent().contains(geometry);
	}
	
	@Test
	void testProgramSourceWithTessellation() {
		// Given
		String tessControl = "#version 330\nlayout(vertices = 3) out;";
		String tessEval = "#version 330\nlayout(triangles) in;";
		
		// When
		ProgramSource source = new ProgramSource("test", "vertex", null, tessControl, tessEval, "fragment");
		
		// Then
		assertThat(source.getTessControlSource()).isPresent().contains(tessControl);
		assertThat(source.getTessEvalSource()).isPresent().contains(tessEval);
	}
	
	@Test
	void testProgramSourceIsValid() {
		// Valid: has vertex and fragment
		ProgramSource valid = new ProgramSource("test", "vertex", null, null, null, "fragment");
		assertThat(valid.isValid()).isTrue();
		
		// Invalid: missing vertex
		ProgramSource noVertex = new ProgramSource("test", null, null, null, null, "fragment");
		assertThat(noVertex.isValid()).isFalse();
		
		// Invalid: missing fragment
		ProgramSource noFragment = new ProgramSource("test", "vertex", null, null, null, null);
		assertThat(noFragment.isValid()).isFalse();
		
		// Invalid: missing both
		ProgramSource neither = new ProgramSource("test", null, null, null, null, null);
		assertThat(neither.isValid()).isFalse();
	}
	
	@Test
	void testProgramSourceRequireValid() {
		// Given
		ProgramSource valid = new ProgramSource("test", "vertex", null, null, null, "fragment");
		ProgramSource invalid = new ProgramSource("test", null, null, null, null, null);
		
		// When/Then
		assertThat(valid.requireValid()).isPresent().contains(valid);
		assertThat(invalid.requireValid()).isEmpty();
	}
	
	@Test
	void testGettersReturnOptionals() {
		// Given
		ProgramSource source = new ProgramSource("test", "vertex", null, null, null, "fragment");
		
		// When/Then - All getters return Optional
		assertThat(source.getVertexSource()).isInstanceOf(Optional.class);
		assertThat(source.getGeometrySource()).isInstanceOf(Optional.class);
		assertThat(source.getTessControlSource()).isInstanceOf(Optional.class);
		assertThat(source.getTessEvalSource()).isInstanceOf(Optional.class);
		assertThat(source.getFragmentSource()).isInstanceOf(Optional.class);
	}
}
