package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

@Environment(EnvType.CLIENT)
public class DynamicUniforms implements AutoCloseable {
	public static final int TRANSFORM_UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec3().putMat4f().putFloat().get();
	private static final int INITIAL_CAPACITY = 2;
	private final DynamicUniformStorage<DynamicUniforms.Transform> transforms = new DynamicUniformStorage<>("Dynamic Transforms UBO", TRANSFORM_UBO_SIZE, 2);

	public void reset() {
		this.transforms.endFrame();
	}

	public void close() {
		this.transforms.close();
	}

	public GpuBufferSlice writeTransform(Matrix4fc matrix4fc, Vector4fc vector4fc, Vector3fc vector3fc, Matrix4fc matrix4fc2, float f) {
		return this.transforms
			.writeUniform(new DynamicUniforms.Transform(new Matrix4f(matrix4fc), new Vector4f(vector4fc), new Vector3f(vector3fc), new Matrix4f(matrix4fc2), f));
	}

	public GpuBufferSlice[] writeTransforms(DynamicUniforms.Transform... transforms) {
		return this.transforms.writeUniforms(transforms);
	}

	@Environment(EnvType.CLIENT)
	public record Transform(Matrix4fc modelView, Vector4fc colorModulator, Vector3fc modelOffset, Matrix4fc textureMatrix, float lineWidth)
		implements DynamicUniformStorage.DynamicUniform {
		@Override
		public void write(ByteBuffer byteBuffer) {
			Std140Builder.intoBuffer(byteBuffer)
				.putMat4f(this.modelView)
				.putVec4(this.colorModulator)
				.putVec3(this.modelOffset)
				.putMat4f(this.textureMatrix)
				.putFloat(this.lineWidth);
		}
	}
}
