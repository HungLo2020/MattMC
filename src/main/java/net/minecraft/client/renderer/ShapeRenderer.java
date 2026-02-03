package net.minecraft.client.renderer;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sodium.client.render.vertex.VertexConsumerUtils;
import net.sodium.client.render.vertex.buffer.BufferBuilderExtension;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ShapeRenderer {
	public static void renderShape(PoseStack poseStack, VertexConsumer vertexConsumer, VoxelShape voxelShape, double d, double e, double f, int i) {
		PoseStack.Pose pose = poseStack.last();
		voxelShape.forAllEdges((g, h, j, k, l, m) -> {
			Vector3f vector3f = new Vector3f((float)(k - g), (float)(l - h), (float)(m - j)).normalize();
			vertexConsumer.addVertex(pose, (float)(g + d), (float)(h + e), (float)(j + f)).setColor(i).setNormal(pose, vector3f);
			vertexConsumer.addVertex(pose, (float)(k + d), (float)(l + e), (float)(m + f)).setColor(i).setNormal(pose, vector3f);
		});
	}

	public static void renderLineBox(PoseStack.Pose pose, VertexConsumer vertexConsumer, AABB aABB, float f, float g, float h, float i) {
		renderLineBox(pose, vertexConsumer, aABB.minX, aABB.minY, aABB.minZ, aABB.maxX, aABB.maxY, aABB.maxZ, f, g, h, i, f, g, h);
	}

	public static void renderLineBox(
		PoseStack.Pose pose, VertexConsumer vertexConsumer, double d, double e, double f, double g, double h, double i, float j, float k, float l, float m
	) {
		renderLineBox(pose, vertexConsumer, d, e, f, g, h, i, j, k, l, m, j, k, l);
	}

	public static void renderLineBox(
		PoseStack.Pose pose,
		VertexConsumer vertexConsumer,
		double d,
		double e,
		double f,
		double g,
		double h,
		double i,
		float j,
		float k,
		float l,
		float m,
		float n,
		float o,
		float p
	) {
		// Sodium: Use fast intrinsics path if available (merged from LevelRendererMixin outlines)
		var writer = VertexConsumerUtils.convertOrLog(vertexConsumer);

		if (writer != null) {
			sodium$renderLineBoxFast(pose, vertexConsumer, writer, d, e, f, g, h, i, j, k, l, m, n, o, p);
			return;
		}

		// Fallback to vanilla rendering
		float q = (float)d;
		float r = (float)e;
		float s = (float)f;
		float t = (float)g;
		float u = (float)h;
		float v = (float)i;
		vertexConsumer.addVertex(pose, q, r, s).setColor(j, o, p, m).setNormal(pose, 1.0F, 0.0F, 0.0F);
		vertexConsumer.addVertex(pose, t, r, s).setColor(j, o, p, m).setNormal(pose, 1.0F, 0.0F, 0.0F);
		vertexConsumer.addVertex(pose, q, r, s).setColor(n, k, p, m).setNormal(pose, 0.0F, 1.0F, 0.0F);
		vertexConsumer.addVertex(pose, q, u, s).setColor(n, k, p, m).setNormal(pose, 0.0F, 1.0F, 0.0F);
		vertexConsumer.addVertex(pose, q, r, s).setColor(n, o, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F);
		vertexConsumer.addVertex(pose, q, r, v).setColor(n, o, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F);
		vertexConsumer.addVertex(pose, t, r, s).setColor(j, k, l, m).setNormal(pose, 0.0F, 1.0F, 0.0F);
		vertexConsumer.addVertex(pose, t, u, s).setColor(j, k, l, m).setNormal(pose, 0.0F, 1.0F, 0.0F);
		vertexConsumer.addVertex(pose, t, u, s).setColor(j, k, l, m).setNormal(pose, -1.0F, 0.0F, 0.0F);
		vertexConsumer.addVertex(pose, q, u, s).setColor(j, k, l, m).setNormal(pose, -1.0F, 0.0F, 0.0F);
		vertexConsumer.addVertex(pose, q, u, s).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F);
		vertexConsumer.addVertex(pose, q, u, v).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F);
		vertexConsumer.addVertex(pose, q, u, v).setColor(j, k, l, m).setNormal(pose, 0.0F, -1.0F, 0.0F);
		vertexConsumer.addVertex(pose, q, r, v).setColor(j, k, l, m).setNormal(pose, 0.0F, -1.0F, 0.0F);
		vertexConsumer.addVertex(pose, q, r, v).setColor(j, k, l, m).setNormal(pose, 1.0F, 0.0F, 0.0F);
		vertexConsumer.addVertex(pose, t, r, v).setColor(j, k, l, m).setNormal(pose, 1.0F, 0.0F, 0.0F);
		vertexConsumer.addVertex(pose, t, r, v).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, -1.0F);
		vertexConsumer.addVertex(pose, t, r, s).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, -1.0F);
		vertexConsumer.addVertex(pose, q, u, v).setColor(j, k, l, m).setNormal(pose, 1.0F, 0.0F, 0.0F);
		vertexConsumer.addVertex(pose, t, u, v).setColor(j, k, l, m).setNormal(pose, 1.0F, 0.0F, 0.0F);
		vertexConsumer.addVertex(pose, t, r, v).setColor(j, k, l, m).setNormal(pose, 0.0F, 1.0F, 0.0F);
		vertexConsumer.addVertex(pose, t, u, v).setColor(j, k, l, m).setNormal(pose, 0.0F, 1.0F, 0.0F);
		vertexConsumer.addVertex(pose, t, u, s).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F);
		vertexConsumer.addVertex(pose, t, u, v).setColor(j, k, l, m).setNormal(pose, 0.0F, 0.0F, 1.0F);
	}

	public static void addChainedFilledBoxVertices(
		PoseStack poseStack, VertexConsumer vertexConsumer, double d, double e, double f, double g, double h, double i, float j, float k, float l, float m
	) {
		addChainedFilledBoxVertices(poseStack, vertexConsumer, (float)d, (float)e, (float)f, (float)g, (float)h, (float)i, j, k, l, m);
	}

	public static void addChainedFilledBoxVertices(
		PoseStack poseStack, VertexConsumer vertexConsumer, float f, float g, float h, float i, float j, float k, float l, float m, float n, float o
	) {
		Matrix4f matrix4f = poseStack.last().pose();
		vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
		vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
	}

	public static void renderFace(
		Matrix4f matrix4f,
		VertexConsumer vertexConsumer,
		Direction direction,
		float f,
		float g,
		float h,
		float i,
		float j,
		float k,
		float l,
		float m,
		float n,
		float o
	) {
		switch (direction) {
			case DOWN:
				vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
				break;
			case UP:
				vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
				break;
			case NORTH:
				vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
				break;
			case SOUTH:
				vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
				break;
			case WEST:
				vertexConsumer.addVertex(matrix4f, f, g, h).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, f, g, k).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, f, j, k).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, f, j, h).setColor(l, m, n, o);
				break;
			case EAST:
				vertexConsumer.addVertex(matrix4f, i, g, h).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, j, h).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, j, k).setColor(l, m, n, o);
				vertexConsumer.addVertex(matrix4f, i, g, k).setColor(l, m, n, o);
		}
	}

	public static void renderVector(PoseStack poseStack, VertexConsumer vertexConsumer, Vector3f vector3f, Vec3 vec3, int i) {
		PoseStack.Pose pose = poseStack.last();
		vertexConsumer.addVertex(pose, vector3f).setColor(i).setNormal(pose, (float)vec3.x, (float)vec3.y, (float)vec3.z);
		vertexConsumer.addVertex(pose, (float)(vector3f.x() + vec3.x), (float)(vector3f.y() + vec3.y), (float)(vector3f.z() + vec3.z))
			.setColor(i)
			.setNormal(pose, (float)vec3.x, (float)vec3.y, (float)vec3.z);
	}

	// Sodium: Fast line box rendering using intrinsics (merged from LevelRendererMixin outlines)
	private static void sodium$renderLineBoxFast(PoseStack.Pose matrices, VertexConsumer vertexConsumer, net.sodium.api.vertex.buffer.VertexBufferWriter writer,
			double x1, double y1, double z1, double x2, double y2, double z2,
			float red, float green, float blue, float alpha, float xAxisRed, float yAxisGreen, float zAxisBlue) {
		org.joml.Matrix4f position = matrices.pose();
		org.joml.Matrix3f normal = matrices.normal();

		float x1f = (float) x1;
		float y1f = (float) y1;
		float z1f = (float) z1;
		float x2f = (float) x2;
		float y2f = (float) y2;
		float z2f = (float) z2;

		int color = net.sodium.api.util.ColorABGR.pack(red, green, blue, alpha);

		float v1x = org.joml.Math.fma(position.m00(), x1f, org.joml.Math.fma(position.m10(), y1f, org.joml.Math.fma(position.m20(), z1f, position.m30())));
		float v1y = org.joml.Math.fma(position.m01(), x1f, org.joml.Math.fma(position.m11(), y1f, org.joml.Math.fma(position.m21(), z1f, position.m31())));
		float v1z = org.joml.Math.fma(position.m02(), x1f, org.joml.Math.fma(position.m12(), y1f, org.joml.Math.fma(position.m22(), z1f, position.m32())));

		float v2x = org.joml.Math.fma(position.m00(), x2f, org.joml.Math.fma(position.m10(), y1f, org.joml.Math.fma(position.m20(), z1f, position.m30())));
		float v2y = org.joml.Math.fma(position.m01(), x2f, org.joml.Math.fma(position.m11(), y1f, org.joml.Math.fma(position.m21(), z1f, position.m31())));
		float v2z = org.joml.Math.fma(position.m02(), x2f, org.joml.Math.fma(position.m12(), y1f, org.joml.Math.fma(position.m22(), z1f, position.m32())));

		float v3x = org.joml.Math.fma(position.m00(), x1f, org.joml.Math.fma(position.m10(), y2f, org.joml.Math.fma(position.m20(), z1f, position.m30())));
		float v3y = org.joml.Math.fma(position.m01(), x1f, org.joml.Math.fma(position.m11(), y2f, org.joml.Math.fma(position.m21(), z1f, position.m31())));
		float v3z = org.joml.Math.fma(position.m02(), x1f, org.joml.Math.fma(position.m12(), y2f, org.joml.Math.fma(position.m22(), z1f, position.m32())));

		float v4x = org.joml.Math.fma(position.m00(), x1f, org.joml.Math.fma(position.m10(), y1f, org.joml.Math.fma(position.m20(), z2f, position.m30())));
		float v4y = org.joml.Math.fma(position.m01(), x1f, org.joml.Math.fma(position.m11(), y1f, org.joml.Math.fma(position.m21(), z2f, position.m31())));
		float v4z = org.joml.Math.fma(position.m02(), x1f, org.joml.Math.fma(position.m12(), y1f, org.joml.Math.fma(position.m22(), z2f, position.m32())));

		float v5x = org.joml.Math.fma(position.m00(), x2f, org.joml.Math.fma(position.m10(), y2f, org.joml.Math.fma(position.m20(), z1f, position.m30())));
		float v5y = org.joml.Math.fma(position.m01(), x2f, org.joml.Math.fma(position.m11(), y2f, org.joml.Math.fma(position.m21(), z1f, position.m31())));
		float v5z = org.joml.Math.fma(position.m02(), x2f, org.joml.Math.fma(position.m12(), y2f, org.joml.Math.fma(position.m22(), z1f, position.m32())));

		float v6x = org.joml.Math.fma(position.m00(), x1f, org.joml.Math.fma(position.m10(), y2f, org.joml.Math.fma(position.m20(), z2f, position.m30())));
		float v6y = org.joml.Math.fma(position.m01(), x1f, org.joml.Math.fma(position.m11(), y2f, org.joml.Math.fma(position.m21(), z2f, position.m31())));
		float v6z = org.joml.Math.fma(position.m02(), x1f, org.joml.Math.fma(position.m12(), y2f, org.joml.Math.fma(position.m22(), z2f, position.m32())));

		float v7x = org.joml.Math.fma(position.m00(), x2f, org.joml.Math.fma(position.m10(), y1f, org.joml.Math.fma(position.m20(), z2f, position.m30())));
		float v7y = org.joml.Math.fma(position.m01(), x2f, org.joml.Math.fma(position.m11(), y1f, org.joml.Math.fma(position.m21(), z2f, position.m31())));
		float v7z = org.joml.Math.fma(position.m02(), x2f, org.joml.Math.fma(position.m12(), y1f, org.joml.Math.fma(position.m22(), z2f, position.m32())));

		float v8x = org.joml.Math.fma(position.m00(), x2f, org.joml.Math.fma(position.m10(), y2f, org.joml.Math.fma(position.m20(), z2f, position.m30())));
		float v8y = org.joml.Math.fma(position.m01(), x2f, org.joml.Math.fma(position.m11(), y2f, org.joml.Math.fma(position.m21(), z2f, position.m31())));
		float v8z = org.joml.Math.fma(position.m02(), x2f, org.joml.Math.fma(position.m12(), y2f, org.joml.Math.fma(position.m22(), z2f, position.m32())));

		if (vertexConsumer instanceof BufferBuilderExtension ext) {
			ext.sodium$duplicateVertex();
		}

		sodium$writeLineVertices(writer, v1x, v1y, v1z, net.sodium.api.util.ColorABGR.pack(red, yAxisGreen, zAxisBlue, alpha), net.sodium.api.util.NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
		sodium$writeLineVertices(writer, v2x, v2y, v2z, net.sodium.api.util.ColorABGR.pack(red, yAxisGreen, zAxisBlue, alpha), net.sodium.api.util.NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
		sodium$writeLineVertices(writer, v1x, v1y, v1z, net.sodium.api.util.ColorABGR.pack(xAxisRed, green, zAxisBlue, alpha), net.sodium.api.util.NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
		sodium$writeLineVertices(writer, v3x, v3y, v3z, net.sodium.api.util.ColorABGR.pack(xAxisRed, green, zAxisBlue, alpha), net.sodium.api.util.NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
		sodium$writeLineVertices(writer, v1x, v1y, v1z, net.sodium.api.util.ColorABGR.pack(xAxisRed, yAxisGreen, blue, alpha), net.sodium.api.util.NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
		sodium$writeLineVertices(writer, v4x, v4y, v4z, net.sodium.api.util.ColorABGR.pack(xAxisRed, yAxisGreen, blue, alpha), net.sodium.api.util.NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
		sodium$writeLineVertices(writer, v2x, v2y, v2z, color, net.sodium.api.util.NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
		sodium$writeLineVertices(writer, v5x, v5y, v5z, color, net.sodium.api.util.NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
		sodium$writeLineVertices(writer, v5x, v5y, v5z, color, net.sodium.api.util.NormI8.pack(-normal.m00(), -normal.m01(), -normal.m02()));
		sodium$writeLineVertices(writer, v3x, v3y, v3z, color, net.sodium.api.util.NormI8.pack(-normal.m00(), -normal.m01(), -normal.m02()));
		sodium$writeLineVertices(writer, v3x, v3y, v3z, color, net.sodium.api.util.NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
		sodium$writeLineVertices(writer, v6x, v6y, v6z, color, net.sodium.api.util.NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
		sodium$writeLineVertices(writer, v6x, v6y, v6z, color, net.sodium.api.util.NormI8.pack(-normal.m10(), -normal.m11(), -normal.m12()));
		sodium$writeLineVertices(writer, v4x, v4y, v4z, color, net.sodium.api.util.NormI8.pack(-normal.m10(), -normal.m11(), -normal.m12()));
		sodium$writeLineVertices(writer, v4x, v4y, v4z, color, net.sodium.api.util.NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
		sodium$writeLineVertices(writer, v7x, v7y, v7z, color, net.sodium.api.util.NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
		sodium$writeLineVertices(writer, v7x, v7y, v7z, color, net.sodium.api.util.NormI8.pack(-normal.m20(), -normal.m21(), -normal.m22()));
		sodium$writeLineVertices(writer, v2x, v2y, v2z, color, net.sodium.api.util.NormI8.pack(-normal.m20(), -normal.m21(), -normal.m22()));
		sodium$writeLineVertices(writer, v6x, v6y, v6z, color, net.sodium.api.util.NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
		sodium$writeLineVertices(writer, v8x, v8y, v8z, color, net.sodium.api.util.NormI8.pack(normal.m00(), normal.m01(), normal.m02()));
		sodium$writeLineVertices(writer, v7x, v7y, v7z, color, net.sodium.api.util.NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
		sodium$writeLineVertices(writer, v8x, v8y, v8z, color, net.sodium.api.util.NormI8.pack(normal.m10(), normal.m11(), normal.m12()));
		sodium$writeLineVertices(writer, v5x, v5y, v5z, color, net.sodium.api.util.NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
		sodium$writeLineVertex(writer, v8x, v8y, v8z, color, net.sodium.api.util.NormI8.pack(normal.m20(), normal.m21(), normal.m22()));
	}

	private static void sodium$writeLineVertices(net.sodium.api.vertex.buffer.VertexBufferWriter writer, float x, float y, float z, int color, int normal) {
		try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
			long buffer = stack.nmalloc(2 * net.sodium.api.vertex.format.common.LineVertex.STRIDE);
			long ptr = buffer;

			for (int i = 0; i < 2; i++) {
				net.sodium.api.vertex.format.common.LineVertex.put(ptr, x, y, z, color, normal);
				ptr += net.sodium.api.vertex.format.common.LineVertex.STRIDE;
			}

			writer.push(stack, buffer, 2, net.sodium.api.vertex.format.common.LineVertex.FORMAT);
		}
	}

	private static void sodium$writeLineVertex(net.sodium.api.vertex.buffer.VertexBufferWriter writer, float x, float y, float z, int color, int normal) {
		try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
			long buffer = stack.nmalloc(net.sodium.api.vertex.format.common.LineVertex.STRIDE);
			net.sodium.api.vertex.format.common.LineVertex.put(buffer, x, y, z, color, normal);

			writer.push(stack, buffer, 1, net.sodium.api.vertex.format.common.LineVertex.FORMAT);
		}
	}
}
