package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

@Environment(EnvType.CLIENT)
public class LobsterRenderState extends LivingEntityRenderState {
	public int variant;
	public float attackProgress;
	public float prevAttackProgress;
}
