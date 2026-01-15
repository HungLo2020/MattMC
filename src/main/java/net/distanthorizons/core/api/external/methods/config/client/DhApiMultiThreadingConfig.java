package net.distanthorizons.core.api.external.methods.config.client;

import net.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import net.distanthorizons.api.interfaces.config.client.IDhApiMultiThreadingConfig;
import net.distanthorizons.core.config.api.DhApiConfigValue;
import net.distanthorizons.core.config.Config;

public class DhApiMultiThreadingConfig implements IDhApiMultiThreadingConfig
{
	public static DhApiMultiThreadingConfig INSTANCE = new DhApiMultiThreadingConfig();
	
	private DhApiMultiThreadingConfig() { }
	
	
	
	@Override
	public IDhApiConfigValue<Integer> threadCount()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Common.MultiThreading.numberOfThreads); }
	
	@Override
	public IDhApiConfigValue<Double> threadRuntimeRatio()
	{ return new DhApiConfigValue<Double, Double>(Config.Common.MultiThreading.threadRunTimeRatio); }
	
	
	
}
