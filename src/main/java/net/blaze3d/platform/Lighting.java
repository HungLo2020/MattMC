package net.blaze3d.platform;

import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.Std140Builder;
import net.blaze3d.buffers.Std140SizeCalculator;
import java.nio.ByteBuffer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.util.Mth;
import net.vulkanic.VulkanicAPI;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class Lighting implements AutoCloseable {
	private static final Vector3f DIFFUSE_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
	private static final Vector3f DIFFUSE_LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
	private static final Vector3f NETHER_DIFFUSE_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
	private static final Vector3f NETHER_DIFFUSE_LIGHT_1 = new Vector3f(-0.2F, -1.0F, 0.7F).normalize();
	private static final Vector3f INVENTORY_DIFFUSE_LIGHT_0 = new Vector3f(0.2F, -1.0F, 1.0F).normalize();
	private static final Vector3f INVENTORY_DIFFUSE_LIGHT_1 = new Vector3f(-0.2F, -1.0F, 0.0F).normalize();
	public static final int UBO_SIZE = new Std140SizeCalculator().putVec3().putVec3().get();
	@Nullable
	private final GpuBuffer buffer;
	private final int paddedSize;

	public Lighting() {
		this.paddedSize = Mth.roundToward(UBO_SIZE, VulkanicAPI.getBackendUniformOffsetAlignment());
		if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
			// Rust semantic world/GUI lighting is derived from copied gameplay
			// inputs; no Java lighting UBO or encoder writes belong on this route.
			this.buffer = null;
			return;
		}
		this.buffer = VulkanicAPI.createBuffer(() -> "Lighting UBO", 136, this.paddedSize * Lighting.Entry.values().length);
		Matrix4f matrix4fLevelUpright = new Matrix4f().scaling(1.0F, -1.0F, 1.0F);
		this.updateBuffer(
			Lighting.Entry.LEVEL_UPRIGHT,
			matrix4fLevelUpright.transformDirection(DIFFUSE_LIGHT_0, new Vector3f()),
			matrix4fLevelUpright.transformDirection(DIFFUSE_LIGHT_1, new Vector3f())
		);
		Matrix4f matrix4f = new Matrix4f().rotationY((float) (-Math.PI / 8)).rotateX((float) (Math.PI * 3.0 / 4.0));
		this.updateBuffer(
			Lighting.Entry.ITEMS_FLAT, matrix4f.transformDirection(DIFFUSE_LIGHT_0, new Vector3f()), matrix4f.transformDirection(DIFFUSE_LIGHT_1, new Vector3f())
		);
		Matrix4f matrix4f2 = new Matrix4f()
			.scaling(1.0F, -1.0F, 1.0F)
			.rotateYXZ(1.0821041F, 3.2375858F, 0.0F)
			.rotateYXZ((float) (-Math.PI / 8), (float) (Math.PI * 3.0 / 4.0), 0.0F);
		this.updateBuffer(
			Lighting.Entry.ITEMS_3D, matrix4f2.transformDirection(DIFFUSE_LIGHT_0, new Vector3f()), matrix4f2.transformDirection(DIFFUSE_LIGHT_1, new Vector3f())
		);
		Matrix4f matrix4f3 = new Matrix4f()
			.rotateYXZ(1.0821041F, 3.2375858F, 0.0F)
			.rotateYXZ((float) (-Math.PI / 8), (float) (Math.PI * 3.0 / 4.0), 0.0F);
		this.updateBuffer(
			Lighting.Entry.ITEMS_3D_UPRIGHT,
			matrix4f3.transformDirection(DIFFUSE_LIGHT_0, new Vector3f()),
			matrix4f3.transformDirection(DIFFUSE_LIGHT_1, new Vector3f())
		);
		this.updateBuffer(Lighting.Entry.ENTITY_IN_UI, INVENTORY_DIFFUSE_LIGHT_0, INVENTORY_DIFFUSE_LIGHT_1);
		Matrix4f matrix4f4 = new Matrix4f();
		this.updateBuffer(
			Lighting.Entry.PLAYER_SKIN,
			matrix4f4.transformDirection(INVENTORY_DIFFUSE_LIGHT_0, new Vector3f()),
			matrix4f4.transformDirection(INVENTORY_DIFFUSE_LIGHT_1, new Vector3f())
		);
	}

	public void updateLevel(boolean bl) {
		if (bl) {
			this.updateBuffer(Lighting.Entry.LEVEL, NETHER_DIFFUSE_LIGHT_0, NETHER_DIFFUSE_LIGHT_1);
			this.updateBuffer(
				Lighting.Entry.LEVEL_UPRIGHT,
				new Matrix4f().scaling(1.0F, -1.0F, 1.0F).transformDirection(NETHER_DIFFUSE_LIGHT_0, new Vector3f()),
				new Matrix4f().scaling(1.0F, -1.0F, 1.0F).transformDirection(NETHER_DIFFUSE_LIGHT_1, new Vector3f())
			);
		} else {
			this.updateBuffer(Lighting.Entry.LEVEL, DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1);
			this.updateBuffer(
				Lighting.Entry.LEVEL_UPRIGHT,
				new Matrix4f().scaling(1.0F, -1.0F, 1.0F).transformDirection(DIFFUSE_LIGHT_0, new Vector3f()),
				new Matrix4f().scaling(1.0F, -1.0F, 1.0F).transformDirection(DIFFUSE_LIGHT_1, new Vector3f())
			);
		}
	}

	private void updateBuffer(Lighting.Entry entry, Vector3f vector3f, Vector3f vector3f2) {
		if (this.buffer == null) {
			return;
		}
		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, UBO_SIZE).putVec3(vector3f).putVec3(vector3f2).get();
			VulkanicAPI.createCommandEncoder().writeToBuffer(this.buffer.slice(entry.ordinal() * this.paddedSize, this.paddedSize), byteBuffer);
		}
	}

	public void setupFor(Lighting.Entry entry) {
		if (this.buffer == null) {
			return;
		}
		VulkanicAPI.setShaderLights(this.buffer.slice(entry.ordinal() * this.paddedSize, UBO_SIZE));
	}

	public void close() {
		if (this.buffer != null) {
			this.buffer.close();
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum Entry {
		LEVEL,
		LEVEL_UPRIGHT,
		ITEMS_FLAT,
		ITEMS_3D,
		ITEMS_3D_UPRIGHT,
		ENTITY_IN_UI,
		PLAYER_SKIN
    }
}
