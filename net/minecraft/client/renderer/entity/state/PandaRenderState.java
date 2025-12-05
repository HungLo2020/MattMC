package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.animal.Panda.Gene;

@Environment(EnvType.CLIENT)
public class PandaRenderState extends HoldingEntityRenderState {
	public Gene variant = Gene.NORMAL;
	public boolean isUnhappy;
	public boolean isSneezing;
	public int sneezeTime;
	public boolean isEating;
	public boolean isScared;
	public boolean isSitting;
	public float sitAmount;
	public float lieOnBackAmount;
	public float rollAmount;
	public float rollTime;
}
