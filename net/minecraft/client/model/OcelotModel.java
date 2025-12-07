package net.minecraft.client.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.FelineRenderState;

@Environment(EnvType.CLIENT)
public class OcelotModel extends FelineModel<FelineRenderState> {
	public OcelotModel(ModelPart modelPart) {
		super(modelPart);
	}
}
