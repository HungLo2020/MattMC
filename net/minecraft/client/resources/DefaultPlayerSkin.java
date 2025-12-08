package net.minecraft.client.resources;

import net.minecraft.server.profile.PlayerProfile;
import java.util.UUID;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.ClientAsset.ResourceTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

@Environment(EnvType.CLIENT)
public class DefaultPlayerSkin {
	private static final PlayerSkin[] DEFAULT_SKINS = new PlayerSkin[]{
		create("entity/player/slim/alex", PlayerModelType.SLIM),
		create("entity/player/slim/ari", PlayerModelType.SLIM),
		create("entity/player/slim/efe", PlayerModelType.SLIM),
		create("entity/player/slim/kai", PlayerModelType.SLIM),
		create("entity/player/slim/makena", PlayerModelType.SLIM),
		create("entity/player/slim/noor", PlayerModelType.SLIM),
		create("entity/player/slim/steve", PlayerModelType.SLIM),
		create("entity/player/slim/sunny", PlayerModelType.SLIM),
		create("entity/player/slim/zuri", PlayerModelType.SLIM),
		create("entity/player/wide/alex", PlayerModelType.WIDE),
		create("entity/player/wide/ari", PlayerModelType.WIDE),
		create("entity/player/wide/efe", PlayerModelType.WIDE),
		create("entity/player/wide/kai", PlayerModelType.WIDE),
		create("entity/player/wide/makena", PlayerModelType.WIDE),
		create("entity/player/wide/noor", PlayerModelType.WIDE),
		create("entity/player/wide/steve", PlayerModelType.WIDE),
		create("entity/player/wide/sunny", PlayerModelType.WIDE),
		create("entity/player/wide/zuri", PlayerModelType.WIDE)
	};

	public static ResourceLocation getDefaultTexture() {
		return getDefaultSkin().body().texturePath();
	}

	public static PlayerSkin getDefaultSkin() {
		return DEFAULT_SKINS[6];
	}

	public static PlayerSkin get(UUID uUID) {
		return DEFAULT_SKINS[Math.floorMod(uUID.hashCode(), DEFAULT_SKINS.length)];
	}

	public static PlayerSkin get(PlayerProfile playerProfile) {
		return get(gameProfile.getId());
	}

	private static PlayerSkin create(String string, PlayerModelType playerModelType) {
		return new PlayerSkin(new ResourceTexture(ResourceLocation.withDefaultNamespace(string)), null, null, playerModelType, true);
	}
}
