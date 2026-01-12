package com.github.alexthe666.citadel.client.render.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class CitadelLecternRenderState extends BlockEntityRenderState {
    public boolean hasBook;
    public float yRot;
    public ItemStack book = ItemStack.EMPTY;
}
