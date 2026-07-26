package net.vulkanic.gui;

public enum AbsorptionHeartVariant {
	CONTAINER("container"),
	ABSORBING("absorbing"),
	WITHERED("withered");

	private final String id;

	AbsorptionHeartVariant(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}
}
