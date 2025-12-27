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

package com.seibel.distanthorizons.fabric.hooks;

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.hooks.MinecraftLevelHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Hook implementation for Minecraft level updates.
 * Replaces MixinMinecraft.updateLevelInEngines.
 */
public class DhMinecraftLevelHook implements MinecraftLevelHooks {
    private ClientLevel lastLevel = null;

    @Override
    public void onLevelUpdateInEngines(@Nullable ClientLevel newLevel) {
        if (this.lastLevel != null && newLevel != this.lastLevel) {
            ClientApi.INSTANCE.clientLevelUnloadEvent(ClientLevelWrapper.getWrapper(this.lastLevel));
        }

        if (newLevel != null) {
            ClientApi.INSTANCE.clientLevelLoadEvent(ClientLevelWrapper.getWrapper(newLevel, true));
        }

        this.lastLevel = newLevel;
    }
}
