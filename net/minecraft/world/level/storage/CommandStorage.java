package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

public class CommandStorage {
	private static final String ID_PREFIX = "command_storage_";
	private final Map<String, CommandStorage.Container> namespaces = new HashMap();
	private final DimensionDataStorage storage;

	public CommandStorage(DimensionDataStorage dimensionDataStorage) {
		this.storage = dimensionDataStorage;
	}

	public CompoundTag get(ResourceLocation resourceLocation) {
		CommandStorage.Container container = this.getContainer(resourceLocation.getNamespace());
		return container != null ? container.get(resourceLocation.getPath()) : new CompoundTag();
	}

	@Nullable
	private CommandStorage.Container getContainer(String string) {
		CommandStorage.Container container = (CommandStorage.Container)this.namespaces.get(string);
		if (container != null) {
			return container;
		} else {
			CommandStorage.Container container2 = this.storage.get(CommandStorage.Container.type(string));
			if (container2 != null) {
				this.namespaces.put(string, container2);
			}

			return container2;
		}
	}

	private CommandStorage.Container getOrCreateContainer(String string) {
		CommandStorage.Container container = (CommandStorage.Container)this.namespaces.get(string);
		if (container != null) {
			return container;
		} else {
			CommandStorage.Container container2 = this.storage.computeIfAbsent(CommandStorage.Container.type(string));
			this.namespaces.put(string, container2);
			return container2;
		}
	}

	public void set(ResourceLocation resourceLocation, CompoundTag compoundTag) {
		this.getOrCreateContainer(resourceLocation.getNamespace()).put(resourceLocation.getPath(), compoundTag);
	}

	public Stream<ResourceLocation> keys() {
		return this.namespaces.entrySet().stream().flatMap(entry -> ((CommandStorage.Container)entry.getValue()).getKeys((String)entry.getKey()));
	}

	static String createId(String string) {
		return "command_storage_" + string;
	}

	static class Container extends SavedData {
		public static final Codec<CommandStorage.Container> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.unboundedMap(ExtraCodecs.RESOURCE_PATH_CODEC, CompoundTag.CODEC).fieldOf("contents").forGetter(container -> container.storage)
				)
				.apply(instance, CommandStorage.Container::new)
		);
		private final Map<String, CompoundTag> storage;

		private Container(Map<String, CompoundTag> map) {
			this.storage = new HashMap(map);
		}

		private Container() {
			this(new HashMap());
		}

		public static SavedDataType<CommandStorage.Container> type(String string) {
			return new SavedDataType<>(CommandStorage.createId(string), CommandStorage.Container::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
		}

		public CompoundTag get(String string) {
			CompoundTag compoundTag = (CompoundTag)this.storage.get(string);
			return compoundTag != null ? compoundTag : new CompoundTag();
		}

		public void put(String string, CompoundTag compoundTag) {
			if (compoundTag.isEmpty()) {
				this.storage.remove(string);
			} else {
				this.storage.put(string, compoundTag);
			}

			this.setDirty();
		}

		public Stream<ResourceLocation> getKeys(String string) {
			return this.storage.keySet().stream().map(string2 -> ResourceLocation.fromNamespaceAndPath(string, string2));
		}
	}
}
