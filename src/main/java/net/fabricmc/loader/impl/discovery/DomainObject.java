package net.fabricmc.loader.impl.discovery;

import net.fabricmc.loader.api.Version;

interface DomainObject {
	String getId();

	interface Mod extends DomainObject {
		Version getVersion();
	}
}
