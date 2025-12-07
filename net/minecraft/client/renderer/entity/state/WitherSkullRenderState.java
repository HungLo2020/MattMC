package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.model.SkullModelBase;

@Environment(EnvType.CLIENT)
public class WitherSkullRenderState extends EntityRenderState {
	public boolean isDangerous;
	public final SkullModelBase.State modelState = new SkullModelBase.State();
}
