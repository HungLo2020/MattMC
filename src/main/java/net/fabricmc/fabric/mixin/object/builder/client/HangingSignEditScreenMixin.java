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

package net.fabricmc.fabric.mixin.object.builder.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screens.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.ingame.HangingSignEditScreen;
import net.minecraft.server.packss.ResourceLocation;

@Mixin(HangingSignEditScreen.class)
public abstract class HangingSignEditScreenMixin extends AbstractSignEditScreen {
	private HangingSignEditScreenMixin(SignBlockEntity blockEntity, boolean filtered, boolean bl) {
		super(blockEntity, filtered, bl);
	}

	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ResourceLocation;ofVanilla(Ljava/lang/String;)Lnet/minecraft/util/ResourceLocation;"))
	private ResourceLocation init(String id, Operation<ResourceLocation> original) {
		if (signType.name().indexOf(ResourceLocation.NAMESPACE_SEPARATOR) != -1) {
			ResourceLocation identifier = ResourceLocation.of(signType.name());
			return identifier.withPath(path -> "textures/gui/hanging_signs/" + path + ".png");
		}

		return original.call(id);
	}
}
