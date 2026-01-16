package com.seibel.distanthorizons.core.jar.installer;

import com.electronwill.nightconfig.core.Config;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.DhLogger;

import java.net.URL;
import java.util.*;

/**
 * Modrinth integration has been disabled.
 * This class is kept as a stub to maintain compatibility.
 *
 * @author coolGi
 */
public class ModrinthGetter
{
private static final DhLogger LOGGER = new DhLoggerBuilder().build();

public static final String projectID = "distanthorizons";
/** Functions should only be accessed once this is true */
public static boolean initted = false;
public static ArrayList<Config> projectRelease = new ArrayList<>();
public static Map<String, Config> idToJson = new HashMap<>();

public static List<String> releaseID = new ArrayList<>();
public static List<String> mcVersions = new ArrayList<>();
public static Map<String, String> releaseNames = new HashMap<>();
public static Map<String, List<String>> mcVerToReleaseID = new HashMap<>();
public static Map<String, URL> downloadUrl = new HashMap<>();
public static Map<String, String> changeLogs = new HashMap<>();


public static boolean init()
{
LOGGER.info("Modrinth integration disabled - no network calls will be made");
initted = false;
return false;
}

public static String getLatestIDForVersion(String mcVersion)
{
return "";
}

public static String getLatestNameForVersion(String mcVersion)
{
return "";
}

public static String getLatestShaForVersion(String mcVersion)
{
return "";
}

public static URL getLatestDownloadForVersion(String mcVersion)
{
return null;
}
}
