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

package com.seibel.distanthorizons.common.wrappers.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IDimensionTypeWrapper;

import net.minecraft.world.level.dimension.DimensionType;

public class DimensionTypeWrapper implements IDimensionTypeWrapper
{
	private static final ConcurrentMap<String, DimensionTypeWrapper> DIMENSION_WRAPPER_BY_NAME = new ConcurrentHashMap<>();
	private final DimensionType dimensionType;
	
	private final String name;
	
	
	
	//=============//
	// Constructor //
	//=============//
	
	public DimensionTypeWrapper(DimensionType dimensionType)
	{
		this.dimensionType = dimensionType; 
		
		this.name = determineName(dimensionType);
	}
	
	public static DimensionTypeWrapper getDimensionTypeWrapper(DimensionType dimensionType)
	{
		String dimName = determineName(dimensionType);
		
		// check if the dimension has already been wrapped
		if (DIMENSION_WRAPPER_BY_NAME.containsKey(dimName) 
			&& DIMENSION_WRAPPER_BY_NAME.get(dimName) != null)
		{
			return DIMENSION_WRAPPER_BY_NAME.get(dimName);
		}
		
		
		// create the missing wrapper
		DimensionTypeWrapper dimensionTypeWrapper = new DimensionTypeWrapper(dimensionType);
		
		DIMENSION_WRAPPER_BY_NAME.put(dimName, dimensionTypeWrapper);
		return dimensionTypeWrapper;
	}
	private static String determineName(DimensionType dimensionType)
	{
		return dimensionType.effectsLocation().getPath();
	}
	
	public static void clearMap() { DIMENSION_WRAPPER_BY_NAME.clear(); }
	
	
	
	//=================//
	// wrapper methods //
	//=================//
	
	@Override
	public String getName() { return this.name; }
	
	@Override
	public boolean hasCeiling() { return this.dimensionType.hasCeiling(); }
	
	@Override
	public boolean hasSkyLight() { return this.dimensionType.hasSkyLight(); }
	
	@Override
	public Object getWrappedMcObject() { return this.dimensionType; }
	
	@Override
	public boolean isTheEnd() { return this.getName().equalsIgnoreCase("the_end"); }
	
	@Override
	public double getCoordinateScale() { return this.dimensionType.coordinateScale(); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public boolean equals(Object obj)
	{
		if (obj.getClass() != DimensionTypeWrapper.class)
		{
			return false;
		}
		else
		{
			DimensionTypeWrapper other = (DimensionTypeWrapper) obj;
			return other.getName().equals(this.getName());
		}
	}
	
	
	
}
