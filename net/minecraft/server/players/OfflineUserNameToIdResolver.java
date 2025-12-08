package net.minecraft.server.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Offline implementation of UserNameToIdResolver that stores profiles locally.
 */
public class OfflineUserNameToIdResolver implements UserNameToIdResolver {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Map<String, NameAndId> byName = new HashMap<>();
	private final Map<UUID, NameAndId> byId = new HashMap<>();
	private final File cacheFile;
	private boolean dirty = false;

	public OfflineUserNameToIdResolver(File cacheFile) {
		this.cacheFile = cacheFile;
		this.load();
	}

	@Override
	public void add(NameAndId nameAndId) {
		this.byName.put(nameAndId.name().toLowerCase(), nameAndId);
		this.byId.put(nameAndId.id(), nameAndId);
		this.dirty = true;
	}

	@Override
	public Optional<NameAndId> get(String username) {
		return Optional.ofNullable(this.byName.get(username.toLowerCase()));
	}

	@Override
	public Optional<NameAndId> get(UUID uuid) {
		return Optional.ofNullable(this.byId.get(uuid));
	}

	@Override
	public void resolveOfflineUsers(boolean resolve) {
		// In offline mode, all users are already offline
	}

	@Override
	public void save() {
		if (!this.dirty) {
			return;
		}

		try {
			JsonArray jsonArray = new JsonArray();
			
			for (NameAndId nameAndId : this.byId.values()) {
				JsonObject jsonObject = new JsonObject();
				nameAndId.appendTo(jsonObject);
				jsonArray.add(jsonObject);
			}

			Path path = this.cacheFile.toPath();
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				writer.write(jsonArray.toString());
			}

			this.dirty = false;
		} catch (IOException e) {
			LOGGER.error("Failed to save user cache", e);
		}
	}

	private void load() {
		if (!this.cacheFile.exists()) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(this.cacheFile.toPath(), StandardCharsets.UTF_8)) {
			JsonArray jsonArray = GsonHelper.fromJson(reader, JsonArray.class);
			
			if (jsonArray != null) {
				for (JsonElement element : jsonArray) {
					if (element instanceof JsonObject jsonObject) {
						NameAndId nameAndId = NameAndId.fromJson(jsonObject);
						if (nameAndId != null) {
							this.byName.put(nameAndId.name().toLowerCase(), nameAndId);
							this.byId.put(nameAndId.id(), nameAndId);
						}
					}
				}
			}
		} catch (FileNotFoundException e) {
			// File doesn't exist yet, that's fine
		} catch (IOException e) {
			LOGGER.error("Failed to load user cache", e);
		}
	}
}
