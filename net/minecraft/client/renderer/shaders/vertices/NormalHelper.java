package net.minecraft.client.renderer.shaders.vertices;

import net.minecraft.util.Mth;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Helpers for computing face normals and tangent vectors.
 * 
 * VERBATIM from IRIS: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/vertices/NormalHelper.java
 */
public abstract class NormalHelper {
	private NormalHelper() {
	}

	public static int invertPackedNormal(int packed) {
		int ix = -(packed & 255);
		int iy = -((packed >> 8) & 255);
		int iz = -((packed >> 16) & 255);

		ix &= 255;
		iy &= 255;
		iz &= 255;

		return (packed & 0xFF000000) | (iz << 16) | (iy << 8) | ix;
	}

	public static void octahedronEncode(Vector2f output, float x, float y, float z) {
		float nX = x, nY = y, nZ = z;

		float invL1 = 1.0f / (Math.abs(nX) + Math.abs(nY) + Math.abs(nZ));
		nX *= invL1;
		nY *= invL1;
		nZ *= invL1;

		float oX, oY;
		if (nZ >= 0.0f) {
			oX = nX;
			oY = nY;
		} else {
			float absNX = Math.abs(nX);
			float absNY = Math.abs(nY);
			oX = (1.0f - absNY) * (nX >= 0.0f ? 1.0f : -1.0f);
			oY = (1.0f - absNX) * (nY >= 0.0f ? 1.0f : -1.0f);
		}

		output.set(oX, oY);
	}

	public static void tangentEncode(Vector2f output, Vector4f tangent) {
		octahedronEncode(output, tangent.x, tangent.y, tangent.z);
		float y_sign = output.y >= 0.0f ? 64.0f / 127.0f : -64.0f / 127.0f;
		output.y *= 63.0f / 127.0f;
		output.y = tangent.w >= 0.0f ? output.y : output.y + y_sign;
	}

	/**
	 * Computes the face normal of the given quad and saves it in the provided non-null vector.
	 *
	 * <p>Assumes counter-clockwise winding order, which is the norm.
	 * Expects convex quads with all points co-planar.
	 */
	public static void computeFaceNormal(Vector3f saveTo, QuadView q) {
		final float x0 = q.x(0);
		final float y0 = q.y(0);
		final float z0 = q.z(0);
		final float x1 = q.x(1);
		final float y1 = q.y(1);
		final float z1 = q.z(1);
		final float x2 = q.x(2);
		final float y2 = q.y(2);
		final float z2 = q.z(2);
		final float x3 = q.x(3);
		final float y3 = q.y(3);
		final float z3 = q.z(3);

		computeFaceNormalManual(saveTo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
	}

	/**
	 * Computes the face normal of the given quad and saves it in the provided non-null vector.
	 *
	 * <p>Assumes counter-clockwise winding order, which is the norm.
	 * Expects convex quads with all points co-planar.
	 */
	public static void computeFaceNormalManual(Vector3f saveTo,
											   float x0, float y0, float z0,
											   float x1, float y1, float z1,
											   float x2, float y2, float z2,
											   float x3, float y3, float z3) {
		final float dx0 = x2 - x0;
		final float dy0 = y2 - y0;
		final float dz0 = z2 - z0;
		final float dx1 = x3 - x1;
		final float dy1 = y3 - y1;
		final float dz1 = z3 - z1;

		float normX = dy0 * dz1 - dz0 * dy1;
		float normY = dz0 * dx1 - dx0 * dz1;
		float normZ = dx0 * dy1 - dy0 * dx1;

		saveTo.set(normX, normY, normZ);

		saveTo.normalize();
	}

	/**
	 * Computes the face normal of the given tri and saves it in the provided non-null vector.
	 *
	 * <p>Assumes counter-clockwise winding order, which is the norm.
	 */
	public static void computeFaceNormalTri(Vector3f saveTo, TriView t) {
		final float x0 = t.x(0);
		final float y0 = t.y(0);
		final float z0 = t.z(0);
		final float x1 = t.x(1);
		final float y1 = t.y(1);
		final float z1 = t.z(1);
		final float x2 = t.x(2);
		final float y2 = t.y(2);
		final float z2 = t.z(2);

		computeFaceNormalManual(saveTo, x0, y0, z0, x1, y1, z1, x2, y2, z2, x0, y0, z0);
	}

	public static int computeTangentSmooth(float normalX, float normalY, float normalZ, TriView t) {
		// Capture all of the relevant vertex positions
		float x0 = t.x(0);
		float y0 = t.y(0);
		float z0 = t.z(0);

		float x1 = t.x(1);
		float y1 = t.y(1);
		float z1 = t.z(1);

		float x2 = t.x(2);
		float y2 = t.y(2);
		float z2 = t.z(2);

		// Project all vertices onto normal plane
		float d0 = x0 * normalX + y0 * normalY + z0 * normalZ;
		float d1 = x1 * normalX + y1 * normalY + z1 * normalZ;
		float d2 = x2 * normalX + y2 * normalY + z2 * normalZ;

		x0 -= d0 * normalX;
		y0 -= d0 * normalY;
		z0 -= d0 * normalZ;

		x1 -= d1 * normalX;
		y1 -= d1 * normalY;
		z1 -= d1 * normalZ;

		x2 -= d2 * normalX;
		y2 -= d2 * normalY;
		z2 -= d2 * normalZ;


		float edge1x = x1 - x0;
		float edge1y = y1 - y0;
		float edge1z = z1 - z0;

		float edge2x = x2 - x0;
		float edge2y = y2 - y0;
		float edge2z = z2 - z0;

		float u0 = t.u(0);
		float v0 = t.v(0);

		float u1 = t.u(1);
		float v1 = t.v(1);

		float u2 = t.u(2);
		float v2 = t.v(2);

		float deltaU1 = u1 - u0;
		float deltaV1 = v1 - v0;
		float deltaU2 = u2 - u0;
		float deltaV2 = v2 - v0;

		float fdenom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
		float f;

		if (fdenom == 0.0) {
			f = 1.0f;
		} else {
			f = 1.0f / fdenom;
		}

		float tangentx = f * (deltaV2 * edge1x - deltaV1 * edge2x);
		float tangenty = f * (deltaV2 * edge1y - deltaV1 * edge2y);
		float tangentz = f * (deltaV2 * edge1z - deltaV1 * edge2z);
		float tcoeff = rsqrt(tangentx * tangentx + tangenty * tangenty + tangentz * tangentz);
		tangentx *= tcoeff;
		tangenty *= tcoeff;
		tangentz *= tcoeff;

		float bitangentx = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
		float bitangenty = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
		float bitangentz = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
		float bitcoeff = rsqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz);
		bitangentx *= bitcoeff;
		bitangenty *= bitcoeff;
		bitangentz *= bitcoeff;

		float pbitangentx = tangenty * normalZ - tangentz * normalY;
		float pbitangenty = tangentz * normalX - tangentx * normalZ;
		float pbitangentz = tangentx * normalY - tangenty * normalX;

		float dot = (bitangentx * pbitangentx) + (bitangenty * pbitangenty) + (bitangentz * pbitangentz);
		float tangentW;

		if (dot < 0) {
			tangentW = -1.0F;
		} else {
			tangentW = 1.0F;
		}

		return NormI8.pack(tangentx, tangenty, tangentz, tangentW);
	}

	public static int computeTangent(float normalX, float normalY, float normalZ, QuadView q) {
		// Use first 3 vertices as triangle for tangent calculation
		float x0 = q.x(0);
		float y0 = q.y(0);
		float z0 = q.z(0);

		float x1 = q.x(1);
		float y1 = q.y(1);
		float z1 = q.z(1);

		float x2 = q.x(2);
		float y2 = q.y(2);
		float z2 = q.z(2);

		float u0 = q.u(0);
		float v0 = q.v(0);

		float u1 = q.u(1);
		float v1 = q.v(1);

		float u2 = q.u(2);
		float v2 = q.v(2);

		return computeTangent(normalX, normalY, normalZ, x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2);
	}

	public static int computeTangent(float normalX, float normalY, float normalZ,
									 float x0, float y0, float z0, float u0, float v0,
									 float x1, float y1, float z1, float u1, float v1,
									 float x2, float y2, float z2, float u2, float v2) {
		float edge1x = x1 - x0;
		float edge1y = y1 - y0;
		float edge1z = z1 - z0;

		float edge2x = x2 - x0;
		float edge2y = y2 - y0;
		float edge2z = z2 - z0;

		float deltaU1 = u1 - u0;
		float deltaV1 = v1 - v0;
		float deltaU2 = u2 - u0;
		float deltaV2 = v2 - v0;

		float fdenom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
		float f;

		if (fdenom == 0.0) {
			f = 1.0f;
		} else {
			f = 1.0f / fdenom;
		}

		float tangentx = f * (deltaV2 * edge1x - deltaV1 * edge2x);
		float tangenty = f * (deltaV2 * edge1y - deltaV1 * edge2y);
		float tangentz = f * (deltaV2 * edge1z - deltaV1 * edge2z);
		float tcoeff = rsqrt(tangentx * tangentx + tangenty * tangenty + tangentz * tangentz);
		tangentx *= tcoeff;
		tangenty *= tcoeff;
		tangentz *= tcoeff;

		if (tangentx == 0.0f && tangenty == 0.0f && tangentz == 0.0f) {
			return NormI8.pack(1.0f, 0.0f, 0.0f, 1.0f);  // Default tangent
		}

		float bitangentx = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
		float bitangenty = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
		float bitangentz = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
		float bitcoeff = rsqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz);
		bitangentx *= bitcoeff;
		bitangenty *= bitcoeff;
		bitangentz *= bitcoeff;

		float pbitangentx = tangenty * normalZ - tangentz * normalY;
		float pbitangenty = tangentz * normalX - tangentx * normalZ;
		float pbitangentz = tangentx * normalY - tangenty * normalX;

		float dot = (bitangentx * pbitangentx) + (bitangenty * pbitangenty) + (bitangentz * pbitangentz);
		float tangentW;

		if (dot < 0) {
			tangentW = -1.0F;
		} else {
			tangentW = 1.0F;
		}

		return NormI8.pack(tangentx, tangenty, tangentz, tangentW);
	}

	private static float rsqrt(float value) {
		if (value == 0.0f) {
			return 1.0f;
		} else {
			return (float) (1.0 / Math.sqrt(value));
		}
	}
}
