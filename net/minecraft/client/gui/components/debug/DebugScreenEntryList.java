package net.minecraft.client.gui.components.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StrictJsonParser;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class DebugScreenEntryList {
	private static final Logger LOGGER = LogUtils.getLogger();
	private Map<ResourceLocation, DebugScreenEntryStatus> allStatuses;
	private final List<ResourceLocation> currentlyEnabled = new ArrayList();
	private boolean isF3Visible = false;
	@Nullable
	private DebugScreenProfile profile;
	private final File debugProfileFile;
	private long currentlyEnabledVersion;

	public DebugScreenEntryList(File file) {
		this.debugProfileFile = new File(file, "debug-profile.json");
		this.load();
	}

	public void load() {
		try {
			if (!this.debugProfileFile.isFile()) {
				this.loadDefaultProfile();
				this.rebuildCurrentList();
				return;
			}

			String string = FileUtils.readFileToString(this.debugProfileFile);
			Dynamic<JsonElement> dynamic = new Dynamic<>(JsonOps.INSTANCE, StrictJsonParser.parse(string));
			DataResult<DebugScreenEntryList.SerializedOptions> dataResult = DebugScreenEntryList.SerializedOptions.CODEC.parse(dynamic);
			DebugScreenEntryList.SerializedOptions serializedOptions = dataResult.getOrThrow(
				stringx -> new IOException("Could not parse debug profile JSON: " + stringx)
			);
			if (serializedOptions.profile().isPresent()) {
				this.loadProfile((DebugScreenProfile)serializedOptions.profile().get());
			} else {
				this.allStatuses = new HashMap();
				if (serializedOptions.custom().isPresent()) {
					this.allStatuses.putAll((Map)serializedOptions.custom().get());
				}

				this.profile = null;
			}
		} catch (JsonSyntaxException | IOException var5) {
			LOGGER.error("Couldn't read debug profile file {}, resetting to default", this.debugProfileFile, var5);
			this.loadDefaultProfile();
			this.save();
		}

		this.rebuildCurrentList();
	}

	public void loadProfile(DebugScreenProfile debugScreenProfile) {
		this.profile = debugScreenProfile;
		Map<ResourceLocation, DebugScreenEntryStatus> map = (Map<ResourceLocation, DebugScreenEntryStatus>)DebugScreenEntries.PROFILES.get(debugScreenProfile);
		this.allStatuses = new HashMap(map);
		this.rebuildCurrentList();
	}

	private void loadDefaultProfile() {
		this.profile = DebugScreenProfile.DEFAULT;
		this.allStatuses = new HashMap((Map)DebugScreenEntries.PROFILES.get(DebugScreenProfile.DEFAULT));
	}

	public DebugScreenEntryStatus getStatus(ResourceLocation resourceLocation) {
		DebugScreenEntryStatus debugScreenEntryStatus = (DebugScreenEntryStatus)this.allStatuses.get(resourceLocation);
		return debugScreenEntryStatus == null ? DebugScreenEntryStatus.NEVER : debugScreenEntryStatus;
	}

	public boolean isCurrentlyEnabled(ResourceLocation resourceLocation) {
		return this.currentlyEnabled.contains(resourceLocation);
	}

	public void setStatus(ResourceLocation resourceLocation, DebugScreenEntryStatus debugScreenEntryStatus) {
		this.profile = null;
		this.allStatuses.put(resourceLocation, debugScreenEntryStatus);
		this.rebuildCurrentList();
		this.save();
	}

	public boolean toggleStatus(ResourceLocation resourceLocation) {
		switch ((DebugScreenEntryStatus)this.allStatuses.get(resourceLocation)) {
			case ALWAYS_ON:
				this.setStatus(resourceLocation, DebugScreenEntryStatus.NEVER);
				return false;
			case IN_F3:
				if (this.isF3Visible) {
					this.setStatus(resourceLocation, DebugScreenEntryStatus.NEVER);
					return false;
				}

				this.setStatus(resourceLocation, DebugScreenEntryStatus.ALWAYS_ON);
				return true;
			case NEVER:
				if (this.isF3Visible) {
					this.setStatus(resourceLocation, DebugScreenEntryStatus.IN_F3);
				} else {
					this.setStatus(resourceLocation, DebugScreenEntryStatus.ALWAYS_ON);
				}

				return true;
			case null:
			default:
				this.setStatus(resourceLocation, DebugScreenEntryStatus.ALWAYS_ON);
				return true;
		}
	}

	public Collection<ResourceLocation> getCurrentlyEnabled() {
		return this.currentlyEnabled;
	}

	public void toggleF3Visible() {
		this.setF3Visible(!this.isF3Visible);
	}

	public void setF3Visible(boolean bl) {
		if (this.isF3Visible != bl) {
			this.isF3Visible = bl;
			this.rebuildCurrentList();
		}
	}

	public boolean isF3Visible() {
		return this.isF3Visible;
	}

	public void rebuildCurrentList() {
		this.currentlyEnabled.clear();
		boolean bl = Minecraft.getInstance().showOnlyReducedInfo();

		for (Entry<ResourceLocation, DebugScreenEntryStatus> entry : this.allStatuses.entrySet()) {
			if (entry.getValue() == DebugScreenEntryStatus.ALWAYS_ON || this.isF3Visible && entry.getValue() == DebugScreenEntryStatus.IN_F3) {
				DebugScreenEntry debugScreenEntry = DebugScreenEntries.getEntry((ResourceLocation)entry.getKey());
				if (debugScreenEntry != null && debugScreenEntry.isAllowed(bl)) {
					this.currentlyEnabled.add((ResourceLocation)entry.getKey());
				}
			}
		}

		this.currentlyEnabled.sort(ResourceLocation::compareTo);
		this.currentlyEnabledVersion++;
	}

	public long getCurrentlyEnabledVersion() {
		return this.currentlyEnabledVersion;
	}

	public boolean isUsingProfile(DebugScreenProfile debugScreenProfile) {
		return this.profile == debugScreenProfile;
	}

	public void save() {
		DebugScreenEntryList.SerializedOptions serializedOptions = new DebugScreenEntryList.SerializedOptions(
			Optional.ofNullable(this.profile), this.profile == null ? Optional.of(this.allStatuses) : Optional.empty()
		);

		try {
			FileUtils.writeStringToFile(
				this.debugProfileFile, DebugScreenEntryList.SerializedOptions.CODEC.encodeStart(JsonOps.INSTANCE, serializedOptions).getOrThrow().toString()
			);
		} catch (IOException var3) {
			LOGGER.error("Failed to save debug profile file {}", this.debugProfileFile, var3);
		}
	}

	@Environment(EnvType.CLIENT)
	record SerializedOptions(Optional<DebugScreenProfile> profile, Optional<Map<ResourceLocation, DebugScreenEntryStatus>> custom) {
		private static final Codec<Map<ResourceLocation, DebugScreenEntryStatus>> CUSTOM_ENTRIES_CODEC = Codec.unboundedMap(
			ResourceLocation.CODEC, DebugScreenEntryStatus.CODEC
		);
		public static final Codec<DebugScreenEntryList.SerializedOptions> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					DebugScreenProfile.CODEC.optionalFieldOf("profile").forGetter(DebugScreenEntryList.SerializedOptions::profile),
					CUSTOM_ENTRIES_CODEC.optionalFieldOf("custom").forGetter(DebugScreenEntryList.SerializedOptions::custom)
				)
				.apply(instance, DebugScreenEntryList.SerializedOptions::new)
		);
	}
}
