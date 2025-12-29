/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.client.particle;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleSpriteManager;
import net.minecraft.client.texture.TextureAtlasSprite;
import net.minecraft.client.texture.TextureAtlas;
import net.minecraft.util.Atlases;
import net.minecraft.core.random.Random;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;

public record FabricSpriteProviderImpl(ParticleSpriteManager.SimpleSpriteProvider delegate) implements FabricSpriteProvider {
	@Override
	public TextureAtlas getAtlas() {
		return Minecraft.getInstance().getAtlasManager().getAtlasTexture(Atlases.PARTICLES);
	}

	@Override
	public List<TextureAtlasSprite> getSprites() {
		return delegate.sprites;
	}

	@Override
	public TextureAtlasSprite getSprite(int i, int j) {
		return delegate.getSprite(i, j);
	}

	@Override
	public TextureAtlasSprite getSprite(Random random) {
		return delegate.getSprite(random);
	}

	@Override
	public TextureAtlasSprite getFirst() {
		return delegate.getFirst();
	}
}
