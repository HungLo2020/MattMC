package net.minecraft.server.bossevents;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class CustomBossEvents {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Codec<Map<ResourceLocation, CustomBossEvent.Packed>> EVENTS_CODEC = Codec.unboundedMap(
		ResourceLocation.CODEC, CustomBossEvent.Packed.CODEC
	);
	private final Map<ResourceLocation, CustomBossEvent> events = Maps.<ResourceLocation, CustomBossEvent>newHashMap();

	@Nullable
	public CustomBossEvent get(ResourceLocation resourceLocation) {
		return (CustomBossEvent)this.events.get(resourceLocation);
	}

	public CustomBossEvent create(ResourceLocation resourceLocation, Component component) {
		CustomBossEvent customBossEvent = new CustomBossEvent(resourceLocation, component);
		this.events.put(resourceLocation, customBossEvent);
		return customBossEvent;
	}

	public void remove(CustomBossEvent customBossEvent) {
		this.events.remove(customBossEvent.getTextId());
	}

	public Collection<ResourceLocation> getIds() {
		return this.events.keySet();
	}

	public Collection<CustomBossEvent> getEvents() {
		return this.events.values();
	}

	public CompoundTag save(HolderLookup.Provider provider) {
		Map<ResourceLocation, CustomBossEvent.Packed> map = Util.mapValues(this.events, CustomBossEvent::pack);
		return (CompoundTag)EVENTS_CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), map).getOrThrow();
	}

	public void load(CompoundTag compoundTag, HolderLookup.Provider provider) {
		Map<ResourceLocation, CustomBossEvent.Packed> map = (Map<ResourceLocation, CustomBossEvent.Packed>)EVENTS_CODEC.parse(
				provider.createSerializationContext(NbtOps.INSTANCE), compoundTag
			)
			.resultOrPartial(string -> LOGGER.error("Failed to parse boss bar events: {}", string))
			.orElse(Map.of());
		map.forEach((resourceLocation, packed) -> this.events.put(resourceLocation, CustomBossEvent.load(resourceLocation, packed)));
	}

	public void onPlayerConnect(ServerPlayer serverPlayer) {
		for (CustomBossEvent customBossEvent : this.events.values()) {
			customBossEvent.onPlayerConnect(serverPlayer);
		}
	}

	public void onPlayerDisconnect(ServerPlayer serverPlayer) {
		for (CustomBossEvent customBossEvent : this.events.values()) {
			customBossEvent.onPlayerDisconnect(serverPlayer);
		}
	}
}
