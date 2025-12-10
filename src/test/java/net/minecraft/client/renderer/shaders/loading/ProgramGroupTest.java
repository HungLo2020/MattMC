package net.minecraft.client.renderer.shaders.loading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ProgramGroup enum.
 * Follows Step 15 of NEW-SHADER-PLAN.md.
 */
class ProgramGroupTest {
	
	@Test
	void testProgramGroupExists() {
		// Verify class exists
		assertThatCode(() -> {
			Class.forName("net.minecraft.client.renderer.shaders.loading.ProgramGroup");
		}).doesNotThrowAnyException();
	}
	
	@Test
	void testAllGroupsExist() {
		// Verify all IRIS groups exist (IRIS ProgramGroup.java:3-13)
		assertThat(ProgramGroup.Setup).isNotNull();
		assertThat(ProgramGroup.Begin).isNotNull();
		assertThat(ProgramGroup.Shadow).isNotNull();
		assertThat(ProgramGroup.ShadowComposite).isNotNull();
		assertThat(ProgramGroup.Prepare).isNotNull();
		assertThat(ProgramGroup.Gbuffers).isNotNull();
		assertThat(ProgramGroup.Deferred).isNotNull();
		assertThat(ProgramGroup.Composite).isNotNull();
		assertThat(ProgramGroup.Final).isNotNull();
		assertThat(ProgramGroup.Dh).isNotNull();
	}
	
	@Test
	void testGroupBaseNames() {
		// Verify base names match IRIS exactly
		assertThat(ProgramGroup.Setup.getBaseName()).isEqualTo("setup");
		assertThat(ProgramGroup.Begin.getBaseName()).isEqualTo("begin");
		assertThat(ProgramGroup.Shadow.getBaseName()).isEqualTo("shadow");
		assertThat(ProgramGroup.ShadowComposite.getBaseName()).isEqualTo("shadowcomp");
		assertThat(ProgramGroup.Prepare.getBaseName()).isEqualTo("prepare");
		assertThat(ProgramGroup.Gbuffers.getBaseName()).isEqualTo("gbuffers");
		assertThat(ProgramGroup.Deferred.getBaseName()).isEqualTo("deferred");
		assertThat(ProgramGroup.Composite.getBaseName()).isEqualTo("composite");
		assertThat(ProgramGroup.Final.getBaseName()).isEqualTo("final");
		assertThat(ProgramGroup.Dh.getBaseName()).isEqualTo("dh");
	}
	
	@Test
	void testEnumCount() {
		// Verify we have all 10 groups from IRIS
		assertThat(ProgramGroup.values()).hasSize(10);
	}
}
