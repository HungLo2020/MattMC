package net.voxelmap.fabric;

import net.voxelmap.VoxelConstants;
import net.fabricmc.api.ClientModInitializer;

public class VoxelmapFabricMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        new VoxelmapSettingsChannelHandler();
        new VoxelmapWorldIdChannelHandler();
        VoxelConstants.setEvents(new FabricEvents());
        VoxelConstants.setPacketBridge(new FabricPacketBridge());
        VoxelConstants.setModApiBride(new FabricModApiBridge());
    }
}
