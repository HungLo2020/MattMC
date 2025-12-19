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

package com.seibel.distanthorizons.fabric.wrappers.modAccessor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;


#if MC_VER < MC_1_20_1
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
#endif
#if MC_VER < MC_1_17_1
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
#else 
#endif

public class SodiumAccessor implements ISodiumAccessor
{
	/**
	 * True if sodium 0.5 or less is present. <br>
	 * This field is public because it's also used to check if we need Indium to be present. <br>
	 * We need Indium if Sodium 0.5 or less is present.
	 */
	public static final boolean isSodiumV5OrLess;
	
	#if MC_VER >= MC_1_20_1
	private static MethodHandle setFogOcclusionMethod;
	private static Object sodiumPerformanceOptions;
	#endif
	
	static {
		isSodiumV5OrLess = !classPresent("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
	}
	
	
	
	//======================//
	// mod accessor methods //
	//======================//
	
	@Override
	public String getModName() { return "Sodium-Fabric"; }
	
	
	
	//================//
	// sodium methods //
	//================//
	
	/** An overwrite for a config in sodium 0.5 to fix their terrain from showing */
	@Override
	public void setFogOcclusion(boolean occlusionEnabled)
	{
		#if MC_VER >= MC_1_20_1
		try
		{
			if (sodiumPerformanceOptions == null)
			{
				if (isSodiumV5OrLess)
				{
					// sodium 0.5
					
					Class<?> optionsClass = Class.forName("me.jellysquid.mods.sodium.client.gui.SodiumGameOptions");
					Object basicOptions = MethodHandles.lookup().findStatic(
							Class.forName("me.jellysquid.mods.sodium.client.SodiumClientMod"), 
							"options", MethodType.methodType(optionsClass)).invoke();
					sodiumPerformanceOptions = optionsClass.getDeclaredField("performance").get(basicOptions);
					setFogOcclusionMethod = MethodHandles.lookup()
							.findSetter(Class.forName(
											"me.jellysquid.mods.sodium.client.gui.SodiumGameOptions$PerformanceSettings"),
									"useFogOcclusion", boolean.class);
					
					// alternate option if referencing Sodium 0.5 directly
					//me.jellysquid.mods.sodium.client.SodiumClientMod.options().performance.useFogOcclusion = b;
				} 
				else
				{
					// sodium 0.6
					
					Class<?> optionsClass = Class.forName("net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions");
					Object basicOptions = MethodHandles.lookup().findStatic(
							Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod"),
							"options", MethodType.methodType(optionsClass)).invoke();
					sodiumPerformanceOptions = optionsClass.getDeclaredField("performance").get(basicOptions);
					setFogOcclusionMethod = MethodHandles.lookup()
							.findSetter(Class.forName(
											"net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions$PerformanceSettings"),
									"useFogOcclusion", boolean.class);
				}
			}
			
			setFogOcclusionMethod.invoke(sodiumPerformanceOptions, occlusionEnabled);
		}
		catch (Throwable e)
		{
			throw new RuntimeException(e);
		}
		#endif
	}

	
	
	//================//
	// helper methods //
	//================//
	
	private static boolean classPresent(String className)
	{
		try
		{
			Class.forName(className);
			return true;
		}
		catch (ClassNotFoundException e)
		{
			return false;
		}
	}
	
}
