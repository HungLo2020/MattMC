package net.fabricmc.loader.impl.metadata;

@SuppressWarnings("deprecation")
public interface EntrypointMetadata extends net.fabricmc.loader.metadata.EntrypointMetadata {
	@Override
	String getAdapter();
	@Override
	String getValue();
}
