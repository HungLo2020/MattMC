package net.irisshaders.iris.gl.shader;

import net.vulkanic.VulkanicAPI;
import net.vulkanic.VulkanicShaderHandle;

/**
 * Contains a workaround for a crash in nglShaderSource on some AMD drivers. Copied from
 * <a href="https://github.com/grondag/canvas/commit/820bf754092ccaf8d0c169620c2ff575722d7d96">the following Canvas commit.</a>
 */
public class ShaderWorkarounds {
	/**
	 * Identical in function to glShaderSource(int, CharSequence) but
	 * passes a null pointer for string length to force the driver to rely on the null
	 * terminator for string length.  This is a workaround for an apparent flaw with some
	 * AMD drivers that don't receive or interpret the length correctly, resulting in
	 * an access violation when the driver tries to read past the string memory.
	 *
	 * <p>Hat tip to fewizz for the find and the fix.
	 */
	public static void safeShaderSource(int glId, CharSequence source) {
		VulkanicAPI.uploadShaderSource(VulkanicAPI.getCommandContext(), glId, source);
	}

	public static void safeShaderSource(VulkanicShaderHandle shader, CharSequence source) {
		VulkanicAPI.uploadShaderSource(VulkanicAPI.getCommandContext(), shader, source);
	}
}
