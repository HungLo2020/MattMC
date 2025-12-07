package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SpawnerRenderState extends BlockEntityRenderState {
	@Nullable
	public EntityRenderState displayEntity;
	public float spin;
	public float scale;
}
