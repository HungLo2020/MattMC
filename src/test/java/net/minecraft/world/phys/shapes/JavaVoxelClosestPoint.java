package net.minecraft.world.phys.shapes;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Literal current public Java query, pinned to Git 8b9173b39. Box extraction stays at its existing production route. */
final class JavaVoxelClosestPoint {
	static Optional<Vec3> closestPointTo(VoxelShape source, Vec3 vec3) {
		if (source.isEmpty()) {
			return Optional.empty();
		} else {
			Vec3[] vec3s = new Vec3[1];
			source.forAllBoxes((d, e, f, g, h, i) -> {
				double j = Mth.clamp(vec3.x(), d, g);
				double k = Mth.clamp(vec3.y(), e, h);
				double l = Mth.clamp(vec3.z(), f, i);
				if (vec3s[0] == null || vec3.distanceToSqr(j, k, l) < vec3.distanceToSqr(vec3s[0])) {
					vec3s[0] = new Vec3(j, k, l);
				}
			});
			return Optional.of(vec3s[0]);
		}
	}

	static Optional<Vec3> vanilla(VoxelShape source, Vec3 vec3) {
		if (source.isEmpty()) {
			return Optional.empty();
		} else {
			Vec3[] vec3s = new Vec3[1];
			JavaVoxelBoxList.forAllBoxes(source, (d, e, f, g, h, i) -> {
				double j = Mth.clamp(vec3.x(), d, g);
				double k = Mth.clamp(vec3.y(), e, h);
				double l = Mth.clamp(vec3.z(), f, i);
				if (vec3s[0] == null || vec3.distanceToSqr(j, k, l) < vec3.distanceToSqr(vec3s[0])) {
					vec3s[0] = new Vec3(j, k, l);
				}
			});
			return Optional.of(vec3s[0]);
		}
	}

}
