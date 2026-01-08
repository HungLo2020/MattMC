package com.seibel.distanthorizons.core.jar.installer;

import com.electronwill.nightconfig.core.Config;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.DhLogger;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;

/**
 * Web download functionality has been disabled.
 * This class is kept as a stub to maintain compatibility.
 *
 * @author coolGi
 */
public class WebDownloader
{
private static final DhLogger LOGGER = new DhLoggerBuilder().build();


public static boolean netIsAvailable()
{
LOGGER.info("Network connectivity check disabled");
return false;
}

public static void downloadAsFile(URL url, File file) throws Exception
{
throw new Exception("Web download functionality has been disabled");
}

public static String downloadAsFile(String url, File file) throws Exception
{
throw new Exception("Web download functionality has been disabled");
}

public static String downloadAsString(URL url) throws Exception
{
throw new Exception("Web download functionality has been disabled");
}

public static Config parseWebJson(String url) throws Exception
{
throw new Exception("Web download functionality has been disabled");
}

public static ArrayList<Config> parseWebJsonList(String url) throws Exception
{
throw new Exception("Web download functionality has been disabled");
}

public static String calculateChecksum(File file) throws Exception
{
throw new Exception("Checksum calculation disabled");
}

public static String formatMarkdownToHtml(String markdown, int maxWidth)
{
LOGGER.warn("Markdown formatting disabled - returning empty string");
return "";
}
}
