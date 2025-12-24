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


import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;

public class IrisAccessor implements IIrisAccessor
{
	private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
	private Object irisApiInstance;
	
	public IrisAccessor() {
		try {
			Class<?> irisApiClass = Class.forName(IRIS_API_CLASS);
			java.lang.reflect.Method getInstanceMethod = irisApiClass.getMethod("getInstance");
			irisApiInstance = getInstanceMethod.invoke(null);
		} catch (Exception e) {
			irisApiInstance = null;
		}
	}
	
	@Override
	public String getModName() { return "iris"; }
	
	@Override
	public boolean isShaderPackInUse() {
		if (irisApiInstance == null) return false;
		try {
			java.lang.reflect.Method method = irisApiInstance.getClass().getMethod("isShaderPackInUse");
			return (Boolean) method.invoke(irisApiInstance);
		} catch (Exception e) {
			return false;
		}
	}
	
	@Override
	public boolean isRenderingShadowPass() {
		if (irisApiInstance == null) return false;
		try {
			java.lang.reflect.Method method = irisApiInstance.getClass().getMethod("isRenderingShadowPass");
			return (Boolean) method.invoke(irisApiInstance);
		} catch (Exception e) {
			return false;
		}
	}
	
}

