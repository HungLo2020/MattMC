package com.mojang.blaze3d.vertex;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public interface VertexSorting {
	VertexSorting DISTANCE_TO_ORIGIN = byDistance(0.0F, 0.0F, 0.0F);
	// Sodium: Use optimized orthographic Z sorting (from VertexSortingMixin)
	VertexSorting ORTHOGRAPHIC_Z = net.caffeinemc.mods.sodium.client.util.sorting.VertexSorters.orthographicZ();

	static VertexSorting byDistance(float f, float g, float h) {
		// Sodium: Use optimized distance sorting (from VertexSortingMixin)
		return net.caffeinemc.mods.sodium.client.util.sorting.VertexSorters.distance(f, g, h);
	}

	static VertexSorting byDistance(Vector3fc vector3fc) {
		return byDistance(vector3fc::distanceSquared);
	}

	static VertexSorting byDistance(VertexSorting.DistanceFunction distanceFunction) {
		// Sodium: Use optimized fallback sorting (from VertexSortingMixin)
		return net.caffeinemc.mods.sodium.client.util.sorting.VertexSorters.fallback(distanceFunction);
	}

	int[] sort(CompactVectorArray compactVectorArray);

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface DistanceFunction {
		float apply(Vector3f vector3f);
	}
}
