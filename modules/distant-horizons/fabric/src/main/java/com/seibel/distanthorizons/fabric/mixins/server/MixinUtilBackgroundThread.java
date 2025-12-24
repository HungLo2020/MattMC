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

package com.seibel.distanthorizons.fabric.mixins.server;

import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import net.distant_horizons.core.util.objects.RunOnThisThreadExecutorService;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.Util;

import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * This is needed for DH's world gen so we can run
 * world gen on our own threads instead of using MC thread pools.
 *
 * @see MixinTracingExecutor
 * @see RunOnThisThreadExecutorService
 */
@Mixin(Util.class)
public class MixinUtilBackgroundThread
{
	// replaced with TracingExecutor in MC 1.21.3+
	
	// replaced with TracingExecutor in MC 1.21.3+

	// replaced with TracingExecutor in MC 1.21.3+
	
}
