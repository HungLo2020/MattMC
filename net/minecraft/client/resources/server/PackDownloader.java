package net.minecraft.client.resources.server;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.server.packs.DownloadQueue.BatchResult;
import net.minecraft.server.packs.DownloadQueue.DownloadRequest;

@Environment(EnvType.CLIENT)
public interface PackDownloader {
	void download(Map<UUID, DownloadRequest> map, Consumer<BatchResult> consumer);
}
