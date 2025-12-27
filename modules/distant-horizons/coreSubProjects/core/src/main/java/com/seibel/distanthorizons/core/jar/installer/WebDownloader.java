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
}
	{
		StringBuilder stringBuilder = new StringBuilder();
//        URL url = new URL(urlS);
		
		URLConnection urlConnection = url.openConnection();
		urlConnection.setConnectTimeout(1000);
		urlConnection.setReadTimeout(1000);
		BufferedReader bReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
		
		String line;
		while ((line = bReader.readLine()) != null)
		{
			stringBuilder.append(line);
		}
		
		return (stringBuilder.toString());
	}
	
	public static String formatMarkdownToHtml(String md, int width)
	{
		String str = String.format("<html><div style=\"width:%dpx;\">%s</div></html>", width, md);
		return new MarkdownFormatter.HTMLFormat().convertTo(str);
	}
	
	
	
	public static Config parseWebJson(String url) throws Exception
	{
		return parseWebJson(new URL(url));
	}
	public static Config parseWebJson(URL url) throws Exception
	{
		return JsonFormat.minimalInstance().createParser().parse(WebDownloader.downloadAsString(url));
	}
	
	public static ArrayList<Config> parseWebJsonList(String url) throws Exception
	{
		return parseWebJsonList(new URL(url));
	}
	public static ArrayList<Config> parseWebJsonList(URL url) throws Exception
	{
		// Is there a better way of doing this?
		return JsonFormat.minimalInstance().createParser().parse("{\"E\":" + WebDownloader.downloadAsString(url) + "}").get("E");
	}
	
	
	
	// Taken from https://mkyong.com/java/how-to-generate-a-file-checksum-value-in-java/ but added some comments
	/**
	 * @param filepath Path to the file
	 * @param md The checksum. Can be gotten by "MessageDigest.getInstance("SHA-256")" and can replace string with something like SHA, MD2, MD5, SHA-256, SHA-384...
	 * @return Returns the checksum using the previous md
	 */
	private static String checksum(String filepath, MessageDigest md) throws IOException
	{
		// file hashing with DigestInputStream
		try (DigestInputStream dis = new DigestInputStream(new FileInputStream(filepath), md))
		{
			while (dis.read() != -1) ; //empty loop to clear the data
			md = dis.getMessageDigest();
		}
		
		// bytes to hex
		StringBuilder result = new StringBuilder();
		for (byte b : md.digest())
		{
			result.append(String.format("%02x", b));
		}
		return result.toString();
		
	}
	
}
