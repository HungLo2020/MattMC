package net.minecraft.client.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;

@Environment(EnvType.CLIENT)
public class GiantZombieModel extends AbstractZombieModel<ZombieRenderState> {
	public GiantZombieModel(ModelPart modelPart) {
		super(modelPart);
	}
}
