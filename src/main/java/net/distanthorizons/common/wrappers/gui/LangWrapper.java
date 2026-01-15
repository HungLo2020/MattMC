package net.distanthorizons.common.wrappers.gui;

import net.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;
import net.minecraft.client.resources.language.I18n;

public class LangWrapper implements ILangWrapper
{
	public static final LangWrapper INSTANCE = new LangWrapper();
	@Override
	public boolean langExists(String str)
	{
		return I18n.exists(str);
	}
	
	@Override
	public String getLang(String str)
	{
		return I18n.get(str);
	}
	
}
