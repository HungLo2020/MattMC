package net.minecraft.core.component;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.NativeItemState;

public final class PatchedDataComponentMap extends NativeItemState.Overlay implements DataComponentMap {
	public PatchedDataComponentMap(DataComponentMap prototype) {super(prototype);}
	private PatchedDataComponentMap(DataComponentMap prototype,NativeItemState.Patch patch) {super(prototype,patch);}
	private PatchedDataComponentMap(NativeItemState.Overlay source,boolean stackCopy) {super(source,stackCopy);}
	private PatchedDataComponentMap(NativeItemState.Overlay source,long handle,long address,long adopted) {super(source,handle,address,adopted);}
	/** The public component facade is the actual shared stack/component lease. */
	public static PatchedDataComponentMap fromNativeStack(NativeItemState.Stack stack) {return (PatchedDataComponentMap)stack.components();}
	public static PatchedDataComponentMap fromNativePatch(DataComponentMap prototype,NativeItemState.Patch patch){return new PatchedDataComponentMap(prototype,patch);}
	public static PatchedDataComponentMap copyNativeStack(NativeItemState.Overlay source){return new PatchedDataComponentMap(source,true);}
	/** A copy of {@code source} a native operation already prepared, adopted as its owner. */
	public static PatchedDataComponentMap adoptPreparedStack(NativeItemState.Overlay source,long handle,long address,long adopted){return new PatchedDataComponentMap(source,handle,address,adopted);}
	private PatchedDataComponentMap(NativeItemState.Pending pending,long handle,long address) {super(pending,handle,address);}
	public static PatchedDataComponentMap adoptPendingStack(NativeItemState.Pending pending,long handle,long address){return new PatchedDataComponentMap(pending,handle,address);}
	public static PatchedDataComponentMap copyNativeComponents(NativeItemState.Overlay source){return new PatchedDataComponentMap(source,false);}
	public NativeItemState.Overlay nativeState(){return this;}

	public static PatchedDataComponentMap fromPatch(DataComponentMap dataComponentMap, DataComponentPatch dataComponentPatch) {
		if (isPatchSanitized(dataComponentMap, dataComponentPatch.map)) {
			return (PatchedDataComponentMap)NativeItemState.Overlay.fromPatchMap(dataComponentMap, dataComponentPatch.map);
		} else {
			PatchedDataComponentMap patchedDataComponentMap = new PatchedDataComponentMap(dataComponentMap);
			patchedDataComponentMap.applyPatch(dataComponentPatch);
			return patchedDataComponentMap;
		}
	}

	private static boolean isPatchSanitized(DataComponentMap dataComponentMap, Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap) {
		for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(reference2ObjectMap)) {
			Object object = dataComponentMap.get((DataComponentType)entry.getKey());
			Optional<?> optional = (Optional<?>)entry.getValue();
			if (optional.isPresent() && optional.get().equals(object)) {
				return false;
			}

			if (optional.isEmpty() && object == null) {
				return false;
			}
		}

		return true;
	}

	@Nullable
	@Override
	public <T> T get(DataComponentType<? extends T> dataComponentType) {
		return super.get(dataComponentType);
	}

	public boolean hasNonDefault(DataComponentType<?> dataComponentType) {
		return super.hasOverride(dataComponentType);
	}

	@Nullable
	public <T> T set(DataComponentType<T> dataComponentType, @Nullable T object) {
		return super.set(dataComponentType, object);
	}

	@Nullable
	public <T> T set(TypedDataComponent<T> typedDataComponent) {
		return this.set(typedDataComponent.type(), typedDataComponent.value());
	}

	@Nullable
	public <T> T remove(DataComponentType<? extends T> dataComponentType) {
		return super.remove(dataComponentType);
	}

	public void applyPatch(DataComponentPatch dataComponentPatch) {
		this.ensureMapOwnership();

		for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(dataComponentPatch.map)) {
			this.applyPatch((DataComponentType<?>)entry.getKey(), (Optional<?>)entry.getValue());
		}
	}

	private void applyPatch(DataComponentType<?> dataComponentType, Optional<?> optional) {
		super.applyPrepared(dataComponentType, optional);
	}

	public void restorePatch(DataComponentPatch dataComponentPatch) {
		this.ensureMapOwnership();
		var patch = super.patchView();
		patch.clear();
		patch.putAll(dataComponentPatch.map);
	}

	public void clearPatch() {
		super.clear();
	}

	public void setAll(DataComponentMap dataComponentMap) {
		for (TypedDataComponent<?> typedDataComponent : dataComponentMap) {
			typedDataComponent.applyTo(this);
		}
	}

	private void ensureMapOwnership() {
		super.beginPatchMutation();
	}

	@Override
	public Set<DataComponentType<?>> keySet() {
		if (super.patchSize() == 0) {
			return this.prototype.keySet();
		} else {
			Set<DataComponentType<?>> set = new ReferenceArraySet<>(this.prototype.keySet());

			for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(super.patchView())) {
				Optional<?> optional = (Optional<?>)entry.getValue();
				if (optional.isPresent()) {
					set.add((DataComponentType)entry.getKey());
				} else {
					set.remove(entry.getKey());
				}
			}

			return set;
		}
	}

	@Override
	public Iterator<TypedDataComponent<?>> iterator() {
		if (super.patchSize() == 0) {
			return this.prototype.iterator();
		} else {
			List<TypedDataComponent<?>> list = new ArrayList(super.patchSize() + this.prototype.size());

			for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(super.patchView())) {
				if (((Optional)entry.getValue()).isPresent()) {
					list.add(TypedDataComponent.createUnchecked((DataComponentType)entry.getKey(), ((Optional)entry.getValue()).get()));
				}
			}

			for (TypedDataComponent<?> typedDataComponent : this.prototype) {
				if (!super.hasOverride(typedDataComponent.type())) {
					list.add(typedDataComponent);
				}
			}

			return list.iterator();
		}
	}

	@Override
	public int size() {
		int i = this.prototype.size();

		for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(super.patchView())) {
			boolean bl = ((Optional)entry.getValue()).isPresent();
			boolean bl2 = this.prototype.has((DataComponentType<?>)entry.getKey());
			if (bl != bl2) {
				i += bl ? 1 : -1;
			}
		}

		return i;
	}

	public DataComponentPatch asPatch() {
		NativeItemState.Patch patch = super.nativePatchSnapshot();
		return patch == null ? DataComponentPatch.EMPTY : new DataComponentPatch(patch.mapView());
	}

	public PatchedDataComponentMap copy() {
		return copyNativeComponents(this);
	}

	public DataComponentMap toImmutableMap() {
		return (DataComponentMap)(super.patchSize() == 0 ? this.prototype : this.copy());
	}

	public boolean equals(Object object) {
		return this == object
			? true
			: object instanceof PatchedDataComponentMap patchedDataComponentMap
				&& this.prototype.equals(patchedDataComponentMap.prototype)
				&& super.patchView().equals(patchedDataComponentMap.patchView());
	}

	public int hashCode() {
		return this.prototype.hashCode() + super.patchView().hashCode() * 31;
	}

	public String toString() {
		return "{" + (String)this.stream().map(TypedDataComponent::toString).collect(Collectors.joining(", ")) + "}";
	}
}
