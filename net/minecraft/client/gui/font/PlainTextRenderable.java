package net.minecraft.client.gui.font;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public interface PlainTextRenderable extends TextRenderable {
	@Override
	default void render(Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, boolean bl) {
		float f = 0.0F;
		if (this.shadowColor() != 0) {
			this.renderSprite(matrix4f, vertexConsumer, i, this.x() + this.shadowOffset(), this.y() + this.shadowOffset(), 0.0F, this.shadowColor());
			if (!bl) {
				f += 0.03F;
			}
		}

		this.renderSprite(matrix4f, vertexConsumer, i, this.x(), this.y(), f, this.color());
	}

	void renderSprite(Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, float f, float g, float h, int j);

	float x();

	float y();

	int color();

	int shadowColor();

	float shadowOffset();
}
