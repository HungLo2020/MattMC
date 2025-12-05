package net.minecraft.server.players;

import com.google.gson.JsonObject;

public class ServerOpListEntry extends StoredUserEntry<NameAndId> {
	private final int level;
	private final boolean bypassesPlayerLimit;

	public ServerOpListEntry(NameAndId nameAndId, int i, boolean bl) {
		super(nameAndId);
		this.level = i;
		this.bypassesPlayerLimit = bl;
	}

	public ServerOpListEntry(JsonObject jsonObject) {
		super(NameAndId.fromJson(jsonObject));
		this.level = jsonObject.has("level") ? jsonObject.get("level").getAsInt() : 0;
		this.bypassesPlayerLimit = jsonObject.has("bypassesPlayerLimit") && jsonObject.get("bypassesPlayerLimit").getAsBoolean();
	}

	public int getLevel() {
		return this.level;
	}

	public boolean getBypassesPlayerLimit() {
		return this.bypassesPlayerLimit;
	}

	@Override
	protected void serialize(JsonObject jsonObject) {
		if (this.getUser() != null) {
			this.getUser().appendTo(jsonObject);
			jsonObject.addProperty("level", this.level);
			jsonObject.addProperty("bypassesPlayerLimit", this.bypassesPlayerLimit);
		}
	}
}
