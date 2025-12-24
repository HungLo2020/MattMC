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

import net.distant_horizons.core.dependencyInjection.SingletonInjector;
import net.distant_horizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.distant_horizons.core.wrapperInterfaces.modAccessor.ISodiumAccessor;
import net.minecraft.client.Minecraft;



public class SodiumAccessor implements ISodiumAccessor
{
	/**
	 * True if sodium 0.5 or less is present. <br>
	 * This field is public because it's also used to check if we need Indium to be present. <br>
	 * We need Indium if Sodium 0.5 or less is present.
	 */
	public static final boolean isSodiumV5OrLess;
	
	
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
		// in newer versions of Sodium this doesn't appear to be an issue so it can probably just be ignored
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
