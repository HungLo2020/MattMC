package net.fabricmc.loader.launch.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.util.UrlUtil;

/**
 * @deprecated Internal API, do not use
 */
@Deprecated
public class FabricLauncherBase implements FabricLauncher {
	private final net.fabricmc.loader.impl.launch.FabricLauncher parent = net.fabricmc.loader.impl.launch.FabricLauncherBase.getLauncher();

	public static Class<?> getClass(String className) throws ClassNotFoundException {
		return Class.forName(className, true, getLauncher().getTargetClassLoader());
	}

	public static FabricLauncher getLauncher() {
		return new FabricLauncherBase();
	}

	@Override
	public void propose(URL url) {
		parent.addToClassPath(UrlUtil.asPath(url));
	}

	@Override
	public EnvType getEnvironmentType() {
		return FabricLoader.getInstance().getEnvironmentType();
	}

	@Override
	public boolean isClassLoaded(String name) {
		return parent.isClassLoaded(name);
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		return parent.getResourceAsStream(name);
	}

	@Override
	public ClassLoader getTargetClassLoader() {
		return parent.getTargetClassLoader();
	}

	@Override
	public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
		return parent.getClassByteArray(name, runTransformers);
	}

	@Override
	public boolean isDevelopment() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	@Override
	public Collection<URL> getLoadTimeDependencies() {
		List<URL> ret = new ArrayList<>();

		for (Path path : parent.getClassPath()) {
			try {
				ret.add(UrlUtil.asUrl(path));
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}

		return ret;
	}
}
