package com.mojang.blaze3d.resource;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public interface GraphicsResourceAllocator {
	GraphicsResourceAllocator UNPOOLED = new GraphicsResourceAllocator() {
		@Override
		public <T> T acquire(ResourceDescriptor<T> resourceDescriptor) {
			T object = resourceDescriptor.allocate();
			resourceDescriptor.prepare(object);
			return object;
		}

		@Override
		public <T> void release(ResourceDescriptor<T> resourceDescriptor, T object) {
			resourceDescriptor.free(object);
		}
	};

	<T> T acquire(ResourceDescriptor<T> resourceDescriptor);

	<T> void release(ResourceDescriptor<T> resourceDescriptor, T object);
}
