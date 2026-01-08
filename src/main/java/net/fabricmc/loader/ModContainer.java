package net.fabricmc.loader;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import net.fabricmc.loader.impl.util.UrlUtil;
import net.fabricmc.loader.metadata.LoaderModMetadata;

/**
 * @deprecated Use {@link net.fabricmc.loader.api.ModContainer} instead
 */
@Deprecated
public abstract class ModContainer implements net.fabricmc.loader.api.ModContainer {
	public abstract LoaderModMetadata getInfo();
	protected abstract List<Path> getCodeSourcePaths();

	public URL getOriginUrl() {
		try {
			return UrlUtil.asUrl(getCodeSourcePaths().get(0));
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}
}
