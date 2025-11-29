package net.matt.quantize.modules.entities;

import net.matt.quantize.Quantize;
import net.matt.quantize.network.NetworkHandler;
import net.matt.quantize.network.packet.DanceJukeboxMessage;
import net.minecraft.core.BlockPos;

public interface IDancesToJukebox {

    void setDancing(boolean dancing);

    void setJukeboxPos(BlockPos pos);

    default void onClientPlayMusicDisc(int entityId, BlockPos pos, boolean dancing) {
        NetworkHandler.sendMSGToServer(new DanceJukeboxMessage(entityId, dancing, pos));
        this.setDancing(dancing);
        if (dancing) {
            this.setJukeboxPos(pos);
        } else {
            this.setJukeboxPos(null);
        }
    }
}
