package net.distanthorizons.core.jar.updater;

import net.distanthorizons.core.logging.DhLogger;
import net.distanthorizons.core.logging.DhLoggerBuilder;

import java.io.File;

/**
 * Self-updater functionality has been disabled.
 * This class is kept as a stub to maintain compatibility.
 *
 * @author coolGi
 */
public class SelfUpdater
{
private static final DhLogger LOGGER = new DhLoggerBuilder().build();

/** As we cannot delete(or replace) the jar while the mod is running, we just have this to delete it once the game closes */
public static boolean deleteOldJarOnJvmShutdown = false;

public static File newFileLocation;


/**
 * Should be called on the game starting.
 * Auto-update functionality disabled - always returns false.
 *
 * @return Whether it should open the update ui (always false)
 */
public static boolean onStart()
{
LOGGER.info("Auto-updater has been disabled - no update checks will be performed");
return false;
}

/**
 * Called on game close.
 * Auto-update functionality disabled - no-op.
 */
public static void onClose()
{
// Auto-update disabled - nothing to do
}

/**
 * Update the mod.
 * Auto-update functionality disabled - no-op.
 */
public static void updateMod()
{
LOGGER.warn("Auto-updater has been disabled - cannot update mod");
}
}
