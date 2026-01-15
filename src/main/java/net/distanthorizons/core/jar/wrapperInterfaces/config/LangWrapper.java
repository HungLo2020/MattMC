package net.distanthorizons.core.jar.wrapperInterfaces.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.json.JsonFormat;
import net.distanthorizons.core.jar.JarUtils;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.wrapperInterfaces.config.ILangWrapper;
import net.distanthorizons.core.logging.DhLogger;

import java.util.Locale;

public class LangWrapper implements ILangWrapper
{
	public static final LangWrapper INSTANCE = new LangWrapper();
	
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	private static final Config JSON_OBJECT = Config.inMemory();
	
	
	
	public static void init()
	{
		try
		{
			// FIXME: Is there something in the config that the parser cant read?
			JsonFormat.fancyInstance().createParser().parse(
					JarUtils.convertInputStreamToString(JarUtils.accessFile("assets/lod/lang/" + Locale.getDefault().toString().toLowerCase() + ".json")),
					JSON_OBJECT, ParsingMode.REPLACE
			);
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to read lang file, error: ["+e.getMessage()+"]", e);
		}
	}
	
	@Override
	public boolean langExists(String str) { return JSON_OBJECT.get(str) != null; }
	
	@Override
	public String getLang(String str)
	{
		if (JSON_OBJECT.get(str) != null)
		{
			return (String) JSON_OBJECT.get(str);
		}
		else
		{
			return str;
		}
	}
	
	
	
}
