package net.minecraft.client.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;

@Environment(EnvType.CLIENT)
public abstract class SkullModelBase extends Model<SkullModelBase.State> {
	public SkullModelBase(ModelPart modelPart) {
		super(modelPart, RenderType::entityTranslucent);
	}

	@Environment(EnvType.CLIENT)
	public static class State {
		public float animationPos;
		public float yRot;
		public float xRot;
	}
}
