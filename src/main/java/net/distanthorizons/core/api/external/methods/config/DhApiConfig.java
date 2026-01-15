package net.distanthorizons.core.api.external.methods.config;

import net.distanthorizons.api.interfaces.config.IDhApiConfig;
import net.distanthorizons.api.interfaces.config.both.IDhApiWorldGenerationConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.*;
import com.seibel.distanthorizons.core.api.external.methods.config.client.*;
import net.distanthorizons.api.interfaces.config.client.IDhApiDebuggingConfig;
import net.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig;
import net.distanthorizons.api.interfaces.config.client.IDhApiMultiThreadingConfig;
import net.distanthorizons.api.interfaces.config.client.IDhApiMultiplayerConfig;
import net.distanthorizons.core.api.external.methods.config.client.DhApiDebuggingConfig;
import net.distanthorizons.core.api.external.methods.config.client.DhApiGraphicsConfig;
import net.distanthorizons.core.api.external.methods.config.client.DhApiMultiThreadingConfig;
import net.distanthorizons.core.api.external.methods.config.client.DhApiMultiplayerConfig;
import net.distanthorizons.core.api.external.methods.config.common.DhApiWorldGenerationConfig;

public class DhApiConfig implements IDhApiConfig
{
	public static final DhApiConfig INSTANCE = new DhApiConfig();
	
	private DhApiConfig() { }
	
	
	
	@Override
	public IDhApiGraphicsConfig graphics() { return DhApiGraphicsConfig.INSTANCE; }
	@Override
	public IDhApiWorldGenerationConfig worldGenerator() { return DhApiWorldGenerationConfig.INSTANCE; }
	@Override
	public IDhApiMultiplayerConfig multiplayer() { return DhApiMultiplayerConfig.INSTANCE; }
	@Override
	public IDhApiMultiThreadingConfig multiThreading() { return DhApiMultiThreadingConfig.INSTANCE; }
	@Override
	public IDhApiDebuggingConfig debugging() { return DhApiDebuggingConfig.INSTANCE; }
	
}
