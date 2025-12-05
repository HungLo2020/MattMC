package com.mojang.realmsclient.dto;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import com.mojang.util.UUIDTypeAdapter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsServer extends ValueObject implements ReflectionBasedSerialization {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int NO_VALUE = -1;
	public static final Component WORLD_CLOSED_COMPONENT = Component.translatable("mco.play.button.realm.closed");
	@SerializedName("id")
	public long id = -1L;
	@Nullable
	@SerializedName("remoteSubscriptionId")
	public String remoteSubscriptionId;
	@Nullable
	@SerializedName("name")
	public String name;
	@SerializedName("motd")
	public String motd = "";
	@SerializedName("state")
	public RealmsServer.State state = RealmsServer.State.CLOSED;
	@Nullable
	@SerializedName("owner")
	public String owner;
	@SerializedName("ownerUUID")
	@JsonAdapter(UUIDTypeAdapter.class)
	public UUID ownerUUID = Util.NIL_UUID;
	@SerializedName("players")
	public List<PlayerInfo> players = Lists.<PlayerInfo>newArrayList();
	@SerializedName("slots")
	private List<RealmsSlot> slotList = createEmptySlots();
	@Exclude
	public Map<Integer, RealmsSlot> slots = new HashMap();
	@SerializedName("expired")
	public boolean expired;
	@SerializedName("expiredTrial")
	public boolean expiredTrial = false;
	@SerializedName("daysLeft")
	public int daysLeft;
	@SerializedName("worldType")
	public RealmsServer.WorldType worldType = RealmsServer.WorldType.NORMAL;
	@SerializedName("isHardcore")
	public boolean isHardcore = false;
	@SerializedName("gameMode")
	public int gameMode = -1;
	@SerializedName("activeSlot")
	public int activeSlot = -1;
	@Nullable
	@SerializedName("minigameName")
	public String minigameName;
	@SerializedName("minigameId")
	public int minigameId = -1;
	@Nullable
	@SerializedName("minigameImage")
	public String minigameImage;
	@SerializedName("parentWorldId")
	public long parentRealmId = -1L;
	@Nullable
	@SerializedName("parentWorldName")
	public String parentWorldName;
	@SerializedName("activeVersion")
	public String activeVersion = "";
	@SerializedName("compatibility")
	public RealmsServer.Compatibility compatibility = RealmsServer.Compatibility.UNVERIFIABLE;
	@Nullable
	@SerializedName("regionSelectionPreference")
	public RegionSelectionPreferenceDto regionSelectionPreference;

	public String getDescription() {
		return this.motd;
	}

	@Nullable
	public String getName() {
		return this.name;
	}

	@Nullable
	public String getMinigameName() {
		return this.minigameName;
	}

	public void setName(String string) {
		this.name = string;
	}

	public void setDescription(String string) {
		this.motd = string;
	}

	public static RealmsServer parse(GuardedSerializer guardedSerializer, String string) {
		try {
			RealmsServer realmsServer = guardedSerializer.fromJson(string, RealmsServer.class);
			if (realmsServer == null) {
				LOGGER.error("Could not parse McoServer: {}", string);
				return new RealmsServer();
			} else {
				finalize(realmsServer);
				return realmsServer;
			}
		} catch (Exception var3) {
			LOGGER.error("Could not parse McoServer: {}", var3.getMessage());
			return new RealmsServer();
		}
	}

	public static void finalize(RealmsServer realmsServer) {
		if (realmsServer.players == null) {
			realmsServer.players = Lists.<PlayerInfo>newArrayList();
		}

		if (realmsServer.slotList == null) {
			realmsServer.slotList = createEmptySlots();
		}

		if (realmsServer.slots == null) {
			realmsServer.slots = new HashMap();
		}

		if (realmsServer.worldType == null) {
			realmsServer.worldType = RealmsServer.WorldType.NORMAL;
		}

		if (realmsServer.activeVersion == null) {
			realmsServer.activeVersion = "";
		}

		if (realmsServer.compatibility == null) {
			realmsServer.compatibility = RealmsServer.Compatibility.UNVERIFIABLE;
		}

		if (realmsServer.regionSelectionPreference == null) {
			realmsServer.regionSelectionPreference = RegionSelectionPreferenceDto.DEFAULT;
		}

		sortInvited(realmsServer);
		finalizeSlots(realmsServer);
	}

	private static void sortInvited(RealmsServer realmsServer) {
		realmsServer.players
			.sort(
				(playerInfo, playerInfo2) -> ComparisonChain.start()
					.compareFalseFirst(playerInfo2.getAccepted(), playerInfo.getAccepted())
					.compare(playerInfo.getName().toLowerCase(Locale.ROOT), playerInfo2.getName().toLowerCase(Locale.ROOT))
					.result()
			);
	}

	private static void finalizeSlots(RealmsServer realmsServer) {
		realmsServer.slotList.forEach(realmsSlot -> realmsServer.slots.put(realmsSlot.slotId, realmsSlot));

		for (int i = 1; i <= 3; i++) {
			if (!realmsServer.slots.containsKey(i)) {
				realmsServer.slots.put(i, RealmsSlot.defaults(i));
			}
		}
	}

	private static List<RealmsSlot> createEmptySlots() {
		List<RealmsSlot> list = new ArrayList();
		list.add(RealmsSlot.defaults(1));
		list.add(RealmsSlot.defaults(2));
		list.add(RealmsSlot.defaults(3));
		return list;
	}

	public boolean isCompatible() {
		return this.compatibility.isCompatible();
	}

	public boolean needsUpgrade() {
		return this.compatibility.needsUpgrade();
	}

	public boolean needsDowngrade() {
		return this.compatibility.needsDowngrade();
	}

	public boolean shouldPlayButtonBeActive() {
		boolean bl = !this.expired && this.state == RealmsServer.State.OPEN;
		return bl && (this.isCompatible() || this.needsUpgrade() || this.isSelfOwnedServer());
	}

	private boolean isSelfOwnedServer() {
		return Minecraft.getInstance().isLocalPlayer(this.ownerUUID);
	}

	public int hashCode() {
		return Objects.hash(new Object[]{this.id, this.name, this.motd, this.state, this.owner, this.expired});
	}

	public boolean equals(Object object) {
		if (object == null) {
			return false;
		} else if (object == this) {
			return true;
		} else if (object.getClass() != this.getClass()) {
			return false;
		} else {
			RealmsServer realmsServer = (RealmsServer)object;
			return new EqualsBuilder()
				.append(this.id, realmsServer.id)
				.append(this.name, realmsServer.name)
				.append(this.motd, realmsServer.motd)
				.append(this.state, realmsServer.state)
				.append(this.owner, realmsServer.owner)
				.append(this.expired, realmsServer.expired)
				.append(this.worldType, this.worldType)
				.isEquals();
		}
	}

	public RealmsServer clone() {
		RealmsServer realmsServer = new RealmsServer();
		realmsServer.id = this.id;
		realmsServer.remoteSubscriptionId = this.remoteSubscriptionId;
		realmsServer.name = this.name;
		realmsServer.motd = this.motd;
		realmsServer.state = this.state;
		realmsServer.owner = this.owner;
		realmsServer.players = this.players;
		realmsServer.slotList = this.slotList.stream().map(RealmsSlot::clone).toList();
		realmsServer.slots = this.cloneSlots(this.slots);
		realmsServer.expired = this.expired;
		realmsServer.expiredTrial = this.expiredTrial;
		realmsServer.daysLeft = this.daysLeft;
		realmsServer.worldType = this.worldType;
		realmsServer.isHardcore = this.isHardcore;
		realmsServer.gameMode = this.gameMode;
		realmsServer.ownerUUID = this.ownerUUID;
		realmsServer.minigameName = this.minigameName;
		realmsServer.activeSlot = this.activeSlot;
		realmsServer.minigameId = this.minigameId;
		realmsServer.minigameImage = this.minigameImage;
		realmsServer.parentWorldName = this.parentWorldName;
		realmsServer.parentRealmId = this.parentRealmId;
		realmsServer.activeVersion = this.activeVersion;
		realmsServer.compatibility = this.compatibility;
		realmsServer.regionSelectionPreference = this.regionSelectionPreference != null ? this.regionSelectionPreference.clone() : null;
		return realmsServer;
	}

	public Map<Integer, RealmsSlot> cloneSlots(Map<Integer, RealmsSlot> map) {
		Map<Integer, RealmsSlot> map2 = Maps.<Integer, RealmsSlot>newHashMap();

		for (Entry<Integer, RealmsSlot> entry : map.entrySet()) {
			map2.put(
				(Integer)entry.getKey(), new RealmsSlot((Integer)entry.getKey(), ((RealmsSlot)entry.getValue()).options.clone(), ((RealmsSlot)entry.getValue()).settings)
			);
		}

		return map2;
	}

	public boolean isSnapshotRealm() {
		return this.parentRealmId != -1L;
	}

	public boolean isMinigameActive() {
		return this.worldType == RealmsServer.WorldType.MINIGAME;
	}

	public String getWorldName(int i) {
		return this.name == null
			? ((RealmsSlot)this.slots.get(i)).options.getSlotName(i)
			: this.name + " (" + ((RealmsSlot)this.slots.get(i)).options.getSlotName(i) + ")";
	}

	public ServerData toServerData(String string) {
		return new ServerData((String)Objects.requireNonNullElse(this.name, "unknown server"), string, ServerData.Type.REALM);
	}

	@Environment(EnvType.CLIENT)
	public static enum Compatibility {
		UNVERIFIABLE,
		INCOMPATIBLE,
		RELEASE_TYPE_INCOMPATIBLE,
		NEEDS_DOWNGRADE,
		NEEDS_UPGRADE,
		COMPATIBLE;

		public boolean isCompatible() {
			return this == COMPATIBLE;
		}

		public boolean needsUpgrade() {
			return this == NEEDS_UPGRADE;
		}

		public boolean needsDowngrade() {
			return this == NEEDS_DOWNGRADE;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class McoServerComparator implements Comparator<RealmsServer> {
		private final String refOwner;

		public McoServerComparator(String string) {
			this.refOwner = string;
		}

		public int compare(RealmsServer realmsServer, RealmsServer realmsServer2) {
			return ComparisonChain.start()
				.compareTrueFirst(realmsServer.isSnapshotRealm(), realmsServer2.isSnapshotRealm())
				.compareTrueFirst(realmsServer.state == RealmsServer.State.UNINITIALIZED, realmsServer2.state == RealmsServer.State.UNINITIALIZED)
				.compareTrueFirst(realmsServer.expiredTrial, realmsServer2.expiredTrial)
				.compareTrueFirst(Objects.equals(realmsServer.owner, this.refOwner), Objects.equals(realmsServer2.owner, this.refOwner))
				.compareFalseFirst(realmsServer.expired, realmsServer2.expired)
				.compareTrueFirst(realmsServer.state == RealmsServer.State.OPEN, realmsServer2.state == RealmsServer.State.OPEN)
				.compare(realmsServer.id, realmsServer2.id)
				.result();
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum State {
		CLOSED,
		OPEN,
		UNINITIALIZED;
	}

	@Environment(EnvType.CLIENT)
	public static enum WorldType {
		NORMAL,
		MINIGAME,
		ADVENTUREMAP,
		EXPERIENCE,
		INSPIRATION;
	}
}
