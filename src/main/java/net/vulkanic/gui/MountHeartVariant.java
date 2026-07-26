package net.vulkanic.gui;

public enum MountHeartVariant {
	VEHICLE("vehicle");

	private final String id;

	MountHeartVariant(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}
}
