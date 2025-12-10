// This file is copied VERBATIM from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.loading;

/**
 * Categorizes shader programs into logical groups.
 * 
 * COPIED VERBATIM from IRIS's ProgramGroup.java.
 * Reference: frnsrc/Iris-1.21.9/.../shaderpack/loading/ProgramGroup.java
 * 
 * Step 15 of NEW-SHADER-PLAN.md
 */
public enum ProgramGroup {
	Setup("setup"),
	Begin("begin"),
	Shadow("shadow"),
	ShadowComposite("shadowcomp"),
	Prepare("prepare"),
	Gbuffers("gbuffers"),
	Deferred("deferred"),
	Composite("composite"),
	Final("final"),
	Dh("dh");

	private final String baseName;

	ProgramGroup(String baseName) {
		this.baseName = baseName;
	}

	public String getBaseName() {
		return baseName;
	}
}
