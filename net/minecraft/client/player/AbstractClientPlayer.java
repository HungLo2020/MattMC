package net.minecraft.client.player;

import net.minecraft.server.profile.PlayerProfile;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Parrot.Variant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractClientPlayer extends Player implements ClientAvatarEntity {
	@Nullable
	private PlayerInfo playerInfo;
	private final boolean showExtraEars;
	private final ClientAvatarState clientAvatarState = new ClientAvatarState();

	public AbstractClientPlayer(ClientLevel clientLevel, PlayerProfile playerProfile) {
		super(clientLevel, gameProfile);
		this.showExtraEars = "deadmau5".equals(this.getGameProfile().getName());
	}

	@Nullable
	public GameType gameMode() {
		PlayerInfo playerInfo = this.getPlayerInfo();
		return playerInfo != null ? playerInfo.getGameMode() : null;
	}

	@Nullable
	protected PlayerInfo getPlayerInfo() {
		if (this.playerInfo == null) {
			this.playerInfo = Minecraft.getInstance().getConnection().getPlayerInfo(this.getUUID());
		}

		return this.playerInfo;
	}

	public void tick() {
		this.clientAvatarState.tick(this.position(), this.getDeltaMovement());
		super.tick();
	}

	protected void addWalkedDistance(float f) {
		this.clientAvatarState.addWalkDistance(f);
	}

	@Override
	public ClientAvatarState avatarState() {
		return this.clientAvatarState;
	}

	@Nullable
	@Override
	public Component belowNameDisplay() {
		Scoreboard scoreboard = this.level().getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);
		if (objective != null) {
			ReadOnlyScoreInfo readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(this, objective);
			Component component = ReadOnlyScoreInfo.safeFormatValue(readOnlyScoreInfo, objective.numberFormatOrDefault(StyledFormat.NO_STYLE));
			return Component.empty().append(component).append(CommonComponents.SPACE).append(objective.getDisplayName());
		} else {
			return null;
		}
	}

	@Override
	public PlayerSkin getSkin() {
		PlayerInfo playerInfo = this.getPlayerInfo();
		return playerInfo == null ? DefaultPlayerSkin.get(this.getUUID()) : playerInfo.getSkin();
	}

	@Nullable
	@Override
	public Variant getParrotVariantOnShoulder(boolean bl) {
		return (Variant)(bl ? this.getShoulderParrotLeft() : this.getShoulderParrotRight()).orElse(null);
	}

	public void rideTick() {
		super.rideTick();
		this.avatarState().resetBob();
	}

	public void aiStep() {
		this.updateBob();
		super.aiStep();
	}

	protected void updateBob() {
		float f;
		if (this.onGround() && !this.isDeadOrDying() && !this.isSwimming()) {
			f = Math.min(0.1F, (float)this.getDeltaMovement().horizontalDistance());
		} else {
			f = 0.0F;
		}

		this.avatarState().updateBob(f);
	}

	public float getFieldOfViewModifier(boolean bl, float f) {
		float g = 1.0F;
		if (this.getAbilities().flying) {
			g *= 1.1F;
		}

		float h = this.getAbilities().getWalkingSpeed();
		if (h != 0.0F) {
			float i = (float)this.getAttributeValue(Attributes.MOVEMENT_SPEED) / h;
			g *= (i + 1.0F) / 2.0F;
		}

		if (this.isUsingItem()) {
			if (this.getUseItem().is(Items.BOW)) {
				float i = Math.min(this.getTicksUsingItem() / 20.0F, 1.0F);
				g *= 1.0F - Mth.square(i) * 0.15F;
			} else if (bl && this.isScoping()) {
				return 0.1F;
			}
		}

		return Mth.lerp(f, 1.0F, g);
	}

	@Override
	public boolean showExtraEars() {
		return this.showExtraEars;
	}
}
