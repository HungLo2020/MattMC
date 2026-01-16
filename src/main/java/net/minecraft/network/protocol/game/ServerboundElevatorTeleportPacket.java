package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundElevatorTeleportPacket implements Packet<ServerGamePacketListener> {
	public static final StreamCodec<FriendlyByteBuf, ServerboundElevatorTeleportPacket> STREAM_CODEC = Packet.codec(
		ServerboundElevatorTeleportPacket::write, ServerboundElevatorTeleportPacket::new
	);
	private final BlockPos from;
	private final BlockPos to;

	public ServerboundElevatorTeleportPacket(BlockPos from, BlockPos to) {
		this.from = from;
		this.to = to;
	}

	private ServerboundElevatorTeleportPacket(FriendlyByteBuf friendlyByteBuf) {
		this.from = friendlyByteBuf.readBlockPos();
		this.to = friendlyByteBuf.readBlockPos();
	}

	private void write(FriendlyByteBuf friendlyByteBuf) {
		friendlyByteBuf.writeBlockPos(this.from);
		friendlyByteBuf.writeBlockPos(this.to);
	}

	@Override
	public PacketType<ServerboundElevatorTeleportPacket> type() {
		return GamePacketTypes.SERVERBOUND_ELEVATOR_TELEPORT;
	}

	public void handle(ServerGamePacketListener serverGamePacketListener) {
		serverGamePacketListener.handleElevatorTeleport(this);
	}

	public BlockPos getFrom() {
		return this.from;
	}

	public BlockPos getTo() {
		return this.to;
	}
}
