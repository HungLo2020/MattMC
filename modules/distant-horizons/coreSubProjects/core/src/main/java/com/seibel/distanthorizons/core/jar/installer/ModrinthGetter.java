/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
