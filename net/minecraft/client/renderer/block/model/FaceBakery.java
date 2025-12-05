package net.minecraft.client.renderer.block.model;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.math.MatrixUtil;
import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class FaceBakery {
	public static final int VERTEX_INT_SIZE = 8;
	public static final int VERTEX_COUNT = 4;
	private static final int COLOR_INDEX = 3;
	public static final int UV_INDEX = 4;
	private static final Vector3fc NO_RESCALE = new Vector3f(1.0F, 1.0F, 1.0F);
	private static final Vector3fc BLOCK_MIDDLE = new Vector3f(0.5F, 0.5F, 0.5F);

	@VisibleForTesting
	static BlockElementFace.UVs defaultFaceUV(Vector3fc vector3fc, Vector3fc vector3fc2, Direction direction) {
		return switch (direction) {
			case DOWN -> new BlockElementFace.UVs(vector3fc.x(), 16.0F - vector3fc2.z(), vector3fc2.x(), 16.0F - vector3fc.z());
			case UP -> new BlockElementFace.UVs(vector3fc.x(), vector3fc.z(), vector3fc2.x(), vector3fc2.z());
			case NORTH -> new BlockElementFace.UVs(16.0F - vector3fc2.x(), 16.0F - vector3fc2.y(), 16.0F - vector3fc.x(), 16.0F - vector3fc.y());
			case SOUTH -> new BlockElementFace.UVs(vector3fc.x(), 16.0F - vector3fc2.y(), vector3fc2.x(), 16.0F - vector3fc.y());
			case WEST -> new BlockElementFace.UVs(vector3fc.z(), 16.0F - vector3fc2.y(), vector3fc2.z(), 16.0F - vector3fc.y());
			case EAST -> new BlockElementFace.UVs(16.0F - vector3fc2.z(), 16.0F - vector3fc2.y(), 16.0F - vector3fc.z(), 16.0F - vector3fc.y());
			default -> throw new MatchException(null, null);
		};
	}

	public static BakedQuad bakeQuad(
		Vector3fc vector3fc,
		Vector3fc vector3fc2,
		BlockElementFace blockElementFace,
		TextureAtlasSprite textureAtlasSprite,
		Direction direction,
		ModelState modelState,
		@Nullable BlockElementRotation blockElementRotation,
		boolean bl,
		int i
	) {
		BlockElementFace.UVs uVs = blockElementFace.uvs();
		if (uVs == null) {
			uVs = defaultFaceUV(vector3fc, vector3fc2, direction);
		}

		uVs = shrinkUVs(textureAtlasSprite, uVs);
		Matrix4fc matrix4fc = modelState.inverseFaceTransformation(direction);
		int[] is = makeVertices(
			uVs,
			blockElementFace.rotation(),
			matrix4fc,
			textureAtlasSprite,
			direction,
			setupShape(vector3fc, vector3fc2),
			modelState.transformation(),
			blockElementRotation
		);
		Direction direction2 = calculateFacing(is);
		if (blockElementRotation == null) {
			recalculateWinding(is, direction2);
		}

		return new BakedQuad(is, blockElementFace.tintIndex(), direction2, textureAtlasSprite, bl, i);
	}

	private static BlockElementFace.UVs shrinkUVs(TextureAtlasSprite textureAtlasSprite, BlockElementFace.UVs uVs) {
		float f = uVs.minU();
		float g = uVs.minV();
		float h = uVs.maxU();
		float i = uVs.maxV();
		float j = textureAtlasSprite.uvShrinkRatio();
		float k = (f + f + h + h) / 4.0F;
		float l = (g + g + i + i) / 4.0F;
		return new BlockElementFace.UVs(Mth.lerp(j, f, k), Mth.lerp(j, g, l), Mth.lerp(j, h, k), Mth.lerp(j, i, l));
	}

	private static int[] makeVertices(
		BlockElementFace.UVs uVs,
		Quadrant quadrant,
		Matrix4fc matrix4fc,
		TextureAtlasSprite textureAtlasSprite,
		Direction direction,
		float[] fs,
		Transformation transformation,
		@Nullable BlockElementRotation blockElementRotation
	) {
		FaceInfo faceInfo = FaceInfo.fromFacing(direction);
		int[] is = new int[32];

		for (int i = 0; i < 4; i++) {
			bakeVertex(is, i, faceInfo, uVs, quadrant, matrix4fc, fs, textureAtlasSprite, transformation, blockElementRotation);
		}

		return is;
	}

	private static float[] setupShape(Vector3fc vector3fc, Vector3fc vector3fc2) {
		float[] fs = new float[Direction.values().length];
		fs[FaceInfo.Constants.MIN_X] = vector3fc.x() / 16.0F;
		fs[FaceInfo.Constants.MIN_Y] = vector3fc.y() / 16.0F;
		fs[FaceInfo.Constants.MIN_Z] = vector3fc.z() / 16.0F;
		fs[FaceInfo.Constants.MAX_X] = vector3fc2.x() / 16.0F;
		fs[FaceInfo.Constants.MAX_Y] = vector3fc2.y() / 16.0F;
		fs[FaceInfo.Constants.MAX_Z] = vector3fc2.z() / 16.0F;
		return fs;
	}

	private static void bakeVertex(
		int[] is,
		int i,
		FaceInfo faceInfo,
		BlockElementFace.UVs uVs,
		Quadrant quadrant,
		Matrix4fc matrix4fc,
		float[] fs,
		TextureAtlasSprite textureAtlasSprite,
		Transformation transformation,
		@Nullable BlockElementRotation blockElementRotation
	) {
		FaceInfo.VertexInfo vertexInfo = faceInfo.getVertexInfo(i);
		Vector3f vector3f = new Vector3f(fs[vertexInfo.xFace], fs[vertexInfo.yFace], fs[vertexInfo.zFace]);
		applyElementRotation(vector3f, blockElementRotation);
		applyModelRotation(vector3f, transformation);
		float f = BlockElementFace.getU(uVs, quadrant, i);
		float g = BlockElementFace.getV(uVs, quadrant, i);
		float j;
		float h;
		if (MatrixUtil.isIdentity(matrix4fc)) {
			h = f;
			j = g;
		} else {
			Vector3f vector3f2 = matrix4fc.transformPosition(new Vector3f(cornerToCenter(f), cornerToCenter(g), 0.0F));
			h = centerToCorner(vector3f2.x);
			j = centerToCorner(vector3f2.y);
		}

		fillVertex(is, i, vector3f, textureAtlasSprite, h, j);
	}

	private static float cornerToCenter(float f) {
		return f - 0.5F;
	}

	private static float centerToCorner(float f) {
		return f + 0.5F;
	}

	private static void fillVertex(int[] is, int i, Vector3f vector3f, TextureAtlasSprite textureAtlasSprite, float f, float g) {
		int j = i * 8;
		is[j] = Float.floatToRawIntBits(vector3f.x());
		is[j + 1] = Float.floatToRawIntBits(vector3f.y());
		is[j + 2] = Float.floatToRawIntBits(vector3f.z());
		is[j + 3] = -1;
		is[j + 4] = Float.floatToRawIntBits(textureAtlasSprite.getU(f));
		is[j + 4 + 1] = Float.floatToRawIntBits(textureAtlasSprite.getV(g));
	}

	private static void applyElementRotation(Vector3f vector3f, @Nullable BlockElementRotation blockElementRotation) {
		if (blockElementRotation != null) {
			Vector3fc vector3fc = blockElementRotation.axis().getPositive().getUnitVec3f();
			Matrix4fc matrix4fc = new Matrix4f().rotation(blockElementRotation.angle() * (float) (Math.PI / 180.0), vector3fc);
			Vector3fc vector3fc2 = blockElementRotation.rescale() ? computeRescale(blockElementRotation) : NO_RESCALE;
			rotateVertexBy(vector3f, blockElementRotation.origin(), matrix4fc, vector3fc2);
		}
	}

	private static Vector3fc computeRescale(BlockElementRotation blockElementRotation) {
		if (blockElementRotation.angle() == 0.0F) {
			return NO_RESCALE;
		} else {
			float f = Math.abs(blockElementRotation.angle());
			float g = 1.0F / Mth.cos(f * (float) (Math.PI / 180.0));

			return switch (blockElementRotation.axis()) {
				case X -> new Vector3f(1.0F, g, g);
				case Y -> new Vector3f(g, 1.0F, g);
				case Z -> new Vector3f(g, g, 1.0F);
				default -> throw new MatchException(null, null);
			};
		}
	}

	private static void applyModelRotation(Vector3f vector3f, Transformation transformation) {
		if (transformation != Transformation.identity()) {
			rotateVertexBy(vector3f, BLOCK_MIDDLE, transformation.getMatrix(), NO_RESCALE);
		}
	}

	private static void rotateVertexBy(Vector3f vector3f, Vector3fc vector3fc, Matrix4fc matrix4fc, Vector3fc vector3fc2) {
		vector3f.sub(vector3fc);
		matrix4fc.transformPosition(vector3f);
		vector3f.mul(vector3fc2);
		vector3f.add(vector3fc);
	}

	private static Direction calculateFacing(int[] is) {
		Vector3f vector3f = vectorFromData(is, 0);
		Vector3f vector3f2 = vectorFromData(is, 8);
		Vector3f vector3f3 = vectorFromData(is, 16);
		Vector3f vector3f4 = new Vector3f(vector3f).sub(vector3f2);
		Vector3f vector3f5 = new Vector3f(vector3f3).sub(vector3f2);
		Vector3f vector3f6 = new Vector3f(vector3f5).cross(vector3f4).normalize();
		if (!vector3f6.isFinite()) {
			return Direction.UP;
		} else {
			Direction direction = null;
			float f = 0.0F;

			for (Direction direction2 : Direction.values()) {
				float g = vector3f6.dot(direction2.getUnitVec3f());
				if (g >= 0.0F && g > f) {
					f = g;
					direction = direction2;
				}
			}

			return direction == null ? Direction.UP : direction;
		}
	}

	private static float xFromData(int[] is, int i) {
		return Float.intBitsToFloat(is[i]);
	}

	private static float yFromData(int[] is, int i) {
		return Float.intBitsToFloat(is[i + 1]);
	}

	private static float zFromData(int[] is, int i) {
		return Float.intBitsToFloat(is[i + 2]);
	}

	private static Vector3f vectorFromData(int[] is, int i) {
		return new Vector3f(xFromData(is, i), yFromData(is, i), zFromData(is, i));
	}

	private static void recalculateWinding(int[] is, Direction direction) {
		int[] js = new int[is.length];
		System.arraycopy(is, 0, js, 0, is.length);
		float[] fs = new float[Direction.values().length];
		fs[FaceInfo.Constants.MIN_X] = 999.0F;
		fs[FaceInfo.Constants.MIN_Y] = 999.0F;
		fs[FaceInfo.Constants.MIN_Z] = 999.0F;
		fs[FaceInfo.Constants.MAX_X] = -999.0F;
		fs[FaceInfo.Constants.MAX_Y] = -999.0F;
		fs[FaceInfo.Constants.MAX_Z] = -999.0F;

		for (int i = 0; i < 4; i++) {
			int j = 8 * i;
			float f = xFromData(js, j);
			float g = yFromData(js, j);
			float h = zFromData(js, j);
			if (f < fs[FaceInfo.Constants.MIN_X]) {
				fs[FaceInfo.Constants.MIN_X] = f;
			}

			if (g < fs[FaceInfo.Constants.MIN_Y]) {
				fs[FaceInfo.Constants.MIN_Y] = g;
			}

			if (h < fs[FaceInfo.Constants.MIN_Z]) {
				fs[FaceInfo.Constants.MIN_Z] = h;
			}

			if (f > fs[FaceInfo.Constants.MAX_X]) {
				fs[FaceInfo.Constants.MAX_X] = f;
			}

			if (g > fs[FaceInfo.Constants.MAX_Y]) {
				fs[FaceInfo.Constants.MAX_Y] = g;
			}

			if (h > fs[FaceInfo.Constants.MAX_Z]) {
				fs[FaceInfo.Constants.MAX_Z] = h;
			}
		}

		FaceInfo faceInfo = FaceInfo.fromFacing(direction);

		for (int jx = 0; jx < 4; jx++) {
			int k = 8 * jx;
			FaceInfo.VertexInfo vertexInfo = faceInfo.getVertexInfo(jx);
			float hx = fs[vertexInfo.xFace];
			float l = fs[vertexInfo.yFace];
			float m = fs[vertexInfo.zFace];
			is[k] = Float.floatToRawIntBits(hx);
			is[k + 1] = Float.floatToRawIntBits(l);
			is[k + 2] = Float.floatToRawIntBits(m);

			for (int n = 0; n < 4; n++) {
				int o = 8 * n;
				float p = xFromData(js, o);
				float q = yFromData(js, o);
				float r = zFromData(js, o);
				if (Mth.equal(hx, p) && Mth.equal(l, q) && Mth.equal(m, r)) {
					is[k + 4] = js[o + 4];
					is[k + 4 + 1] = js[o + 4 + 1];
				}
			}
		}
	}

	public static void extractPositions(int[] is, Consumer<Vector3f> consumer) {
		for (int i = 0; i < 4; i++) {
			consumer.accept(vectorFromData(is, 8 * i));
		}
	}
}
