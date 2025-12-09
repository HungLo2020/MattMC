package net.minecraft.client.renderer.shaders.option;

/**
 * Represents the type/source of a shader option.
 * Copied verbatim from IRIS 1.21.9.
 */
public enum OptionType {
	/**
	 * Options declared with #define directives
	 */
	DEFINE,

	/**
	 * Options declared with const declarations
	 */
	CONST
}
