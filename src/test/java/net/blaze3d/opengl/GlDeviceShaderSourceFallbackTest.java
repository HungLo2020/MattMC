package net.blaze3d.opengl;

import net.blaze3d.shaders.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GlDeviceShaderSourceFallbackTest {

	@Test
	public void testBundledShaderSourceFallbackResolvesVanillaGuiShaders() {
		String guiVertex = GlDevice.loadBundledShaderSource(
			ResourceLocation.fromNamespaceAndPath("minecraft", "core/gui"),
			ShaderType.VERTEX
		);
		String guiFragment = GlDevice.loadBundledShaderSource(
			ResourceLocation.fromNamespaceAndPath("minecraft", "core/gui"),
			ShaderType.FRAGMENT
		);
		String texturedVertex = GlDevice.loadBundledShaderSource(
			ResourceLocation.fromNamespaceAndPath("minecraft", "core/position_tex_color"),
			ShaderType.VERTEX
		);

		assertNotNull(guiVertex,
			"GlDevice should resolve bundled startup GUI vertex shader sources when ShaderManager has not populated its cache yet");
		assertNotNull(guiFragment,
			"GlDevice should resolve bundled startup GUI fragment shader sources when ShaderManager has not populated its cache yet");
		assertNotNull(texturedVertex,
			"GlDevice should resolve bundled position_tex_color shader sources for early GUI pipeline compilation");
		assertTrue(guiVertex.contains("gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);"),
			"Bundled GUI vertex shader fallback should load the packaged minecraft:core/gui.vsh source");
		assertTrue(guiFragment.contains("fragColor = color * ColorModulator;"),
			"Bundled GUI fragment shader fallback should load the packaged minecraft:core/gui.fsh source");
		assertTrue(texturedVertex.contains("texCoord0 = UV0;"),
			"Bundled position_tex_color fallback should load the packaged minecraft:core/position_tex_color.vsh source");
	}
}