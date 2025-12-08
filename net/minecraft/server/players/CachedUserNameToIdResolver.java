package net.minecraft.server.players;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.server.profile.PlayerProfile;
import net.minecraft.server.profile.GameProfileRepository;
import net.minecraft.server.profile.ProfileLookupCallback;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import net.minecraft.util.StringUtil;
import org.slf4j.Logger;

public class CachedUserNameToIdResolver implements UserNameToIdResolver {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int GAMEPROFILES_MRU_LIMIT = 1000;
	private static final int GAMEPROFILES_EXPIRATION_MONTHS = 1;
	private boolean resolveOfflineUsers = true;
	private final Map<String, CachedUserNameToIdResolver.GameProfileInfo> profilesByName = new ConcurrentHashMap();
	private final Map<UUID, CachedUserNameToIdResolver.GameProfileInfo> profilesByUUID = new ConcurrentHashMap();
	private final GameProfileRepository profileRepository;
	private final Gson gson = new GsonBuilder().create();
	private final File file;
	private final AtomicLong operationCount = new AtomicLong();

	public CachedUserNameToIdResolver(GameProfileRepository gameProfileRepository, File file) {
		this.profileRepository = gameProfileRepository;
		this.file = file;
		Lists.reverse(this.load()).forEach(this::safeAdd);
	}

	private void safeAdd(CachedUserNameToIdResolver.GameProfileInfo gameProfileInfo) {
		NameAndId nameAndId = gameProfileInfo.nameAndId();
		gameProfileInfo.setLastAccess(this.getNextOperation());
		this.profilesByName.put(nameAndId.name().toLowerCase(Locale.ROOT), gameProfileInfo);
		this.profilesByUUID.put(nameAndId.id(), gameProfileInfo);
	}

	private Optional<NameAndId> lookupGameProfile(GameProfileRepository gameProfileRepository, String string) {
		if (!StringUtil.isValidPlayerName(string)) {
			return this.createUnknownProfile(string);
		} else {
			// Offline mode - profile repository not functional
			final PlayerProfile[] foundProfile = {null};
			// In offline mode, GameProfileRepository doesn't actually fetch profiles
			// We'll just return empty and let createUnknownProfile handle it
			Optional<NameAndId> optional = foundProfile[0] != null ? Optional.of(new NameAndId(foundProfile[0])) : Optional.empty();
			return optional.isEmpty() ? this.createUnknownProfile(string) : optional;
		}
	}

	private Optional<NameAndId> createUnknownProfile(String string) {
		return this.resolveOfflineUsers ? Optional.of(NameAndId.createOffline(string)) : Optional.empty();
	}

	@Override
	public void resolveOfflineUsers(boolean bl) {
		this.resolveOfflineUsers = bl;
	}

	@Override
	public void add(NameAndId nameAndId) {
		this.addInternal(nameAndId);
	}

	private CachedUserNameToIdResolver.GameProfileInfo addInternal(NameAndId nameAndId) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.add(2, 1);
		Date date = calendar.getTime();
		CachedUserNameToIdResolver.GameProfileInfo gameProfileInfo = new CachedUserNameToIdResolver.GameProfileInfo(nameAndId, date);
		this.safeAdd(gameProfileInfo);
		this.save();
		return gameProfileInfo;
	}

	private long getNextOperation() {
		return this.operationCount.incrementAndGet();
	}

	@Override
	public Optional<NameAndId> get(String string) {
		String string2 = string.toLowerCase(Locale.ROOT);
		CachedUserNameToIdResolver.GameProfileInfo gameProfileInfo = (CachedUserNameToIdResolver.GameProfileInfo)this.profilesByName.get(string2);
		boolean bl = false;
		if (gameProfileInfo != null && new Date().getTime() >= gameProfileInfo.expirationDate.getTime()) {
			this.profilesByUUID.remove(gameProfileInfo.nameAndId().id());
			this.profilesByName.remove(gameProfileInfo.nameAndId().name().toLowerCase(Locale.ROOT));
			bl = true;
			gameProfileInfo = null;
		}

		Optional<NameAndId> optional;
		if (gameProfileInfo != null) {
			gameProfileInfo.setLastAccess(this.getNextOperation());
			optional = Optional.of(gameProfileInfo.nameAndId());
		} else {
			Optional<NameAndId> optional2 = this.lookupGameProfile(this.profileRepository, string2);
			if (optional2.isPresent()) {
				optional = Optional.of(this.addInternal((NameAndId)optional2.get()).nameAndId());
				bl = false;
			} else {
				optional = Optional.empty();
			}
		}

		if (bl) {
			this.save();
		}

		return optional;
	}

	@Override
	public Optional<NameAndId> get(UUID uUID) {
		CachedUserNameToIdResolver.GameProfileInfo gameProfileInfo = (CachedUserNameToIdResolver.GameProfileInfo)this.profilesByUUID.get(uUID);
		if (gameProfileInfo == null) {
			return Optional.empty();
		} else {
			gameProfileInfo.setLastAccess(this.getNextOperation());
			return Optional.of(gameProfileInfo.nameAndId());
		}
	}

	private static DateFormat createDateFormat() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
	}

	private List<CachedUserNameToIdResolver.GameProfileInfo> load() {
		List<CachedUserNameToIdResolver.GameProfileInfo> list = Lists.<CachedUserNameToIdResolver.GameProfileInfo>newArrayList();

		try {
			Reader reader = Files.newReader(this.file, StandardCharsets.UTF_8);

			Object var9;
			label60: {
				try {
					JsonArray jsonArray = this.gson.fromJson(reader, JsonArray.class);
					if (jsonArray == null) {
						var9 = list;
						break label60;
					}

					DateFormat dateFormat = createDateFormat();
					jsonArray.forEach(jsonElement -> readGameProfile(jsonElement, dateFormat).ifPresent(list::add));
				} catch (Throwable var6) {
					if (reader != null) {
						try {
							reader.close();
						} catch (Throwable var5) {
							var6.addSuppressed(var5);
						}
					}

					throw var6;
				}

				if (reader != null) {
					reader.close();
				}

				return list;
			}

			if (reader != null) {
				reader.close();
			}

			return (List<CachedUserNameToIdResolver.GameProfileInfo>)var9;
		} catch (FileNotFoundException var7) {
		} catch (JsonParseException | IOException var8) {
			LOGGER.warn("Failed to load profile cache {}", this.file, var8);
		}

		return list;
	}

	@Override
	public void save() {
		JsonArray jsonArray = new JsonArray();
		DateFormat dateFormat = createDateFormat();
		this.getTopMRUProfiles(1000).forEach(gameProfileInfo -> jsonArray.add(writeGameProfile(gameProfileInfo, dateFormat)));
		String string = this.gson.toJson((JsonElement)jsonArray);

		try {
			Writer writer = Files.newWriter(this.file, StandardCharsets.UTF_8);

			try {
				writer.write(string);
			} catch (Throwable var8) {
				if (writer != null) {
					try {
						writer.close();
					} catch (Throwable var7) {
						var8.addSuppressed(var7);
					}
				}

				throw var8;
			}

			if (writer != null) {
				writer.close();
			}
		} catch (IOException var9) {
		}
	}

	private Stream<CachedUserNameToIdResolver.GameProfileInfo> getTopMRUProfiles(int i) {
		return ImmutableList.copyOf(this.profilesByUUID.values())
			.stream()
			.sorted(Comparator.comparing(CachedUserNameToIdResolver.GameProfileInfo::lastAccess).reversed())
			.limit(i);
	}

	private static JsonElement writeGameProfile(CachedUserNameToIdResolver.GameProfileInfo gameProfileInfo, DateFormat dateFormat) {
		JsonObject jsonObject = new JsonObject();
		gameProfileInfo.nameAndId().appendTo(jsonObject);
		jsonObject.addProperty("expiresOn", dateFormat.format(gameProfileInfo.expirationDate()));
		return jsonObject;
	}

	private static Optional<CachedUserNameToIdResolver.GameProfileInfo> readGameProfile(JsonElement jsonElement, DateFormat dateFormat) {
		if (jsonElement.isJsonObject()) {
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			NameAndId nameAndId = NameAndId.fromJson(jsonObject);
			if (nameAndId != null) {
				JsonElement jsonElement2 = jsonObject.get("expiresOn");
				if (jsonElement2 != null) {
					String string = jsonElement2.getAsString();

					try {
						Date date = dateFormat.parse(string);
						return Optional.of(new CachedUserNameToIdResolver.GameProfileInfo(nameAndId, date));
					} catch (ParseException var7) {
						LOGGER.warn("Failed to parse date {}", string, var7);
					}
				}
			}
		}

		return Optional.empty();
	}

	static class GameProfileInfo {
		private final NameAndId nameAndId;
		final Date expirationDate;
		private volatile long lastAccess;

		GameProfileInfo(NameAndId nameAndId, Date date) {
			this.nameAndId = nameAndId;
			this.expirationDate = date;
		}

		public NameAndId nameAndId() {
			return this.nameAndId;
		}

		public Date expirationDate() {
			return this.expirationDate;
		}

		public void setLastAccess(long l) {
			this.lastAccess = l;
		}

		public long lastAccess() {
			return this.lastAccess;
		}
	}
}
