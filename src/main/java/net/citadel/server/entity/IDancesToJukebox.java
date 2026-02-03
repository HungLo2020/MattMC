package net.citadel.server.entity;

import net.minecraft.core.BlockPos;
// Removed NeoForge import - TODO: implement with hooks/packets

public interface IDancesToJukebox {

    void setDancing(boolean dancing);
    void setJukeboxPos(BlockPos pos);

    default void onClientPlayMusicDisc(int entityId, BlockPos pos, boolean dancing) {
        // TODO: Implement network packet sending without NeoForge
        // PacketDistributor.sendToServer(new DanceJukeboxMessage(entityId, dancing, pos));
        this.setDancing(dancing);
        if (dancing) {
            this.setJukeboxPos(pos);
        } else {
            this.setJukeboxPos(null);
        }
    }
}
