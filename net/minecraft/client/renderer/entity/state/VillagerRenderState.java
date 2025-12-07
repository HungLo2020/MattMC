package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.entity.npc.VillagerData;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class VillagerRenderState extends HoldingEntityRenderState implements VillagerDataHolderRenderState {
	public boolean isUnhappy;
	@Nullable
	public VillagerData villagerData;

	@Nullable
	@Override
	public VillagerData getVillagerData() {
		return this.villagerData;
	}
}
