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
