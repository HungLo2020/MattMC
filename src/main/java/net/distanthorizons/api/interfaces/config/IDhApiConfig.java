package net.distanthorizons.api.interfaces.config;

import net.distanthorizons.api.interfaces.config.both.IDhApiWorldGenerationConfig;
import net.distanthorizons.api.interfaces.config.client.*;
import net.distanthorizons.api.interfaces.config.client.IDhApiDebuggingConfig;
import net.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig;
import net.distanthorizons.api.interfaces.config.client.IDhApiMultiThreadingConfig;
import net.distanthorizons.api.interfaces.config.client.IDhApiMultiplayerConfig;

/**
 * This interfaces holds all config groups
 * the API has access to for easy access.
 *
 * @author James Seibel
 * @version 2023-6-14
 * @since API 1.0.0
 */
public interface IDhApiConfig
{
	IDhApiGraphicsConfig graphics();
	IDhApiWorldGenerationConfig worldGenerator();
	IDhApiMultiplayerConfig multiplayer();
	IDhApiMultiThreadingConfig multiThreading();
	// note: DON'T add the Auto Updater to this API. We only want the user's to have the ability to control when things are downloaded to their machines.
	//IDhApiLoggingConfig logging(); // TODO implement
	IDhApiDebuggingConfig debugging();
	
}
