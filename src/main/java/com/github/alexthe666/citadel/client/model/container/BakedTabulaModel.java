package com.github.alexthe666.citadel.client.model.container;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

// Citadel: BakedModel and ItemOverrides were moved/removed in 1.21
// This is a simplified placeholder - full Tabula model rendering would require
// adapting to 1.21's completely redesigned model system
public class BakedTabulaModel {
    private final ImmutableList<BakedQuad> quads;
    private final TextureAtlasSprite particle;
    private final ImmutableMap<ItemDisplayContext, Transformation> transforms;

    public BakedTabulaModel(ImmutableList<BakedQuad> quads, TextureAtlasSprite particle, ImmutableMap<ItemDisplayContext, Transformation> transforms) {
        this.quads = quads;
        this.particle = particle;
        this.transforms = transforms;
    }

    // TODO: Implement with 1.21's model system if Tabula models are needed
    public List<BakedQuad> getQuads(BlockState state, Direction direction, RandomSource random) {
        return this.quads;
    }

    public TextureAtlasSprite getParticleIcon() {
        return this.particle;
    }
}
