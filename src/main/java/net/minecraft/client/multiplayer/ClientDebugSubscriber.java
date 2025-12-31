package net.minecraft.client.multiplayer;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundDebugSubscriptionRequestPacket;
import net.minecraft.util.debug.DebugSubscription;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.util.debug.DebugSubscription.Event;
import net.minecraft.util.debug.DebugSubscription.Update;
import net.minecraft.util.debug.DebugValueAccess.EventVisitor;
import net.minecraft.util.debugchart.RemoteDebugSampleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ClientDebugSubscriber {
	private final ClientPacketListener connection;
	private final DebugScreenOverlay debugScreenOverlay;
	private Set<DebugSubscription<?>> remoteSubscriptions = Set.of();
	private final Map<DebugSubscription<?>, ClientDebugSubscriber.ValueMaps<?>> valuesBySubscription = new HashMap();

	public ClientDebugSubscriber(ClientPacketListener clientPacketListener, DebugScreenOverlay debugScreenOverlay) {
		this.debugScreenOverlay = debugScreenOverlay;
		this.connection = clientPacketListener;
	}

	private static void addFlag(Set<DebugSubscription<?>> set, DebugSubscription<?> debugSubscription, boolean bl) {
		if (bl) {
			set.add(debugSubscription);
		}
	}

	private Set<DebugSubscription<?>> requestedSubscriptions() {
		Set<DebugSubscription<?>> set = new ReferenceOpenHashSet<>();
		addFlag(set, RemoteDebugSampleType.TICK_TIME.subscription(), this.debugScreenOverlay.showFpsCharts());
		if (SharedConstants.DEBUG_ENABLED) {
			addFlag(set, DebugSubscriptions.BEES, SharedConstants.DEBUG_BEES);
			addFlag(set, DebugSubscriptions.BEE_HIVES, SharedConstants.DEBUG_BEES);
			addFlag(set, DebugSubscriptions.BRAINS, SharedConstants.DEBUG_BRAIN);
			addFlag(set, DebugSubscriptions.BREEZES, SharedConstants.DEBUG_BREEZE_MOB);
			addFlag(set, DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS, SharedConstants.DEBUG_ENTITY_BLOCK_INTERSECTION);
			addFlag(set, DebugSubscriptions.ENTITY_PATHS, SharedConstants.DEBUG_PATHFINDING);
			addFlag(set, DebugSubscriptions.GAME_EVENTS, SharedConstants.DEBUG_GAME_EVENT_LISTENERS);
			addFlag(set, DebugSubscriptions.GAME_EVENT_LISTENERS, SharedConstants.DEBUG_GAME_EVENT_LISTENERS);
			addFlag(set, DebugSubscriptions.GOAL_SELECTORS, SharedConstants.DEBUG_GOAL_SELECTOR || SharedConstants.DEBUG_BEES);
			addFlag(set, DebugSubscriptions.NEIGHBOR_UPDATES, SharedConstants.DEBUG_NEIGHBORSUPDATE);
			addFlag(set, DebugSubscriptions.POIS, SharedConstants.DEBUG_POI);
			addFlag(set, DebugSubscriptions.RAIDS, SharedConstants.DEBUG_RAIDS);
			addFlag(set, DebugSubscriptions.REDSTONE_WIRE_ORIENTATIONS, SharedConstants.DEBUG_EXPERIMENTAL_REDSTONEWIRE_UPDATE_ORDER);
			addFlag(set, DebugSubscriptions.STRUCTURES, SharedConstants.DEBUG_STRUCTURES);
			addFlag(set, DebugSubscriptions.VILLAGE_SECTIONS, SharedConstants.DEBUG_VILLAGE_SECTIONS);
		}

		return set;
	}

	public void clear() {
		this.remoteSubscriptions = Set.of();
		this.dropLevel();
	}

	public void tick(long l) {
		Set<DebugSubscription<?>> set = this.requestedSubscriptions();
		if (!set.equals(this.remoteSubscriptions)) {
			this.remoteSubscriptions = set;
			this.onSubscriptionsChanged(set);
		}

		this.valuesBySubscription.forEach((debugSubscription, valueMaps) -> {
			if (debugSubscription.expireAfterTicks() != 0) {
				valueMaps.purgeExpired(l);
			}
		});
	}

	private void onSubscriptionsChanged(Set<DebugSubscription<?>> set) {
		this.valuesBySubscription.keySet().retainAll(set);

		for (DebugSubscription<?> debugSubscription : set) {
			this.valuesBySubscription.computeIfAbsent(debugSubscription, debugSubscriptionx -> new ClientDebugSubscriber.ValueMaps());
		}

		this.connection.send(new ServerboundDebugSubscriptionRequestPacket(set));
	}

	@Nullable
	<V> ClientDebugSubscriber.ValueMaps<V> getValueMaps(DebugSubscription<V> debugSubscription) {
		return (ClientDebugSubscriber.ValueMaps<V>)this.valuesBySubscription.get(debugSubscription);
	}

	@Nullable
	private <K, V> ClientDebugSubscriber.ValueMap<K, V> getValueMap(DebugSubscription<V> debugSubscription, ClientDebugSubscriber.ValueMapType<K, V> valueMapType) {
		ClientDebugSubscriber.ValueMaps<V> valueMaps = this.getValueMaps(debugSubscription);
		return valueMaps != null ? valueMapType.get(valueMaps) : null;
	}

	@Nullable
	<K, V> V getValue(DebugSubscription<V> debugSubscription, K object, ClientDebugSubscriber.ValueMapType<K, V> valueMapType) {
		ClientDebugSubscriber.ValueMap<K, V> valueMap = this.getValueMap(debugSubscription, valueMapType);
		return valueMap != null ? valueMap.getValue(object) : null;
	}

	public DebugValueAccess createDebugValueAccess(Level level) {
		return new DebugValueAccess() {
			public <T> void forEachChunk(DebugSubscription<T> debugSubscription, BiConsumer<ChunkPos, T> biConsumer) {
				ClientDebugSubscriber.this.forEachValue(debugSubscription, ClientDebugSubscriber.chunks(), biConsumer);
			}

			@Nullable
			public <T> T getChunkValue(DebugSubscription<T> debugSubscription, ChunkPos chunkPos) {
				return ClientDebugSubscriber.this.getValue(debugSubscription, chunkPos, ClientDebugSubscriber.chunks());
			}

			public <T> void forEachBlock(DebugSubscription<T> debugSubscription, BiConsumer<BlockPos, T> biConsumer) {
				ClientDebugSubscriber.this.forEachValue(debugSubscription, ClientDebugSubscriber.blocks(), biConsumer);
			}

			@Nullable
			public <T> T getBlockValue(DebugSubscription<T> debugSubscription, BlockPos blockPos) {
				return ClientDebugSubscriber.this.getValue(debugSubscription, blockPos, ClientDebugSubscriber.blocks());
			}

			public <T> void forEachEntity(DebugSubscription<T> debugSubscription, BiConsumer<Entity, T> biConsumer) {
				ClientDebugSubscriber.this.forEachValue(debugSubscription, ClientDebugSubscriber.entities(), (uUID, object) -> {
					Entity entity = level.getEntity(uUID);
					if (entity != null) {
						biConsumer.accept(entity, object);
					}
				});
			}

			@Nullable
			public <T> T getEntityValue(DebugSubscription<T> debugSubscription, Entity entity) {
				return ClientDebugSubscriber.this.getValue(debugSubscription, entity.getUUID(), ClientDebugSubscriber.entities());
			}

			public <T> void forEachEvent(DebugSubscription<T> debugSubscription, EventVisitor<T> eventVisitor) {
				ClientDebugSubscriber.ValueMaps<T> valueMaps = ClientDebugSubscriber.this.getValueMaps(debugSubscription);
				if (valueMaps != null) {
					long l = level.getGameTime();

					for (ClientDebugSubscriber.ValueWrapper<T> valueWrapper : valueMaps.events) {
						int i = (int)(valueWrapper.expiresAfterTime() - l);
						int j = debugSubscription.expireAfterTicks();
						eventVisitor.accept(valueWrapper.value(), i, j);
					}
				}
			}
		};
	}

	public <T> void updateChunk(long l, ChunkPos chunkPos, Update<T> update) {
		this.updateMap(l, chunkPos, update, chunks());
	}

	public <T> void updateBlock(long l, BlockPos blockPos, Update<T> update) {
		this.updateMap(l, blockPos, update, blocks());
	}

	public <T> void updateEntity(long l, Entity entity, Update<T> update) {
		this.updateMap(l, entity.getUUID(), update, entities());
	}

	public <T> void pushEvent(long l, Event<T> event) {
		ClientDebugSubscriber.ValueMaps<T> valueMaps = this.getValueMaps(event.subscription());
		if (valueMaps != null) {
			valueMaps.events.add(new ClientDebugSubscriber.ValueWrapper<>(event.value(), l + event.subscription().expireAfterTicks()));
		}
	}

	private <K, V> void updateMap(long l, K object, Update<V> update, ClientDebugSubscriber.ValueMapType<K, V> valueMapType) {
		ClientDebugSubscriber.ValueMap<K, V> valueMap = this.getValueMap(update.subscription(), valueMapType);
		if (valueMap != null) {
			valueMap.apply(l, object, update);
		}
	}

	<K, V> void forEachValue(DebugSubscription<V> debugSubscription, ClientDebugSubscriber.ValueMapType<K, V> valueMapType, BiConsumer<K, V> biConsumer) {
		ClientDebugSubscriber.ValueMap<K, V> valueMap = this.getValueMap(debugSubscription, valueMapType);
		if (valueMap != null) {
			valueMap.forEach(biConsumer);
		}
	}

	public void dropLevel() {
		this.valuesBySubscription.clear();
	}

	public void dropChunk(ChunkPos chunkPos) {
		if (!this.valuesBySubscription.isEmpty()) {
			for (ClientDebugSubscriber.ValueMaps<?> valueMaps : this.valuesBySubscription.values()) {
				valueMaps.dropChunkAndBlocks(chunkPos);
			}
		}
	}

	public void dropEntity(Entity entity) {
		if (!this.valuesBySubscription.isEmpty()) {
			for (ClientDebugSubscriber.ValueMaps<?> valueMaps : this.valuesBySubscription.values()) {
				valueMaps.entityValues.removeKey(entity.getUUID());
			}
		}
	}

	static <T> ClientDebugSubscriber.ValueMapType<UUID, T> entities() {
		return valueMaps -> valueMaps.entityValues;
	}

	static <T> ClientDebugSubscriber.ValueMapType<BlockPos, T> blocks() {
		return valueMaps -> valueMaps.blockValues;
	}

	static <T> ClientDebugSubscriber.ValueMapType<ChunkPos, T> chunks() {
		return valueMaps -> valueMaps.chunkValues;
	}

	@Environment(EnvType.CLIENT)
	static class ValueMap<K, V> {
		private final Map<K, ClientDebugSubscriber.ValueWrapper<V>> values = new HashMap();

		public void removeValues(Predicate<ClientDebugSubscriber.ValueWrapper<V>> predicate) {
			this.values.values().removeIf(predicate);
		}

		public void removeKey(K object) {
			this.values.remove(object);
		}

		public void removeKeys(Predicate<K> predicate) {
			this.values.keySet().removeIf(predicate);
		}

		@Nullable
		public V getValue(K object) {
			ClientDebugSubscriber.ValueWrapper<V> valueWrapper = (ClientDebugSubscriber.ValueWrapper<V>)this.values.get(object);
			return valueWrapper != null ? valueWrapper.value() : null;
		}

		public void apply(long l, K object, Update<V> update) {
			if (update.value().isPresent()) {
				this.values.put(object, new ClientDebugSubscriber.ValueWrapper<>(update.value().get(), l + update.subscription().expireAfterTicks()));
			} else {
				this.values.remove(object);
			}
		}

		public void forEach(BiConsumer<K, V> biConsumer) {
			this.values.forEach((object, valueWrapper) -> biConsumer.accept(object, valueWrapper.value()));
		}
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface ValueMapType<K, V> {
		ClientDebugSubscriber.ValueMap<K, V> get(ClientDebugSubscriber.ValueMaps<V> valueMaps);
	}

	@Environment(EnvType.CLIENT)
	static class ValueMaps<V> {
		final ClientDebugSubscriber.ValueMap<ChunkPos, V> chunkValues = new ClientDebugSubscriber.ValueMap<>();
		final ClientDebugSubscriber.ValueMap<BlockPos, V> blockValues = new ClientDebugSubscriber.ValueMap<>();
		final ClientDebugSubscriber.ValueMap<UUID, V> entityValues = new ClientDebugSubscriber.ValueMap<>();
		final List<ClientDebugSubscriber.ValueWrapper<V>> events = new ArrayList();

		public void purgeExpired(long l) {
			Predicate<ClientDebugSubscriber.ValueWrapper<V>> predicate = valueWrapper -> valueWrapper.hasExpired(l);
			this.chunkValues.removeValues(predicate);
			this.blockValues.removeValues(predicate);
			this.entityValues.removeValues(predicate);
			this.events.removeIf(predicate);
		}

		public void dropChunkAndBlocks(ChunkPos chunkPos) {
			this.chunkValues.removeKey(chunkPos);
			this.blockValues.removeKeys(chunkPos::contains);
		}
	}

	@Environment(EnvType.CLIENT)
	record ValueWrapper<T>(T value, long expiresAfterTime) {
		private static final long NO_EXPIRY = -1L;

		public boolean hasExpired(long l) {
			return this.expiresAfterTime == -1L ? false : l >= this.expiresAfterTime;
		}
	}
}
