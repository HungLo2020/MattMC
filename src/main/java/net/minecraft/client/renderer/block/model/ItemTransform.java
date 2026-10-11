package net.minecraft.client.renderer.block.model;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public record ItemTransform(Vector3fc rotation, Vector3fc translation, Vector3fc scale) {
	public static final ItemTransform NO_TRANSFORM = new ItemTransform(new Vector3f(), new Vector3f(), new Vector3f(1.0F, 1.0F, 1.0F));

	public void apply(boolean bl, PoseStack.Pose pose) {
		if (this == NO_TRANSFORM) {
			pose.translate(-0.5F, -0.5F, -0.5F);
		} else {
			float f;
			float g;
			float h;
			if (bl) {
				f = -this.translation.x();
				g = -this.rotation.y();
				h = -this.rotation.z();
			} else {
				f = this.translation.x();
				g = this.rotation.y();
				h = this.rotation.z();
			}

			pose.translate(f, this.translation.y(), this.translation.z());
			pose.rotate(new Quaternionf().rotationXYZ(this.rotation.x() * (float) (Math.PI / 180.0), g * (float) (Math.PI / 180.0), h * (float) (Math.PI / 180.0)));
			pose.scale(this.scale.x(), this.scale.y(), this.scale.z());
			pose.translate(-0.5F, -0.5F, -0.5F);
		}
	}

}
