package net.fabricmc.loader.launch.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

import net.fabricmc.api.EnvType;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public interface FabricLauncher {
	void propose(URL url);
	EnvType getEnvironmentType();
	boolean isClassLoaded(String name);
	InputStream getResourceAsStream(String name);
	ClassLoader getTargetClassLoader();
	byte[] getClassByteArray(String name, boolean runTransformers) throws IOException;
	boolean isDevelopment();
	Collection<URL> getLoadTimeDependencies();
}
