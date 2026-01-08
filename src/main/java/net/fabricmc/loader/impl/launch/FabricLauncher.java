package net.fabricmc.loader.impl.launch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.Manifest;

import net.fabricmc.api.EnvType;

public interface FabricLauncher {
	MappingConfiguration getMappingConfiguration();

	void addToClassPath(Path path, String... allowedPrefixes);
	void setAllowedPrefixes(Path path, String... prefixes);
	void setValidParentClassPath(Collection<Path> paths);

	EnvType getEnvironmentType();

	boolean isClassLoaded(String name);

	/**
	 * Load a class into the game's class loader even if its bytes are only available from the parent class loader.
	 */
	Class<?> loadIntoTarget(String name) throws ClassNotFoundException;

	InputStream getResourceAsStream(String name);

	ClassLoader getTargetClassLoader();

	/**
	 * Gets the byte array for a particular class.
	 *
	 * @param name The name of the class to retrieve
	 * @param runTransformers Whether to run all transformers <i>except mixin</i> on the class
	 */
	byte[] getClassByteArray(String name, boolean runTransformers) throws IOException;

	Manifest getManifest(Path originPath);

	boolean isDevelopment();

	String getEntrypoint();

	List<Path> getClassPath();
}
