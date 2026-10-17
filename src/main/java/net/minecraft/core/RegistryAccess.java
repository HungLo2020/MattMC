package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import net.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;

public interface RegistryAccess extends HolderLookup.Provider {
	Logger LOGGER = LogUtils.getLogger();
	RegistryAccess.Frozen EMPTY = new RegistryAccess.ImmutableRegistryAccess(Map.of()).freeze();

	@Override
	<E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> resourceKey);

	default <E> Registry<E> lookupOrThrow(ResourceKey<? extends Registry<? extends E>> resourceKey) {
		return (Registry<E>)this.lookup(resourceKey).orElseThrow(() -> new IllegalStateException("Missing registry: " + resourceKey));
	}

	Stream<RegistryAccess.RegistryEntry<?>> registries();

	@Override
	default Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
		return this.registries().map(registryEntry -> registryEntry.key);
	}

	static RegistryAccess.Frozen fromRegistryOfRegistries(Registry<? extends Registry<?>> registry) {
		return new RegistryAccess.Frozen() {
			@Override
			public <T> Optional<Registry<T>> lookup(ResourceKey<? extends Registry<? extends T>> resourceKey) {
				Registry<Registry<T>> registryx = (Registry<Registry<T>>)registry;
				return registryx.getOptional((ResourceKey<Registry<T>>)resourceKey);
			}

			@Override
			public Stream<RegistryAccess.RegistryEntry<?>> registries() {
				return registry.entrySet().stream().map(RegistryAccess.RegistryEntry::fromMapEntry);
			}

			@Override
			public RegistryAccess.Frozen freeze() {
				return this;
			}
		};
	}

	default RegistryAccess.Frozen freeze() {
		final class FrozenAccess extends RegistryAccess.ImmutableRegistryAccess implements RegistryAccess.Frozen {
			private FrozenAccess(final Stream<RegistryAccess.RegistryEntry<?>> stream, AtomicBoolean controlled) {
				super(stream, controlled);
			}
		}

		AtomicBoolean controlled = new AtomicBoolean(true);
		return new FrozenAccess(this.registries().map(entry -> {
			// Record provenance while performing the original finalization, not
			// by probing a lookup later. Unknown registry implementations still
			// follow the original freeze path but cannot grant native admission.
			RegistryAccess.RegistryEntry<?> frozen = entry.freeze();
			Class<?> type = entry.value().getClass();
			boolean standard = type == MappedRegistry.class || type == DefaultedMappedRegistry.class;
			if (!standard || frozen.value() != entry.value()) controlled.set(false);
			return frozen;
		}), controlled);
	}

	public interface Frozen extends RegistryAccess {
	}

	public static class ImmutableRegistryAccess implements RegistryAccess {
		private final Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>> registries;
		private final boolean controlledFinalizedLookups;

		public ImmutableRegistryAccess(List<? extends Registry<?>> list) {
			this.registries = (Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>>)list.stream()
				.collect(Collectors.toUnmodifiableMap(Registry::key, registry -> registry));
			this.controlledFinalizedLookups = false;
		}

		public ImmutableRegistryAccess(Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>> map) {
			this.registries = Map.copyOf(map);
			this.controlledFinalizedLookups = false;
		}

		public ImmutableRegistryAccess(Stream<RegistryAccess.RegistryEntry<?>> stream) {
			this.registries = (Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>>)stream.collect(
				ImmutableMap.toImmutableMap(RegistryAccess.RegistryEntry::key, RegistryAccess.RegistryEntry::value)
			);
			this.controlledFinalizedLookups = false;
		}

		/** Only the final internal snapshot can call this constructor. */
		private ImmutableRegistryAccess(Stream<RegistryAccess.RegistryEntry<?>> stream, AtomicBoolean controlled) {
			this.registries = stream.collect(ImmutableMap.toImmutableMap(RegistryAccess.RegistryEntry::key, RegistryAccess.RegistryEntry::value));
			this.controlledFinalizedLookups = controlled.get();
		}

		/** Provenance from successful standard finalization; performs no lookup. */
		public final boolean hasControlledFinalizedLookups() {
			return this.controlledFinalizedLookups;
		}

		@Override
		public <E> Optional<Registry<E>> lookup(ResourceKey<? extends Registry<? extends E>> resourceKey) {
			return Optional.ofNullable((Registry)this.registries.get(resourceKey)).map(registry -> registry);
		}

		@Override
		public Stream<RegistryAccess.RegistryEntry<?>> registries() {
			return this.registries.entrySet().stream().map(RegistryAccess.RegistryEntry::fromMapEntry);
		}
	}

	public record RegistryEntry<T>(ResourceKey<? extends Registry<T>> key, Registry<T> value) {

		private static <T, R extends Registry<? extends T>> RegistryAccess.RegistryEntry<T> fromMapEntry(Entry<? extends ResourceKey<? extends Registry<?>>, R> entry) {
			return fromUntyped((ResourceKey<? extends Registry<?>>)entry.getKey(), (Registry<?>)entry.getValue());
		}

		private static <T> RegistryAccess.RegistryEntry<T> fromUntyped(ResourceKey<? extends Registry<?>> resourceKey, Registry<?> registry) {
			return new RegistryAccess.RegistryEntry<>((ResourceKey<? extends Registry<T>>)resourceKey, (Registry<T>)registry);
		}

		private RegistryAccess.RegistryEntry<T> freeze() {
			return new RegistryAccess.RegistryEntry<>(this.key, this.value.freeze());
		}
	}
}
