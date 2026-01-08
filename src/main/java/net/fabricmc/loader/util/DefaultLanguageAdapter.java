package net.fabricmc.loader.util;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

/**
 * @deprecated Use {@link LanguageAdapter} instead.
 */
@Deprecated
public final class DefaultLanguageAdapter implements LanguageAdapter {
	public static final DefaultLanguageAdapter INSTANCE = new DefaultLanguageAdapter();

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		return LanguageAdapter.getDefault().create(mod, value, type);
	}
}
