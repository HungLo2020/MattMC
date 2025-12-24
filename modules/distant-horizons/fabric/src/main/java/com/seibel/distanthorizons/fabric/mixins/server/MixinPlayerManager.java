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

import net.distant_horizons.core.logging.DhLoggerBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.distant_horizons.core.logging.DhLogger;

/**
 * Mixin to fire ServerPlayConnectionEvents.JOIN when a player connects to the server.
 * This is required because the Fabric API stub doesn't include the mixin to invoke the event.
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerManager
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	@Shadow
	@Final
	private MinecraftServer server;
	
	/**
	 * Injects into PlayerList.placeNewPlayer to fire the ServerPlayConnectionEvents.JOIN event
	 * when a player finishes joining the server.
	 * 
	 * The injection point is right after the player's game mode is set and before the join message is sent,
	 * which matches when Fabric API fires the JOIN event.
	 */
	@Inject(
		method = "placeNewPlayer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/players/PlayerList;sendPlayerPermissionLevel(Lnet/minecraft/server/level/ServerPlayer;)V"
		)
	)
	private void onPlayerJoin(Connection connection, ServerPlayer player, CommonListenerCookie arg, CallbackInfo ci)
	{
		//LOGGER.info("[DH-PLAYER-JOIN-MIXIN] ========== Player joining server ==========");
		//LOGGER.info("[DH-PLAYER-JOIN-MIXIN] Player: " + player.getName().getString());
		//LOGGER.info("[DH-PLAYER-JOIN-MIXIN] Thread: " + Thread.currentThread().getName());
		//LOGGER.info("[DH-PLAYER-JOIN-MIXIN] Firing ServerPlayConnectionEvents.JOIN event...");
		
		try
		{
			// Create a simple PacketSender implementation
			PacketSender sender = new PacketSender() {
				@Override
				public void sendPacket(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
					// Minimal implementation - DH doesn't use packet sending in event handlers
				}
				
				@Override
				public void sendPacket(net.minecraft.resources.ResourceLocation channel, net.minecraft.network.FriendlyByteBuf buf) {
					// Minimal implementation - DH doesn't use packet sending in event handlers
				}
			};
			
			// Fire the JOIN event
			ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(player, sender, this.server);
			//LOGGER.info("[DH-PLAYER-JOIN-MIXIN] ServerPlayConnectionEvents.JOIN event fired successfully");
		}
		catch (Exception e)
		{
			//LOGGER.error("[DH-PLAYER-JOIN-MIXIN] ERROR firing JOIN event", e);
		}
	}
}
