package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class RealmsSlotUpdateDto implements ReflectionBasedSerialization {
	@SerializedName("slotId")
	public final int slotId;
	@SerializedName("spawnProtection")
	private final int spawnProtection;
	@SerializedName("forceGameMode")
	private final boolean forceGameMode;
	@SerializedName("difficulty")
	private final int difficulty;
	@SerializedName("gameMode")
	private final int gameMode;
	@SerializedName("slotName")
	private final String slotName;
	@SerializedName("version")
	private final String version;
	@SerializedName("compatibility")
	private final RealmsServer.Compatibility compatibility;
	@SerializedName("worldTemplateId")
	private final long templateId;
	@Nullable
	@SerializedName("worldTemplateImage")
	private final String templateImage;
	@SerializedName("hardcore")
	private final boolean hardcore;

	public RealmsSlotUpdateDto(int i, RealmsWorldOptions realmsWorldOptions, boolean bl) {
		this.slotId = i;
		this.spawnProtection = realmsWorldOptions.spawnProtection;
		this.forceGameMode = realmsWorldOptions.forceGameMode;
		this.difficulty = realmsWorldOptions.difficulty;
		this.gameMode = realmsWorldOptions.gameMode;
		this.slotName = realmsWorldOptions.getSlotName(i);
		this.version = realmsWorldOptions.version;
		this.compatibility = realmsWorldOptions.compatibility;
		this.templateId = realmsWorldOptions.templateId;
		this.templateImage = realmsWorldOptions.templateImage;
		this.hardcore = bl;
	}
}
