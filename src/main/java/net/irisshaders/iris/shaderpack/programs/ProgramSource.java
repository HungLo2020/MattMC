package net.irisshaders.iris.shaderpack.programs;

import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;

import java.util.Optional;

public class ProgramSource {
	private final String name;
	private final String vertexSource;
	private final String geometrySource;
	private final String tessControlSource;
	private final String tessEvalSource;
	private final String fragmentSource;
	private final ProgramDirectives directives;
	private final ProgramSet parent;

	private ProgramSource(String name, String vertexSource, String geometrySource, String tessControlSource, String tessEvalSource, String fragmentSource,
						  ProgramDirectives directives, ProgramSet parent) {
		this.name = name;
		this.vertexSource = vertexSource;
		this.geometrySource = geometrySource;
		this.tessControlSource = tessControlSource;
		this.tessEvalSource = tessEvalSource;
		this.fragmentSource = fragmentSource;
		this.directives = directives;
		this.parent = parent;
	}

	public ProgramSource(String name, String vertexSource, String geometrySource, String tessControlSource, String tessEvalSource, String fragmentSource,
						 ProgramSet parent, ShaderProperties properties, BlendModeOverride defaultBlendModeOverride) {
		this.name = name;
		this.vertexSource = vertexSource;
		this.geometrySource = geometrySource;
		this.tessControlSource = tessControlSource;
		this.tessEvalSource = tessEvalSource;
		this.fragmentSource = fragmentSource;
		this.parent = parent;
		this.directives = new ProgramDirectives(this, properties,
			PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS, defaultBlendModeOverride);
	}

	public String getName() {
		return name;
	}

	public Optional<String> getFragmentSource() {
		return Optional.ofNullable(fragmentSource);
	}

	public ProgramSet getParent() {
		return parent;
	}

	public boolean isValid() {
		return vertexSource != null && fragmentSource != null;
	}

	public Optional<ProgramSource> requireValid() {
		if (this.isValid()) {
			return Optional.of(this);
		} else {
			return Optional.empty();
		}
	}
}
