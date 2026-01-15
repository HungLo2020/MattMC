package net.distanthorizons.core.wrapperInterfaces.config;

import net.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

public interface ILangWrapper extends IBindable
{
	
	boolean langExists(String str);
	
	String getLang(String str);
	
}
