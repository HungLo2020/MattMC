package net.irisshaders.iris.shaderpack;

import net.iris.gl.buffer.ShaderStorageBufferHolder;
import net.iris.shaderpack.properties.IndirectPointer;

public record FilledIndirectPointer(int buffer, long offset) {
	public static FilledIndirectPointer basedOff(ShaderStorageBufferHolder holder, IndirectPointer pointer) {
		if (pointer == null || holder == null) return null;

		return new FilledIndirectPointer(holder.getBufferIndex(pointer.buffer()), pointer.offset());
	}
}
