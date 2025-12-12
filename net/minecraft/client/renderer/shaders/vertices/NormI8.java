package net.minecraft.client.renderer.shaders.vertices;

/**
 * Packs and unpacks normals/tangents in I8 format.
 * 
 * VERBATIM from IRIS: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/vertices/NormI8.java
 */
public class NormI8 {
	public static int pack(float x, float y, float z, float w) {
		int ix = (int) (x * 127.0f) & 0xFF;
		int iy = (int) (y * 127.0f) & 0xFF;
		int iz = (int) (z * 127.0f) & 0xFF;
		int iw = (int) (w * 127.0f) & 0xFF;

		return (ix) | (iy << 8) | (iz << 16) | (iw << 24);
	}

	public static float unpackX(int packed) {
		return ((byte) (packed)) / 127.0f;
	}

	public static float unpackY(int packed) {
		return ((byte) (packed >> 8)) / 127.0f;
	}

	public static float unpackZ(int packed) {
		return ((byte) (packed >> 16)) / 127.0f;
	}

	public static float unpackW(int packed) {
		return ((byte) (packed >> 24)) / 127.0f;
	}
}
