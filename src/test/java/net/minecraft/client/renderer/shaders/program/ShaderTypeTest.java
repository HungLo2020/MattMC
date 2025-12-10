package net.minecraft.client.renderer.shaders.program;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL43C;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ShaderType enum.
 * Follows Step 11 of NEW-SHADER-PLAN.md.
 */
class ShaderTypeTest {
	
	@Test
	void testVertexShaderType() {
		assertThat(ShaderType.VERTEX.id).isEqualTo(GL20.GL_VERTEX_SHADER);
	}
	
	@Test
	void testFragmentShaderType() {
		assertThat(ShaderType.FRAGMENT.id).isEqualTo(GL20.GL_FRAGMENT_SHADER);
	}
	
	@Test
	void testGeometryShaderType() {
		assertThat(ShaderType.GEOMETRY.id).isEqualTo(GL32C.GL_GEOMETRY_SHADER);
	}
	
	@Test
	void testComputeShaderType() {
		assertThat(ShaderType.COMPUTE.id).isEqualTo(GL43C.GL_COMPUTE_SHADER);
	}
	
	@Test
	void testTesselationControlShaderType() {
		assertThat(ShaderType.TESSELATION_CONTROL.id).isEqualTo(GL43C.GL_TESS_CONTROL_SHADER);
	}
	
	@Test
	void testTesselationEvalShaderType() {
		assertThat(ShaderType.TESSELATION_EVAL.id).isEqualTo(GL43C.GL_TESS_EVALUATION_SHADER);
	}
	
	@Test
	void testAllShaderTypesPresent() {
		// Verify all 6 shader types from IRIS are present
		ShaderType[] types = ShaderType.values();
		assertThat(types).hasSize(6);
		assertThat(types).contains(
			ShaderType.VERTEX,
			ShaderType.FRAGMENT,
			ShaderType.GEOMETRY,
			ShaderType.COMPUTE,
			ShaderType.TESSELATION_CONTROL,
			ShaderType.TESSELATION_EVAL
		);
	}
}
