package net.minecraft.client.multiplayer.prediction;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface PredictiveAction {
	Packet<ServerGamePacketListener> predict(int i);
}
