package net.minecraft.client.renderer.shaders.loading;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ProgramId enum.
 * Follows Step 15 of NEW-SHADER-PLAN.md.
 */
class ProgramIdTest {
	
	@Test
	void testProgramIdExists() {
		// Verify class exists
		assertThatCode(() -> {
			Class.forName("net.minecraft.client.renderer.shaders.loading.ProgramId");
		}).doesNotThrowAnyException();
	}
	
	@Test
	void testShadowPrograms() {
		// Shadow programs (IRIS ProgramId.java:12-18)
		assertThat(ProgramId.Shadow).isNotNull();
		assertThat(ProgramId.ShadowSolid).isNotNull();
		assertThat(ProgramId.ShadowCutout).isNotNull();
		assertThat(ProgramId.ShadowWater).isNotNull();
		assertThat(ProgramId.ShadowEntities).isNotNull();
		assertThat(ProgramId.ShadowLightning).isNotNull();
		assertThat(ProgramId.ShadowBlock).isNotNull();
	}
	
	@Test
	void testGbuffersBasicPrograms() {
		// Basic tier (IRIS ProgramId.java:20-21)
		assertThat(ProgramId.Basic).isNotNull();
		assertThat(ProgramId.Line).isNotNull();
	}
	
	@Test
	void testGbuffersTexturedPrograms() {
		// Textured tier (IRIS ProgramId.java:23-27)
		assertThat(ProgramId.Textured).isNotNull();
		assertThat(ProgramId.TexturedLit).isNotNull();
		assertThat(ProgramId.SkyBasic).isNotNull();
		assertThat(ProgramId.SkyTextured).isNotNull();
		assertThat(ProgramId.Clouds).isNotNull();
	}
	
	@Test
	void testGbuffersTerrainPrograms() {
		// Terrain tier (IRIS ProgramId.java:29-32)
		assertThat(ProgramId.Terrain).isNotNull();
		assertThat(ProgramId.TerrainSolid).isNotNull();
		assertThat(ProgramId.TerrainCutout).isNotNull();
		assertThat(ProgramId.DamagedBlock).isNotNull();
	}
	
	@Test
	void testGbuffersEntityPrograms() {
		// Entity tier (IRIS ProgramId.java:39-47)
		assertThat(ProgramId.Entities).isNotNull();
		assertThat(ProgramId.EntitiesTrans).isNotNull();
		assertThat(ProgramId.Lightning).isNotNull();
		assertThat(ProgramId.Particles).isNotNull();
		assertThat(ProgramId.ParticlesTrans).isNotNull();
		assertThat(ProgramId.EntitiesGlowing).isNotNull();
		assertThat(ProgramId.ArmorGlint).isNotNull();
		assertThat(ProgramId.SpiderEyes).isNotNull();
	}
	
	@Test
	void testSourceNames() {
		// Verify source name generation
		assertThat(ProgramId.Shadow.getSourceName()).isEqualTo("shadow");
		assertThat(ProgramId.ShadowSolid.getSourceName()).isEqualTo("shadow_solid");
		assertThat(ProgramId.Basic.getSourceName()).isEqualTo("gbuffers_basic");
		assertThat(ProgramId.Terrain.getSourceName()).isEqualTo("gbuffers_terrain");
		assertThat(ProgramId.Final.getSourceName()).isEqualTo("final");
	}
	
	@Test
	void testFallbackChain() {
		// Test fallback chain (IRIS pattern)
		// TerrainCutout -> Terrain -> TexturedLit -> Textured -> Basic
		assertThat(ProgramId.TerrainCutout.getFallback()).isEqualTo(Optional.of(ProgramId.Terrain));
		assertThat(ProgramId.Terrain.getFallback()).isEqualTo(Optional.of(ProgramId.TexturedLit));
		assertThat(ProgramId.TexturedLit.getFallback()).isEqualTo(Optional.of(ProgramId.Textured));
		assertThat(ProgramId.Textured.getFallback()).isEqualTo(Optional.of(ProgramId.Basic));
		assertThat(ProgramId.Basic.getFallback()).isEqualTo(Optional.empty());
	}
	
	@Test
	void testNoFallback() {
		// Programs with no fallback
		assertThat(ProgramId.Shadow.getFallback()).isEqualTo(Optional.empty());
		assertThat(ProgramId.Basic.getFallback()).isEqualTo(Optional.empty());
		assertThat(ProgramId.Final.getFallback()).isEqualTo(Optional.empty());
	}
	
	@Test
	void testProgramGroups() {
		// Verify programs belong to correct groups
		assertThat(ProgramId.Shadow.getGroup()).isEqualTo(ProgramGroup.Shadow);
		assertThat(ProgramId.Basic.getGroup()).isEqualTo(ProgramGroup.Gbuffers);
		assertThat(ProgramId.Terrain.getGroup()).isEqualTo(ProgramGroup.Gbuffers);
		assertThat(ProgramId.DhTerrain.getGroup()).isEqualTo(ProgramGroup.Dh);
		assertThat(ProgramId.Final.getGroup()).isEqualTo(ProgramGroup.Final);
	}
	
	@Test
	void testEnumCount() {
		// Verify we have 39 programs from IRIS subset
		assertThat(ProgramId.values()).hasSize(39);
	}
}
