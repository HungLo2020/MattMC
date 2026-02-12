package net.sodium.client.gl.shader;

import java.nio.ByteBuffer;

import net.vulkanic.VulkanicAPI;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Contains a workaround for a crash in nglShaderSource on some AMD drivers. Copied from the following Canvas commit:
 * https://github.com/grondag/canvas/commit/820bf754092ccaf8d0c169620c2ff575722d7d96
 */
class ShaderWorkarounds {
	/**
	 * Identical in function to glShaderSource but
	 * passes a null pointer for string length to force the driver to rely on the null
	 * terminator for string length.  This is a workaround for an apparent flaw with some
	 * AMD drivers that don't receive or interpret the length correctly, resulting in
	 * an access violation when the driver tries to read past the string memory.
	 *
	 * <p>Hat tip to fewizz for the find and the fix.
	 */
	static void safeShaderSource(int glId, CharSequence source) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			final ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, true);
			final PointerBuffer pointers = stack.mallocPointer(1);
			pointers.put(sourceBuffer);

			VulkanicAPI.uploadShaderSource(glId, pointers.address0(), 1, 0);
			org.lwjgl.system.APIUtil.apiArrayFree(pointers.address0(), 1);
		}
	}
}
