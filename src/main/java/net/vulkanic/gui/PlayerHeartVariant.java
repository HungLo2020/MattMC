package net.vulkanic.gui;

public enum PlayerHeartVariant {
	CONTAINER("container"),
	NORMAL("normal"),
	POISONED("poisoned"),
	WITHERED("withered"),
	FROZEN("frozen");

	private final String id;

	PlayerHeartVariant(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}
}
