/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.fabric.mixins.client;

import net.distant_horizons.core.config.Config;
import net.distant_horizons.core.dependencyInjection.SingletonInjector;
import net.distant_horizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Camera;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.minecraft.world.level.material.FogType;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogData;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(FogRenderer.class)
public class MixinFogRenderer
{
	// Using this instead of Float.MAX_VALUE because Sodium don't like it.
	@Unique
	private static final float A_REALLY_REALLY_BIG_VALUE = 420694206942069.F;
	@Unique
	private static final float A_EVEN_LARGER_VALUE = 42069420694206942069.F;
	
	
	
	@Unique
	private static void unused()
	{
		boolean cancelFog = cancelFog();
		
		if (cancelFog)
		{
		}
		
	}
	
	
	
	// In MC's FogRenderer they clamp the "renderDistanceEnd" fog field to the render distance,
	// which prevents us from disabling the vanilla fog.
	// This mixin fires after they set the "renderDistanceEnd" so we can change it.
	@WrapOperation(
		method = "setupFog",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F",
			opcode = org.objectweb.asm.Opcodes.PUTFIELD
		)
	)
	private void onSetRenderDistanceEnd(FogData instance, float value, Operation<Void> original) 
	{
		if (cancelFog())
		{
			instance.environmentalStart = A_REALLY_REALLY_BIG_VALUE;
			instance.environmentalEnd = A_EVEN_LARGER_VALUE;
			
			instance.renderDistanceStart = A_REALLY_REALLY_BIG_VALUE;
			instance.renderDistanceEnd = A_EVEN_LARGER_VALUE;
		}
		
		// Always call the original with the modified or original value
		original.call(instance, value);
	}
	
	
	
	@Unique
	private static boolean cancelFog()
	{
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		Entity entity = camera.getEntity();	
		
		
		boolean cameraNotInFluid = cameraNotInFluid(camera);
		boolean isSpecialFog = (entity instanceof LivingEntity) && ((LivingEntity) entity).hasEffect(MobEffects.BLINDNESS);
		
		boolean cancelFog = !isSpecialFog;
		cancelFog = cancelFog && cameraNotInFluid;
		cancelFog = cancelFog && !SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class).isFogStateSpecial();
		cancelFog = cancelFog && !Config.Client.Advanced.Graphics.Fog.enableVanillaFog.get();
		
		return cancelFog;
	}
	
	@Unique
	private static boolean cameraNotInFluid(Camera camera)
	{
		FogType fogTypes = camera.getFluidInCamera();
		boolean cameraNotInFluid = fogTypes == FogType.NONE;
		
		return cameraNotInFluid;
	}
	
	
	
}
