// This file is copied VERBATIM from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.loading;

/**
 * Identifies arrays of shader programs (composite, deferred, etc.).
 * 
 * COPIED VERBATIM from IRIS's ProgramArrayId.java.
 * Reference: frnsrc/Iris-1.21.9/.../shaderpack/loading/ProgramArrayId.java
 * 
 * Step 15 of NEW-SHADER-PLAN.md
 */
public enum ProgramArrayId {
	Setup(ProgramGroup.Setup, 100),
	Begin(ProgramGroup.Begin, 100),
	ShadowComposite(ProgramGroup.ShadowComposite, 100),
	Prepare(ProgramGroup.Prepare, 100),
	Deferred(ProgramGroup.Deferred, 100),
	Composite(ProgramGroup.Composite, 100),
	;

	private final ProgramGroup group;
	private final int numPrograms;

	ProgramArrayId(ProgramGroup group, int numPrograms) {
		this.group = group;
		this.numPrograms = numPrograms;
	}

	public ProgramGroup getGroup() {
		return group;
	}

	public String getSourcePrefix() {
		return group.getBaseName();
	}

	public int getNumPrograms() {
		return numPrograms;
	}
}
