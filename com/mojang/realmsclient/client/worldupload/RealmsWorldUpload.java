package com.mojang.realmsclient.client.worldupload;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.FileUpload;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsSlot;
import com.mojang.realmsclient.dto.UploadInfo;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.exception.RetryCallException;
import com.mojang.realmsclient.gui.screens.UploadResult;
import com.mojang.realmsclient.util.UploadTokenCache;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.User;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsWorldUpload {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int UPLOAD_RETRIES = 20;
	private final RealmsClient client = RealmsClient.getOrCreate();
	private final Path worldFolder;
	private final RealmsSlot realmsSlot;
	private final User user;
	private final long realmId;
	private final RealmsWorldUploadStatusTracker statusCallback;
	private volatile boolean cancelled;
	@Nullable
	private FileUpload uploadTask;

	public RealmsWorldUpload(Path path, RealmsSlot realmsSlot, User user, long l, RealmsWorldUploadStatusTracker realmsWorldUploadStatusTracker) {
		this.worldFolder = path;
		this.realmsSlot = realmsSlot;
		this.user = user;
		this.realmId = l;
		this.statusCallback = realmsWorldUploadStatusTracker;
	}

	public CompletableFuture<?> packAndUpload() {
		return CompletableFuture.runAsync(
			() -> {
				File file = null;

				try {
					UploadInfo uploadInfo = this.requestUploadInfoWithRetries();
					file = RealmsUploadWorldPacker.pack(this.worldFolder, () -> this.cancelled);
					this.statusCallback.setUploading();
					FileUpload fileUpload = new FileUpload(
						file,
						this.realmId,
						this.realmsSlot.slotId,
						uploadInfo,
						this.user,
						SharedConstants.getCurrentVersion().name(),
						this.realmsSlot.options.version,
						this.statusCallback.getUploadStatus()
					);
					this.uploadTask = fileUpload;
					UploadResult uploadResult = fileUpload.upload();
					String string = uploadResult.getSimplifiedErrorMessage();
					if (string != null) {
						throw new RealmsUploadFailedException(string);
					}

					UploadTokenCache.invalidate(this.realmId);
					this.client.updateSlot(this.realmId, this.realmsSlot.slotId, this.realmsSlot.options, this.realmsSlot.settings);
				} catch (IOException var11) {
					throw new RealmsUploadFailedException(var11.getMessage());
				} catch (RealmsServiceException var12) {
					throw new RealmsUploadFailedException(var12.realmsError.errorMessage());
				} catch (CancellationException | InterruptedException var13) {
					throw new RealmsUploadCanceledException();
				} finally {
					if (file != null) {
						LOGGER.debug("Deleting file {}", file.getAbsolutePath());
						file.delete();
					}
				}
			},
			Util.backgroundExecutor()
		);
	}

	public void cancel() {
		this.cancelled = true;
		if (this.uploadTask != null) {
			this.uploadTask.cancel();
			this.uploadTask = null;
		}
	}

	private UploadInfo requestUploadInfoWithRetries() throws RealmsServiceException, InterruptedException {
		for (int i = 0; i < 20; i++) {
			try {
				UploadInfo uploadInfo = this.client.requestUploadInfo(this.realmId);
				if (this.cancelled) {
					throw new RealmsUploadCanceledException();
				}

				if (uploadInfo != null) {
					if (!uploadInfo.isWorldClosed()) {
						throw new RealmsUploadWorldNotClosedException();
					}

					return uploadInfo;
				}
			} catch (RetryCallException var3) {
				Thread.sleep(var3.delaySeconds * 1000L);
			}
		}

		throw new RealmsUploadWorldNotClosedException();
	}
}
