package net.minecraft.client.renderer.shaders.loading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ProgramArrayId enum.
 * Follows Step 15 of NEW-SHADER-PLAN.md.
 */
class ProgramArrayIdTest {
	
	@Test
	void testProgramArrayIdExists() {
		// Verify class exists
		assertThatCode(() -> {
			Class.forName("net.minecraft.client.renderer.shaders.loading.ProgramArrayId");
		}).doesNotThrowAnyException();
	}
	
	@Test
	void testAllArrayIdsExist() {
		// Verify all IRIS array IDs exist (IRIS ProgramArrayId.java:3-10)
		assertThat(ProgramArrayId.Setup).isNotNull();
		assertThat(ProgramArrayId.Begin).isNotNull();
		assertThat(ProgramArrayId.ShadowComposite).isNotNull();
		assertThat(ProgramArrayId.Prepare).isNotNull();
		assertThat(ProgramArrayId.Deferred).isNotNull();
		assertThat(ProgramArrayId.Composite).isNotNull();
	}
	
	@Test
	void testArrayGroups() {
		// Verify groups match IRIS
		assertThat(ProgramArrayId.Setup.getGroup()).isEqualTo(ProgramGroup.Setup);
		assertThat(ProgramArrayId.Begin.getGroup()).isEqualTo(ProgramGroup.Begin);
		assertThat(ProgramArrayId.ShadowComposite.getGroup()).isEqualTo(ProgramGroup.ShadowComposite);
		assertThat(ProgramArrayId.Prepare.getGroup()).isEqualTo(ProgramGroup.Prepare);
		assertThat(ProgramArrayId.Deferred.getGroup()).isEqualTo(ProgramGroup.Deferred);
		assertThat(ProgramArrayId.Composite.getGroup()).isEqualTo(ProgramGroup.Composite);
	}
	
	@Test
	void testSourcePrefixes() {
		// Verify source prefixes match group base names
		assertThat(ProgramArrayId.Setup.getSourcePrefix()).isEqualTo("setup");
		assertThat(ProgramArrayId.Begin.getSourcePrefix()).isEqualTo("begin");
		assertThat(ProgramArrayId.ShadowComposite.getSourcePrefix()).isEqualTo("shadowcomp");
		assertThat(ProgramArrayId.Prepare.getSourcePrefix()).isEqualTo("prepare");
		assertThat(ProgramArrayId.Deferred.getSourcePrefix()).isEqualTo("deferred");
		assertThat(ProgramArrayId.Composite.getSourcePrefix()).isEqualTo("composite");
	}
	
	@Test
	void testNumPrograms() {
		// All arrays support 100 programs (IRIS ProgramArrayId.java:4-9)
		assertThat(ProgramArrayId.Setup.getNumPrograms()).isEqualTo(100);
		assertThat(ProgramArrayId.Begin.getNumPrograms()).isEqualTo(100);
		assertThat(ProgramArrayId.ShadowComposite.getNumPrograms()).isEqualTo(100);
		assertThat(ProgramArrayId.Prepare.getNumPrograms()).isEqualTo(100);
		assertThat(ProgramArrayId.Deferred.getNumPrograms()).isEqualTo(100);
		assertThat(ProgramArrayId.Composite.getNumPrograms()).isEqualTo(100);
	}
	
	@Test
	void testEnumCount() {
		// Verify we have all 6 array IDs from IRIS
		assertThat(ProgramArrayId.values()).hasSize(6);
	}
}
