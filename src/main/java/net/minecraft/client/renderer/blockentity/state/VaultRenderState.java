package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class VaultRenderState extends BlockEntityRenderState {
	@Nullable
	public ItemClusterRenderState displayItem;
	public float spin;
}
