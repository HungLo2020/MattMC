package net.minecraft.client.model;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;

@Environment(EnvType.CLIENT)
public class CopperGolemStatueModel extends Model<Direction> {
	public CopperGolemStatueModel(ModelPart modelPart) {
		super(modelPart, RenderType::entityCutoutNoCull);
	}

	public void setupAnim(Direction direction) {
		this.root.y = 0.0F;
		this.root.yRot = direction.getOpposite().toYRot() * (float) (Math.PI / 180.0);
		this.root.zRot = (float) Math.PI;
	}
}
