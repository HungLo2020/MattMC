package net.matt.quantize.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public class TeleportRequest {
    private final BlockPos from, to;

    public TeleportRequest(BlockPos from, BlockPos to) {
        this.from = from;
        this.to = to;
    }

    public BlockPos getFrom() {
        return from;
    }

    public BlockPos getTo() {
        return to;
    }

    public static TeleportRequest decode(FriendlyByteBuf buf) {
        return new TeleportRequest(buf.readBlockPos(), buf.readBlockPos());
    }

    public static void encode(TeleportRequest msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.from);
        buf.writeBlockPos(msg.to);
    }
}
