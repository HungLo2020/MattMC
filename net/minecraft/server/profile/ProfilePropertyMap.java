package net.minecraft.server.profile;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * A multimap of profile properties, allowing multiple values for the same property name.
 * Replaces Mojang's PropertyMap from authlib.
 */
public final class ProfilePropertyMap {
	private final ListMultimap<String, ProfileProperty> properties;

	public ProfilePropertyMap() {
		this.properties = ArrayListMultimap.create();
	}

	public ProfilePropertyMap(ListMultimap<String, ProfileProperty> properties) {
		this.properties = ArrayListMultimap.create(properties);
	}

	/**
	 * Adds a property to this map.
	 */
	public void put(String name, ProfileProperty property) {
		Objects.requireNonNull(name, "Property name cannot be null");
		Objects.requireNonNull(property, "Property cannot be null");
		this.properties.put(name, property);
	}

	/**
	 * Gets all properties with the given name.
	 */
	public Collection<ProfileProperty> get(String name) {
		return Collections.unmodifiableCollection(this.properties.get(name));
	}

	/**
	 * Gets all properties in this map.
	 */
	public Collection<ProfileProperty> values() {
		return Collections.unmodifiableCollection(this.properties.values());
	}

	/**
	 * Removes all properties with the given name.
	 */
	public void removeAll(String name) {
		this.properties.removeAll(name);
	}

	/**
	 * Checks if this map contains any properties with the given name.
	 */
	public boolean containsKey(String name) {
		return this.properties.containsKey(name);
	}

	/**
	 * Clears all properties from this map.
	 */
	public void clear() {
		this.properties.clear();
	}

	/**
	 * Returns the number of properties in this map.
	 */
	public int size() {
		return this.properties.size();
	}

	/**
	 * Checks if this map is empty.
	 */
	public boolean isEmpty() {
		return this.properties.isEmpty();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ProfilePropertyMap other)) {
			return false;
		}
		return this.properties.equals(other.properties);
	}

	@Override
	public int hashCode() {
		return this.properties.hashCode();
	}

	@Override
	public String toString() {
		return "ProfilePropertyMap[" + this.properties.size() + " properties]";
	}
}
