package net.minecraft.client.multiplayer;

import net.minecraft.server.profile.PlayerProfile;
import java.util.function.Supplier;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignedMessageValidator;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PlayerInfo {
	private final PlayerProfile profile;
	@Nullable
	Supplier<PlayerSkin> skinLookup; // Package-private for ClientPacketListener access
	private GameType gameMode = GameType.DEFAULT_MODE;
	private int latency;
	@Nullable
	private Component tabListDisplayName;
	private boolean showHat = true;
	@Nullable
	private RemoteChatSession chatSession;
	private SignedMessageValidator messageValidator;
	private int tabListOrder;

	public PlayerInfo(PlayerProfile playerProfile, boolean bl) {
		this.profile = playerProfile;
		this.messageValidator = fallbackMessageValidator(bl);
	}

	private static Supplier<PlayerSkin> createSkinLookup(PlayerProfile playerProfile) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean bl = !minecraft.isLocalPlayer(playerProfile.id());
		return minecraft.getSkinManager().createLookup(playerProfile, bl);
	}

	public PlayerProfile getProfile() {
		return this.profile;
	}

	@Nullable
	public RemoteChatSession getChatSession() {
		return this.chatSession;
	}

	public SignedMessageValidator getMessageValidator() {
		return this.messageValidator;
	}

	public boolean hasVerifiableChat() {
		return this.chatSession != null;
	}

	protected void setChatSession(RemoteChatSession remoteChatSession) {
		this.chatSession = remoteChatSession;
		this.messageValidator = remoteChatSession.createMessageValidator(ProfilePublicKey.EXPIRY_GRACE_PERIOD);
	}

	protected void clearChatSession(boolean bl) {
		this.chatSession = null;
		this.messageValidator = fallbackMessageValidator(bl);
	}

	private static SignedMessageValidator fallbackMessageValidator(boolean bl) {
		return bl ? SignedMessageValidator.REJECT_ALL : SignedMessageValidator.ACCEPT_UNSIGNED;
	}

	public GameType getGameMode() {
		return this.gameMode;
	}

	protected void setGameMode(GameType gameType) {
		this.gameMode = gameType;
	}

	public int getLatency() {
		return this.latency;
	}

	protected void setLatency(int i) {
		this.latency = i;
	}

	public PlayerSkin getSkin() {
		// First check if there's a custom skin in the client cache
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener connection = minecraft.getConnection();
		if (connection != null) {
			ClientSkinCache skinCache = connection.getClientSkinCache();
			if (skinCache != null && skinCache.hasSkin(this.profile.id())) {
				PlayerSkin cachedSkin = skinCache.getSkin(this.profile.id());
				if (cachedSkin != null) {
					return cachedSkin;
				}
			}
		}
		
		// Fall back to default skin lookup
		if (this.skinLookup == null) {
			this.skinLookup = createSkinLookup(this.profile);
		}

		return (PlayerSkin)this.skinLookup.get();
	}

	@Nullable
	public PlayerTeam getTeam() {
		return Minecraft.getInstance().level.getScoreboard().getPlayersTeam(this.getProfile().name());
	}

	public void setTabListDisplayName(@Nullable Component component) {
		this.tabListDisplayName = component;
	}

	@Nullable
	public Component getTabListDisplayName() {
		return this.tabListDisplayName;
	}

	public void setShowHat(boolean bl) {
		this.showHat = bl;
	}

	public boolean showHat() {
		return this.showHat;
	}

	public void setTabListOrder(int i) {
		this.tabListOrder = i;
	}

	public int getTabListOrder() {
		return this.tabListOrder;
	}
}
