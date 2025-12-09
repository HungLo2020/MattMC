package net.minecraft.client.renderer.shaders.option;

import net.minecraft.client.renderer.shaders.pack.AbsolutePackPath;

/**
 * Tracks the location of a shader option in source files.
 * Copied verbatim from IRIS 1.21.9.
 */
public class OptionLocation {
	private final AbsolutePackPath source;
	private final int line;

	public OptionLocation(AbsolutePackPath source, int line) {
		this.source = source;
		this.line = line;
	}

	public AbsolutePackPath getSource() {
		return source;
	}

	public int getLine() {
		return line;
	}

	@Override
	public String toString() {
		return source.getPathString() + ":" + line;
	}
}
