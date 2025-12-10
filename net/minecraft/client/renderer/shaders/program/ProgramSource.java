// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.program;

import java.util.Optional;

/**
 * Represents shader source code for a program.
 * 
 * COPIED VERBATIM from IRIS's ProgramSource.java (simplified for Step 12).
 * Reference: frnsrc/Iris-1.21.9/.../shaderpack/programs/ProgramSource.java
 * 
 * Step 12 of NEW-SHADER-PLAN.md
 */
public class ProgramSource {
	private final String name;
	private final String vertexSource;
	private final String geometrySource;
	private final String tessControlSource;
	private final String tessEvalSource;
	private final String fragmentSource;
	// Note: ProgramDirectives will be added in later steps when properties system is more complete

	public ProgramSource(String name, String vertexSource, String geometrySource, String tessControlSource, String tessEvalSource, String fragmentSource) {
		// IRIS ProgramSource.java:20-30
		this.name = name;
		this.vertexSource = vertexSource;
		this.geometrySource = geometrySource;
		this.tessControlSource = tessControlSource;
		this.tessEvalSource = tessEvalSource;
		this.fragmentSource = fragmentSource;
	}

	public String getName() {
		// IRIS ProgramSource.java:49-51
		return name;
	}

	public Optional<String> getVertexSource() {
		// IRIS ProgramSource.java:53-55
		return Optional.ofNullable(vertexSource);
	}

	public Optional<String> getGeometrySource() {
		// IRIS ProgramSource.java:57-59
		return Optional.ofNullable(geometrySource);
	}

	public Optional<String> getTessControlSource() {
		// IRIS ProgramSource.java:61-63
		return Optional.ofNullable(tessControlSource);
	}

	public Optional<String> getTessEvalSource() {
		// IRIS ProgramSource.java:65-67
		return Optional.ofNullable(tessEvalSource);
	}

	public Optional<String> getFragmentSource() {
		// IRIS ProgramSource.java:69-71
		return Optional.ofNullable(fragmentSource);
	}

	public boolean isValid() {
		// IRIS ProgramSource.java:81-83
		return vertexSource != null && fragmentSource != null;
	}

	public Optional<ProgramSource> requireValid() {
		// IRIS ProgramSource.java:85-91
		if (this.isValid()) {
			return Optional.of(this);
		} else {
			return Optional.empty();
		}
	}
}
