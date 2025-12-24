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

package com.seibel.distanthorizons.common.wrappers;

import net.distant_horizons.common.wrappers.gui.ClassicConfigGUI;
import net.distant_horizons.common.wrappers.gui.LangWrapper;
import net.distant_horizons.common.wrappers.level.KeyedClientLevelManager;
import net.distant_horizons.common.wrappers.minecraft.MinecraftGLWrapper;
import net.distant_horizons.common.wrappers.minecraft.MinecraftServerWrapper;
import net.distant_horizons.core.level.IKeyedClientLevelManager;
import net.distant_horizons.core.wrapperInterfaces.config.IConfigGui;
import net.distant_horizons.core.wrapperInterfaces.config.ILangWrapper;
import net.distant_horizons.common.wrappers.minecraft.MinecraftClientWrapper;
import net.distant_horizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import net.distant_horizons.core.dependencyInjection.SingletonInjector;
import net.distant_horizons.core.wrapperInterfaces.IVersionConstants;
import net.distant_horizons.core.wrapperInterfaces.IWrapperFactory;
import net.distant_horizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import net.distant_horizons.core.wrapperInterfaces.minecraft.IMinecraftGLWrapper;
import net.distant_horizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import net.distant_horizons.core.wrapperInterfaces.minecraft.IMinecraftSharedWrapper;

/**
 * Binds all necessary dependencies, so we
 * can access them in Core. <br>
 * This needs to be called before any Core classes
 * are loaded.
 *
 * @author James Seibel
 * @author Ran
 * @version 12-1-2021
 */
public class DependencySetup
{
	
	public static void createSharedBindings()
	{
		SingletonInjector.INSTANCE.bind(ILangWrapper.class, LangWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IVersionConstants.class, VersionConstants.INSTANCE);
		SingletonInjector.INSTANCE.bind(IWrapperFactory.class, WrapperFactory.INSTANCE);
		SingletonInjector.INSTANCE.bind(IKeyedClientLevelManager.class, KeyedClientLevelManager.INSTANCE);
	}
	
	public static void createServerBindings()
	{ SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class, MinecraftServerWrapper.INSTANCE); }
	
	public static void createClientBindings()
	{
		SingletonInjector.INSTANCE.bind(IMinecraftClientWrapper.class, MinecraftClientWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMinecraftSharedWrapper.class, MinecraftClientWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMinecraftRenderWrapper.class, MinecraftRenderWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IMinecraftGLWrapper.class, MinecraftGLWrapper.INSTANCE);
		SingletonInjector.INSTANCE.bind(IConfigGui.class, ClassicConfigGUI.CONFIG_CORE_INTERFACE);
	}
	
}
