package net.minecraft.client.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Unit;

@Environment(EnvType.CLIENT)
public class GuardianParticleModel extends Model<Unit> {
	public GuardianParticleModel(ModelPart modelPart) {
		super(modelPart, RenderType::entityCutoutNoCull);
	}
}
