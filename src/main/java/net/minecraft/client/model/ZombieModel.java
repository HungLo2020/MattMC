package net.minecraft.client.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;

@Environment(EnvType.CLIENT)
public class ZombieModel<S extends ZombieRenderState> extends AbstractZombieModel<S> {
	public ZombieModel(ModelPart modelPart) {
		super(modelPart);
	}
}
