package net.fabricmc.loader.impl.metadata;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.metadata.ContactInformation;

public class ContactInformationImpl implements ContactInformation {
	private final Map<String, String> map;

	public ContactInformationImpl(Map<String, String> map) {
		this.map = Collections.unmodifiableMap(map);
	}

	@Override
	public Optional<String> get(String key) {
		return Optional.ofNullable(map.get(key));
	}

	@Override
	public Map<String, String> asMap() {
		return map;
	}
}
