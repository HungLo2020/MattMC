package net.minecraft.core;

import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultedMappedRegistry<T> extends MappedRegistry<T> implements DefaultedRegistry<T> {
	private final ResourceLocation defaultKey;
	private Holder.Reference<T> defaultValue;

	public DefaultedMappedRegistry(String string, ResourceKey<? extends Registry<T>> resourceKey, Lifecycle lifecycle, boolean bl) {
		super(resourceKey, lifecycle, bl);
		this.defaultKey = ResourceLocation.parse(string);
	}

	@Override
	public Holder.Reference<T> register(ResourceKey<T> resourceKey, T object, RegistrationInfo registrationInfo) {
		Holder.Reference<T> reference = super.register(resourceKey, object, registrationInfo);
		if (this.defaultKey.equals(resourceKey.location())) {
			this.defaultValue = reference;
		}

		return reference;
	}

	@Override
	public int getId(@Nullable T object) {
		int i = super.getId(object);
		return i == -1 ? super.getId(this.defaultValue.value()) : i;
	}

	@NotNull
	@Override
	public ResourceLocation getKey(T object) {
		ResourceLocation resourceLocation = super.getKey(object);
		return resourceLocation == null ? this.defaultKey : resourceLocation;
	}

	@NotNull
	@Override
	public T getValue(@Nullable ResourceLocation resourceLocation) {
		T object = super.getValue(resourceLocation);
		return object == null ? this.defaultValue.value() : object;
	}

	@Override
	public Optional<T> getOptional(@Nullable ResourceLocation resourceLocation) {
		return Optional.ofNullable(super.getValue(resourceLocation));
	}

	@Override
	public Optional<Holder.Reference<T>> getAny() {
		return Optional.ofNullable(this.defaultValue);
	}

	@NotNull
	@Override
	public T byId(int i) {
		T object = super.byId(i);
		return object == null ? this.defaultValue.value() : object;
	}

	@Override
	public Optional<Holder.Reference<T>> getRandom(RandomSource randomSource) {
		return super.getRandom(randomSource).or(() -> Optional.of(this.defaultValue));
	}

	@Override
	public ResourceLocation getDefaultKey() {
		return this.defaultKey;
	}
}
