package net.blaze3d.vertex;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.Direction;
import net.sodium.api.util.ColorABGR;
import net.sodium.api.util.NormI8;
import net.sodium.api.vertex.attributes.common.ColorAttribute;
import net.sodium.api.vertex.attributes.common.TextureAttribute;
import net.sodium.api.vertex.buffer.VertexBufferWriter;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public class SheetedDecalTextureGenerator implements VertexConsumer, VertexBufferWriter {
	private final VertexConsumer delegate;
	private final Matrix4f cameraInversePose;
	private final Matrix3f normalInversePose;
	private final float textureScale;
	private final Vector3f worldPos = new Vector3f();
	private final Vector3f normal = new Vector3f();
	private float x;
	private float y;
	private float z;
	
	// Sodium: VertexBufferWriter optimization
	private boolean canUseIntrinsics;

	public SheetedDecalTextureGenerator(VertexConsumer vertexConsumer, PoseStack.Pose pose, float f) {
		this.delegate = vertexConsumer;
		this.cameraInversePose = new Matrix4f(pose.pose()).invert();
		this.normalInversePose = new Matrix3f(pose.normal()).invert();
		this.textureScale = f;
		
		// Sodium: Check if delegate supports intrinsics
		this.canUseIntrinsics = VertexBufferWriter.tryOf(this.delegate) != null;
	}

	@Override
	public VertexConsumer addVertex(float f, float g, float h) {
		this.x = f;
		this.y = g;
		this.z = h;
		this.delegate.addVertex(f, g, h);
		return this;
	}

	@Override
	public VertexConsumer setColor(int i, int j, int k, int l) {
		this.delegate.setColor(-1);
		return this;
	}

	@Override
	public VertexConsumer setUv(float f, float g) {
		return this;
	}

	@Override
	public VertexConsumer setUv1(int i, int j) {
		this.delegate.setUv1(i, j);
		return this;
	}

	@Override
	public VertexConsumer setUv2(int i, int j) {
		this.delegate.setUv2(i, j);
		return this;
	}

	@Override
	public VertexConsumer setNormal(float f, float g, float h) {
		this.delegate.setNormal(f, g, h);
		Vector3f vector3f = this.normalInversePose.transform(f, g, h, this.normal);
		Direction direction = Direction.getApproximateNearest(vector3f.x(), vector3f.y(), vector3f.z());
		Vector3f vector3f2 = this.cameraInversePose.transformPosition(this.x, this.y, this.z, this.worldPos);
		vector3f2.rotateY((float) Math.PI);
		vector3f2.rotateX((float) (-Math.PI / 2));
		vector3f2.rotate(direction.getRotation());
		this.delegate.setUv(-vector3f2.x() * this.textureScale, -vector3f2.y() * this.textureScale);
		return this;
	}
	
	// Sodium: VertexBufferWriter implementation for fast vertex processing
	@Override
	public boolean canUseIntrinsics() {
		return this.canUseIntrinsics;
	}
	
	@Override
	public void push(MemoryStack stack, long ptr, int count, VertexFormat format) {
		transform(ptr, count, format,
				this.normalInversePose, this.cameraInversePose, this.textureScale);
		
		VertexBufferWriter.of(this.delegate)
				.push(stack, ptr, count, format);
	}
	
	/**
	 * Transforms the overlay UVs element of each vertex to create a perspective-mapped effect.
	 *
	 * @param ptr    The buffer of vertices to transform
	 * @param count  The number of vertices to transform
	 * @param format The format of the vertices
	 * @param inverseNormalMatrix The inverted normal matrix
	 * @param inverseTextureMatrix The inverted texture matrix
	 * @param textureScale The amount which the overlay texture should be adjusted
	 */
	private static void transform(long ptr, int count, VertexFormat format,
								  Matrix3f inverseNormalMatrix, Matrix4f inverseTextureMatrix, float textureScale) {
		long stride = format.getVertexSize();
		
		var offsetPosition = format.getOffset(VertexFormatElement.POSITION);
		var offsetColor = format.getOffset(VertexFormatElement.COLOR);
		var offsetNormal = format.getOffset(VertexFormatElement.NORMAL);
		var offsetTexture = format.getOffset(VertexFormatElement.UV0);
		
		int color = ColorABGR.pack(1.0f, 1.0f, 1.0f, 1.0f);
		
		var normal = new Vector3f(Float.NaN);
		var position = new Vector4f(Float.NaN);
		
		for (int vertexIndex = 0; vertexIndex < count; vertexIndex++) {
			position.x = MemoryUtil.memGetFloat(ptr + offsetPosition + 0);
			position.y = MemoryUtil.memGetFloat(ptr + offsetPosition + 4);
			position.z = MemoryUtil.memGetFloat(ptr + offsetPosition + 8);
			position.w = 1.0f;
			
			int packedNormal = MemoryUtil.memGetInt(ptr + offsetNormal);
			normal.x = NormI8.unpackX(packedNormal);
			normal.y = NormI8.unpackY(packedNormal);
			normal.z = NormI8.unpackZ(packedNormal);
			
			Vector3f transformedNormal = inverseNormalMatrix.transform(normal);
			Direction direction = Direction.getApproximateNearest(transformedNormal.x(), transformedNormal.y(), transformedNormal.z());
			
			Vector4f transformedTexture = inverseTextureMatrix.transform(position);
			transformedTexture.rotateY(3.1415927F);
			transformedTexture.rotateX(-1.5707964F);
			transformedTexture.rotate(direction.getRotation());
			
			float textureU = -transformedTexture.x() * textureScale;
			float textureV = -transformedTexture.y() * textureScale;
			
			ColorAttribute.set(ptr + offsetColor, color);
			TextureAttribute.put(ptr + offsetTexture, textureU, textureV);
			
			ptr += stride;
		}
	}
}
