package net.minecraft.client.renderer.shaders.helpers;

import java.util.Objects;

/**
 * A triple (3-tuple) container.
 * 
 * VERBATIM copy from IRIS.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/helpers/Tri.java
 */
public record Tri<X, Y, Z>(X first, Y second, Z third) {

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		//noinspection rawtypes
		if (!(obj instanceof Tri tri)) return false;
		return Objects.equals(tri.first, this.first) && Objects.equals(tri.second, this.second) && Objects.equals(tri.third, this.third);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((first == null) ? 0 : first.hashCode());
		result = prime * result + ((second == null) ? 0 : second.hashCode());
		result = prime * result + ((third == null) ? 0 : third.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "First: " + first.toString() + " Second: " + second.toString() + " Third: " + third.toString();
	}
}
