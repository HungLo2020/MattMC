package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.geom.ModelPart;

@Environment(EnvType.CLIENT)
public interface HeadedModel {
	ModelPart getHead();

	default void translateToHead(PoseStack poseStack) {
		this.getHead().translateAndRotate(poseStack);
	}
}
